package com.kairon.projects.api;

import java.util.List;

/**
 * A paginated slice of {@link ProjectView}s — the {@code api} package's own
 * paging shape so the web layer never has to import
 * {@code org.springframework.data.domain.Page} (docs/milestones/M3 §journal
 * precedent).
 */
public record ProjectPage(List<ProjectView> content, int page, long totalElements) {
}
