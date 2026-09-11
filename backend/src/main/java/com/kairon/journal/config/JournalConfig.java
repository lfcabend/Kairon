package com.kairon.journal.config;

import com.kairon.journal.app.JournalProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring local to the journal module. Mirrors {@code TodoConfig}. */
@Configuration
@EnableConfigurationProperties(JournalProperties.class)
public class JournalConfig {
}
