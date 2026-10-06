package com.example.orchestrator.run;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TemplateTaskExecutionAgent implements TaskExecutionAgent {
    private static final String ROOT = "generated/url-shortener/";

    @Override
    public List<ArtifactDraft> execute(UUID runId, String requirement, String codebaseContext, String clarificationNotes,
                                       RunScenario scenario, TaskBlueprint task) {
        return switch (task.nodeKey()) {
            case "requirement-analysis" -> List.of(markdown(
                    "requirements.md", "# Requirement analysis\n\n## Request\n\n" + requirement
                            + "\n\n## Acceptance criteria\n\n- Create short links for valid HTTP and HTTPS destinations.\n"
                            + "- Redirect from a short code to its destination.\n- Track aggregate click counts.\n"
                            + "- Respect optional link expiration.\n- Validate API input and return meaningful errors.\n"
                            + "\nScenario: " + scenario + ".\n"));
                        case "codebase-analysis" -> List.of(markdown("codebase-analysis.md", "# Brownfield analysis\n\n"
                            + "## Request\n\n" + requirement + "\n\n## Supplied codebase context\n\n"
                            + (codebaseContext == null || codebaseContext.isBlank()
                            ? "No codebase context was supplied. This scenario is a template only; provide a reviewed module/API/data-flow summary for meaningful brownfield reasoning."
                            : codebaseContext)
                            + "\n\n## Review focus\n\nMap link creation, redirect lookup, persistence, and existing tests before changing behavior. Preserve existing redirect contracts and avoid raw visitor IP collection.\n"));
                        case "ambiguity-analysis" -> List.of(markdown("clarification.md", "# Clarification required\n\n"
                            + "## Request\n\n" + requirement + "\n\n## Questions to resolve\n\n"
                            + "1. Should an omitted expiry mean that a link never expires?\n"
                            + "2. Which timezone should be used for user-supplied expiry timestamps?\n"
                            + "3. Should past or immediate expiry values be rejected?\n"
                            + "4. Can expired short codes ever be reused, or must they remain reserved?\n\n"
                            + "No implementation should proceed until these policy decisions are answered.\n\n## Human response\n\n"
                            + (clarificationNotes == null || clarificationNotes.isBlank()
                                ? "Awaiting human clarification."
                                : clarificationNotes)
                            + "\n"));
            case "architecture" -> List.of(markdown("architecture.md", "# Proposed architecture\n\n"
                    + "Spring Boot REST API with a JPA-backed link entity and repository. Link creation validates "
                    + "HTTP(S) URLs and assigns a random short code. Redirect requests increment an aggregate counter. "
                    + "PostgreSQL is the production database; generated tests use an isolated database profile.\n\n"
                    + "Management authentication, abuse controls, and production deployment settings require review "
                    + "before exposure. Raw visitor IP addresses are not collected.\n"));
            case "implementation" -> implementationArtifacts();
            case "tests" -> List.of(javaSource("src/test/java/com/example/urlshortener/LinkServiceTest.java", serviceTest()));
            case "documentation" -> List.of(markdown("README.md", "# URL Shortener\n\n"
                    + "Spring Boot API for creating expiring short links, redirecting by code, and viewing aggregate "
                    + "click counts. Configure PostgreSQL with `DATABASE_URL`, `DATABASE_USERNAME`, and "
                    + "`DATABASE_PASSWORD`. Run tests with `mvn test`.\n\n"
                    + "Management endpoints are unauthenticated in this generated prototype and must not be exposed "
                    + "publicly until authentication, rate limits, and abuse controls are added.\n"));
            case "release-readiness" -> List.of(markdown("release-review.md", "# Release readiness\n\n"
                    + "Generated artifacts are proposals only. Review API behavior, URL validation, short-code "
                    + "collision handling, expiry semantics, analytics privacy, authentication, migrations, and test "
                    + "results before accepting or applying them. No generated command has been executed.\n"));
            default -> throw new IllegalArgumentException("No execution adapter exists for task " + task.nodeKey());
        };
    }

    private List<ArtifactDraft> implementationArtifacts() {
        return List.of(
                xml("pom.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                          <modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.0.8</version><relativePath/></parent>
                          <groupId>com.example</groupId><artifactId>url-shortener</artifactId><version>0.0.1-SNAPSHOT</version>
                          <properties><java.version>21</java.version></properties>
                          <dependencies>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                          </dependencies>
                          <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
                        </project>
                        """),
                properties("src/main/resources/application.properties", """
                        spring.application.name=url-shortener
                        spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/urlshortener}
                        spring.datasource.username=${DATABASE_USERNAME:urlshortener}
                        spring.datasource.password=${DATABASE_PASSWORD:urlshortener}
                        spring.jpa.hibernate.ddl-auto=validate
                        spring.jpa.open-in-view=false
                        server.error.include-message=never
                        """),
                sql("src/main/resources/db/migration/V1__create_short_link.sql", """
                        CREATE TABLE short_link (
                            code VARCHAR(16) PRIMARY KEY,
                            target_url VARCHAR(2048) NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL,
                            expires_at TIMESTAMPTZ,
                            click_count BIGINT NOT NULL DEFAULT 0
                        );
                        """),
                javaSource("src/main/java/com/example/urlshortener/UrlShortenerApplication.java", """
                        package com.example.urlshortener;

                        import org.springframework.boot.SpringApplication;
                        import org.springframework.boot.autoconfigure.SpringBootApplication;

                        @SpringBootApplication
                        public class UrlShortenerApplication {
                            public static void main(String[] args) {
                                SpringApplication.run(UrlShortenerApplication.class, args);
                            }
                        }
                        """),
                javaSource("src/main/java/com/example/urlshortener/Link.java", """
                        package com.example.urlshortener;

                        import jakarta.persistence.Column;
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.Table;
                        import java.time.Instant;

                        @Entity
                        @Table(name = "short_link")
                        public class Link {
                            @Id
                            @Column(length = 16)
                            private String code;
                            @Column(nullable = false, length = 2048)
                            private String targetUrl;
                            @Column(nullable = false)
                            private Instant createdAt;
                            private Instant expiresAt;
                            @Column(nullable = false)
                            private long clickCount;

                            protected Link() {}

                            public Link(String code, String targetUrl, Instant createdAt, Instant expiresAt) {
                                this.code = code;
                                this.targetUrl = targetUrl;
                                this.createdAt = createdAt;
                                this.expiresAt = expiresAt;
                                this.clickCount = 0;
                            }

                            public void recordClick() { clickCount++; }
                            public String getCode() { return code; }
                            public String getTargetUrl() { return targetUrl; }
                            public Instant getCreatedAt() { return createdAt; }
                            public Instant getExpiresAt() { return expiresAt; }
                            public long getClickCount() { return clickCount; }
                        }
                        """),
                javaSource("src/main/java/com/example/urlshortener/LinkRepository.java", """
                        package com.example.urlshortener;

                        import org.springframework.data.jpa.repository.JpaRepository;
                        import jakarta.persistence.LockModeType;
                        import org.springframework.data.jpa.repository.Lock;
                        import org.springframework.data.jpa.repository.Query;
                        import org.springframework.data.repository.query.Param;
                        import java.util.Optional;

                        public interface LinkRepository extends JpaRepository<Link, String> {
                            @Lock(LockModeType.PESSIMISTIC_WRITE)
                            @Query("select link from Link link where link.code = :code")
                            Optional<Link> findByCodeForUpdate(@Param("code") String code);
                        }
                        """),
                javaSource("src/main/java/com/example/urlshortener/LinkService.java", """
                        package com.example.urlshortener;

                        import java.net.URI;
                        import java.time.Instant;
                        import java.util.Optional;
                        import java.util.UUID;
                        import org.springframework.http.HttpStatus;
                        import org.springframework.stereotype.Service;
                        import org.springframework.transaction.annotation.Transactional;
                        import org.springframework.web.server.ResponseStatusException;

                        @Service
                        public class LinkService {
                            private final LinkRepository repository;

                            public LinkService(LinkRepository repository) { this.repository = repository; }

                            @Transactional
                            public Link create(String targetUrl, Instant expiresAt) {
                                URI target;
                                try {
                                    target = URI.create(targetUrl);
                                } catch (IllegalArgumentException exception) {
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid destination URL");
                                }
                                if (!("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))
                                        || target.getHost() == null) {
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only absolute HTTP(S) URLs are allowed");
                                }
                                if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry must be in the future");
                                }
                                for (int attempt = 0; attempt < 5; attempt++) {
                                    String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                                    if (!repository.existsById(code)) {
                                        return repository.save(new Link(code, target.toString(), Instant.now(), expiresAt));
                                    }
                                }
                                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate a short code");
                            }

                            @Transactional
                            public Optional<Link> resolve(String code) {
                                return repository.findByCodeForUpdate(code).filter(link -> {
                                    if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(Instant.now())) {
                                        repository.delete(link);
                                        return false;
                                    }
                                    link.recordClick();
                                    return true;
                                });
                            }

                            @Transactional(readOnly = true)
                            public Link analytics(String code) {
                                return repository.findById(code)
                                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
                            }
                        }
                        """),
                javaSource("src/main/java/com/example/urlshortener/LinkController.java", """
                        package com.example.urlshortener;

                        import jakarta.validation.Valid;
                        import jakarta.validation.constraints.NotBlank;
                        import jakarta.validation.constraints.Size;
                        import java.time.Instant;
                        import org.springframework.http.HttpHeaders;
                        import org.springframework.http.HttpStatus;
                        import org.springframework.http.ResponseEntity;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        import org.springframework.web.server.ResponseStatusException;

                        @RestController
                        @RequestMapping("/api/v1/links")
                        public class LinkController {
                            private final LinkService service;
                            public LinkController(LinkService service) { this.service = service; }

                            @PostMapping
                            public LinkResponse create(@Valid @RequestBody CreateLinkRequest request) {
                                Link link = service.create(request.url(), request.expiresAt());
                                return new LinkResponse(link.getCode(), link.getTargetUrl(), link.getCreatedAt(), link.getExpiresAt());
                            }

                            @GetMapping("/{code}")
                            public ResponseEntity<Void> redirect(@PathVariable String code) {
                                Link link = service.resolve(code).orElseThrow(() ->
                                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
                                return ResponseEntity.status(HttpStatus.FOUND)
                                        .header(HttpHeaders.LOCATION, link.getTargetUrl()).build();
                            }

                            @GetMapping("/{code}/analytics")
                            public AnalyticsResponse analytics(@PathVariable String code) {
                                Link link = service.analytics(code);
                                return new AnalyticsResponse(link.getCode(), link.getClickCount(), link.getCreatedAt(), link.getExpiresAt());
                            }

                            public record CreateLinkRequest(@NotBlank @Size(max = 2048) String url, Instant expiresAt) {}
                            public record LinkResponse(String code, String url, Instant createdAt, Instant expiresAt) {}
                            public record AnalyticsResponse(String code, long clicks, Instant createdAt, Instant expiresAt) {}
                        }
                        """));
    }

    private String serviceTest() {
        return """
                package com.example.urlshortener;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.assertThatThrownBy;
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.when;
                import java.time.Instant;
                import java.util.Optional;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoExtension;
                import org.springframework.web.server.ResponseStatusException;

                @ExtendWith(MockitoExtension.class)
                class LinkServiceTest {
                    @Mock LinkRepository repository;
                    LinkService service;

                    @BeforeEach void setUp() { service = new LinkService(repository); }

                    @Test void createsLinkForHttpDestination() {
                        when(repository.existsById(any())).thenReturn(false);
                        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                        Link link = service.create("https://example.com/path", null);
                        assertThat(link.getCode()).hasSize(16);
                        assertThat(link.getTargetUrl()).isEqualTo("https://example.com/path");
                    }

                    @Test void rejectsNonHttpDestination() {
                        assertThatThrownBy(() -> service.create("javascript:alert(1)", null))
                                .isInstanceOf(ResponseStatusException.class);
                    }

                    @Test void incrementsClicksWhenResolvingActiveLink() {
                        Link link = new Link("abc123", "https://example.com", Instant.now(), null);
                        when(repository.findByCodeForUpdate("abc123")).thenReturn(Optional.of(link));
                        assertThat(service.resolve("abc123")).contains(link);
                        assertThat(link.getClickCount()).isEqualTo(1);
                    }
                }
                """;
    }

    private ArtifactDraft javaSource(String path, String content) {
        return new ArtifactDraft(ROOT + path, "text/x-java-source", content);
    }

    private ArtifactDraft markdown(String path, String content) {
        return new ArtifactDraft(ROOT + path, "text/markdown", content);
    }

    private ArtifactDraft properties(String path, String content) {
        return new ArtifactDraft(ROOT + path, "text/plain", content);
    }

    private ArtifactDraft xml(String path, String content) {
        return new ArtifactDraft(ROOT + path, "application/xml", content);
    }

    private ArtifactDraft sql(String path, String content) {
        return new ArtifactDraft(ROOT + path, "text/plain", content);
    }
}