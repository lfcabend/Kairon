package com.kairon.journal.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.journal.app.JournalService.CreateCommand;
import com.kairon.journal.app.JournalService.PatchCommand;
import com.kairon.journal.domain.JournalEntry;
import com.kairon.journal.repo.JournalEntryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000b1"));
    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Mock
    JournalEntryRepository entries;

    JournalService service;

    @BeforeEach
    void setUp() {
        service = new JournalService(entries, new JournalProperties(0), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JournalEntry entryOnDay(int position) {
        return JournalEntry.create(USER.value(), DAY, position, "title@" + position, "content", null);
    }

    @Test
    void createAppendsAtMaxPositionPlus100() {
        when(entries.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of(entryOnDay(100), entryOnDay(250)));
        when(entries.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntryView created = service.create(USER, new CreateCommand(DAY, "  Morning  ", "hello", 3));

        assertThat(created.position()).isEqualTo(350);
        assertThat(created.title()).isEqualTo("Morning");
        assertThat(created.mood()).isEqualTo(3);
    }

    @Test
    void createOnAnEmptyDayStartsAt100() {
        when(entries.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of());
        when(entries.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntryView created = service.create(USER, new CreateCommand(DAY, null, "first", null));

        assertThat(created.position()).isEqualTo(100);
        assertThat(created.title()).isNull();
    }

    @Test
    void createRejectsAMoodOutsideOneToFiveWith400() {
        when(entries.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(USER, new CreateCommand(DAY, "x", "y", 9)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void aForeignOrMissingRowIs404() {
        UUID id = UUID.randomUUID();
        when(entries.findByIdAndUserIdAndDeletedAtIsNull(id, USER.value())).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.patch(USER, id, new PatchCommand("x", null, null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void staleExpectedVersionIs409() {
        JournalEntry entry = entryOnDay(100); // version 0
        when(entries.findByIdAndUserIdAndDeletedAtIsNull(entry.getId(), USER.value()))
                .thenReturn(Optional.of(entry));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.patch(USER, entry.getId(),
                new PatchCommand(null, null, null, 7L)));

        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void patchRejectsAMoodOutsideOneToFiveWith400() {
        JournalEntry entry = entryOnDay(100);
        when(entries.findByIdAndUserIdAndDeletedAtIsNull(entry.getId(), USER.value()))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.patch(USER, entry.getId(),
                new PatchCommand(null, null, 0, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void patchUpdatesTitleContentAndMoodTogether() {
        JournalEntry entry = entryOnDay(100);
        when(entries.findByIdAndUserIdAndDeletedAtIsNull(entry.getId(), USER.value()))
                .thenReturn(Optional.of(entry));

        JournalEntryView patched = service.patch(USER, entry.getId(),
                new PatchCommand("New title", "New content", 5, null));

        assertThat(patched.title()).isEqualTo("New title");
        assertThat(patched.content()).isEqualTo("New content");
        assertThat(patched.mood()).isEqualTo(5);
    }

    @Test
    void rangeRejectsAWindowWiderThanTheConfiguredMax() {
        assertThatThrownBy(() -> service.range(USER, DAY, DAY.plusDays(93)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void hasEntryForDayDelegatesToTheRepository() {
        when(entries.existsByUserIdAndDayAndDeletedAtIsNull(USER.value(), DAY)).thenReturn(true);

        assertThat(service.hasEntryForDay(USER, DAY)).isTrue();
    }
}
