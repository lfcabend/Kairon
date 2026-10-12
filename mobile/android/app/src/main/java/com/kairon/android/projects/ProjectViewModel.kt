package com.kairon.android.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.ProjectCategoryControllerApi
import com.kairon.android.client.api.ProjectControllerApi
import com.kairon.android.client.model.CreateProjectCategoryRequest
import com.kairon.android.client.model.PatchProjectCategoryRequest
import com.kairon.android.client.model.ProjectCategoryResponse
import com.kairon.android.client.model.ProjectResponse
import com.kairon.android.client.model.ReorderCategoriesRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.requireSuccessful
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ProjectViewModel"

enum class ProjectSortMode { CATEGORY, PRIORITY }

data class ProjectListUiState(
    val categories: List<ProjectCategoryResponse> = emptyList(),
    val projects: List<ProjectResponse> = emptyList(),
    val sortMode: ProjectSortMode = ProjectSortMode.CATEGORY,
    val loading: Boolean = true,
    val error: String? = null,
    val filterText: String = "",
) {
    /** Local (no refetch) name match for the quick filter — mirrors the web app's `matchesFilter`. */
    val filteredProjects: List<ProjectResponse>
        get() {
            val needle = filterText.trim()
            if (needle.isEmpty()) return projects
            return projects.filter { it.name?.contains(needle, ignoreCase = true) == true }
        }
}

/**
 * Backs [ProjectListScreen] — both categories and the project list are
 * live-fetched, not Room-backed (D10): a small/cheap-to-refetch set for
 * categories, and a view (grouped/sorted) that's simpler to just refetch
 * than to reconcile against an asynchronously-updated Room cache.
 */
@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectApi: ProjectControllerApi,
    private val categoryApi: ProjectCategoryControllerApi,
) : ViewModel() {

    private val categories = MutableStateFlow<List<ProjectCategoryResponse>>(emptyList())
    private val projects = MutableStateFlow<List<ProjectResponse>>(emptyList())
    private val sortMode = MutableStateFlow(ProjectSortMode.CATEGORY)
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val filterText = MutableStateFlow("")

    val state: StateFlow<ProjectListUiState> = combine(
        combine(categories, projects, sortMode, loading, error) { cats, projs, sort, isLoading, err ->
            ProjectListUiState(cats, projs, sort, isLoading, err)
        },
        filterText,
    ) { base, filter -> base.copy(filterText = filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectListUiState())

    init {
        refresh()
    }

    fun setSortMode(mode: ProjectSortMode) {
        sortMode.value = mode
        refresh()
    }

    /** Local filter over the already-fetched project list — no refetch (mirrors the web app's quick filter). */
    fun setFilterText(text: String) {
        filterText.value = text
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            runCatching {
                val cats = categoryApi.list3().bodyOrThrow(TAG, "listCategories")
                val projs = when (sortMode.value) {
                    ProjectSortMode.PRIORITY -> projectApi.priorityOrdered().bodyOrThrow(TAG, "priorityOrdered")
                    ProjectSortMode.CATEGORY -> projectApi.list1(pageSize = 200).bodyOrThrow(TAG, "list").content ?: emptyList()
                }
                cats to projs
            }.onSuccess { (cats, projs) ->
                categories.value = cats
                projects.value = projs
                error.value = null
                loading.value = false
            }.onFailure { ex ->
                AppLog.e(TAG, "refresh.failed", throwable = ex)
                error.value = ex.message ?: "Failed to load projects"
                loading.value = false
            }
        }
    }

    fun deleteProject(id: UUID) {
        viewModelScope.launch {
            runCatching {
                projectApi.delete2(id).requireSuccessful(TAG, "deleteProject")
            }.onSuccess {
                projects.value = projects.value.filterNot { it.id == id }
                AppLog.i(TAG, "deleteProject.success", mapOf("id" to id))
            }.onFailure { ex ->
                AppLog.e(TAG, "deleteProject.failed", mapOf("id" to id), throwable = ex)
                error.value = ex.message ?: "Failed to delete project"
            }
        }
    }

    fun createCategory(name: String, color: String?) {
        viewModelScope.launch {
            runCatching {
                categoryApi.create4(CreateProjectCategoryRequest(name = name, color = color)).bodyOrThrow(TAG, "createCategory")
            }.onSuccess {
                categories.value = categories.value + it
                AppLog.i(TAG, "createCategory.success", mapOf("id" to it.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "createCategory.failed", throwable = ex)
                error.value = ex.message ?: "Failed to create category"
            }
        }
    }

    fun renameCategory(category: ProjectCategoryResponse, name: String, color: String?) {
        viewModelScope.launch {
            runCatching {
                categoryApi.patch3(
                    category.id!!,
                    PatchProjectCategoryRequest(name = name, color = color, expectedVersion = category.version),
                ).bodyOrThrow(TAG, "renameCategory")
            }.onSuccess { updated ->
                categories.value = categories.value.map { if (it.id == updated.id) updated else it }
                AppLog.i(TAG, "renameCategory.success", mapOf("id" to updated.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "renameCategory.failed", mapOf("id" to category.id), throwable = ex)
                error.value = ex.message ?: "Failed to rename category"
            }
        }
    }

    fun deleteCategory(category: ProjectCategoryResponse) {
        viewModelScope.launch {
            runCatching {
                categoryApi.delete3(category.id!!).requireSuccessful(TAG, "deleteCategory")
            }.onSuccess {
                categories.value = categories.value.filterNot { it.id == category.id }
                refresh()
                AppLog.i(TAG, "deleteCategory.success", mapOf("id" to category.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "deleteCategory.failed", mapOf("id" to category.id), throwable = ex)
                error.value = ex.message ?: "Failed to delete category"
            }
        }
    }

    fun reorderCategories(orderedIds: List<UUID>) {
        viewModelScope.launch {
            runCatching {
                categoryApi.reorder3(ReorderCategoriesRequest(orderedIds)).bodyOrThrow(TAG, "reorderCategories")
            }.onSuccess { reordered ->
                categories.value = reordered
                AppLog.i(TAG, "reorderCategories.success", mapOf("count" to reordered.size))
            }.onFailure { ex ->
                AppLog.e(TAG, "reorderCategories.failed", throwable = ex)
                error.value = ex.message ?: "Failed to reorder categories"
            }
        }
    }
}
