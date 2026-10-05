package com.kairon.assistant.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * One item of {@link ProjectEditPayload#reorderOperations()} — a full new
 * sibling order for one parent group.
 */
public record ReorderOperationPayload(
        @JsonPropertyDescription("The parent task's real id whose children are being reordered; "
                + "omit for the top-level group.")
        String parentRef,
        @JsonPropertyDescription("Every current sibling in this group, in the new order, by real id "
                + "(or a key from a task also being added in this response). Must list exactly that "
                + "group's full membership.")
        List<String> orderedRefs) {
}
