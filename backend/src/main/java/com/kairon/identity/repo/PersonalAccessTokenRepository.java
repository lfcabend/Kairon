package com.kairon.identity.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.identity.domain.PersonalAccessToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, UUID> {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<PersonalAccessToken> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update PersonalAccessToken t set t.revokedAt = :now "
            + "where t.id = :id and t.userId = :userId and t.revokedAt is null")
    int revokeByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
}
