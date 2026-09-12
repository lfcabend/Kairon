package com.kairon.projects.api;

import java.util.List;

/** A paginated slice of {@link ProjectTaskView}s — the {@code api} package's own paging shape. */
public record ProjectTaskPage(List<ProjectTaskView> content, int page, long totalElements) {
}
