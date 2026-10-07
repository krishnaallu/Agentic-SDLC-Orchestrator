package com.example.orchestrator.workflow;

import com.example.orchestrator.application.ArtifactDraft;
import com.example.orchestrator.persistence.OrchestrationArtifact;
import com.example.orchestrator.persistence.OrchestrationArtifactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class IsolatedProjectValidator {
    private static final String ROOT = "generated/url-shortener/";
    private static final String DEFAULT_IMAGE = "maven:3.9.9-eclipse-temurin-21";
    private final OrchestrationArtifactRepository artifactRepository;
    private final boolean enabled;
    private final boolean required;
    private final String image;
    private final Path mavenRepository;
    private final Duration timeout;

    public IsolatedProjectValidator(OrchestrationArtifactRepository artifactRepository,
                                    @Value("${orchestrator.validation.enabled:true}") boolean enabled,
                                    @Value("${orchestrator.validation.required:true}") boolean required,
                                    @Value("${orchestrator.validation.image:maven:3.9.9-eclipse-temurin-21}") String image,
                                    @Value("${orchestrator.validation.maven-repository:${user.home}/.m2/repository}") String mavenRepository,
                                    @Value("${orchestrator.validation.timeout:PT8M}") Duration timeout) {
        this.artifactRepository = artifactRepository;
        this.enabled = enabled;
        this.required = required;
        this.image = image;
        this.mavenRepository = Path.of(mavenRepository).toAbsolutePath().normalize();
        this.timeout = timeout;
    }

    public ProjectExecutionResult validate(UUID runId, List<ArtifactDraft> currentDrafts) {
        if (!enabled) {
            return ProjectExecutionResult.skipped("Docker project validation disabled by configuration");
        }
        if (!Files.isDirectory(mavenRepository)) {
            return ProjectExecutionResult.skipped("Local Maven artifact cache is missing; offline sandbox cannot resolve dependencies");
        }

        Path workspace = null;
        String containerName = "url-shortener-validation-" + UUID.randomUUID();
        BoundedLog output = new BoundedLog(48000);
        try {
            workspace = Files.createTempDirectory("url-shortener-validation-");
            materialize(workspace, runId, currentDrafts);
            List<String> command = dockerCommand(workspace, containerName);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Thread outputReader = new Thread(() -> captureOutput(process.getInputStream(), output), "sandbox-output-reader");
            outputReader.setDaemon(true);
            outputReader.start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                outputReader.join(1000);
                return new ProjectExecutionResult(true, false, 124,
                        "Isolated Maven validation timed out after " + timeout + "\n" + output.snapshot());
            }
            outputReader.join(1000);
            int exitCode = process.exitValue();
            return new ProjectExecutionResult(true, exitCode == 0, exitCode,
                    (exitCode == 0 ? "Isolated Maven test run passed" : "Isolated Maven test run failed")
                    + " (exit " + exitCode + ")\n" + output.snapshot());
        } catch (IOException exception) {
            return new ProjectExecutionResult(true, false, 127,
                    "Could not start isolated Docker validation: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProjectExecutionResult(true, false, 130, "Isolated validation interrupted");
        } finally {
            removeContainer(containerName);
            deleteRecursively(workspace);
        }
    }

    private void removeContainer(String containerName) {
        try {
            Process cleanup = new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true).start();
            if (!cleanup.waitFor(10, TimeUnit.SECONDS)) {
                cleanup.destroyForcibly();
            }
        } catch (IOException ignored) {
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean required() {
        return required;
    }

    private void materialize(Path workspace, UUID runId, List<ArtifactDraft> currentDrafts) throws IOException {
        List<ArtifactContent> artifacts = new ArrayList<>(artifactRepository.findByRun_IdOrderByPathAsc(runId).stream()
                .map(artifact -> new ArtifactContent(artifact.getPath(), artifact.getContent())).toList());
        if (currentDrafts != null) {
            currentDrafts.stream().map(draft -> new ArtifactContent(draft.path(), draft.content())).forEach(artifacts::add);
        }
        for (ArtifactContent artifact : artifacts) {
            if (artifact.path() == null || !artifact.path().startsWith(ROOT)
                    || artifact.path().contains("..") || artifact.path().contains("\\")) {
                throw new IllegalArgumentException("Artifact failed sandbox path validation");
            }
            Path destination = workspace.resolve(artifact.path().substring(ROOT.length())).normalize();
            if (!destination.startsWith(workspace) || artifact.content() == null) {
                throw new IllegalArgumentException("Artifact escaped isolated workspace");
            }
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, artifact.content(), StandardCharsets.UTF_8);
        }
        if (!Files.isRegularFile(workspace.resolve("pom.xml"))) {
            throw new IllegalStateException("Generated project is missing pom.xml");
        }
    }

    private List<String> dockerCommand(Path workspace, String containerName) {
        String sourceMount = "type=bind,source=" + workspace.toAbsolutePath() + ",target=/source,readonly";
        String cacheMount = "type=bind,source=" + mavenRepository + ",target=/m2/repository,readonly";
        return List.of("docker", "run", "--rm", "--name", containerName, "--network=none", "--cpus=1",
            "--memory=2g", "--memory-swap=2g", "--pids-limit=128", "--ulimit", "nofile=1024:1024",
                "--read-only", "--security-opt=no-new-privileges", "--cap-drop=ALL", "--user=1000:1000",
            "--tmpfs=/tmp:rw,exec,nosuid,nodev,size=256m,uid=1000,gid=1000,mode=1777",
            "--tmpfs=/workspace:rw,exec,nosuid,nodev,size=1g,uid=1000,gid=1000,mode=1777",
            "--mount", sourceMount, "--mount", cacheMount,
            "--workdir=/workspace", "--env", "HOME=/tmp", "--env",
            "MAVEN_OPTS=-Djava.io.tmpdir=/tmp -Djansi.tmpdir=/tmp -Dstyle.color=never",
            "--entrypoint=/bin/sh", image, "-c",
                "cp -R /source/. /workspace/ && mvn -o -B -ntp -Dmaven.repo.local=/m2/repository test; "
                    + "result=$?; if [ $result -ne 0 ]; then "
                    + "for report in /workspace/target/surefire-reports/*.txt; do "
                    + "[ -f \"$report\" ] && tail -n 180 \"$report\"; "
                    + "done; grep -h -E 'Caused by:|UnsatisfiedDependencyException|BeanCreationException|NoSuchBeanDefinitionException|Could not resolve placeholder|Error creating bean' "
                    + "/workspace/target/surefire-reports/*.txt | cut -c1-1200 | tail -n 40; "
                    + "fi; exit $result");
    }

    private void captureOutput(InputStream input, BoundedLog output) {
        byte[] buffer = new byte[1024];
        try (input) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.append(buffer, count);
            }
        } catch (IOException exception) {
            output.append(("\n[output capture ended: " + exception.getClass().getSimpleName() + "]")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record ArtifactContent(String path, String content) {
    }

    private static final class BoundedLog {
        private final int maxBytes;
        private final StringBuilder content = new StringBuilder();

        private BoundedLog(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        private synchronized void append(byte[] bytes, int length) {
            content.append(new String(bytes, 0, length, StandardCharsets.UTF_8));
            if (content.length() > maxBytes) {
                content.delete(0, content.length() - maxBytes);
            }
        }

        private synchronized void append(byte[] bytes) {
            append(bytes, bytes.length);
        }

        private synchronized String snapshot() {
            return content.toString();
        }
    }
}