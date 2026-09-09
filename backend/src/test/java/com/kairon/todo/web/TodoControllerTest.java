package com.kairon.todo.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.app.RolloverService;
import com.kairon.todo.app.RolloverService.RolloverPreview;
import com.kairon.todo.app.RolloverService.SourceDay;
import com.kairon.todo.app.TodoService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class TodoControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000000d01");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TodoService todos;

    @MockitoBean
    RolloverService rollovers;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static TodoItemView view(String title, int position) {
        return new TodoItemView(UUID.randomUUID(), DAY, title, null, "OPEN", 0, position, null,
                null, null, null, Instant.parse("2026-09-09T08:00:00Z"),
                Instant.parse("2026-09-09T08:00:00Z"), 0);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void listByDayReturnsABareArray() throws Exception {
        when(todos.list(any(), eq(DAY))).thenReturn(List.of(view("a", 100), view("b", 200)));

        mvc.perform(get("/api/v1/todo").param("day", "2026-09-09").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("a"))
                .andExpect(jsonPath("$[1].position").value(200));
    }

    @Test
    void listWithNeitherDayNorRangeIs400() throws Exception {
        mvc.perform(get("/api/v1/todo").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201AndTheItem() throws Exception {
        when(todos.create(any(), any(TodoService.CreateCommand.class)))
                .thenReturn(view("write the plan", 100));

        mvc.perform(post("/api/v1/todo").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2026-09-09\",\"title\":\"write the plan\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("write the plan"));
    }

    @Test
    void createWithABlankTitleIs400() throws Exception {
        mvc.perform(post("/api/v1/todo").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2026-09-09\",\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithAPriorityOutOfRangeIs400() throws Exception {
        mvc.perform(post("/api/v1/todo").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2026-09-09\",\"title\":\"x\",\"priority\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem("priority")));
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/todo").param("day", "2026-09-09"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void completeTogglesViaThePathSuffix() throws Exception {
        UUID id = UUID.randomUUID();
        when(todos.complete(any(), eq(id), anyBoolean())).thenReturn(view("done", 100));

        mvc.perform(post("/api/v1/todo/" + id + ":complete").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("done"));
    }

    @Test
    void reorderMismatchSurfacesThe400FromTheService() throws Exception {
        when(todos.reorder(any(), any(), any()))
                .thenThrow(ApiException.badRequest("`orderedIds` must list exactly the non-deleted items for that day."));

        mvc.perform(post("/api/v1/todo:reorder").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2026-09-09\",\"orderedIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("orderedIds")));
    }

    @Test
    void patchWithAStaleVersionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(todos.patch(any(), eq(id), any()))
                .thenThrow(ApiException.conflict("This item was modified by another request. Reload and try again."));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/todo/" + id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("conflict")));
    }

    @Test
    void rolloverPreviewReturnsGroupedSourceDays() throws Exception {
        when(rollovers.preview(any(), eq(DAY))).thenReturn(new RolloverPreview(
                List.of(new SourceDay(DAY.minusDays(2), List.of(view("carry", 100)))), 1));

        mvc.perform(get("/api/v1/todo/rollover-preview").param("onDay", "2026-09-09").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.sourceDays[0].items[0].title").value("carry"));
    }

    @Test
    void rolloverReturnsTheCreatedItems() throws Exception {
        when(rollovers.rollover(any(), any())).thenReturn(List.of(view("carried", 100)));

        mvc.perform(post("/api/v1/todo:rollover").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toDay\":\"2026-09-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolledOver[0].title").value("carried"));
    }

    @Test
    void rolloverUndoReturnsTheReopenedSources() throws Exception {
        when(rollovers.undo(any(), any())).thenReturn(List.of(view("restored", 100)));

        mvc.perform(post("/api/v1/todo:rollover-undo").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"createdIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reopened[0].title").value("restored"));
    }
}
