package com.kairon.assistant.web;

import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.web.ProjectEditDtos.ProjectEditRequest;
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
public class ProjectEditController {

    private static final Logger log = LoggerFactory.getLogger(ProjectEditController.class);

    private final AssistantRunService assistantRuns;

    public ProjectEditController(AssistantRunService assistantRuns) {
        this.assistantRuns = assistantRuns;
    }

    @PostMapping("/assistant/project-edits")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView generate(@CurrentUser UserId userId, @Valid @RequestBody ProjectEditRequest req) {
        log.debug("POST /assistant/project-edits userId={} projectId={}", userId.value(), req.projectId());
        return assistantRuns.requestProjectEdit(userId, req.projectId(), req.description());
    }
}
