package com.kairon.assistant.web;

import java.util.UUID;

import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.web.AssistantDtos.TodoSuggestionRequest;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AssistantRunController {

    private static final Logger log = LoggerFactory.getLogger(AssistantRunController.class);

    private final AssistantRunService assistantRuns;

    public AssistantRunController(AssistantRunService assistantRuns) {
        this.assistantRuns = assistantRuns;
    }

    @PostMapping("/assistant/todo-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView suggestTodos(@CurrentUser UserId userId, @Valid @RequestBody TodoSuggestionRequest req) {
        log.debug("POST /assistant/todo-suggestions userId={} day={} horizon={}",
                userId.value(), req.day(), req.horizon());
        return assistantRuns.requestTodoSuggestions(userId, req.day(), req.horizon());
    }

    @GetMapping("/assistant/runs/{id}")
    public AssistantRunView getRun(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("GET /assistant/runs/{} userId={}", id, userId.value());
        return assistantRuns.get(userId, id);
    }

    @DeleteMapping("/assistant/runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRun(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("DELETE /assistant/runs/{} userId={}", id, userId.value());
        assistantRuns.delete(userId, id);
    }
}
