package com.kairon.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The app's cryptographic beans, shared by every module: the HS256 key used to
 * sign and verify access tokens, and the password encoder (Argon2id default, per
 * docs/DESIGN.md §6).
 */
@Configuration
public class CryptoConfig {

    /**
     * Derives a fixed 256-bit MAC key from the configured secret so any secret of
     * reasonable entropy works without operators having to supply exactly 32 bytes.
     */
    private static SecretKey hmacKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "HmacSHA256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey(properties.secret())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(hmacKey(properties.secret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(
                "argon2",
                Map.of(
                        "argon2", argon2,
                        "bcrypt", new BCryptPasswordEncoder()));
        // Accept bare (un-prefixed) hashes as Argon2id for imported/legacy rows.
        encoder.setDefaultPasswordEncoderForMatches(argon2);
        return encoder;
    }
}
