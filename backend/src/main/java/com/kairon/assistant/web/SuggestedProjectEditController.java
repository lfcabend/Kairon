package com.kairon.assistant.web;

import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantSuggestedProjectEditView;
import com.kairon.assistant.app.SuggestedProjectEditService;
import com.kairon.assistant.web.ProjectEditDtos.AcceptProjectEditRequest;
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
public class SuggestedProjectEditController {

    private static final Logger log = LoggerFactory.getLogger(SuggestedProjectEditController.class);

    private final SuggestedProjectEditService suggestedProjectEdits;

    public SuggestedProjectEditController(SuggestedProjectEditService suggestedProjectEdits) {
        this.suggestedProjectEdits = suggestedProjectEdits;
    }

    @PostMapping("/assistant/suggested-project-edits/{id}:accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView accept(@CurrentUser UserId userId, @PathVariable UUID id,
            @RequestBody(required = false) AcceptProjectEditRequest req) {
        List<String> excludedOperationKeys = req == null ? List.of() : req.excludedOperationKeys();
        log.debug("POST /assistant/suggested-project-edits/{}:accept userId={} excluded={}",
                id, userId.value(), excludedOperationKeys.size());
        return suggestedProjectEdits.accept(userId, id, excludedOperationKeys);
    }

    @PostMapping("/assistant/suggested-project-edits/{id}:dismiss")
    public AssistantSuggestedProjectEditView dismiss(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("POST /assistant/suggested-project-edits/{}:dismiss userId={}", id, userId.value());
        return suggestedProjectEdits.dismiss(userId, id);
    }
}
