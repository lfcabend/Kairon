package com.kairon.assistant.web;

import java.time.LocalDate;

import com.kairon.assistant.app.Horizon;

import jakarta.validation.constraints.NotNull;

final class AssistantDtos {

    private AssistantDtos() {
    }

    record TodoSuggestionRequest(@NotNull LocalDate day, @NotNull Horizon horizon) {
    }
}
