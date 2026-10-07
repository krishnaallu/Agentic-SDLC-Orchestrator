package com.example.orchestrator.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestGuardFilterTests {
    @Test
    void rejectsRequestsAfterPerClientWindowLimit() throws Exception {
        ApiRequestGuardFilter filter = new ApiRequestGuardFilter(2, 10, 1024);
        AtomicInteger chainCalls = new AtomicInteger();

        for (int index = 0; index < 3; index++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/runs/metrics/reliability");
            request.setRemoteAddr("192.0.2.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (servletRequest, servletResponse) -> chainCalls.incrementAndGet();
            filter.doFilter(request, response, chain);
            if (index == 2) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
            }
        }
        assertThat(chainCalls).hasValue(2);
    }

    @Test
    void rejectsOversizedAndUnknownLengthWrites() throws Exception {
        ApiRequestGuardFilter filter = new ApiRequestGuardFilter(10, 10, 1024);
        MockHttpServletRequest oversized = new MockHttpServletRequest("POST", "/api/v1/runs");
        oversized.setContent(new byte[1025]);
        MockHttpServletResponse oversizedResponse = new MockHttpServletResponse();
        filter.doFilter(oversized, oversizedResponse, (request, response) -> {
            throw new AssertionError("Oversized request reached the application");
        });
        assertThat(oversizedResponse.getStatus()).isEqualTo(413);

        MockHttpServletRequest unknownLength = new MockHttpServletRequest("POST", "/api/v1/runs");
        MockHttpServletResponse unknownLengthResponse = new MockHttpServletResponse();
        filter.doFilter(unknownLength, unknownLengthResponse, (request, response) -> {
            throw new AssertionError("Unknown-length write reached the application");
        });
        assertThat(unknownLengthResponse.getStatus()).isEqualTo(411);
    }
}