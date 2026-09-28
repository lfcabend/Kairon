package com.kairon.assistant.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.domain.AssistantRunStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantRunRepository extends JpaRepository<AssistantRun, UUID> {

    Optional<AssistantRun> findByIdAndUserId(UUID id, UUID userId);

    // D3 — the in-flight guard requestSummary (and the scheduled sweep) checks
    // before queuing a new WEEKLY_SUMMARY/MONTHLY_SUMMARY run.
    boolean existsByUserIdAndKindInAndStatusIn(UUID userId, List<AssistantRunKind> kinds,
            List<AssistantRunStatus> statuses);

    // Backs GET /assistant/runs (D12) — most recent first, optional kind/date filters.
    // The `has*` flags (rather than `:kinds IS NULL`/`:from IS NULL` directly) are
    // required, not stylistic: a bind parameter used only in an `x IS NULL` check
    // with no other typed context in the same disjunct gives Postgres nothing to
    // infer its type from over the JDBC extended protocol ("could not determine
    // data type of parameter $N") — every parameter here now also appears in an
    // unambiguous typed comparison, so `kinds`/`from`/`to` may still be non-null
    // placeholders (an empty list / any Instant) when their flag is false, since
    // the `OR` short-circuits them logically but Postgres still type-checks both sides.
    @Query("""
            SELECT r FROM AssistantRun r
            WHERE r.userId = :userId
              AND (:hasKinds = false OR r.kind IN :kinds)
              AND (:hasFrom = false OR r.createdAt >= :from)
              AND (:hasTo = false OR r.createdAt < :to)
            ORDER BY r.createdAt DESC
            """)
    Page<AssistantRun> findPage(@Param("userId") UUID userId, @Param("hasKinds") boolean hasKinds,
            @Param("kinds") List<AssistantRunKind> kinds, @Param("hasFrom") boolean hasFrom,
            @Param("from") Instant from, @Param("hasTo") boolean hasTo, @Param("to") Instant to,
            Pageable pageable);

    // Pre-flight monthly token budget check (D8): sums both token columns across
    // every run — including FAILED ones, since a failed call can still have been
    // billed for whatever tokens it consumed before erroring — created since the
    // start of the current UTC calendar month.
    @Query("""
            SELECT COALESCE(SUM(COALESCE(r.inputTokens, 0) + COALESCE(r.outputTokens, 0)), 0)
            FROM AssistantRun r
            WHERE r.userId = :userId AND r.createdAt >= :monthStart
            """)
    long sumTokensSince(@Param("userId") UUID userId, @Param("monthStart") Instant monthStart);
}
