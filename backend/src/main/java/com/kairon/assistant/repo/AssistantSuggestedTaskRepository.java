package com.kairon.assistant.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedTask;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantSuggestedTaskRepository extends JpaRepository<AssistantSuggestedTask, UUID> {

    Optional<AssistantSuggestedTask> findByIdAndUserId(UUID id, UUID userId);

    List<AssistantSuggestedTask> findByRunIdOrderByPositionAsc(UUID runId);
}
