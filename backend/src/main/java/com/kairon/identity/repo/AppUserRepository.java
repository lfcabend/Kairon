package com.kairon.identity.repo;

import java.util.Optional;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
