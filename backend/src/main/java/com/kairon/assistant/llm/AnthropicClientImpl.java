package com.kairon.assistant.llm;

import java.util.List;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

import com.kairon.assistant.app.AssistantProperties;
import com.kairon.assistant.app.AssistantUpstreamException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The only class in the app that touches the Anthropic Java SDK
 * ({@code com.anthropic..}) — enforced by ArchUnit's
 * {@code onlyAssistantImportsTheAnthropicSdk} rule (docs/adr/0002). Active
 * unless {@code kairon.assistant.fake-client=true} swaps in
 * {@link FakeAnthropicClient} instead (Playwright e2e, §9 Q3 of the M8 plan —
 * never a real network call in CI).
 */
@Component
@ConditionalOnProperty(prefix = "kairon.assistant", name = "fake-client", havingValue = "false",
        matchIfMissing = true)
class AnthropicClientImpl implements AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClientImpl.class);

    // A generated project plan (up to 40 tasks + dependencies + descriptions) needs
    // materially more room than the 4096 originally used for both calls — a real
    // plan truncated mid-JSON at that cap and failed to parse. 16000 matches the
    // documented safe default for a non-streaming structured-output request.
    private static final long MAX_OUTPUT_TOKENS = 16_000L;

    private final com.anthropic.client.AnthropicClient sdk;
    private final MeterRegistry meterRegistry;

    AnthropicClientImpl(AssistantProperties properties, MeterRegistry meterRegistry) {
        this.sdk = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.requestTimeout())
                .build();
        this.meterRegistry = meterRegistry;
    }

    @Override
    @CircuitBreaker(name = "anthropic", fallbackMethod = "suggestTodosFallback")
    public TodoSuggestionsResult suggestTodos(TodoSuggestionRequest request) {
        // The system prompt is a fixed constant (TodoSuggestionContextBuilder) —
        // every per-request fact lives in the user content instead — so it's a
        // stable prefix worth Anthropic's prompt caching (M8 §4.5).
        StructuredMessageCreateParams<TodoSuggestionsPayload> params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(request.systemPrompt())
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .outputConfig(TodoSuggestionsPayload.class)
                .addUserMessage(request.userContent())
                .build();

        StructuredMessage<TodoSuggestionsPayload> response;
        try {
            response = sdk.messages().create(params);
        } catch (RateLimitException | InternalServerException | AnthropicIoException e) {
            throw new AssistantUpstreamException(true, "The assistant is temporarily unavailable.", e);
        } catch (AnthropicInvalidDataException e) {
            // The SDK parses the structured-output payload eagerly inside create() —
            // a response cut off by maxTokens (or any other malformed JSON) surfaces
            // here, not as a truncation flag on the response itself.
            log.warn("Anthropic response failed structured-output parsing (likely truncated): {}", e.getMessage());
            throw new AssistantUpstreamException(false,
                    "The assistant's response was too large or invalid to use. Try a shorter description.", e);
        } catch (AnthropicServiceException e) {
            throw new AssistantUpstreamException(false, "The assistant could not complete this request.", e);
        }

        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            log.warn("Anthropic call refused model={}", request.model());
            throw new AssistantUpstreamException(false, "The assistant declined to respond.", null);
        }

        TodoSuggestionsPayload payload = response.content().stream()
                .flatMap(cb -> cb.text().stream())
                .findFirst()
                .map(com.anthropic.models.messages.StructuredTextBlock::text)
                .orElseThrow(() -> new AssistantUpstreamException(false,
                        "The assistant returned an empty response.", null));

        long inputTokens = response.usage().inputTokens();
        long outputTokens = response.usage().outputTokens();
        meterRegistry.counter("assistant.tokens", "kind", "TODO_SUGGESTION", "direction", "input")
                .increment(inputTokens);
        meterRegistry.counter("assistant.tokens", "kind", "TODO_SUGGESTION", "direction", "output")
                .increment(outputTokens);

        return new TodoSuggestionsResult(payload.suggestions(), request.model(), inputTokens, outputTokens);
    }

    // Resilience4j fallback — invoked on any exception from suggestTodos above,
    // including once the circuit is open (skips the call entirely).
    @SuppressWarnings("unused")
    private TodoSuggestionsResult suggestTodosFallback(TodoSuggestionRequest request, Throwable t) {
        if (t instanceof AssistantUpstreamException upstream) {
            throw upstream;
        }
        log.warn("Anthropic call failed via circuit breaker: {}", t.toString());
        throw new AssistantUpstreamException(true, "The assistant is temporarily unavailable.", t);
    }

    @Override
    @CircuitBreaker(name = "anthropic", fallbackMethod = "generateProjectPlanFallback")
    public ProjectPlanResult generateProjectPlan(ProjectPlanRequest request) {
        // Same shape as suggestTodos above (M8.5 §4.3): a fixed system prompt (no
        // per-request data) is a stable prefix worth Anthropic's prompt caching.
        StructuredMessageCreateParams<ProjectPlanPayload> params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(request.systemPrompt())
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .outputConfig(ProjectPlanPayload.class)
                .addUserMessage(request.userContent())
                .build();

        StructuredMessage<ProjectPlanPayload> response;
        try {
            response = sdk.messages().create(params);
        } catch (RateLimitException | InternalServerException | AnthropicIoException e) {
            throw new AssistantUpstreamException(true, "The assistant is temporarily unavailable.", e);
        } catch (AnthropicInvalidDataException e) {
            // The SDK parses the structured-output payload eagerly inside create() —
            // a response cut off by maxTokens (or any other malformed JSON) surfaces
            // here, not as a truncation flag on the response itself.
            log.warn("Anthropic response failed structured-output parsing (likely truncated): {}", e.getMessage());
            throw new AssistantUpstreamException(false,
                    "The assistant's response was too large or invalid to use. Try a shorter description.", e);
        } catch (AnthropicServiceException e) {
            throw new AssistantUpstreamException(false, "The assistant could not complete this request.", e);
        }

        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            log.warn("Anthropic call refused model={}", request.model());
            throw new AssistantUpstreamException(false, "The assistant declined to respond.", null);
        }

        ProjectPlanPayload payload = response.content().stream()
                .flatMap(cb -> cb.text().stream())
                .findFirst()
                .map(com.anthropic.models.messages.StructuredTextBlock::text)
                .orElseThrow(() -> new AssistantUpstreamException(false,
                        "The assistant returned an empty response.", null));

        long inputTokens = response.usage().inputTokens();
        long outputTokens = response.usage().outputTokens();
        meterRegistry.counter("assistant.tokens", "kind", "PROJECT_GENERATION", "direction", "input")
                .increment(inputTokens);
        meterRegistry.counter("assistant.tokens", "kind", "PROJECT_GENERATION", "direction", "output")
                .increment(outputTokens);

        return new ProjectPlanResult(payload, request.model(), inputTokens, outputTokens);
    }

    @SuppressWarnings("unused")
    private ProjectPlanResult generateProjectPlanFallback(ProjectPlanRequest request, Throwable t) {
        if (t instanceof AssistantUpstreamException upstream) {
            throw upstream;
        }
        log.warn("Anthropic call failed via circuit breaker: {}", t.toString());
        throw new AssistantUpstreamException(true, "The assistant is temporarily unavailable.", t);
    }
}
