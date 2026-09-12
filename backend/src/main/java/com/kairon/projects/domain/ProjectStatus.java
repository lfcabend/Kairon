package com.kairon.projects.domain;

/**
 * Lifecycle of a {@link Project}. New projects start {@code PLANNING}.
 * {@code ARCHIVED} is excluded from the default list view (D10) and from
 * priority ranking (D20).
 */
public enum ProjectStatus {
    PLANNING,
    ACTIVE,
    ON_HOLD,
    DONE,
    ARCHIVED
}
