package com.kairon.planning.config;

import com.kairon.planning.app.SyncProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring local to the planning module. Mirrors {@code TodoConfig}/{@code JournalConfig}. */
@Configuration
@EnableConfigurationProperties(SyncProperties.class)
public class PlanningConfig {
}
