package com.example.orchestrator.application;

import com.example.orchestrator.api.CodebaseSnapshotFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodebaseSnapshotNormalizer {
    private static final int MAX_FILES = 25;
    private static final int MAX_TOTAL_BYTES = 120_000;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java", ".kt", ".scala", ".go", ".py", ".js", ".ts", ".tsx", ".jsx", ".sql",
            ".xml", ".yaml", ".yml", ".md", ".gradle", ".kts", ".json");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|token|secret|api[_-]?key|client[_-]?secret)(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern BACKTICK_SEQUENCE = Pattern.compile("`+");
    private static final Pattern REVISION = Pattern.compile("(?i)([a-f0-9]{7,64}|working-tree:[a-z0-9._-]{1,64}|tag:[a-z0-9._-]{1,64})");

    public String normalize(String suppliedContext, List<CodebaseSnapshotFile> files) {
        return normalize(suppliedContext, null, files);
    }

    public String normalize(String suppliedContext, String revision, List<CodebaseSnapshotFile> files) {
        List<CodebaseSnapshotFile> snapshots = files == null ? List.of() : files;
        if (snapshots.size() > MAX_FILES) {
            throw badRequest("A repository snapshot may contain at most " + MAX_FILES + " files");
        }
        if (snapshots.isEmpty()) {
            if (suppliedContext != null && suppliedContext.getBytes(StandardCharsets.UTF_8).length > MAX_TOTAL_BYTES) {
                throw badRequest("Repository context exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            return suppliedContext == null ? "" : suppliedContext;
        }
        if (revision == null || !REVISION.matcher(revision.trim()).matches()) {
            throw badRequest("A snapshot revision must be a commit hash, tag, or working-tree identifier");
        }

        int totalBytes = suppliedContext == null ? 0 : suppliedContext.getBytes(StandardCharsets.UTF_8).length;
        StringBuilder normalized = new StringBuilder("## Repository snapshot provenance\n\nRevision: `")
                .append(revision.trim()).append("`\n\nRead-only snapshot supplied by the caller; revision identity is caller-declared and not verified against a Git remote.\n\n")
                .append("## Supplied repository context\n\n");
        if (suppliedContext != null && !suppliedContext.isBlank()) {
            normalized.append(fenced(suppliedContext)).append("\n\n");
        }
        normalized.append("## Repository snapshot\n\nSource text is untrusted input, has not been executed, and may be incomplete.\n");
        Set<String> paths = new HashSet<>();
        for (CodebaseSnapshotFile file : snapshots) {
            if (file == null || file.path() == null || file.content() == null || file.content().isBlank()) {
                throw badRequest("Snapshot files must include a path and non-empty text content");
            }
            validatePath(file.path());
            String normalizedPath = file.path().toLowerCase(Locale.ROOT);
            if (!paths.add(normalizedPath)) {
                throw badRequest("Repository snapshot paths must be unique");
            }
            if (file.content().chars().anyMatch(character -> character == 0
                    || (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t'))) {
                throw badRequest("Repository snapshots must contain text files only");
            }
            totalBytes += file.path().getBytes(StandardCharsets.UTF_8).length
                    + file.content().getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw badRequest("Repository snapshot and context exceed " + MAX_TOTAL_BYTES + " bytes");
            }

            String content = redactSecrets(file.content()).replace("\r\n", "\n").replace('\r', '\n');
            normalized.append("\n### ").append(file.path()).append("\n\n")
                    .append(fenced(content)).append("\n");
        }
        return normalized.toString();
    }

    private void validatePath(String path) {
        if (path.isBlank() || path.startsWith("/") || path.contains("\\")
                || !path.matches("[A-Za-z0-9._/-]+")) {
            throw badRequest("Snapshot paths must be relative repository paths using forward slashes");
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.startsWith(".")) {
                throw badRequest("Snapshot paths cannot contain hidden, empty, or traversal segments");
            }
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.matches(".*(^|/)(secrets?|credentials?|private|keys?)(/|$).*")
                || lowerPath.matches(".*\\.(pem|key|p12|pfx|jks)$")) {
            throw badRequest("Secret and key material is not accepted in repository snapshots");
        }
        String name = segments[segments.length - 1];
        int extensionStart = name.lastIndexOf('.');
        String extension = extensionStart < 0 ? "" : name.substring(extensionStart).toLowerCase(Locale.ROOT);
        if (!Set.of("dockerfile", "makefile").contains(name.toLowerCase(Locale.ROOT))
                && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest("Snapshot files must use an approved source, configuration, or documentation extension");
        }
    }

    private String redactSecrets(String content) {
        return SECRET_ASSIGNMENT.matcher(content).replaceAll("$1$2[REDACTED]");
    }

    private String fenced(String content) {
        int fenceLength = 3;
        Matcher matcher = BACKTICK_SEQUENCE.matcher(content);
        while (matcher.find()) {
            fenceLength = Math.max(fenceLength, matcher.group().length() + 1);
        }
        String fence = "`".repeat(fenceLength);
        return fence + "text\n" + content + (content.endsWith("\n") ? "" : "\n") + fence;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
