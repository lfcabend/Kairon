package com.kairon.identity.dev;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a known dev account exists when running under the {@code local} profile,
 * so the Playwright happy path (and manual poking) has something to log in as.
 * Never active in {@code test} or production — it is {@code @Profile("local")} and
 * additionally gated on {@code kairon.dev.seed-user}. Lives in {@code identity}
 * because it touches {@code AppUser}; the architecture rules keep that dependency
 * inside the module.
 */
@Component
@Profile("local")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;
    private final String displayName;

    public DevDataSeeder(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${kairon.dev.seed-user:true}") boolean enabled,
            @Value("${kairon.dev.seed-email:dev@kairon.local}") String email,
            @Value("${kairon.dev.seed-password:dev-password-please}") String password,
            @Value("${kairon.dev.seed-display-name:Dev User}") String displayName) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        String normalized = AppUser.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            return;
        }
        users.save(AppUser.register(normalized, passwordEncoder.encode(password), displayName, "UTC"));
        log.info("Seeded dev account {} (local profile)", normalized);
    }
}
