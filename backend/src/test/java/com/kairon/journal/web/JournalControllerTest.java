package com.kairon.journal.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.journal.api.JournalSearchHitView;
import com.kairon.journal.api.JournalSearchPage;
import com.kairon.journal.app.JournalSearchService;
import com.kairon.journal.app.JournalService;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JournalController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class JournalControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000000d01");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JournalService journal;

    @MockitoBean
    JournalSearchService search;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static JournalEntryView view(String title, int position) {
        return new JournalEntryView(UUID.randomUUID(), DAY, position, title, "content", null,
                Instant.parse("2026-09-09T08:00:00Z"), Instant.parse("2026-09-09T08:00:00Z"), 0);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void listByDayReturnsABareArray() throws Exception {
        when(journal.list(any(), eq(DAY))).thenReturn(List.of(view("a", 100), view("b", 200)));

        mvc.perform(get("/api/v1/journal").param("day", "2026-09-09").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("a"))
                .andExpect(jsonPath("$[1].position").value(200));
    }

    @Test
    void listWithNeitherDayNorRangeIs400() throws Exception {
        mvc.perform(get("/api/v1/journal").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entryDaysReturnsIsoDateArray() throws Exception {
        when(journal.entryDays(any(), eq(DAY), eq(DAY.plusDays(30))))
                .thenReturn(List.of(DAY, DAY.plusDays(2)));

        mvc.perform(get("/api/v1/journal/entry-days")
                        .param("from", "2026-09-09").param("to", "2026-10-09").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("2026-09-09"))
                .andExpect(jsonPath("$[1]").value("2026-09-11"));
    }

    @Test
    void createReturns201AndTheEntry() throws Exception {
        when(journal.create(any(), any(JournalService.CreateCommand.class)))
                .thenReturn(view("Morning plan", 100));

        mvc.perform(post("/api/v1/journal").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2026-09-09\",\"title\":\"Morning plan\",\"content\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Morning plan"));
    }

    @Test
    void createWithAMoodOutOfRangeIs400() throws Exception {
        mvc.perform(post("/api/v1/journal").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2026-09-09\",\"mood\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem("mood")));
    }

    @Test
    void patchWithAStaleVersionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(journal.patch(any(), eq(id), any()))
                .thenThrow(ApiException.conflict("This entry was modified by another request. Reload and try again."));

        mvc.perform(patch("/api/v1/journal/" + id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/journal/" + id).with(asUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/journal").param("day", "2026-09-09"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void searchReturnsThePaginatedEnvelope() throws Exception {
        JournalSearchHitView hit = new JournalSearchHitView(UUID.randomUUID(), DAY, "Evening reflection",
                "…decided to <b>ship</b> the plan…", 3, Instant.parse("2026-09-05T20:14:00Z"));
        when(search.search(any(), eq("ship"), eq(0), eq(20)))
                .thenReturn(new JournalSearchPage(List.of(hit), 0, 1));

        mvc.perform(get("/api/v1/journal:search").param("q", "ship").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Evening reflection"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchWithoutQIs400() throws Exception {
        mvc.perform(get("/api/v1/journal:search").with(asUser()))
                .andExpect(status().isBadRequest());
    }
}
