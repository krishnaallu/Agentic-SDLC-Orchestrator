package com.example.orchestrator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class ApiRequestGuardFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final int requestsPerMinute;
    private final int maximumTrackedClients;
    private final long maximumRequestBytes;

    public ApiRequestGuardFilter(
            @Value("${orchestrator.api.requests-per-minute:120}") int requestsPerMinute,
            @Value("${orchestrator.api.maximum-tracked-clients:10000}") int maximumTrackedClients,
            @Value("${orchestrator.api.maximum-request-bytes:262144}") long maximumRequestBytes) {
        if (requestsPerMinute < 1 || maximumTrackedClients < 1 || maximumRequestBytes < 1) {
            throw new IllegalArgumentException("API request guard limits must be positive");
        }
        this.requestsPerMinute = requestsPerMinute;
        this.maximumTrackedClients = maximumTrackedClients;
        this.maximumRequestBytes = maximumRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/runs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maximumRequestBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body exceeds configured limit");
            return;
        }
        if (contentLength < 0 && ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod())
                || "PATCH".equals(request.getMethod()))) {
            response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED, "Content-Length is required for write requests");
            return;
        }

        long now = System.currentTimeMillis();
        if (requests.incrementAndGet() % 256 == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
        }
        String client = clientKey(request);
        if (!windows.containsKey(client) && windows.size() >= maximumTrackedClients) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
            if (!windows.containsKey(client) && windows.size() >= maximumTrackedClients) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "API rate limiter is at capacity");
                return;
            }
        }

        AtomicBoolean limited = new AtomicBoolean();
        windows.compute(client, (key, current) -> {
            if (current == null || now - current.startedAt >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            if (current.count >= requestsPerMinute) {
                limited.set(true);
                return current;
            }
            return new Window(current.startedAt, current.count + 1);
        });
        if (limited.get()) {
            response.setHeader("Retry-After", "60");
            response.sendError(429, "API request rate exceeded");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        return "peer:" + request.getRemoteAddr();
    }

    private record Window(long startedAt, int count) {
    }
}