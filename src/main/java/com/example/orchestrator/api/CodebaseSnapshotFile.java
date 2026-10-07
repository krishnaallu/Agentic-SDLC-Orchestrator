package com.example.orchestrator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CodebaseSnapshotFile(
        @NotBlank @Size(max = 240) String path,
        @NotNull @Size(max = 8000) String content
) {
}
