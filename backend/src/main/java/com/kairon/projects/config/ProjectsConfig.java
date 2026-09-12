package com.kairon.projects.config;

import com.kairon.projects.app.ProjectsProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring local to the projects module. Mirrors {@code TodoConfig}/{@code JournalConfig}. */
@Configuration
@EnableConfigurationProperties(ProjectsProperties.class)
public class ProjectsConfig {
}
