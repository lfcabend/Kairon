package com.kairon.assistant.repo;

import java.util.List;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantBatch;
import com.kairon.assistant.domain.AssistantBatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantBatchRepository extends JpaRepository<AssistantBatch, UUID> {

    // Backs SummaryBatchPollingScheduler's sweep (M10 D22) — every batch not yet ENDED.
    List<AssistantBatch> findByStatusNot(AssistantBatchStatus status);
}
