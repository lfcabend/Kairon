package com.kairon.assistant.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

final class ProjectPlanDtos {

    private ProjectPlanDtos() {
    }

    record ProjectPlanRequest(@NotBlank String description, @NotNull LocalDate startDate, LocalDate targetDeadline) {
    }

    record AcceptProjectPlanRequest(List<String> excludedTaskKeys) {
    }
}
