package com.kairon.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared-kernel beans that don't belong to a narrower config. A single injectable
 * {@link Clock} keeps time testable across the modules.
 */
@Configuration
public class CommonConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
