package com.kairon.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.projects.app.ProjectCategoryView;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for {@code /api/v1/project-categories}. Controllers
 * never see the entity (ArchUnit-enforced); {@code ProjectCategoryResponse.from}
 * is the one mapping point from the {@code app} view to the wire shape.
 */
final class ProjectCategoryDtos {

    private ProjectCategoryDtos() {
    }

    private static final String HEX_COLOR = "^#[0-9a-fA-F]{6}$";

    record CreateProjectCategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = HEX_COLOR) String color) {
    }

    record PatchProjectCategoryRequest(
            @Size(max = 100) String name,
            @Pattern(regexp = HEX_COLOR) String color,
            Long expectedVersion) {
    }

    record ReorderRequest(@NotEmpty List<UUID> orderedIds) {
    }

    record ProjectCategoryResponse(
            UUID id,
            String name,
            String color,
            int position,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ProjectCategoryResponse from(ProjectCategoryView v) {
            return new ProjectCategoryResponse(v.id(), v.name(), v.color(), v.position(),
                    v.createdAt(), v.updatedAt(), v.version());
        }

        static List<ProjectCategoryResponse> from(List<ProjectCategoryView> views) {
            return views.stream().map(ProjectCategoryResponse::from).toList();
        }
    }
}
