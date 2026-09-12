package com.kairon.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class DeployInfoContributorTest {

    @Test
    void contributesTheImageAndDeployTimeUnderADeployKey() {
        DeployInfoContributor contributor = new DeployInfoContributor(
                "ghcr.io/lfcabend/kairon:abc123", "2026-09-12T10:00:00Z");

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.get("deploy")).isEqualTo(Map.of(
                "image", "ghcr.io/lfcabend/kairon:abc123",
                "deployedAt", "2026-09-12T10:00:00Z"));
    }

    @Test
    void defaultsToUnknownWhenTheEnvVarsAreAbsent() {
        DeployInfoContributor contributor = new DeployInfoContributor("unknown", "unknown");

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.get("deploy")).isEqualTo(Map.of("image", "unknown", "deployedAt", "unknown"));
    }
}
