package com.kairon.projects.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.projects.domain.TaskDependency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    // D4's duplicate check.
    Optional<TaskDependency> findByPredecessorIdAndSuccessorId(UUID predecessorId, UUID successorId);

    // Loads every edge that touches this project, via a subquery join through
    // project_task since task_dependency carries no project_id of its own.
    @Query("""
            SELECT d FROM TaskDependency d
            WHERE d.predecessorId IN (
                SELECT t.id FROM ProjectTask t WHERE t.projectId = :projectId AND t.deletedAt IS NULL
            )
            """)
    List<TaskDependency> findByProjectId(@Param("projectId") UUID projectId);

    // D5 — called alongside a task's soft delete since a soft-deleted
    // project_task row still exists and never fires the FK cascade.
    @Modifying
    @Query("DELETE FROM TaskDependency d WHERE d.predecessorId = :taskId OR d.successorId = :taskId")
    void deleteAllForTask(@Param("taskId") UUID taskId);
}
