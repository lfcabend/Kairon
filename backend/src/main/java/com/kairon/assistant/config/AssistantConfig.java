package com.kairon.assistant.config;

import com.kairon.assistant.app.AssistantProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring local to the assistant module. Mirrors {@code TodoConfig}. */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {
}
