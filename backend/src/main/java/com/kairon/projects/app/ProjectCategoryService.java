package com.kairon.projects.app;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.domain.ProjectCategory;
import com.kairon.projects.repo.ProjectCategoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The category module's application service: CRUD + {@code :reorder} for a
 * user's {@link ProjectCategory} set. Every method takes a {@link UserId} and
 * 404s on someone else's row.
 */
@Service
@Transactional(readOnly = true)
public class ProjectCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ProjectCategoryService.class);
    private static final int POSITION_GAP = 100;
    private static final String DEFAULT_COLOR = "#6366f1";

    private final ProjectCategoryRepository categories;

    public ProjectCategoryService(ProjectCategoryRepository categories) {
        this.categories = categories;
    }

    public record CreateCommand(String name, String color) {
    }

    public record PatchCommand(String name, String color, Long expectedVersion) {
    }

    public List<ProjectCategoryView> list(UserId userId) {
        List<ProjectCategoryView> views = categories.findByUserIdOrderByPositionAsc(userId.value())
                .stream()
                .map(ProjectCategoryMapper::toView)
                .toList();
        log.debug("Listed {} project categor(y/ies) userId={}", views.size(), userId.value());
        return views;
    }

    @Transactional
    public ProjectCategoryView create(UserId userId, CreateCommand command) {
        String name = requireName(command.name());
        if (categories.existsByUserIdAndName(userId.value(), name)) {
            log.warn("Create rejected: category name conflict userId={} name=\"{}\"", userId.value(), name);
            throw ApiException.conflict("A category named \"" + name + "\" already exists.");
        }
        int position = nextPosition(userId.value());
        String color = command.color() != null ? command.color() : DEFAULT_COLOR;
        ProjectCategory saved = categories.save(ProjectCategory.create(userId.value(), name, color, position));
        log.info("Created project category {} userId={}", saved.getId(), userId.value());
        return ProjectCategoryMapper.toView(saved);
    }

    @Transactional
    public ProjectCategoryView patch(UserId userId, UUID id, PatchCommand command) {
        ProjectCategory category = require(userId, id);
        if (command.expectedVersion() != null && command.expectedVersion() != category.getVersion()) {
            log.warn("Patch rejected: version conflict on project category {} userId={} (expected {}, actual {})",
                    id, userId.value(), command.expectedVersion(), category.getVersion());
            throw ApiException.conflict(
                    "This category was modified by another request. Reload and try again.");
        }
        if (command.name() != null) {
            String name = requireName(command.name());
            if (categories.existsByUserIdAndNameAndIdNot(userId.value(), name, id)) {
                log.warn("Patch rejected: category name conflict userId={} name=\"{}\"", userId.value(), name);
                throw ApiException.conflict("A category named \"" + name + "\" already exists.");
            }
            category.rename(name);
        }
        if (command.color() != null) {
            category.recolor(command.color());
        }
        log.info("Patched project category {} userId={}", id, userId.value());
        return ProjectCategoryMapper.toView(category);
    }

    @Transactional
    public List<ProjectCategoryView> reorder(UserId userId, List<UUID> orderedIds) {
        List<ProjectCategory> current = categories.findByUserIdOrderByPositionAsc(userId.value());
        Map<UUID, ProjectCategory> byId = new HashMap<>();
        for (ProjectCategory category : current) {
            byId.put(category.getId(), category);
        }
        if (orderedIds.size() != byId.size() || !byId.keySet().equals(new HashSet<>(orderedIds))) {
            log.warn("Reorder rejected: userId={} sent {} id(s), user holds {} categor(y/ies)",
                    userId.value(), orderedIds.size(), byId.size());
            throw ApiException.badRequest("`orderedIds` must list exactly the user's current categories.");
        }
        int position = POSITION_GAP;
        List<ProjectCategoryView> result = new java.util.ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            ProjectCategory category = byId.get(id);
            category.moveTo(position);
            result.add(ProjectCategoryMapper.toView(category));
            position += POSITION_GAP;
        }
        log.info("Reordered {} project categor(y/ies) userId={}", result.size(), userId.value());
        return result;
    }

    @Transactional
    public void delete(UserId userId, UUID id) {
        ProjectCategory category = require(userId, id);
        categories.delete(category);
        log.info("Deleted project category {} userId={}", id, userId.value());
    }

    private ProjectCategory require(UserId userId, UUID id) {
        return categories.findByIdAndUserId(id, userId.value())
                .orElseThrow(() -> {
                    log.debug("Project category {} not visible to userId={} (missing or foreign)",
                            id, userId.value());
                    return ApiException.notFound("Project category not found.");
                });
    }

    private int nextPosition(UUID userId) {
        return categories.findByUserIdOrderByPositionAsc(userId)
                .stream()
                .mapToInt(ProjectCategory::getPosition)
                .max()
                .orElse(0) + POSITION_GAP;
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("Name must not be blank.");
        }
        if (trimmed.length() > 100) {
            throw ApiException.badRequest("Name must be at most 100 characters.");
        }
        return trimmed;
    }
}
