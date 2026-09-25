package com.kairon.assistant.repo;

import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProject;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantSuggestedProjectRepository extends JpaRepository<AssistantSuggestedProject, UUID> {

    Optional<AssistantSuggestedProject> findByIdAndUserId(UUID id, UUID userId);

    Optional<AssistantSuggestedProject> findByRunId(UUID runId);
}
