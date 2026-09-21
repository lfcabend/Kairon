package com.kairon.assistant.app;

import com.kairon.common.error.ApiException;

/**
 * Wraps any failure calling the Anthropic API (a typed SDK exception, a
 * timeout, or an open circuit breaker) with just enough classification for
 * {@link AssistantRunService} to persist a sanitized {@code error} and map to
 * the right {@code application/problem+json} response — never a raw exception
 * message (docs/milestones/M8-assistant-foundations.md D12).
 */
public class AssistantUpstreamException extends RuntimeException {

    private final boolean retryable;
    private final String sanitizedDetail;

    public AssistantUpstreamException(boolean retryable, String sanitizedDetail, Throwable cause) {
        super(sanitizedDetail, cause);
        this.retryable = retryable;
        this.sanitizedDetail = sanitizedDetail;
    }

    public boolean retryable() {
        return retryable;
    }

    public String sanitizedDetail() {
        return sanitizedDetail;
    }

    /** Retryable failures (429/5xx/connection/open-circuit) are 503; anything else is 502. */
    public ApiException toApiException() {
        return retryable
                ? ApiException.serviceUnavailable(sanitizedDetail)
                : ApiException.badGateway(sanitizedDetail);
    }
}
