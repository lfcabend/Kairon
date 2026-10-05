package com.kairon.assistant.web;

import java.time.LocalDate;

import com.kairon.assistant.app.Horizon;
import com.kairon.assistant.app.SummaryPeriod;

import jakarta.validation.constraints.NotNull;

final class AssistantDtos {

    private AssistantDtos() {
    }

    record TodoSuggestionRequest(@NotNull LocalDate day, @NotNull Horizon horizon) {
    }

    record SummaryRequest(@NotNull SummaryPeriod period, @NotNull LocalDate date) {
    }

    record JournalReflectionRequest(@NotNull LocalDate weekOf) {
    }
}
