package com.kairon.planning.web;

import java.time.LocalDate;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.planning.app.PlanningService;
import com.kairon.planning.web.PlanningDtos.PromoteRequest;
import com.kairon.planning.web.PlanningDtos.TodayResponse;
import com.kairon.todo.api.TodoItemView;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/planning/today} — the Today screen's backend
 * (docs/milestones/M6-today.md §4.4). The class mapping is {@code /api/v1}
 * (same reasoning as {@code TodoController}/{@code ProjectTaskController}) so
 * the AIP-style {@code :promote} suffix resolves.
 */
@RestController
@RequestMapping("/api/v1")
public class PlanningController {

    private static final Logger log = LoggerFactory.getLogger(PlanningController.class);

    private final PlanningService planning;

    public PlanningController(PlanningService planning) {
        this.planning = planning;
    }

    @GetMapping("/planning/today")
    public TodayResponse today(@CurrentUser UserId userId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        log.debug("GET /planning/today userId={} date={}", userId.value(), date);
        return TodayResponse.from(planning.today(userId, date));
    }

    @PostMapping("/planning/today:promote")
    @ResponseStatus(HttpStatus.CREATED)
    public TodoItemView promote(@CurrentUser UserId userId, @Valid @RequestBody PromoteRequest request) {
        log.debug("POST /planning/today:promote userId={} projectTaskId={} day={}",
                userId.value(), request.projectTaskId(), request.day());
        return planning.promote(userId, request.projectTaskId(), request.day());
    }
}
