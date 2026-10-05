package com.kairon.assistant.app;

import java.util.List;

/** A full new sibling order for one parent group in a proposed edit diff. */
public record ReorderOperationView(String parentRef, List<String> orderedRefs) {
}
