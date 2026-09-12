package com.kairon.projects.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
