package com.kairon.projects.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Projects module settings ({@code kairon.projects.*}):
 *
 * <ul>
 *   <li>{@code task-list-default-size} — the server-side default page size for
 *       {@code GET /projects/{id}/tasks} when the client doesn't specify one,
 *       backing D1's "one big page" client behaviour.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "kairon.projects")
public record ProjectsProperties(int taskListDefaultSize) {

    public ProjectsProperties {
        if (taskListDefaultSize <= 0) {
            taskListDefaultSize = 200;
        }
    }
}
