package com.kairon.todo.config;

import com.kairon.todo.app.TodoProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring local to the todo module. Mirrors {@code IdentityConfig}. */
@Configuration
@EnableConfigurationProperties(TodoProperties.class)
public class TodoConfig {
}
