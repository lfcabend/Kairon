package com.kairon.identity.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import com.kairon.common.security.JwtProperties;
import com.kairon.common.security.UserId;
import com.kairon.identity.app.JwtAccessTokenService.MintedAccessToken;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenServiceTest {

    private static final String SECRET = "unit-test-signing-secret-reasonably-long";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final SecretKey key = hmacKey(SECRET);
    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();
    private final JwtAccessTokenService service =
            new JwtAccessTokenService(encoder, new JwtProperties(SECRET, "kairon", TTL));

    @Test
    void mintProducesADecodableTokenWithSubjectIssuerAndExpiry() {
        UserId userId = UserId.of(UUID.randomUUID());
        // JWT timestamps are second-precision, and the decoder enforces expiry.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        MintedAccessToken minted = service.mint(userId, now);

        assertThat(minted.ttl()).isEqualTo(TTL);
        Jwt jwt = decoder.decode(minted.value());
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("kairon");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(TTL));
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        MintedAccessToken minted = service.mint(UserId.of(UUID.randomUUID()), Instant.now());
        JwtDecoder foreign = NimbusJwtDecoder.withSecretKey(hmacKey("a-totally-different-secret"))
                .macAlgorithm(MacAlgorithm.HS256).build();

        assertThatThrownBy(() -> foreign.decode(minted.value()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    private static SecretKey hmacKey(String secret) {
        try {
            return new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),
                    "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
