package com.kairon.assistant.repo;

import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantSuggestedProjectEditRepository extends JpaRepository<AssistantSuggestedProjectEdit, UUID> {

    Optional<AssistantSuggestedProjectEdit> findByIdAndUserId(UUID id, UUID userId);

    Optional<AssistantSuggestedProjectEdit> findByRunId(UUID runId);
}
