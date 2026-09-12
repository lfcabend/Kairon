package com.kairon.meta;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Adds the two facts about a running instance that can never be known at build
 * time — the image it was started from and when this deploy happened — to
 * {@code /actuator/info}, alongside the {@code build} section Spring Boot already
 * contributes from {@code build-info.properties}. Both come from env vars the Helm
 * chart sets on the container (deploy/helm/kairon/templates/deployment.yaml); a
 * host run (no Helm) leaves them at "unknown". Backs the web About page
 * (docs/DESIGN.md §11).
 */
@Component
public class DeployInfoContributor implements InfoContributor {

    private final String image;
    private final String deployedAt;

    public DeployInfoContributor(
            @Value("${KAIRON_IMAGE_REF:unknown}") String image,
            @Value("${KAIRON_DEPLOYED_AT:unknown}") String deployedAt) {
        this.image = image;
        this.deployedAt = deployedAt;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("deploy", Map.of("image", image, "deployedAt", deployedAt));
    }
}
