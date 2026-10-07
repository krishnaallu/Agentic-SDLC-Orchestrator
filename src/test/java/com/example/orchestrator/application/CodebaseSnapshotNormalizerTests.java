package com.example.orchestrator.application;

import com.example.orchestrator.api.CodebaseSnapshotFile;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodebaseSnapshotNormalizerTests {
    private final CodebaseSnapshotNormalizer normalizer = new CodebaseSnapshotNormalizer();

    @Test
    void rendersSourceAndRedactsSecretAssignments() {
        String snapshot = normalizer.normalize("Existing service uses Spring MVC", "a1b2c3d", List.of(
                new CodebaseSnapshotFile("src/main/java/LinkService.java",
                        "class LinkService { String token = \\\"sample-secret\\\"; }")));

        assertThat(snapshot).contains("src/main/java/LinkService.java", "class LinkService")
                .contains("untrusted input", "[REDACTED]", "a1b2c3d", "caller-declared")
                .doesNotContain("sample-secret");
    }

    @Test
    void rejectsTraversalAndSecretMaterialPaths() {
        assertThatThrownBy(() -> normalizer.normalize(null, "a1b2c3d", List.of(
                new CodebaseSnapshotFile("src/main/../secrets.yml", "token=value"))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> normalizer.normalize(null, "a1b2c3d", List.of(
                new CodebaseSnapshotFile("config/production.pem", "private-key"))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsSnapshotsOverTheAggregateSizeLimit() {
        List<CodebaseSnapshotFile> files = java.util.stream.IntStream.range(0, 16)
                .mapToObj(index -> new CodebaseSnapshotFile("src/File" + index + ".java", "x".repeat(8000)))
                .toList();

                assertThatThrownBy(() -> normalizer.normalize(null, "a1b2c3d", files))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceed 120000 bytes");
    }

        @Test
        void requiresARevisionForSnapshots() {
                assertThatThrownBy(() -> normalizer.normalize(null, null, List.of(
                                new CodebaseSnapshotFile("src/main/java/LinkService.java", "class LinkService {}"))))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("snapshot revision");
        }
}
