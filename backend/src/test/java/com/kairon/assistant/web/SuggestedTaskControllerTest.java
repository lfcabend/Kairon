package com.kairon.assistant.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.kairon.assistant.app.AssistantSuggestedTaskView;
import com.kairon.assistant.app.SuggestedTaskService;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.todo.api.TodoItemView;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuggestedTaskController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class SuggestedTaskControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001401");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SuggestedTaskService suggestedTasks;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void acceptReturns201WithTheCreatedTodo() throws Exception {
        UUID id = UUID.randomUUID();
        UUID createdId = UUID.randomUUID();
        TodoItemView created = new TodoItemView(createdId, DAY, "Order cabinet hardware", null, "OPEN",
                0, 100, 20, null, null, null, null, null, 0);
        when(suggestedTasks.accept(any(), eq(id))).thenReturn(created);

        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + id + ":accept").with(asUser()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(createdId.toString()));
    }

    @Test
    void acceptOnAnAlreadyResolvedSuggestionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(suggestedTasks.accept(any(), eq(id))).thenThrow(ApiException.conflict("Already accepted."));

        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + id + ":accept").with(asUser()))
                .andExpect(status().isConflict());
    }

    @Test
    void dismissReturns200WithTheUpdatedSuggestion() throws Exception {
        UUID id = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AssistantSuggestedTaskView view = new AssistantSuggestedTaskView(id, runId, "Order cabinet hardware",
                null, "Overdue kitchen-remodel task", DAY, 20, null, "DISMISSED", null, 0);
        when(suggestedTasks.dismiss(any(), eq(id))).thenReturn(view);

        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + id + ":dismiss").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void missingOrForeignSuggestionIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(suggestedTasks.dismiss(any(), eq(id))).thenThrow(ApiException.notFound("Suggested task not found."));

        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + id + ":dismiss").with(asUser()))
                .andExpect(status().isNotFound());
    }
}
