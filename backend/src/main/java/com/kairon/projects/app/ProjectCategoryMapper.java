package com.kairon.projects.app;

import com.kairon.projects.domain.ProjectCategory;

/** Hand-rolled entity -> view mapping (D7), following the {@code todo}/{@code journal} precedent. */
final class ProjectCategoryMapper {

    private ProjectCategoryMapper() {
    }

    static ProjectCategoryView toView(ProjectCategory category) {
        return new ProjectCategoryView(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getPosition(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion());
    }
}
