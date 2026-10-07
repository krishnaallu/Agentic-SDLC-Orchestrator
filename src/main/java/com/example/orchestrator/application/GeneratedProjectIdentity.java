package com.example.orchestrator.application;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GeneratedProjectIdentity(String slug) {
    private static final Pattern URL_SHORTENER = Pattern.compile(
            "(?i)\\b(url\\s*[- ]?\\s*short(?:e)?ner|short\\s+links?|short\\s+codes?|shorten(?:ing)?\\s+(?:urls?|links?))\\b");
        private static final Pattern URL_SHORTENER_SNAPSHOT = Pattern.compile(
            "(?i)(\\blinkservice\\b|\\bshort[- ]links?\\b|\\bshort[- ]codes?\\b|generated/url-shortener)");
    private static final Pattern NAMED_PROJECT = Pattern.compile(
            "(?i)\\b(?:build|create|develop|implement|design)\\s+(?:an?\\s+)?"
                    + "([a-z0-9][a-z0-9 -]{0,48}?)\\s+"
                    + "(?:service|system|application|platform|api)\\b");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    public static GeneratedProjectIdentity fromRequirement(String requirement) {
        return fromRequirement(requirement, null, null);
    }

    public static GeneratedProjectIdentity fromRequirement(String requirement,
                                                            com.example.orchestrator.domain.RunScenario scenario,
                                                            String codebaseContext) {
        String text = requirement == null ? "" : requirement;
        if (supportsUrlShortenerTemplate(text, codebaseContext, scenario)) {
            return new GeneratedProjectIdentity("url-shortener");
        }

        Matcher namedProject = NAMED_PROJECT.matcher(text);
        String candidate = namedProject.find() ? namedProject.group(1) : text;
        Matcher tokens = TOKEN.matcher(candidate.toLowerCase(Locale.ROOT));
        StringBuilder slug = new StringBuilder();
        while (tokens.find() && slug.length() < 48) {
            String token = tokens.group();
            if (isFiller(token)) {
                continue;
            }
            int remaining = 48 - slug.length();
            String accepted = token.substring(0, Math.min(token.length(), remaining));
            if (!slug.isEmpty()) {
                slug.append('-');
            }
            slug.append(accepted);
        }
        return new GeneratedProjectIdentity(slug.isEmpty() ? "generated-service" : slug.toString());
    }

    public String root() {
        return "generated/" + slug + "/";
    }

    public static boolean isUrlShortenerRequirement(String requirement) {
        return requirement != null && URL_SHORTENER.matcher(requirement).find();
    }

    public static boolean supportsUrlShortenerTemplate(String requirement, String codebaseContext,
                                                        com.example.orchestrator.domain.RunScenario scenario) {
        if (isUrlShortenerRequirement(requirement)) {
            return true;
        }
        return scenario == com.example.orchestrator.domain.RunScenario.BROWNFIELD
                && codebaseContext != null && URL_SHORTENER_SNAPSHOT.matcher(codebaseContext).find();
    }

    private static boolean isFiller(String token) {
        return switch (token) {
            case "a", "an", "the", "build", "create", "develop", "implement", "design", "new",
                    "service", "system", "application", "platform", "api", "with", "for", "and" -> true;
            default -> false;
        };
    }
}
