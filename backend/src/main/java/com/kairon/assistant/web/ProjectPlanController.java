package com.kairon.assistant.web;

import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.web.ProjectPlanDtos.ProjectPlanRequest;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectPlanController {

    private static final Logger log = LoggerFactory.getLogger(ProjectPlanController.class);

    private final AssistantRunService assistantRuns;

    public ProjectPlanController(AssistantRunService assistantRuns) {
        this.assistantRuns = assistantRuns;
    }

    @PostMapping("/assistant/project-plan")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView generate(@CurrentUser UserId userId, @Valid @RequestBody ProjectPlanRequest req) {
        log.debug("POST /assistant/project-plan userId={} startDate={}", userId.value(), req.startDate());
        return assistantRuns.requestProjectPlan(userId, req.description(), req.startDate(), req.targetDeadline());
    }
}
