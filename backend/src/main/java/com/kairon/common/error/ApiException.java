package com.kairon.common.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * An application error that maps directly to an RFC 7807 problem response.
 * {@link GlobalExceptionHandler} turns it into {@code application/problem+json};
 * services throw the static factories rather than reaching for framework types.
 *
 * <p>Accessing another user's row returns {@link #notFound()} (a 404), never a
 * 403 — existence is not leaked. See docs/DESIGN.md §3.2.
 */
public class ApiException extends RuntimeException {

    private static final String TYPE_PREFIX = "https://kairon.app/problems/";

    private final HttpStatusCode status;
    private final String type;

    public ApiException(HttpStatusCode status, String type, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public URI getType() {
        return URI.create(TYPE_PREFIX + type);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad-request", detail);
    }

    public static ApiException tooManyRequests(String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", detail);
    }
}
