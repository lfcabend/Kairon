package com.kairon.android.assistant

import com.kairon.android.client.model.DependencyOperationView
import com.kairon.android.client.model.PlannedTaskView
import com.kairon.android.client.model.TaskOperationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCascadeTest {

    @Test
    fun excludingAParentCascadesToItsDirectChildOnly() {
        val tasks = listOf(
            PlannedTaskView(key = "a", parentKey = null, name = "Parent"),
            PlannedTaskView(key = "b", parentKey = "a", name = "Child"),
            PlannedTaskView(key = "c", parentKey = null, name = "Other top-level"),
        )

        val effective = effectiveExcludedTaskKeys(tasks, setOf("a"))

        assertTrue(effective.containsAll(setOf("a", "b")))
        assertFalse(effective.contains("c"))
    }

    @Test
    fun excludingAChildDirectlyDoesNotAffectItsSibling() {
        val tasks = listOf(
            PlannedTaskView(key = "a", parentKey = null, name = "Parent"),
            PlannedTaskView(key = "b", parentKey = "a", name = "Child 1"),
            PlannedTaskView(key = "c", parentKey = "a", name = "Child 2"),
        )

        val effective = effectiveExcludedTaskKeys(tasks, setOf("b"))

        assertEquals(setOf("b"), effective)
    }

    @Test
    fun excludingAMultiLevelPlanCascadesOnlyOneLevelDeepPerTheBackendsOwnShape() {
        // The backend's project-plan tree is capped at two levels, so a
        // "grandchild" referencing its grandparent's key as its own parentKey
        // never actually occurs — this confirms the cascade still works
        // correctly when every task's parentKey points at a real exclusion.
        val tasks = listOf(
            PlannedTaskView(key = "root", parentKey = null, name = "Root"),
            PlannedTaskView(key = "mid", parentKey = "root", name = "Mid"),
            PlannedTaskView(key = "leaf", parentKey = "mid", name = "Leaf"),
        )

        val effective = effectiveExcludedTaskKeys(tasks, setOf("root", "mid"))

        assertEquals(setOf("root", "mid", "leaf"), effective)
    }

    @Test
    fun excludedNewTaskKeysOnlyReturnsAddOperationsExcludedByTheirPositionalKey() {
        val operations = listOf(
            TaskOperationView(op = "ADD", key = "new-1", name = "New task"),
            TaskOperationView(op = "UPDATE", existingTaskId = java.util.UUID.randomUUID(), name = "Existing"),
            TaskOperationView(op = "ADD", key = "new-2", name = "Another new task"),
        )

        val excludedNew = excludedNewTaskKeys(operations, excludedKeys = setOf(taskOperationKey(0)))

        assertEquals(setOf("new-1"), excludedNew)
    }

    @Test
    fun aTaskOperationReferencingAnExcludedNewTaskAsParentIsExcludedAndDisabled() {
        val operations = listOf(
            TaskOperationView(op = "ADD", key = "parent-key", name = "Parent task"),
            TaskOperationView(op = "ADD", key = "child-key", parentRef = "parent-key", name = "Child task"),
        )
        val excludedNew = excludedNewTaskKeys(operations, excludedKeys = setOf(taskOperationKey(0)))

        assertTrue(isTaskOperationExcluded(operations[1], 1, excludedKeys = setOf(taskOperationKey(0)), excludedNewKeys = excludedNew))
        assertTrue(isTaskOperationDisabled(operations[1], excludedNew))
    }

    @Test
    fun aTaskOperationExcludedDirectlyIsExcludedButNotDisabled() {
        val operations = listOf(TaskOperationView(op = "ADD", key = "solo", name = "Solo task"))
        val excludedKeys = setOf(taskOperationKey(0))
        val excludedNew = excludedNewTaskKeys(operations, excludedKeys)

        assertTrue(isTaskOperationExcluded(operations[0], 0, excludedKeys, excludedNew))
        assertFalse(isTaskOperationDisabled(operations[0], excludedNew))
    }

    @Test
    fun aDependencyReferencingAnExcludedNewTaskOnEitherEndIsDisabled() {
        val taskOperations = listOf(TaskOperationView(op = "ADD", key = "new-task", name = "New"))
        val excludedNew = excludedNewTaskKeys(taskOperations, excludedKeys = setOf(taskOperationKey(0)))

        val predecessorExcluded = DependencyOperationView(op = "ADD", predecessorRef = "new-task", successorRef = "other")
        val successorExcluded = DependencyOperationView(op = "ADD", predecessorRef = "other", successorRef = "new-task")
        val neitherExcluded = DependencyOperationView(op = "ADD", predecessorRef = "x", successorRef = "y")

        assertTrue(isDependencyOperationDisabled(predecessorExcluded, excludedNew))
        assertTrue(isDependencyOperationDisabled(successorExcluded, excludedNew))
        assertFalse(isDependencyOperationDisabled(neitherExcluded, excludedNew))
    }
}
