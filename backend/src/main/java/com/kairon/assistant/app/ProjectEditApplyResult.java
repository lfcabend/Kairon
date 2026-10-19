package com.kairon.assistant.app;

import java.util.List;

import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;

/**
 * The project's resulting state after {@link McpAssistantService#applyProjectEdit}
 * applies a diff directly — no separate accept step, unlike the
 * {@code submitProjectEdit}/{@code PROPOSED} path. The public,
 * cross-package-visible shape {@code assistant.web.McpAssistantController}
 * maps into its own response DTO, same precedent {@link RenderedContext}
 * already set.
 */
public record ProjectEditApplyResult(ProjectView project, List<ProjectTaskView> tasks) {
}
