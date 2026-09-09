package com.kairon.identity;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M1 acceptance flow (docs/ROADMAP.md): register → call {@code /me} → refresh
 * → logout, against a real PostgreSQL, plus the failure edges that matter
 * (reuse detection, bad password, duplicate email, missing token).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthFlowIntegrationTest {

    private static final String COOKIE = "kairon_refresh";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void registerThenMeThenRefreshThenLogout() throws Exception {
        // --- register -------------------------------------------------------
        MvcResult registered = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correct horse battery",
                                 "displayName":"Ada","timezone":"Europe/Amsterdam"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().path(COOKIE, "/api/v1/auth"))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("SameSite=Strict"))))
                .andReturn();

        String access = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        String refresh1 = registered.getResponse().getCookie(COOKIE).getValue();
        assertThat(refresh1).isNotBlank();

        // --- GET /me with the access token --------------------------------
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ada"))
                .andExpect(jsonPath("$.timezone").value("Europe/Amsterdam"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // --- GET /me without a token is a problem+json 401 ----------------
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401));

        // --- refresh: rotates the cookie ---------------------------------
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refresh1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        String refresh2 = refreshed.getResponse().getCookie(COOKIE).getValue();
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);

        // --- replaying the rotated-away token trips reuse detection -------
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refresh1)))
                .andExpect(status().isUnauthorized());
        // ... and that revoked the whole family, so refresh2 is dead too
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refresh2)))
                .andExpect(status().isUnauthorized());

        // --- login issues a fresh family --------------------------------
        MvcResult loggedIn = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correct horse battery"}"""))
                .andExpect(status().isOk())
                .andReturn();
        String refresh3 = loggedIn.getResponse().getCookie(COOKIE).getValue();

        // --- logout revokes it and clears the cookie -------------------
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, refresh3)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refresh3)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithAWrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","password":"a valid passphrase","displayName":"Grace"}"""))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","password":"not the password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void registeringADuplicateEmailIs409() throws Exception {
        String body = """
                {"email":"linus@example.com","password":"a valid passphrase","displayName":"Linus"}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registerRejectsAShortPasswordWith400AndFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"tim@example.com","password":"short","displayName":"Tim"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem("password")));
    }
}
