package com.kairon.meta;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The M0 walking-skeleton endpoint: proves a request reaches the app and a
 * response carrying the build version comes back. See docs/ROADMAP.md (M0).
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final String version;

    public PingController(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "dev";
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse(true, version);
    }

    public record PingResponse(boolean pong, String version) {
    }
}
