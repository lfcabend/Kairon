package com.kairon.projects.api;

import java.time.LocalDate;
import java.util.List;

import com.kairon.common.security.UserId;

/**
 * The projects module's public port. Other modules depend only on this
 * interface and the DTOs in this package, never on {@code projects.domain} or
 * {@code projects.repo} (ArchUnit-enforced — docs/DESIGN.md §3.1). Implemented
 * by {@code com.kairon.projects.app.ProjectTaskService}.
 *
 * <p>Kept deliberately minimal (D6): sized to what M6's Today screen needs.
 */
public interface ProjectsApi {

    /**
     * Tasks whose {@code [plannedStart, plannedEnd]} window contains {@code day},
     * or that are overdue ({@code plannedEnd < day}) and not {@code DONE}.
     */
    List<ProjectTaskView> dueOrOverdue(UserId userId, LocalDate day);
}
