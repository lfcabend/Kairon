package com.kairon.assistant.repo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantRunRepository extends JpaRepository<AssistantRun, UUID> {

    Optional<AssistantRun> findByIdAndUserId(UUID id, UUID userId);

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
