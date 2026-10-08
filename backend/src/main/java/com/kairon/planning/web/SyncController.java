package com.kairon.planning.web;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.planning.app.SyncService;
import com.kairon.planning.app.SyncType;
import com.kairon.planning.web.SyncDtos.SyncResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/sync} — the Android app's one generic delta-read mechanism
 * (docs/milestones/M11-android-foundation.md D3). No 404/409 cases: an empty
 * {@link SyncDtos.SyncResponse} for a brand-new user, or an unrecognized
 * {@code types} entry (a 400, via {@link SyncType#parse}), are the only two
 * outcomes besides a normal {@code 200}.
 */
@RestController
@RequestMapping("/api/v1")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService sync;

    public SyncController(SyncService sync) {
        this.sync = sync;
    }

    @GetMapping("/sync")
    public SyncResponse sync(@CurrentUser UserId userId,
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) String types) {
        log.debug("GET /sync userId={} since={} types={}", userId.value(), since, types);
        Instant effectiveSince = since != null ? since : Instant.EPOCH;
        Set<SyncType> parsedTypes = SyncType.parse(parseTypes(types));
        return SyncResponse.from(sync.sync(userId, effectiveSince, parsedTypes));
    }

    private static Set<String> parseTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
