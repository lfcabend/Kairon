package com.kairon.journal.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.journal.domain.JournalEntry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    List<JournalEntry> findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(
            UUID userId, LocalDate day);

    List<JournalEntry> findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(
            UUID userId, LocalDate from, LocalDate to);

    Optional<JournalEntry> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    // Backs JournalApi.hasEntryForDay (D6).
    boolean existsByUserIdAndDayAndDeletedAtIsNull(UUID userId, LocalDate day);

    @Query(value = "SELECT DISTINCT day FROM journal_entry "
            + "WHERE user_id = :userId AND day BETWEEN :from AND :to AND deleted_at IS NULL "
            + "ORDER BY day",
            nativeQuery = true)
    List<LocalDate> findEntryDays(@Param("userId") UUID userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // Full-text search: native query (tsvector/ts_rank/ts_headline aren't
    // expressible via derived queries or JPQL functions), paginated (D3, D7).
    @Query(value = """
            SELECT id, day, position, title, mood, created_at, updated_at, version,
                   ts_headline('simple', coalesce(title,'') || ' ' || content,
                               websearch_to_tsquery('simple', :q), 'MaxFragments=1') AS snippet,
                   ts_rank(content_tsv, websearch_to_tsquery('simple', :q)) AS rank
            FROM journal_entry
            WHERE user_id = :userId AND deleted_at IS NULL
              AND content_tsv @@ websearch_to_tsquery('simple', :q)
            ORDER BY rank DESC
            """,
            countQuery = """
            SELECT count(*) FROM journal_entry
            WHERE user_id = :userId AND deleted_at IS NULL
              AND content_tsv @@ websearch_to_tsquery('simple', :q)
            """,
            nativeQuery = true)
    Page<JournalSearchRow> search(@Param("userId") UUID userId, @Param("q") String q, Pageable pageable);
}
