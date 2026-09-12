package com.kairon.projects.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.projects.api.ProjectTaskPage;
import com.kairon.projects.api.ProjectTaskView;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for the project task endpoints. Controllers never
 * see the entity (ArchUnit-enforced); {@code ProjectTaskResponse.from} is the
 * one mapping point from the {@code api} view to the wire shape.
 */
final class ProjectTaskDtos {

    private ProjectTaskDtos() {
    }

    /**
     * {@code isMilestone} is a boxed {@code Boolean}, not a primitive: Jackson
     * fails ("Cannot map `null` into type `boolean`") when a record's primitive
     * component is simply absent from the JSON, since there's no field default
     * to fall back on the way a POJO setter would. Boxed types are the
     * established fix (same reason {@code TodoDtos}/{@code JournalDtos} use
     * {@code Integer}/{@code Boolean} for every optional field).
     */
    record CreateProjectTaskRequest(
            @NotBlank @Size(max = 300) String name,
            String description,
            UUID parentTaskId,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            @DecimalMin("0") BigDecimal estimateHours,
            Boolean isMilestone) {

        boolean isMilestoneOrDefault() {
            return isMilestone != null && isMilestone;
        }
    }

    /** Whole-form overwrite (D15) — every field is always sent, hence {@code @NotNull} throughout. */
    record PatchProjectTaskRequest(
            @NotBlank @Size(max = 300) String name,
            String description,
            @NotNull String status,
            UUID parentTaskId,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            @DecimalMin("0") BigDecimal estimateHours,
            @DecimalMin("0") BigDecimal actualHours,
            @NotNull @Min(0) @Max(100) Integer progressPercent,
            @NotNull Boolean isMilestone,
            Long expectedVersion) {
    }

    record ReorderRequest(UUID parentTaskId, @NotEmpty List<UUID> orderedIds) {
    }

    record ProjectTaskResponse(
            UUID id,
            UUID projectId,
            UUID parentTaskId,
            String name,
            String description,
            String status,
            boolean isMilestone,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            BigDecimal estimateHours,
            BigDecimal actualHours,
            int progressPercent,
            int position,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ProjectTaskResponse from(ProjectTaskView v) {
            return new ProjectTaskResponse(v.id(), v.projectId(), v.parentTaskId(), v.name(), v.description(),
                    v.status(), v.isMilestone(), v.plannedStart(), v.plannedEnd(), v.estimateHours(),
                    v.actualHours(), v.progressPercent(), v.position(), v.createdAt(), v.updatedAt(), v.version());
        }

        static List<ProjectTaskResponse> from(List<ProjectTaskView> views) {
            return views.stream().map(ProjectTaskResponse::from).toList();
        }
    }

    record ProjectTaskPageResponse(List<ProjectTaskResponse> content, int page, long totalElements) {

        static ProjectTaskPageResponse from(ProjectTaskPage p) {
            return new ProjectTaskPageResponse(ProjectTaskResponse.from(p.content()), p.page(), p.totalElements());
        }
    }
}
