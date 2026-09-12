package com.kairon.projects.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.projects.api.ProjectPage;
import com.kairon.projects.api.ProjectView;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for {@code /api/v1/projects}. Controllers never see
 * the entity (ArchUnit-enforced); {@code ProjectResponse.from} is the one
 * mapping point from the {@code api} view to the wire shape.
 */
final class ProjectDtos {

    private ProjectDtos() {
    }

    private static final String HEX_COLOR = "^#[0-9a-fA-F]{6}$";

    record CreateProjectRequest(
            UUID categoryId,
            @NotBlank @Size(max = 200) String name,
            String description,
            String size,
            @Pattern(regexp = HEX_COLOR) String color,
            LocalDate startDate,
            LocalDate endDate) {
    }

    /** Whole-form overwrite (D15) — excludes {@code priorityRank} (D18). */
    record PatchProjectRequest(
            UUID categoryId,
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotNull String status,
            String size,
            @Pattern(regexp = HEX_COLOR) String color,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualStart,
            LocalDate actualEnd,
            Long expectedVersion) {
    }

    record ReorderRequest(@NotEmpty List<UUID> orderedIds) {
    }

    record ProjectResponse(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            String status,
            String size,
            int priorityRank,
            String color,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualStart,
            LocalDate actualEnd,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ProjectResponse from(ProjectView v) {
            return new ProjectResponse(v.id(), v.categoryId(), v.name(), v.description(), v.status(),
                    v.size(), v.priorityRank(), v.color(), v.startDate(), v.endDate(), v.actualStart(),
                    v.actualEnd(), v.createdAt(), v.updatedAt(), v.version());
        }

        static List<ProjectResponse> from(List<ProjectView> views) {
            return views.stream().map(ProjectResponse::from).toList();
        }
    }

    record ProjectPageResponse(List<ProjectResponse> content, int page, long totalElements) {

        static ProjectPageResponse from(ProjectPage p) {
            return new ProjectPageResponse(ProjectResponse.from(p.content()), p.page(), p.totalElements());
        }
    }
}
