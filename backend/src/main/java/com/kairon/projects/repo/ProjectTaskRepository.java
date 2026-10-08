package com.kairon.projects.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.domain.ProjectTask;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

    Page<ProjectTask> findByProjectIdAndDeletedAtIsNullOrderByParentTaskIdAscPositionAsc(
            UUID projectId, Pageable pageable);

    List<ProjectTask> findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(
            UUID projectId, UUID parentTaskId);

    Optional<ProjectTask> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

    // PATCH/DELETE /tasks/{taskId} carry no projectId in the URL — the service
    // resolves the task globally first, then authorizes via its own project.
    Optional<ProjectTask> findByIdAndDeletedAtIsNull(UUID id);

    // Reparent guard: "does this task have children?"
    boolean existsByParentTaskIdAndDeletedAtIsNull(UUID parentTaskId);

    // D8's cascade soft-delete.
    List<ProjectTask> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    // Backs ProjectsApi.dueOrOverdue — joins the owning project for the user_id
    // scope project_task itself doesn't carry.
    @Query("""
            SELECT t FROM ProjectTask t JOIN Project p ON t.projectId = p.id
            WHERE p.userId = :userId AND p.deletedAt IS NULL AND t.deletedAt IS NULL
              AND ((t.plannedStart <= :day AND t.plannedEnd >= :day)
                   OR (t.plannedEnd < :day AND t.status <> com.kairon.projects.domain.ProjectTaskStatus.DONE))
            ORDER BY t.plannedEnd ASC
            """)
    List<ProjectTask> findDueOrOverdue(@Param("userId") UUID userId, @Param("day") LocalDate day);

    // Backs ProjectsApi.openTasksInActiveProjects (M8 D3) — the broader pool of
    // "things that could be worked on" for the assistant's todo-suggestion context,
    // not just what's due/overdue.
    @Query("""
            SELECT t FROM ProjectTask t JOIN Project p ON t.projectId = p.id
            WHERE p.userId = :userId AND p.deletedAt IS NULL AND t.deletedAt IS NULL
              AND p.status IN (com.kairon.projects.domain.ProjectStatus.ACTIVE,
                                com.kairon.projects.domain.ProjectStatus.ON_HOLD)
              AND t.status <> com.kairon.projects.domain.ProjectTaskStatus.DONE
            ORDER BY p.name ASC, t.plannedEnd ASC NULLS LAST
            """)
    List<ProjectTask> findOpenInActiveProjects(@Param("userId") UUID userId);

    // Backs ProjectsApi.projectPeriodStats (M9 D4/D5/D15) — "completed" is
    // approximated as DONE with updatedAt in the window (no completed_at column
    // exists on project_task); categoryId/categoryName are denormalized via a
    // LEFT JOIN so a category-less or since-deleted-category project still groups.
    @Query("""
            SELECT new com.kairon.projects.api.ProjectsApi$ProjectPeriodStats(
                p.id, p.name, c.id, c.name, COUNT(t.id),
                COALESCE(SUM(t.estimateHours), 0), COALESCE(SUM(t.actualHours), 0))
            FROM ProjectTask t JOIN Project p ON t.projectId = p.id
                 LEFT JOIN ProjectCategory c ON p.categoryId = c.id
            WHERE p.userId = :userId AND p.deletedAt IS NULL AND t.deletedAt IS NULL
              AND t.status = com.kairon.projects.domain.ProjectTaskStatus.DONE
              AND t.updatedAt >= :fromInstant AND t.updatedAt < :toInstantExclusive
            GROUP BY p.id, p.name, c.id, c.name
            """)
    List<ProjectsApi.ProjectPeriodStats> projectPeriodStats(@Param("userId") UUID userId,
            @Param("fromInstant") Instant fromInstant, @Param("toInstantExclusive") Instant toInstantExclusive);

    // Backs ProjectsApi.tasksChangedSince (M11 D3/D4) — scoped via the owning project
    // since project_task carries no user_id of its own; deliberately filters neither
    // side's deletedAt so a soft-deleted task (its own, or cascaded from its project's
    // own delete) still surfaces as a tombstone.
    @Query("""
            SELECT t FROM ProjectTask t JOIN Project p ON t.projectId = p.id
            WHERE p.userId = :userId AND t.updatedAt >= :since
            ORDER BY t.updatedAt ASC, t.id ASC
            """)
    List<ProjectTask> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since,
            Pageable pageable);
}
