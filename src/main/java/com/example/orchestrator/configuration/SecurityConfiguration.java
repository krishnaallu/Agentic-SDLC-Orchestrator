package com.example.orchestrator.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${orchestrator.security.enabled:true}") boolean securityEnabled)
            throws Exception {
        var security = http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    if (!securityEnabled) {
                        authorize.anyRequest().permitAll();
                        return;
                    }
                    authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/api/v1/scenarios").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**")
                            .hasAuthority("SCOPE_orchestrator:ops")
                        .requestMatchers("/api/v1/runs/metrics/reliability")
                            .hasAuthority("SCOPE_orchestrator:ops")
                        .requestMatchers(HttpMethod.POST, "/api/v1/runs/*/decision",
                                "/api/v1/runs/*/artifacts/decision")
                            .hasAuthority("SCOPE_orchestrator:approve")
                        .requestMatchers(HttpMethod.POST, "/api/v1/runs", "/api/v1/runs/*/replan",
                                "/api/v1/runs/*/clarification")
                            .hasAuthority("SCOPE_orchestrator:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/runs/*/artifacts/export",
                                "/api/v1/runs/*")
                            .hasAuthority("SCOPE_orchestrator:read")
                        .anyRequest().denyAll();
                    });
                if (securityEnabled) {
                    security.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
                }
                return security.build();
    }
}