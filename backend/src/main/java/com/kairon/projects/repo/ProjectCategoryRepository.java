package com.kairon.projects.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.projects.domain.ProjectCategory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCategoryRepository extends JpaRepository<ProjectCategory, UUID> {

    List<ProjectCategory> findByUserIdOrderByPositionAsc(UUID userId);

    Optional<ProjectCategory> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID selfId);
}
