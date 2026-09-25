package com.kairon.assistant.web;

import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantSuggestedProjectView;
import com.kairon.assistant.app.SuggestedProjectService;
import com.kairon.assistant.web.ProjectPlanDtos.AcceptProjectPlanRequest;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SuggestedProjectController {

    private static final Logger log = LoggerFactory.getLogger(SuggestedProjectController.class);

    private final SuggestedProjectService suggestedProjects;

    public SuggestedProjectController(SuggestedProjectService suggestedProjects) {
        this.suggestedProjects = suggestedProjects;
    }

    @PostMapping("/assistant/suggested-projects/{id}:accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView accept(@CurrentUser UserId userId, @PathVariable UUID id,
            @RequestBody(required = false) AcceptProjectPlanRequest req) {
        List<String> excludedTaskKeys = req == null ? List.of() : req.excludedTaskKeys();
        log.debug("POST /assistant/suggested-projects/{}:accept userId={} excluded={}",
                id, userId.value(), excludedTaskKeys.size());
        return suggestedProjects.accept(userId, id, excludedTaskKeys);
    }

    @PostMapping("/assistant/suggested-projects/{id}:dismiss")
    public AssistantSuggestedProjectView dismiss(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("POST /assistant/suggested-projects/{}:dismiss userId={}", id, userId.value());
        return suggestedProjects.dismiss(userId, id);
    }
}
