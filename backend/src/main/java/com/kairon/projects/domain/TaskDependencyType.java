package com.kairon.projects.domain;

/**
 * All four PMI-style dependency types are modeled from day one
 * (docs/DATA_MODEL.md), but M5's web form only creates {@link #FS} edges
 * (docs/milestones/M5-gantt-dependencies.md D2).
 */
public enum TaskDependencyType {
    FS,
    SS,
    FF,
    SF
}
