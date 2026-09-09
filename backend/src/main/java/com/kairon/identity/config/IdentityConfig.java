package com.kairon.identity.config;

import com.kairon.identity.app.RefreshTokenProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring local to the identity module. */
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class IdentityConfig {
}
