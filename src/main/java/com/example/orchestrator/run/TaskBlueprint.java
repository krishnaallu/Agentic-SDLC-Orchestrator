package com.example.orchestrator.run;

import java.util.List;

public record TaskBlueprint(
        String nodeKey,
        String title,
        String description,
        List<String> dependencies
) {
}