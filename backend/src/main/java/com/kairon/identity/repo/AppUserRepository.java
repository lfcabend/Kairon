package com.kairon.identity.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    // Backs UserAccountApi.usersOptedIntoExecutionSummaries (M9 D7) — the
    // @Scheduled sweep's own cross-user use, never called from a request. jsonb
    // path operators aren't expressible via derived-query syntax or plain JPQL.
    @Query(value = """
            SELECT id FROM app_user
            WHERE (preferences->'assistant'->'executionSummaries'->>'enabled')::boolean = true
            """, nativeQuery = true)
    List<UUID> findIdsOptedIntoExecutionSummaries();
}
