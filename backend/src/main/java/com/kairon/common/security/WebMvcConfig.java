package com.kairon.common.security;

import java.util.List;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link CurrentUserArgumentResolver} with Spring MVC. The SPA
 * resource handling lives separately in {@code com.kairon.common.web.SpaResourceConfig}.
 *
 * <p>The static initializer tells springdoc to omit {@code @CurrentUser}
 * parameters from the generated OpenAPI document — without it, every
 * handler's resolved-from-the-JWT {@code userId} showed up as a spurious
 * required query parameter (harmless for the hand-written web client, which
 * never reads the spec, but a real defect once M11's Android client is
 * generated from this spec with OpenAPI Generator — docs/milestones/
 * M11-android-foundation.md).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
