package com.example.orchestrator.agent;

import com.example.orchestrator.application.ArtifactDraft;
import com.example.orchestrator.application.TaskExecutionAgent;
import com.example.orchestrator.domain.RunScenario;
import com.example.orchestrator.domain.TaskBlueprint;

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
                        case "codebase-analysis" -> brownfieldArtifacts(requirement, codebaseContext);
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
                    + "Spring Boot URL-shortener service with PostgreSQL/Flyway persistence, Redis-backed rate limits, "
                    + "OIDC JWT scopes for management APIs, and public redirects guarded by a DNS/IP destination policy.\n\n"
                    + "## Package tree\n\n```text\n"
                    + "com.example.urlshortener\n"
                    + "  UrlShortenerApplication.java\n"
                    + "  api/LinkController.java                  REST contracts and HTTP mapping\n"
                    + "  service/LinkService.java                link creation, expiry, redirects, analytics\n"
                    + "  domain/Link.java                        link entity/domain state\n"
                    + "  persistence/LinkRepository.java         PostgreSQL access and concurrency operations\n"
                    + "  security/DestinationPolicy.java         normalized URL and address-range policy\n"
                    + "  security/RateLimitFilter.java           Redis quota and request-size controls\n"
                    + "  config/SecurityConfiguration.java       JWT, scope, headers, and CORS policy\n"
                    + "src/main/resources/db/migration/         versioned PostgreSQL schema\n"
                    + "src/test/java/com/example/urlshortener/  service, security, and HTTP behavior tests\n"
                    + "```\n\n"
                    + "Dependency direction is api -> service -> domain; service -> persistence/security ports; "
                    + "configuration wires adapters. Domain does not depend on Spring Web or persistence. Management "
                    + "operations require configured JWT scopes. Raw visitor IP addresses are not persisted.\n"));
            case "implementation" -> implementationArtifacts();
                case "tests" -> List.of(
                    javaSource("src/test/java/com/example/urlshortener/LinkServiceTest.java", serviceTest()),
                    javaSource("src/test/java/com/example/urlshortener/DestinationPolicyTest.java", destinationPolicyTest()),
                    javaSource("src/test/java/com/example/urlshortener/LinkControllerSecurityTest.java", controllerSecurityTest()),
                    javaSource("src/test/java/com/example/urlshortener/LocalProfileSecurityTest.java", localProfileSecurityTest()));
                    case "integration-tests" -> List.of(markdown("integration-test-plan.md", "# Integration test plan\n\n"
                        + "Use a real PostgreSQL Testcontainer and HTTP tests against the Spring application. Verify creation, public redirect, "
                        + "scope-protected analytics, malformed and unsafe destination rejection, expiration, collision retry, and concurrent "
                        + "click increments. These tests remain proposed until the isolated validation runner executes them.\n"));
                    case "security-review" -> List.of(markdown("security-review.md", "# Security review\n\n"
                        + "Review JWT issuer validation and management scopes, public-only redirect route, destination DNS/IP policy, Redis abuse "
                        + "limits, request-size caps, CORS allowlist, HSTS, secret configuration, and privacy. Static checks do not replace dynamic "
                        + "scanning or an independent security review.\n"));
                case "documentation" -> List.of(
                    markdown("README.md", "# URL Shortener\n\n"
                    + "Spring Boot API for creating expiring short links, redirecting by code, and viewing aggregate "
                    + "click counts. Configure PostgreSQL with `DATABASE_URL`, `DATABASE_USERNAME`, and "
                    + "`DATABASE_PASSWORD`, Redis with `REDIS_HOST` / `REDIS_PORT`, JWT issuer with `AUTH_ISSUER_URI`, "
                    + "and `RATE_LIMIT_HASH_KEY` (at least 32 random bytes). Set `ALLOWED_ORIGINS` explicitly. "
                    + "Start PostgreSQL and Redis with `docker compose up -d`; run tests with `mvn test`.\n\n"
                    + "The default `local` profile disables authentication and binds to 127.0.0.1 for local development. "
                    + "For authenticated use, set `SPRING_PROFILES_ACTIVE=secure` and `AUTH_ISSUER_URI`; link creation "
                    + "requires JWT scope `links:write`, analytics requires `links:read`, and redirects remain public. "
                    + "Compose explicitly uses the `secure` profile because loopback binding is not reachable through "
                    + "published container ports. Set `RATE_LIMIT_HASH_KEY` in either profile. Do not trust X-Forwarded-For "
                    + "unless a trusted reverse proxy strips and sets it. Use HTTPS at the edge and restrict allowed CORS origins.\n"),
                    new ArtifactDraft(ROOT + "src/main/resources/api/openapi.yaml", "text/yaml", openApiSchema()));
                    case "deployment-readiness" -> List.of(markdown("deployment-readiness.md", "# Deployment readiness\n\n"
                        + "Run behind an HTTPS reverse proxy. Set PostgreSQL, Redis, JWT issuer, CORS origins, rate-limit HMAC secret, and quota "
                        + "from the secret manager. Apply Flyway migrations before traffic; configure readiness/liveness probes, backups, monitoring, "
                        + "and rollback procedure. Never trust forwarded headers except from a configured trusted proxy.\n"));
            case "release-readiness" -> List.of(markdown("release-review.md", "# Release readiness\n\n"
                    + "Generated artifacts are proposals only. Review API behavior, URL validation, short-code "
                    + "collision handling, expiry semantics, analytics privacy, authentication, migrations, and test "
                    + "results before accepting or applying them. No generated command has been executed.\n"));
                    case "final-validation" -> List.of(markdown("final-engineering-summary.md", "# Final engineering summary\n\n"
                        + "Record requirement traceability, implementation and test artifact inventory, validation evidence, assumptions, risks, "
                        + "limitations, approval lineage, and remaining production actions. Do not claim production readiness unless generated tests "
                        + "have passed in the isolated validation environment.\n"));
            default -> throw new IllegalArgumentException("No execution adapter exists for task " + task.nodeKey());
        };
    }

        private List<ArtifactDraft> brownfieldArtifacts(String requirement, String codebaseContext) {
        String context = codebaseContext == null ? "" : codebaseContext;
        List<String> snapshotPaths = context.lines()
            .filter(line -> line.startsWith("### "))
            .map(line -> line.substring(4).trim())
            .filter(path -> !path.isBlank())
            .distinct()
            .toList();
        String impactedFiles = snapshotPaths.isEmpty()
            ? "No file-level snapshot paths were found; impact cannot be established."
            : snapshotPaths.stream().map(path -> "- `" + path + "` (candidate; confirm through code/test review)")
                .collect(java.util.stream.Collectors.joining("\n"));
        String analysis = "# Brownfield analysis\n\n## Request\n\n" + requirement
            + "\n\n## Read-only snapshot and revision\n\n" + context
            + "\n\n## Candidate impacted files\n\n" + impactedFiles
            + "\n\nImpact candidates are derived from the supplied snapshot inventory, not from an unrestricted repository scan.\n";
        String checks = "# Compatibility and regression checks\n\n"
            + "Snapshot paths are candidates, not a proof of impact. Before accepting a proposal, verify:\n\n"
            + "- Existing HTTP methods, route paths, response codes, and redirect headers remain compatible.\n"
            + "- Existing persisted link rows remain readable after migrations; migration rollback is documented.\n"
            + "- Expiration and code-reuse behavior match the current contract.\n"
            + "- Redirect and click-count behavior passes existing tests and concurrent request tests.\n"
            + "- Authorization changes preserve intended public redirect behavior and protect management APIs.\n"
            + "- Regression tests are added beside the existing tests identified in the snapshot.\n\n"
            + "## Candidate files\n\n" + impactedFiles + "\n";
        return List.of(markdown("codebase-analysis.md", analysis),
            markdown("brownfield-compatibility-checks.md", checks));
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
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
                            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security-test</artifactId><scope>test</scope></dependency>
                          </dependencies>
                          <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
                        </project>
                        """),
                properties("src/main/resources/application.properties", """
                        spring.application.name=url-shortener
                    spring.profiles.default=local
                        spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/urlshortener}
                        spring.datasource.username=${DATABASE_USERNAME:urlshortener}
                        spring.datasource.password=${DATABASE_PASSWORD:urlshortener}
                        spring.jpa.hibernate.ddl-auto=validate
                        spring.jpa.open-in-view=false
                        server.error.include-message=never
                        management.endpoints.web.exposure.include=health,info,prometheus
                        management.endpoint.health.probes.enabled=true
                        spring.data.redis.host=${REDIS_HOST:localhost}
                        spring.data.redis.port=${REDIS_PORT:6379}
                        app.security.allowed-origins=${ALLOWED_ORIGINS:}
                        app.security.rate-limit-hash-key=${RATE_LIMIT_HASH_KEY}
                        app.rate-limit.requests-per-minute=${RATE_LIMIT_PER_MINUTE:60}
                        server.max-http-request-header-size=8KB
                        server.tomcat.max-http-form-post-size=8KB
                        """),
                    properties("src/main/resources/application-local.properties", """
                        app.security.enabled=false
                        server.address=127.0.0.1
                        """),
                    properties("src/main/resources/application-secure.properties", """
                        app.security.enabled=true
                        spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_ISSUER_URI}
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
                                dockerfile("""
                                                FROM maven:3.9.9-eclipse-temurin-21 AS build
                                                WORKDIR /workspace
                                                COPY pom.xml .
                                                COPY src ./src
                                                RUN mvn -B -ntp -DskipTests package

                                                FROM eclipse-temurin:21-jre-alpine
                                                WORKDIR /app
                                                RUN addgroup -S app && adduser -S -G app app
                                                COPY --from=build /workspace/target/url-shortener-0.0.1-SNAPSHOT.jar /app/app.jar
                                                USER app
                                                EXPOSE 8080
                                                ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
                                                """),
                                text("compose.yaml", """
                                                services:
                                                    app:
                                                        build: .
                                                        ports: ["8080:8080"]
                                                        environment:
                                                            SPRING_PROFILES_ACTIVE: secure
                                                            DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-urlshortener}
                                                            DATABASE_USERNAME: ${POSTGRES_USER:-urlshortener}
                                                            DATABASE_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD}
                                                            REDIS_HOST: redis
                                                            AUTH_ISSUER_URI: ${AUTH_ISSUER_URI:?Set AUTH_ISSUER_URI}
                                                            RATE_LIMIT_HASH_KEY: ${RATE_LIMIT_HASH_KEY:?Set a random 32-byte secret}
                                                            ALLOWED_ORIGINS: ${ALLOWED_ORIGINS:-}
                                                        depends_on:
                                                            postgres: {condition: service_healthy}
                                                            redis: {condition: service_healthy}
                                                        read_only: true
                                                        tmpfs: ["/tmp:rw,noexec,nosuid,size=64m"]
                                                        security_opt: ["no-new-privileges:true"]
                                                        cap_drop: ["ALL"]
                                                        healthcheck:
                                                            test: ["CMD", "wget", "-q", "-O", "-", "http://localhost:8080/actuator/health/readiness"]
                                                            interval: 10s
                                                            timeout: 3s
                                                            retries: 10
                                                    postgres:
                                                        image: postgres:17-alpine
                                                        environment:
                                                            POSTGRES_DB: ${POSTGRES_DB:-urlshortener}
                                                            POSTGRES_USER: ${POSTGRES_USER:-urlshortener}
                                                            POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD}
                                                        volumes: ["postgres-data:/var/lib/postgresql/data"]
                                                        healthcheck:
                                                            test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
                                                            interval: 5s
                                                            timeout: 3s
                                                            retries: 10
                                                    redis:
                                                        image: redis:7-alpine
                                                        command: ["redis-server", "--save", "", "--appendonly", "yes"]
                                                        volumes: ["redis-data:/data"]
                                                        healthcheck:
                                                            test: ["CMD", "redis-cli", "ping"]
                                                            interval: 5s
                                                            timeout: 3s
                                                            retries: 10
                                                volumes:
                                                    postgres-data:
                                                    redis-data:
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
                javaSource("src/main/java/com/example/urlshortener/domain/Link.java", """
                    package com.example.urlshortener.domain;

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
                javaSource("src/main/java/com/example/urlshortener/persistence/LinkRepository.java", """
                    package com.example.urlshortener.persistence;

                    import com.example.urlshortener.domain.Link;
                        import org.springframework.data.jpa.repository.JpaRepository;
                        import jakarta.persistence.LockModeType;
                        import org.springframework.data.jpa.repository.Lock;
                        import org.springframework.data.jpa.repository.Modifying;
                        import org.springframework.data.jpa.repository.Query;
                        import org.springframework.data.repository.query.Param;
                        import java.time.Instant;
                        import java.util.Optional;

                        public interface LinkRepository extends JpaRepository<Link, String> {
                            @Lock(LockModeType.PESSIMISTIC_WRITE)
                            @Query("select link from Link link where link.code = :code")
                            Optional<Link> findByCodeForUpdate(@Param("code") String code);

                                @Modifying
                                @Query(value = "INSERT INTO short_link (code, target_url, created_at, expires_at, click_count) "
                                    + "VALUES (:code, :targetUrl, :createdAt, :expiresAt, 0) "
                                    + "ON CONFLICT (code) DO NOTHING", nativeQuery = true)
                                int insertIfAbsent(@Param("code") String code, @Param("targetUrl") String targetUrl,
                                    @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);
                        }
                        """),
                    javaSource("src/main/java/com/example/urlshortener/security/DestinationPolicy.java", destinationPolicySource()),
                    javaSource("src/main/java/com/example/urlshortener/service/LinkService.java", """
                        package com.example.urlshortener.service;

                        import com.example.urlshortener.domain.Link;
                        import com.example.urlshortener.persistence.LinkRepository;
                        import com.example.urlshortener.security.DestinationPolicy;
                        import java.time.Instant;
                        import java.util.Optional;
                        import java.util.UUID;
                        import java.net.URI;
                        import org.springframework.http.HttpStatus;
                        import org.springframework.stereotype.Service;
                        import org.springframework.transaction.annotation.Transactional;
                        import org.springframework.web.server.ResponseStatusException;

                        @Service
                        public class LinkService {
                            private final LinkRepository repository;
                            private final DestinationPolicy destinationPolicy;

                            public LinkService(LinkRepository repository, DestinationPolicy destinationPolicy) {
                                this.repository = repository;
                                this.destinationPolicy = destinationPolicy;
                            }

                            @Transactional
                            public Link create(String targetUrl, Instant expiresAt) {
                                URI target = destinationPolicy.validate(targetUrl);
                                if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry must be in the future");
                                }
                                for (int attempt = 0; attempt < 5; attempt++) {
                                    String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                                    Instant createdAt = Instant.now();
                                    if (repository.insertIfAbsent(code, target.toString(), createdAt, expiresAt) == 1) {
                                        return repository.findById(code).orElseThrow(() ->
                                                new IllegalStateException("Inserted short link could not be reloaded"));
                                    }
                                }
                                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate a short code");
                            }

                            @Transactional
                            public Optional<Link> resolve(String code) {
                                return repository.findByCodeForUpdate(code).filter(link -> {
                                    if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(Instant.now())) {
                                        return false;
                                    }
                                    destinationPolicy.validate(link.getTargetUrl());
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
                javaSource("src/main/java/com/example/urlshortener/config/SecurityConfiguration.java", """
                    package com.example.urlshortener.config;

                        import org.springframework.beans.factory.annotation.Value;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;
                        import org.springframework.http.HttpMethod;
                        import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                        import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
                        import org.springframework.security.config.http.SessionCreationPolicy;
                        import org.springframework.web.cors.CorsConfiguration;
                        import org.springframework.web.cors.CorsConfigurationSource;
                        import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
                        import org.springframework.security.web.SecurityFilterChain;
                        import java.util.Arrays;
                        import java.util.List;

                        @Configuration
                        @EnableMethodSecurity
                        public class SecurityConfiguration {
                            @Bean
                                SecurityFilterChain securityFilterChain(HttpSecurity http,
                                    CorsConfigurationSource corsConfigurationSource,
                                    @Value("${app.security.enabled:true}") boolean securityEnabled) throws Exception {
                                http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                                    .csrf(csrf -> csrf.disable())
                                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                    .headers(headers -> headers.frameOptions(frame -> frame.deny())
                                            .contentTypeOptions(contentType -> {})
                                            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                                    .authorizeHttpRequests(authorize -> {
                                        if (!securityEnabled) {
                                            authorize.anyRequest().permitAll();
                                            return;
                                        }
                                        authorize.requestMatchers("/actuator/health/**").permitAll()
                                            .requestMatchers(HttpMethod.POST, "/api/v1/links").hasAnyAuthority("SCOPE_links:write", "SCOPE_links:admin")
                                            .requestMatchers(HttpMethod.GET, "/api/v1/links/*/analytics").hasAnyAuthority("SCOPE_links:read", "SCOPE_links:admin")
                                            .requestMatchers(HttpMethod.GET, "/api/v1/links/*").permitAll()
                                            .anyRequest().denyAll();
                                    });
                                if (securityEnabled) {
                                    http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));
                                }
                                return http.build();
                            }

                            @Bean
                            CorsConfigurationSource corsConfigurationSource(
                                    @Value("${app.security.allowed-origins:}") String allowedOrigins) {
                                CorsConfiguration cors = new CorsConfiguration();
                                cors.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                                        .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
                                cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
                                cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
                                cors.setAllowCredentials(false);
                                cors.setMaxAge(1800L);
                                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                                source.registerCorsConfiguration("/api/**", cors);
                                return source;
                            }
                        }
                        """),
                                javaSource("src/main/java/com/example/urlshortener/security/RateLimitFilter.java", rateLimitFilterSource()),
                javaSource("src/main/java/com/example/urlshortener/api/LinkController.java", """
                    package com.example.urlshortener.api;

                    import com.example.urlshortener.domain.Link;
                    import com.example.urlshortener.service.LinkService;
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
                package com.example.urlshortener.service;

                import com.example.urlshortener.domain.Link;
                import com.example.urlshortener.persistence.LinkRepository;
                import com.example.urlshortener.security.DestinationPolicy;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.assertThatThrownBy;
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.ArgumentMatchers.nullable;
                import static org.mockito.ArgumentMatchers.anyString;
                import static org.mockito.Mockito.when;
                import static org.mockito.Mockito.times;
                import static org.mockito.Mockito.verify;
                import java.time.Instant;
                import java.util.Optional;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoExtension;
                import java.net.URI;
                import org.springframework.web.server.ResponseStatusException;

                @ExtendWith(MockitoExtension.class)
                class LinkServiceTest {
                    @Mock LinkRepository repository;
                    @Mock DestinationPolicy destinationPolicy;
                    LinkService service;

                    @BeforeEach void setUp() {
                        service = new LinkService(repository, destinationPolicy);
                    }

                    private void allowPublicDestination() {
                        when(destinationPolicy.validate(any())).thenAnswer(invocation -> URI.create(invocation.getArgument(0)));
                    }

                    @Test void createsLinkForHttpDestination() {
                        allowPublicDestination();
                        when(repository.insertIfAbsent(anyString(), anyString(), any(Instant.class), nullable(Instant.class))).thenReturn(1);
                        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.of(
                                new Link(invocation.getArgument(0), "https://example.com/path", Instant.now(), null)));
                        Link link = service.create("https://example.com/path", null);
                        assertThat(link.getCode()).hasSize(16);
                        assertThat(link.getTargetUrl()).isEqualTo("https://example.com/path");
                    }

                    @Test void rejectsNonHttpDestination() {
                        when(destinationPolicy.validate("javascript:alert(1)"))
                                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST));
                        assertThatThrownBy(() -> service.create("javascript:alert(1)", null))
                                .isInstanceOf(ResponseStatusException.class);
                    }

                    @Test void incrementsClicksWhenResolvingActiveLink() {
                        Link link = new Link("abc123", "https://example.com", Instant.now(), null);
                        when(repository.findByCodeForUpdate("abc123")).thenReturn(Optional.of(link));
                        assertThat(service.resolve("abc123")).contains(link);
                        assertThat(link.getClickCount()).isEqualTo(1);
                    }

                    @Test void retriesWhenTheDatabaseReportsACodeCollision() {
                        allowPublicDestination();
                        when(repository.insertIfAbsent(anyString(), anyString(), any(Instant.class), nullable(Instant.class)))
                                .thenReturn(0, 1);
                        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.of(
                                new Link(invocation.getArgument(0), "https://example.com/path", Instant.now(), null)));

                        Link link = service.create("https://example.com/path", null);

                        assertThat(link.getCode()).hasSize(16);
                        verify(repository, times(2)).insertIfAbsent(anyString(), anyString(), any(Instant.class), nullable(Instant.class));
                    }

                    @Test void expiredLinksReturnNotFoundAndRemainReserved() {
                        Link expired = new Link("expired1", "https://example.com", Instant.now().minusSeconds(120),
                                Instant.now().minusSeconds(60));
                        when(repository.findByCodeForUpdate("expired1")).thenReturn(Optional.of(expired));

                        assertThat(service.resolve("expired1")).isEmpty();
                        verify(repository, org.mockito.Mockito.never()).delete(any());
                    }

                    @Test void missingLinksDoNotIncrementAnalytics() {
                        when(repository.findByCodeForUpdate("missing")).thenReturn(Optional.empty());
                        assertThat(service.resolve("missing")).isEmpty();
                        verify(repository, org.mockito.Mockito.never()).delete(any());
                    }
                }
                """;
    }

    private String destinationPolicyTest() {
            return """
                package com.example.urlshortener.security;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.assertThatThrownBy;
                import java.net.InetAddress;
                import org.junit.jupiter.api.Test;
                import org.springframework.web.server.ResponseStatusException;

                class DestinationPolicyTest {
                    @Test void rejectsLoopbackAndPrivateIpv4Targets() {
                    assertThatThrownBy(() -> policy("127.0.0.1").validate("http://attacker.example/admin"))
                        .isInstanceOf(ResponseStatusException.class);
                    assertThatThrownBy(() -> policy("10.2.3.4").validate("http://attacker.example/"))
                        .isInstanceOf(ResponseStatusException.class);
                    assertThatThrownBy(() -> policy("192.168.1.10").validate("http://attacker.example/"))
                        .isInstanceOf(ResponseStatusException.class);
                    }

                    @Test void rejectsCloudMetadataAndNonHttpDestinations() {
                    assertThatThrownBy(() -> policy("169.254.169.254").validate("http://attacker.example/latest/meta-data/"))
                        .isInstanceOf(ResponseStatusException.class);
                    DestinationPolicy policy = new DestinationPolicy(host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});
                    assertThatThrownBy(() -> policy.validate("http://metadata.google.internal/"))
                        .isInstanceOf(ResponseStatusException.class);
                    assertThatThrownBy(() -> policy.validate("javascript:alert(1)"))
                        .isInstanceOf(ResponseStatusException.class);
                    }

                    @Test void rejectsCredentialsEmbeddedInDestination() throws Exception {
                    assertThatThrownBy(() -> policy("93.184.216.34").validate("https://user:password@example.com/"))
                        .isInstanceOf(ResponseStatusException.class);
                    }

                    @Test void acceptsPublicHttpAndHttpsWithDeterministicDns() throws Exception {
                        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
                        DestinationPolicy policy = new DestinationPolicy(host -> new InetAddress[]{publicAddress});
                        assertThat(policy.validate("https://example.test/a/../b").getPath()).isEqualTo("/b");
                    }

                    private DestinationPolicy policy(String address) throws Exception {
                        InetAddress resolved = InetAddress.getByName(address);
                        return new DestinationPolicy(host -> new InetAddress[]{resolved});
                    }
                }
                """;
    }

    private String controllerSecurityTest() {
        return """
                package com.example.urlshortener.api;

                import com.example.urlshortener.config.SecurityConfiguration;
                import com.example.urlshortener.domain.Link;
                import com.example.urlshortener.security.RateLimitFilter;
                import com.example.urlshortener.service.LinkService;
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.ArgumentMatchers.nullable;
                import static org.mockito.Mockito.when;
                import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
                import java.time.Instant;
                import java.util.Optional;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
                import org.springframework.context.annotation.Import;
                import org.springframework.http.HttpHeaders;
                import org.springframework.http.HttpStatus;
                import org.springframework.security.core.authority.SimpleGrantedAuthority;
                import org.springframework.security.oauth2.jwt.JwtDecoder;
                import org.springframework.test.context.bean.override.mockito.MockitoBean;
                import org.springframework.test.context.TestPropertySource;
                import org.springframework.test.web.servlet.MockMvc;

                @WebMvcTest(LinkController.class)
                @TestPropertySource(properties = {"app.security.enabled=true", "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test"})
                @Import(SecurityConfiguration.class)
                class LinkControllerSecurityTest {
                    @Autowired MockMvc mvc;
                    @MockitoBean LinkService service;
                    @MockitoBean JwtDecoder jwtDecoder;
                    @MockitoBean RateLimitFilter rateLimitFilter;

                    @Test void linkCreationRequiresWriteScope() throws Exception {
                        mvc.perform(post("/api/v1/links").contentType("application/json")
                            .content("{\\\"url\\\":\\\"https://example.com\\\"}"))
                                .andExpect(status().isUnauthorized());
                    }

                    @Test void writeScopeCanCreateLink() throws Exception {
                        when(service.create(any(), nullable(Instant.class))).thenReturn(
                                new Link("abc123", "https://example.com", Instant.now(), null));
                        mvc.perform(post("/api/v1/links").with(jwt().authorities(
                                        new SimpleGrantedAuthority("SCOPE_links:write")))
                            .contentType("application/json").content("{\\\"url\\\":\\\"https://example.com\\\"}"))
                                .andExpect(status().isOk());
                    }

                    @Test void redirectIsPublicButAnalyticsRequiresReadScope() throws Exception {
                        Link link = new Link("abc123", "https://example.com", Instant.now(), null);
                        when(service.resolve("abc123")).thenReturn(Optional.of(link));
                        when(service.analytics("abc123")).thenReturn(link);
                        var redirect = new LinkController(service).redirect("abc123");
                        org.assertj.core.api.Assertions.assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
                        org.assertj.core.api.Assertions.assertThat(redirect.getHeaders().getFirst(HttpHeaders.LOCATION))
                            .isEqualTo("https://example.com");
                        mvc.perform(get("/api/v1/links/abc123/analytics")).andExpect(status().isUnauthorized());
                        mvc.perform(get("/api/v1/links/abc123/analytics").with(jwt().authorities(
                                        new SimpleGrantedAuthority("SCOPE_links:read"))))
                                .andExpect(status().isOk());
                    }
                }
                """;
    }

    private String localProfileSecurityTest() {
        return """
                package com.example.urlshortener.api;

                import com.example.urlshortener.config.SecurityConfiguration;
                import com.example.urlshortener.domain.Link;
                import com.example.urlshortener.security.RateLimitFilter;
                import com.example.urlshortener.service.LinkService;
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.ArgumentMatchers.nullable;
                import static org.mockito.Mockito.when;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
                import java.time.Instant;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
                import org.springframework.context.annotation.Import;
                import org.springframework.http.MediaType;
                import org.springframework.test.context.ActiveProfiles;
                import org.springframework.test.context.bean.override.mockito.MockitoBean;
                import org.springframework.test.web.servlet.MockMvc;

                @ActiveProfiles("local")
                @WebMvcTest(LinkController.class)
                @Import(SecurityConfiguration.class)
                class LocalProfileSecurityTest {
                    @Autowired MockMvc mvc;
                    @MockitoBean LinkService service;
                    @MockitoBean RateLimitFilter rateLimitFilter;

                    @Test void localProfileAllowsLinkCreationWithoutJwt() throws Exception {
                        when(service.create(any(), nullable(Instant.class))).thenReturn(
                                new Link("abc123", "https://example.com", Instant.now(), null));
                        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                                .content("{\\\"url\\\":\\\"https://example.com\\\"}"))
                                .andExpect(status().isOk());
                    }
                }
                """;
    }

        private String openApiSchema() {
                return """
                                openapi: 3.1.0
                                info:
                                    title: URL Shortener API
                                    version: 1.0.0
                                servers:
                                    - url: /api/v1
                                security:
                                    - bearerAuth: []
                                paths:
                                    /links:
                                        post:
                                            summary: Create an expiring short link
                                            security:
                                                - bearerAuth: []
                                            x-required-scope: links:write
                                            requestBody:
                                                required: true
                                                content:
                                                    application/json:
                                                        schema:
                                                            $ref: '#/components/schemas/CreateLinkRequest'
                                            responses:
                                                '200':
                                                    description: Link created
                                                    content:
                                                        application/json:
                                                            schema:
                                                                $ref: '#/components/schemas/LinkResponse'
                                                '400': { description: Invalid or unsafe destination/expiry }
                                                '401': { description: Missing or invalid bearer token }
                                                '403': { description: Missing links:write scope }
                                                '429': { description: Rate limit exceeded }
                                    /links/{code}:
                                        get:
                                            summary: Redirect to a safe, active destination
                                            security: []
                                            parameters:
                                                - in: path
                                                    name: code
                                                    required: true
                                                    schema: { type: string, maxLength: 16 }
                                            responses:
                                                '302': { description: Redirect; Location header contains destination }
                                                '404': { description: Unknown, expired, or newly unsafe destination }
                                                '429': { description: Rate limit exceeded }
                                    /links/{code}/analytics:
                                        get:
                                            summary: Read aggregate clicks
                                            x-required-scope: links:read
                                            parameters:
                                                - in: path
                                                    name: code
                                                    required: true
                                                    schema: { type: string, maxLength: 16 }
                                            responses:
                                                '200':
                                                    description: Aggregate analytics
                                                    content:
                                                        application/json:
                                                            schema:
                                                                $ref: '#/components/schemas/AnalyticsResponse'
                                                '401': { description: Missing or invalid bearer token }
                                                '403': { description: Missing links:read scope }
                                                '404': { description: Link not found }
                                components:
                                    securitySchemes:
                                        bearerAuth:
                                            type: http
                                            scheme: bearer
                                            bearerFormat: JWT
                                    schemas:
                                        CreateLinkRequest:
                                            type: object
                                            required: [url]
                                            properties:
                                                url: { type: string, format: uri, maxLength: 2048, description: Absolute public HTTP(S) URL only }
                                                expiresAt: { type: string, format: date-time, nullable: true }
                                        LinkResponse:
                                            type: object
                                            properties:
                                                code: { type: string }
                                                url: { type: string, format: uri }
                                                createdAt: { type: string, format: date-time }
                                                expiresAt: { type: string, format: date-time, nullable: true }
                                        AnalyticsResponse:
                                            type: object
                                            properties:
                                                code: { type: string }
                                                clicks: { type: integer, format: int64 }
                                                createdAt: { type: string, format: date-time }
                                                expiresAt: { type: string, format: date-time, nullable: true }
                                """;
        }

    private String destinationPolicySource() {
        return """
                package com.example.urlshortener.security;

                import java.net.Inet4Address;
                import java.net.Inet6Address;
                import java.net.InetAddress;
                import java.net.URI;
                import java.net.UnknownHostException;
                import java.util.Locale;
                import java.util.Set;
                import org.springframework.http.HttpStatus;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ResponseStatusException;

                @Component
                public class DestinationPolicy {
                    private static final Set<String> BLOCKED_HOSTS = Set.of(
                            "metadata", "metadata.google.internal", "instance-data.ec2.internal");
                    private final HostResolver resolver;

                    public DestinationPolicy() {
                        this(InetAddress::getAllByName);
                    }

                    DestinationPolicy(HostResolver resolver) {
                        this.resolver = resolver;
                    }

                    public URI validate(String value) {
                        URI uri;
                        try {
                            uri = URI.create(value == null ? "" : value.trim()).normalize();
                        } catch (IllegalArgumentException exception) {
                            throw invalidDestination();
                        }
                        String scheme = uri.getScheme();
                        String host = uri.getHost();
                        if (!uri.isAbsolute() || uri.getRawUserInfo() != null || host == null || host.isBlank()
                                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                            throw invalidDestination();
                        }
                        String normalizedHost = host.toLowerCase(Locale.ROOT);
                        if (isBlockedName(normalizedHost)) {
                            throw invalidDestination();
                        }
                        try {
                            InetAddress[] resolved = resolver.resolve(normalizedHost);
                            if (resolved.length == 0 || java.util.Arrays.stream(resolved).anyMatch(address -> !isPublic(address))) {
                                throw invalidDestination();
                            }
                        } catch (UnknownHostException exception) {
                            throw invalidDestination();
                        }
                        return uri;
                    }

                    private boolean isBlockedName(String host) {
                        return BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost")
                                || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa");
                    }

                    private boolean isPublic(InetAddress address) {
                        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                            return false;
                        }
                        byte[] bytes = address.getAddress();
                        if (address instanceof Inet4Address) {
                            int first = bytes[0] & 0xff;
                            int second = bytes[1] & 0xff;
                            return first != 0 && first != 10 && first != 127 && first < 224
                                    && !(first == 100 && second >= 64 && second <= 127)
                                    && !(first == 169 && second == 254)
                                    && !(first == 172 && second >= 16 && second <= 31)
                                    && !(first == 192 && (second == 0 || second == 168))
                                    && !(first == 198 && (second == 18 || second == 19 || second == 51))
                                    && !(first == 203 && second == 0 && (bytes[2] & 0xff) == 113);
                        }
                        if (address instanceof Inet6Address) {
                            int first = bytes[0] & 0xff;
                            int second = bytes[1] & 0xff;
                                return (first & 0xe0) == 0x20
                                    && !(first == 0x20 && second == 0x01
                                    && (((bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8)
                                        || (bytes[2] == 0 && (bytes[3] & 0xff) <= 1)))
                                    && !(first == 0x20 && second == 0x02);
                        }
                        return false;
                    }

                    private ResponseStatusException invalidDestination() {
                        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Destination must be a public absolute HTTP(S) URL");
                    }

                    @FunctionalInterface
                    interface HostResolver {
                        InetAddress[] resolve(String host) throws UnknownHostException;
                    }
                }
                """;
    }

    private String rateLimitFilterSource() {
        return """
                package com.example.urlshortener.security;

                import java.nio.charset.StandardCharsets;
                import java.util.HexFormat;
                import java.util.List;
                import javax.crypto.Mac;
                import javax.crypto.spec.SecretKeySpec;
                import jakarta.servlet.FilterChain;
                import jakarta.servlet.ServletException;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.data.redis.core.StringRedisTemplate;
                import org.springframework.data.redis.core.script.DefaultRedisScript;
                import org.springframework.stereotype.Component;
                import org.springframework.web.filter.OncePerRequestFilter;

                @Component
                public class RateLimitFilter extends OncePerRequestFilter {
                    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
                            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n", Long.class);
                    private final StringRedisTemplate redis;
                    private final byte[] hashKey;
                    private final int limit;

                    public RateLimitFilter(StringRedisTemplate redis,
                            @Value("${app.security.rate-limit-hash-key}") String hashKey,
                            @Value("${app.rate-limit.requests-per-minute:60}") int limit) {
                        if (hashKey == null || hashKey.getBytes(StandardCharsets.UTF_8).length < 32 || limit < 1) {
                            throw new IllegalStateException("Set RATE_LIMIT_HASH_KEY to at least 32 bytes and a positive rate limit");
                        }
                        this.redis = redis;
                        this.hashKey = hashKey.getBytes(StandardCharsets.UTF_8);
                        this.limit = limit;
                    }

                    @Override
                    protected boolean shouldNotFilter(HttpServletRequest request) {
                        return !request.getRequestURI().startsWith("/api/v1/links");
                    }

                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                    FilterChain chain) throws ServletException, java.io.IOException {
                        if (request.getContentLengthLong() > 8192) {
                            response.sendError(413, "Request body exceeds 8 KiB");
                            return;
                        }
                        String category = request.getMethod().equals("POST") ? "create"
                                : request.getRequestURI().endsWith("/analytics") ? "analytics" : "redirect";
                        String key = "url-shortener:rate:" + category + ":" + digest(request.getRemoteAddr());
                        Long count;
                        try {
                            count = redis.execute(INCREMENT, List.of(key), "60");
                        } catch (RuntimeException exception) {
                            response.sendError(503, "Rate-limit service unavailable");
                            return;
                        }
                        if (count == null) {
                            response.sendError(503, "Rate-limit service unavailable");
                            return;
                        }
                        if (count > limit) {
                            response.setHeader("Retry-After", "60");
                            response.sendError(429, "Request rate limit exceeded");
                            return;
                        }
                        chain.doFilter(request, response);
                    }

                    private String digest(String peerAddress) {
                        try {
                            Mac mac = Mac.getInstance("HmacSHA256");
                            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
                            return HexFormat.of().formatHex(mac.doFinal(peerAddress.getBytes(StandardCharsets.UTF_8)));
                        } catch (java.security.GeneralSecurityException exception) {
                            throw new IllegalStateException("HMAC unavailable", exception);
                        }
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

    private ArtifactDraft text(String path, String content) {
        return new ArtifactDraft(ROOT + path, "text/yaml", content);
    }

    private ArtifactDraft dockerfile(String content) {
        return new ArtifactDraft(ROOT + "Dockerfile", "text/x-dockerfile", content);
    }
}