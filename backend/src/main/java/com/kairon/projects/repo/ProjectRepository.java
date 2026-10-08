package com.kairon.projects.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectSize;
import com.kairon.projects.domain.ProjectStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    // One query, nullable filters — avoids a 2^n explosion of derived-query method
    // names for status x categoryId x size combinations.
    @Query("""
            SELECT p FROM Project p
            WHERE p.userId = :userId AND p.deletedAt IS NULL
              AND (:status IS NULL OR p.status = :status)
              AND (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:size IS NULL OR p.size = :size)
            """)
    Page<Project> search(@Param("userId") UUID userId, @Param("status") ProjectStatus status,
            @Param("categoryId") UUID categoryId, @Param("size") ProjectSize size, Pageable pageable);

    // Same as `search` but with a hard status exclusion instead of an optional
    // equality filter — backs D10's "status omitted -> hide ARCHIVED" default,
    // which a single nullable-equality param can't express.
    @Query("""
            SELECT p FROM Project p
            WHERE p.userId = :userId AND p.deletedAt IS NULL AND p.status <> :excludedStatus
              AND (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:size IS NULL OR p.size = :size)
            """)
    Page<Project> searchExcludingStatus(@Param("userId") UUID userId, @Param("excludedStatus") ProjectStatus excludedStatus,
            @Param("categoryId") UUID categoryId, @Param("size") ProjectSize size, Pageable pageable);

    // Backs the flattened Priority sort mode (D19/D21) and ProjectService.reorder's target
    // set (D20): every non-deleted, non-ARCHIVED project for the user, rank-ordered.
    List<Project> findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
            UUID userId, ProjectStatus excludedStatus);

    // Backs "Sort by: Custom order" and ProjectService.reorderInCategory's target set:
    // every non-deleted, non-ARCHIVED project the user holds in one category bucket
    // (categoryId null means the "Uncategorized" bucket), rank-ordered. Spring Data
    // translates a null categoryId argument to "IS NULL" automatically, the same way
    // ProjectTaskRepository's parentTaskId-scoped sibling-group query already does.
    List<Project> findByUserIdAndCategoryIdAndDeletedAtIsNullAndStatusNotOrderByCategoryRankAsc(
            UUID userId, UUID categoryId, ProjectStatus excludedStatus);

    // Backs ProjectsApi.changedSince (M11 D3/D4) — the one query here that deliberately
    // does NOT filter deletedAt IS NULL: a soft-deleted row must still surface so the
    // client can tombstone it locally.
    List<Project> findByUserIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
