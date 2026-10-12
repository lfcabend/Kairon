package com.kairon.android.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.ProjectCategoryControllerApi
import com.kairon.android.client.api.ProjectControllerApi
import com.kairon.android.client.model.CreateProjectCategoryRequest
import com.kairon.android.client.model.PatchProjectCategoryRequest
import com.kairon.android.client.model.PatchProjectRequest
import com.kairon.android.client.model.ProjectCategoryResponse
import com.kairon.android.client.model.ProjectResponse
import com.kairon.android.client.model.ReorderCategoriesRequest
import com.kairon.android.client.model.ReorderProjectsRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.AppError
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.requireSuccessful
import com.kairon.android.core.network.toAppError
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

/** Mirrors the web app's STATUS_OPTIONS (ProjectListPage.tsx) — `null` means "any". */
val PROJECT_STATUS_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Active (not archived)",
    "PLANNING" to "Planning",
    "ACTIVE" to "Active",
    "ON_HOLD" to "On hold",
    "DONE" to "Done",
    "ARCHIVED" to "Archived",
)

/** Mirrors the web app's SIZE_OPTIONS (ProjectListPage.tsx) — `null` means "any size". */
val PROJECT_SIZE_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Any size",
    "XS" to "XS",
    "S" to "S",
    "M" to "M",
    "L" to "L",
    "XL" to "XL",
)

/** The real (non-"any") statuses with display labels, for the project-row quick status change. */
val PROJECT_STATUS_LABELS: List<Pair<String, String>> =
    PROJECT_STATUS_OPTIONS.mapNotNull { (value, label) -> value?.let { it to label } }

data class ProjectListUiState(
    val categories: List<ProjectCategoryResponse> = emptyList(),
    val projects: List<ProjectResponse> = emptyList(),
    val sortMode: ProjectSortMode = ProjectSortMode.CATEGORY,
    val loading: Boolean = true,
    val error: AppError? = null,
    val filterText: String = "",
    val statusFilter: String? = null,
    val sizeFilter: String? = null,
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
    private val error = MutableStateFlow<AppError?>(null)
    private val filterText = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<String?>(null)
    private val sizeFilter = MutableStateFlow<String?>(null)

    val state: StateFlow<ProjectListUiState> = combine(
        combine(categories, projects, sortMode, loading, error) { cats, projs, sort, isLoading, err ->
            ProjectListUiState(cats, projs, sort, isLoading, err)
        },
        filterText,
        statusFilter,
        sizeFilter,
    ) { base, filter, status, size -> base.copy(filterText = filter, statusFilter = status, sizeFilter = size) }
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

    /**
     * Status/size, unlike the name filter, are server-side (mirrors the web app's
     * `useProjects(status, categoryId, size, page)`) — only Category sort mode supports
     * them (`priorityOrdered` takes no filter params, same limitation the web app has).
     */
    fun setStatusFilter(status: String?) {
        statusFilter.value = status
        refresh()
    }

    fun setSizeFilter(size: String?) {
        sizeFilter.value = size
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            runCatching {
                val cats = categoryApi.list3().bodyOrThrow(TAG, "listCategories")
                val projs = when (sortMode.value) {
                    ProjectSortMode.PRIORITY -> projectApi.priorityOrdered().bodyOrThrow(TAG, "priorityOrdered")
                    ProjectSortMode.CATEGORY -> projectApi.list1(
                        status = statusFilter.value,
                        size = sizeFilter.value,
                        pageSize = 200,
                    ).bodyOrThrow(TAG, "list").content ?: emptyList()
                }
                cats to projs
            }.onSuccess { (cats, projs) ->
                categories.value = cats
                projects.value = projs
                error.value = null
                loading.value = false
            }.onFailure { ex ->
                AppLog.e(TAG, "refresh.failed", throwable = ex)
                error.value = ex.toAppError()
                loading.value = false
            }
        }
    }

    /** D10 — re-fetches only the category list, for a category Conflict's "Reload" action. */
    fun reloadCategories() {
        viewModelScope.launch {
            runCatching { categoryApi.list3().bodyOrThrow(TAG, "reloadCategories") }
                .onSuccess { cats ->
                    categories.value = cats
                    error.value = null
                }
                .onFailure { ex -> AppLog.w(TAG, "reloadCategories.failed", throwable = ex) }
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
                error.value = ex.toAppError()
            }
        }
    }

    /** Quick status change from the project row (whole-form `PATCH`, D15 — same echo-current-fields shape as [ProjectFormViewModel.save]). */
    fun changeStatus(project: ProjectResponse, status: String) {
        val id = project.id ?: return
        if (project.status == status) return
        viewModelScope.launch {
            runCatching {
                projectApi.patch2(
                    id,
                    PatchProjectRequest(
                        name = project.name ?: "",
                        status = status,
                        categoryId = project.categoryId,
                        description = project.description,
                        propertySize = project.propertySize,
                        color = project.color,
                        startDate = project.startDate,
                        endDate = project.endDate,
                        actualStart = project.actualStart,
                        actualEnd = project.actualEnd,
                        expectedVersion = project.version,
                    ),
                ).bodyOrThrow(TAG, "changeStatus")
            }.onSuccess { updated ->
                projects.value = projects.value.map { if (it.id == updated.id) updated else it }
                AppLog.i(TAG, "changeStatus.success", mapOf("id" to id, "status" to status))
            }.onFailure { ex ->
                AppLog.e(TAG, "changeStatus.failed", mapOf("id" to id), throwable = ex)
                error.value = ex.toAppError()
            }
        }
    }

    /**
     * "By priority" tap-reorder (web's drag-based "Sort by: Priority", D19 —
     * mirrored here as tap up/down, same precedent [reorderCategories] already
     * set for this app rather than introducing drag gestures). Only meaningful
     * while [sortMode] is [ProjectSortMode.PRIORITY] and no local name filter is
     * narrowing the list — the screen only wires this up under those conditions.
     */
    fun reorderProjects(orderedIds: List<UUID>) {
        viewModelScope.launch {
            runCatching {
                projectApi.reorder1(ReorderProjectsRequest(orderedIds)).bodyOrThrow(TAG, "reorderProjects")
            }.onSuccess { reordered ->
                projects.value = reordered
                AppLog.i(TAG, "reorderProjects.success", mapOf("count" to reordered.size))
            }.onFailure { ex ->
                AppLog.e(TAG, "reorderProjects.failed", throwable = ex)
                error.value = ex.toAppError()
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
                error.value = ex.toAppError()
            }
        }
    }

    /** D9/D10 — a category deleted/modified elsewhere since this list was fetched; categories are never Room-cached, so "Reload" is [reloadCategories]. */
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
            }.onFailure { ex -> handleCategoryFailure(ex, category, "renameCategory") }
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
            }.onFailure { ex -> handleCategoryFailure(ex, category, "deleteCategory") }
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
                error.value = ex.toAppError()
            }
        }
    }

    private fun handleCategoryFailure(ex: Throwable, category: ProjectCategoryResponse, tag: String) {
        val appError = ex.toAppError()
        AppLog.e(TAG, "$tag.failed", mapOf("id" to category.id), throwable = ex)
        if (appError is AppError.NotFound) {
            categories.value = categories.value.filterNot { it.id == category.id }
        }
        error.value = appError
    }
}
