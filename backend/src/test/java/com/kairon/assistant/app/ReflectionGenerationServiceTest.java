package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.ReflectionRequest;
import com.kairon.assistant.llm.AnthropicClient.ReflectionResult;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReflectionGenerationServiceTest {

    private static final UUID USER_ID = UUID.fromString("018f5b3e-0000-7000-8000-0000000000d1");
    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 27);

    @Mock
    AssistantRunRepository runs;

    @Mock
    JournalReflectionContextBuilder contextBuilder;

    @Mock
    AnthropicClient anthropicClient;

    @Mock
    UserAccountApi accounts;

    private AssistantProperties properties;
    private ReflectionGenerationService service;
    private AssistantRun run;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", null, 0, null, null, null,
                new AssistantProperties.JournalReflection(30, 5, "HIGH"));
        service = new ReflectionGenerationService(runs, contextBuilder, anthropicClient, properties, accounts);
        run = AssistantRun.pending(USER_ID, AssistantRunKind.JOURNAL_REFLECTION, START, END, "claude-sonnet-5");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(accounts.assistantPreferences(any())).thenReturn(
                new AssistantPreferencesView(false, false, true, false, null, "balanced"));
        when(contextBuilder.build(any(), any(), any(), any())).thenReturn(
                new JournalReflectionContextBuilder.Context("system", "user content", java.util.Map.of()));
    }

    @Test
    void generate_onSuccess_persistsSucceededRunWithTheModelsMarkdownUnmodified() {
        when(anthropicClient.generateReflection(any(ReflectionRequest.class)))
                .thenReturn(new ReflectionResult("## Patterns\n\nSome reflection.", "claude-sonnet-5", 900, 320));

        service.generate(run.getId());

        assertThat(run.getStatus().name()).isEqualTo("SUCCEEDED");
        // D11 — no appended table, unlike M9's summary output.
        assertThat(run.getOutputMarkdown()).isEqualTo("## Patterns\n\nSome reflection.");
        assertThat(run.getInputTokens()).isEqualTo(900);
        assertThat(run.getOutputTokens()).isEqualTo(320);
    }

    @Test
    void generate_fetchesToneFromThePreferencesApiAndPassesItToTheContextBuilder() {
        when(accounts.assistantPreferences(any())).thenReturn(
                new AssistantPreferencesView(false, false, true, false, null, "direct"));
        when(anthropicClient.generateReflection(any(ReflectionRequest.class)))
                .thenReturn(new ReflectionResult("narrative", "claude-sonnet-5", 100, 50));

        service.generate(run.getId());

        org.mockito.Mockito.verify(contextBuilder).build(UserId.of(USER_ID), START, END, "direct");
    }

    @Test
    void generate_onUpstreamFailure_persistsFailedRunWithASanitizedDetail() {
        when(anthropicClient.generateReflection(any(ReflectionRequest.class)))
                .thenThrow(new AssistantUpstreamException(true, "The assistant is temporarily unavailable.", null));

        service.generate(run.getId());

        assertThat(run.getStatus().name()).isEqualTo("FAILED");
        assertThat(run.getError()).isEqualTo("The assistant is temporarily unavailable.");
        assertThat(run.getOutputMarkdown()).isNull();
    }
}
