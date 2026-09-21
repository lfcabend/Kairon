package com.kairon.assistant.web;

import java.util.UUID;

import com.kairon.assistant.app.AssistantSuggestedTaskView;
import com.kairon.assistant.app.SuggestedTaskService;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoItemView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SuggestedTaskController {

    private static final Logger log = LoggerFactory.getLogger(SuggestedTaskController.class);

    private final SuggestedTaskService suggestedTasks;

    public SuggestedTaskController(SuggestedTaskService suggestedTasks) {
        this.suggestedTasks = suggestedTasks;
    }

    @PostMapping("/assistant/suggested-tasks/{id}:accept")
    @ResponseStatus(HttpStatus.CREATED)
    public TodoItemView accept(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("POST /assistant/suggested-tasks/{}:accept userId={}", id, userId.value());
        return suggestedTasks.accept(userId, id);
    }

    @PostMapping("/assistant/suggested-tasks/{id}:dismiss")
    public AssistantSuggestedTaskView dismiss(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("POST /assistant/suggested-tasks/{}:dismiss userId={}", id, userId.value());
        return suggestedTasks.dismiss(userId, id);
    }
}
