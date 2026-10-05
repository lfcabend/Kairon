package com.kairon.assistant.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

final class ProjectEditDtos {

    private ProjectEditDtos() {
    }

    record ProjectEditRequest(@NotNull UUID projectId, @NotBlank String description) {
    }

    record AcceptProjectEditRequest(List<String> excludedOperationKeys) {
    }
}
