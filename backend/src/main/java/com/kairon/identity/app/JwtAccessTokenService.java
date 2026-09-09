package com.kairon.identity.app;

import java.time.Duration;
import java.time.Instant;

import com.kairon.common.security.JwtProperties;
import com.kairon.common.security.UserId;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Mints short-lived HS256 access tokens carrying the user id as {@code sub}.
 * Validation is Spring Security's resource-server filter using the matching
 * {@code JwtDecoder} bean; nothing here is stored.
 */
@Service
public class JwtAccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtAccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public MintedAccessToken mint(UserId userId, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new MintedAccessToken(token, properties.accessTokenTtl());
    }

    public record MintedAccessToken(String value, Duration ttl) {
    }
}
