package com.kairon.common.web;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React SPA that is packaged into this jar under {@code classpath:/static/}
 * and makes client-side routes work: any GET that is not a real static file and is
 * not an API or Actuator call returns {@code index.html} so the browser-side router
 * can take over. See docs/DESIGN.md §3.3.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";

    private final Resource indexHtml;

    public SpaResourceConfig(@Value(STATIC_LOCATION + "index.html") Resource indexHtml) {
        this.indexHtml = indexHtml;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Content-hashed Vite assets never change under the same name: cache them hard.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC_LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable());

        // Everything else falls through to the SPA fallback resolver below.
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    /**
     * Returns the requested file when it exists; otherwise falls back to
     * {@code index.html} — except for {@code /api/**} and {@code /actuator/**},
     * which must keep their real 404/401 responses rather than serve the SPA.
     */
    private final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                return null;
            }
            return indexHtml.exists() ? indexHtml : null;
        }
    }
}
