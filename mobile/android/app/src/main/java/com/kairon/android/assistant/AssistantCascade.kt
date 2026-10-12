package com.kairon.android.assistant

import com.kairon.android.client.model.DependencyOperationView
import com.kairon.android.client.model.PlannedTaskView
import com.kairon.android.client.model.TaskOperationView

/** Positional exclusion key for a project-edit diff's `taskOperations[index]` (matches the backend's own convention). */
fun taskOperationKey(index: Int): String = "task:$index"

/** Positional exclusion key for a project-edit diff's `dependencyOperations[index]`. */
fun dependencyOperationKey(index: Int): String = "dependency:$index"

/**
 * A project-plan's excluded parent cascades to its children, keyed by the
 * plan's own string `key` — mirrors the backend's accept-time cascade and
 * web's `ProjectPlanReview.effectiveExcluded` (M11.6 D9).
 */
fun effectiveExcludedTaskKeys(tasks: List<PlannedTaskView>, excludedKeys: Set<String>): Set<String> {
    val result = excludedKeys.toMutableSet()
    for (task in tasks) {
        val parentKey = task.parentKey
        val key = task.key
        if (parentKey != null && key != null && parentKey in result) {
            result += key
        }
    }
    return result
}

/**
 * The `key`s of new-task `ADD` operations whose own positional exclusion key
 * (`"task:<i>"`) is in [excludedKeys] — used to cascade to anything else in a
 * project-edit diff that references that new task by its semantic `key`.
 * Mirrors web's `ProjectEditReview.excludedNewTaskKeys` (M9.5 D1 / M11.6 D9).
 */
fun excludedNewTaskKeys(taskOperations: List<TaskOperationView>, excludedKeys: Set<String>): Set<String> {
    val result = mutableSetOf<String>()
    taskOperations.forEachIndexed { index, op ->
        val key = op.key
        if (op.op == "ADD" && key != null && taskOperationKey(index) in excludedKeys) {
            result += key
        }
    }
    return result
}

/** Whether the task operation at [index] is excluded, directly or via an excluded new-task parent. */
fun isTaskOperationExcluded(op: TaskOperationView, index: Int, excludedKeys: Set<String>, excludedNewKeys: Set<String>): Boolean {
    if (taskOperationKey(index) in excludedKeys) return true
    return op.parentRef != null && op.parentRef in excludedNewKeys
}

/** Whether a task operation is disabled (forced-excluded, checkbox not interactive) by an excluded new-task parent. */
fun isTaskOperationDisabled(op: TaskOperationView, excludedNewKeys: Set<String>): Boolean =
    op.parentRef != null && op.parentRef in excludedNewKeys

/** Whether a dependency operation is disabled because it references an excluded new task on either end. */
fun isDependencyOperationDisabled(op: DependencyOperationView, excludedNewKeys: Set<String>): Boolean {
    val predecessor = op.predecessorRef
    val successor = op.successorRef
    return (predecessor != null && predecessor in excludedNewKeys) || (successor != null && successor in excludedNewKeys)
}
