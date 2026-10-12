package com.kairon.android.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.ProjectCategoryControllerApi
import com.kairon.android.client.api.ProjectControllerApi
import com.kairon.android.client.model.CreateProjectRequest
import com.kairon.android.client.model.PatchProjectRequest
import com.kairon.android.client.model.ProjectCategoryResponse
import com.kairon.android.core.data.ProjectDao
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.sync.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ProjectFormViewModel"

val PROJECT_SIZES = listOf("XS", "S", "M", "L", "XL")
val PROJECT_STATUSES = listOf("PLANNING", "ACTIVE", "ON_HOLD", "DONE", "ARCHIVED")

data class ProjectFormUiState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val description: String = "",
    val categoryId: UUID? = null,
    val size: String? = null,
    val color: String = "#6366f1",
    val status: String = "PLANNING",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val categories: List<ProjectCategoryResponse> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val savedProjectId: UUID? = null,
)

/** Create or whole-form edit (D15) a project. */
@HiltViewModel
class ProjectFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectApi: ProjectControllerApi,
    private val categoryApi: ProjectCategoryControllerApi,
    private val projectDao: ProjectDao,
) : ViewModel() {

    private val projectId: UUID? = savedStateHandle.get<String>("projectId")
        ?.takeIf { it != "new" }
        ?.let(UUID::fromString)
    private var expectedVersion: Long? = null
    private var actualStart: LocalDate? = null
    private var actualEnd: LocalDate? = null

    private val _state = MutableStateFlow(ProjectFormUiState(isNew = projectId == null))
    val state: StateFlow<ProjectFormUiState> = _state

    init {
        viewModelScope.launch {
            runCatching { categoryApi.list3().bodyOrThrow(TAG, "listCategories") }
                .onSuccess { cats -> _state.value = _state.value.copy(categories = cats) }
                .onFailure { ex -> AppLog.w(TAG, "listCategories.failed", throwable = ex) }
        }
        val id = projectId
        if (id == null) {
            _state.value = _state.value.copy(loaded = true)
        } else {
            viewModelScope.launch {
                projectDao.findById(id)?.let { project ->
                    expectedVersion = project.version
                    actualStart = project.actualStart
                    actualEnd = project.actualEnd
                    _state.value = _state.value.copy(
                        loaded = true,
                        name = project.name,
                        description = project.description ?: "",
                        categoryId = project.categoryId,
                        size = project.size,
                        color = project.color,
                        status = project.status,
                        startDate = project.startDate,
                        endDate = project.endDate,
                    )
                }
            }
        }
    }

    fun setName(name: String) { _state.value = _state.value.copy(name = name) }
    fun setDescription(description: String) { _state.value = _state.value.copy(description = description) }
    fun setCategoryId(categoryId: UUID?) { _state.value = _state.value.copy(categoryId = categoryId) }
    fun setSize(size: String?) { _state.value = _state.value.copy(size = size) }
    fun setColor(color: String) { _state.value = _state.value.copy(color = color) }
    fun setStatus(status: String) { _state.value = _state.value.copy(status = status) }
    fun setStartDate(date: LocalDate?) { _state.value = _state.value.copy(startDate = date) }
    fun setEndDate(date: LocalDate?) { _state.value = _state.value.copy(endDate = date) }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.value = current.copy(error = "Name is required")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(saving = true, error = null)
            val id = projectId
            runCatching {
                if (id == null) {
                    projectApi.create2(
                        CreateProjectRequest(
                            name = current.name.trim(),
                            categoryId = current.categoryId,
                            description = current.description.trim().ifBlank { null },
                            propertySize = current.size,
                            color = current.color,
                            startDate = current.startDate,
                            endDate = current.endDate,
                        ),
                    ).bodyOrThrow(TAG, "create")
                } else {
                    projectApi.patch2(
                        id,
                        PatchProjectRequest(
                            name = current.name.trim(),
                            status = current.status,
                            categoryId = current.categoryId,
                            description = current.description.trim().ifBlank { null },
                            propertySize = current.size,
                            color = current.color,
                            startDate = current.startDate,
                            endDate = current.endDate,
                            actualStart = actualStart,
                            actualEnd = actualEnd,
                            expectedVersion = expectedVersion,
                        ),
                    ).bodyOrThrow(TAG, "patch")
                }
            }.onSuccess { saved ->
                projectDao.upsert(saved.toEntity())
                AppLog.i(TAG, "save.success", mapOf("id" to saved.id))
                _state.value = _state.value.copy(saving = false, saved = true, savedProjectId = saved.id)
            }.onFailure { ex ->
                AppLog.e(TAG, "save.failed", mapOf("id" to id), throwable = ex)
                _state.value = _state.value.copy(saving = false, error = ex.message ?: "Failed to save project")
            }
        }
    }
}
