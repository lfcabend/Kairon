package com.kairon.identity.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.identity.app.PersonalAccessTokenService;
import com.kairon.identity.web.PersonalAccessTokenDtos.IssuePersonalAccessTokenRequest;
import com.kairon.identity.web.PersonalAccessTokenDtos.IssuedPersonalAccessTokenResponse;
import com.kairon.identity.web.PersonalAccessTokenDtos.PersonalAccessTokenResponse;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/me/personal-access-tokens} — generate, list, and revoke a
 * PAT for the MCP server to use (docs/milestones/M10.5-mcp-server.md §4.2).
 * Deliberately NOT under the {@code /api/v1/assistant/mcp/**} allowlist a PAT
 * itself is restricted to (D3) — a PAT can never mint or manage another PAT,
 * only an ordinary JWT-authenticated session can.
 */
@RestController
@RequestMapping("/api/v1/me/personal-access-tokens")
public class PersonalAccessTokenController {

    private static final Logger log = LoggerFactory.getLogger(PersonalAccessTokenController.class);

    private final PersonalAccessTokenService tokens;

    public PersonalAccessTokenController(PersonalAccessTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedPersonalAccessTokenResponse issue(@CurrentUser UserId userId,
            @Valid @RequestBody IssuePersonalAccessTokenRequest req) {
        log.debug("POST /me/personal-access-tokens userId={} name={}", userId.value(), req.name());
        PersonalAccessTokenService.Issued issued = tokens.issue(userId, req.name(), req.expiresAt());
        return new IssuedPersonalAccessTokenResponse(issued.view().id(), issued.view().name(),
                issued.rawToken(), issued.view().createdAt(), issued.view().expiresAt());
    }

    @GetMapping
    public List<PersonalAccessTokenResponse> list(@CurrentUser UserId userId) {
        log.debug("GET /me/personal-access-tokens userId={}", userId.value());
        return tokens.list(userId).stream().map(PersonalAccessTokenResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("DELETE /me/personal-access-tokens/{} userId={}", id, userId.value());
        tokens.revoke(userId, id, Instant.now());
    }
}
