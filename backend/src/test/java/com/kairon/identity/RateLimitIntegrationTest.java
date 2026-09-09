package com.kairon.identity;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth endpoints are rate-limited by {@code RateLimitFilter}. Capacity is
 * squeezed to 3 here so the fourth rapid attempt is refused with a problem+json 429.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "kairon.security.rate-limit.capacity=3",
        "kairon.security.rate-limit.refill-period=PT1H"
})
class RateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void afterTheBucketIsDrainedTheNextLoginAttemptIs429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("kairon_refresh", "bogus-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("kairon_refresh", "bogus-final")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(header().exists("Retry-After"));
    }
}
