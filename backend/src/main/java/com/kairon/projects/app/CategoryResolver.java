package com.kairon.projects.app;

import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves a model-proposed category reference against the user's existing
 * categories, shared by {@link ProjectPlanImportService} (M8.5 D10) and
 * {@link ProjectEditApplyService} (M9.5) — extracted here rather than
 * duplicated since both callers need the exact same "an existing id wins
 * outright; otherwise create a new category, falling back to a same-name
 * race" logic.
 */
@Component
class CategoryResolver {

    private static final Logger log = LoggerFactory.getLogger(CategoryResolver.class);

    private final ProjectCategoryService categoryService;

    CategoryResolver(ProjectCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * {@code categoryId} (an existing category matched earlier, in the
     * assistant module) wins outright. Otherwise, a non-blank
     * {@code newCategoryName} is created fresh; on a name conflict — someone
     * created a same-named category in the time between proposal and
     * accept — the existing one is reused instead of failing the whole
     * operation.
     */
    UUID resolve(UserId userId, UUID categoryId, String newCategoryName) {
        if (categoryId != null) {
            return categoryId;
        }
        if (newCategoryName == null || newCategoryName.isBlank()) {
            return null;
        }
        try {
            UUID created = categoryService.create(userId,
                    new ProjectCategoryService.CreateCommand(newCategoryName, null)).id();
            log.info("Created category '{}' userId={}", newCategoryName, userId.value());
            return created;
        } catch (ApiException e) {
            return categoryService.list(userId).stream()
                    .filter(c -> c.name().equalsIgnoreCase(newCategoryName))
                    .map(ProjectCategoryView::id)
                    .findFirst()
                    .orElseThrow(() -> e);
        }
    }
}
