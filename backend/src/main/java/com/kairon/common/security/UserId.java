package com.kairon.common.security;

import java.util.UUID;

/**
 * The identity of the authenticated principal, carried through the request as the
 * {@code sub} claim of the access token. Feature modules scope every query by this
 * value (a row that belongs to someone else is a 404, not a 403).
 */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("user id must not be null");
        }
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId fromString(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
