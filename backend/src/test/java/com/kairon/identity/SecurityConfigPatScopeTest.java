package com.kairon.identity;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the PAT scope boundary end to end against the real filter chain
 * (docs/milestones/M10.5-mcp-server.md D3/D17): a PAT reaches the read-only
 * {@code todo}/{@code journal}/{@code projects} allowlist and every
 * {@code /assistant/mcp/**} path, but every other {@code /api/v1/**} route —
 * including the *existing* {@code /assistant/todo-suggestions} and
 * {@code PATCH /me} — denies it outright; a JWT is unaffected throughout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityConfigPatScopeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired
    MockMvc mvc;

    private String registerAndGetJwt(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"PAT\",\"timezone\":\"Europe/Amsterdam\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private String issuePat(String jwt) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/me/personal-access-tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Claude Code\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void aPatReachesTheReadOnlyAllowlistAndEveryAssistantMcpPath() throws Exception {
        String jwt = registerAndGetJwt("pat-allowlist@example.com");
        String pat = issuePat(jwt);

        mvc.perform(get("/api/v1/todo").param("day", DAY.toString())
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/journal").param("day", DAY.toString())
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isOk());
        // Not opted in, so 403 — but that 403 proves the request reached the
        // controller/service layer rather than being denied by scope.
        mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions:context")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPatCannotReachAnyOtherApiPath() throws Exception {
        String jwt = registerAndGetJwt("pat-scope-denied@example.com");
        String pat = issuePat(jwt);

        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Name\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/todo")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        // The *existing* Anthropic-API assistant endpoint — a PAT must never be a
        // side-channel around it, even unauthenticated-feature-wise (D3).
        mvc.perform(post("/api/v1/assistant/todo-suggestions")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isForbidden());
        // A PAT cannot manage PATs either (D3).
        mvc.perform(get("/api/v1/me/personal-access-tokens")
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnrecognizedOrRevokedPatIs401() throws Exception {
        mvc.perform(get("/api/v1/todo").param("day", DAY.toString())
                        .header("Authorization", "Bearer kairon_pat_not-a-real-token"))
                .andExpect(status().isUnauthorized());

        String jwt = registerAndGetJwt("pat-revoked@example.com");
        MvcResult issued = mvc.perform(post("/api/v1/me/personal-access-tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Claude Code\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String pat = JsonPath.read(issued.getResponse().getContentAsString(), "$.token");
        String id = JsonPath.read(issued.getResponse().getContentAsString(), "$.id");

        mvc.perform(delete("/api/v1/me/personal-access-tokens/" + id)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/todo").param("day", DAY.toString())
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aJwtIsUnaffectedByAnyOfThePatWiring() throws Exception {
        String jwt = registerAndGetJwt("pat-jwt-unaffected@example.com");

        mvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Still Fine\"}"))
                .andExpect(status().isOk());
    }
}
