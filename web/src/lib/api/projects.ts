import type {
  CreateProjectBody,
  CreateProjectCategoryBody,
  CreateProjectTaskBody,
  PatchProjectBody,
  PatchProjectCategoryBody,
  PatchProjectTaskBody,
  Project,
  ProjectCategory,
  ProjectTask,
  ProjectTasksPage,
  ProjectsPage,
} from "./types";

import { apiFetch } from "./client";

export interface ProjectListFilter {
  status?: string;
  categoryId?: string;
  size?: string;
  includeArchived?: boolean;
  page?: number;
  pageSize?: number;
  sort?: string;
}

/** Thin wrapper over the category/project/task endpoints, mirroring `todoApi`/`journalApi`. */
export const projectsApi = {
  listCategories: () => apiFetch<ProjectCategory[]>("/project-categories"),

  createCategory: (body: CreateProjectCategoryBody) =>
    apiFetch<ProjectCategory>("/project-categories", { method: "POST", body }),

  patchCategory: (id: string, body: PatchProjectCategoryBody) =>
    apiFetch<ProjectCategory>(`/project-categories/${id}`, { method: "PATCH", body }),

  removeCategory: (id: string) =>
    apiFetch<void>(`/project-categories/${id}`, { method: "DELETE" }),

  reorderCategories: (orderedIds: string[]) =>
    apiFetch<ProjectCategory[]>("/project-categories:reorder", { method: "POST", body: { orderedIds } }),

  list: (filter: ProjectListFilter = {}) => {
    const params = new URLSearchParams();
    if (filter.status) params.set("status", filter.status);
    if (filter.categoryId) params.set("categoryId", filter.categoryId);
    if (filter.size) params.set("size", filter.size);
    if (filter.includeArchived) params.set("includeArchived", "true");
    params.set("page", String(filter.page ?? 0));
    params.set("pageSize", String(filter.pageSize ?? 50));
    if (filter.sort) params.set("sort", filter.sort);
    return apiFetch<ProjectsPage>(`/projects?${params.toString()}`);
  },

  listByPriority: () => apiFetch<Project[]>("/projects/priority-ordered"),

  get: (id: string) => apiFetch<Project>(`/projects/${id}`),

  create: (body: CreateProjectBody) => apiFetch<Project>("/projects", { method: "POST", body }),

  patch: (id: string, body: PatchProjectBody) =>
    apiFetch<Project>(`/projects/${id}`, { method: "PATCH", body }),

  remove: (id: string) => apiFetch<void>(`/projects/${id}`, { method: "DELETE" }),

  reorder: (orderedIds: string[]) =>
    apiFetch<Project[]>("/projects:reorder", { method: "POST", body: { orderedIds } }),

  listTasks: (projectId: string) =>
    apiFetch<ProjectTasksPage>(`/projects/${projectId}/tasks?page=0&size=200`),

  createTask: (projectId: string, body: CreateProjectTaskBody) =>
    apiFetch<ProjectTask>(`/projects/${projectId}/tasks`, { method: "POST", body }),

  patchTask: (taskId: string, body: PatchProjectTaskBody) =>
    apiFetch<ProjectTask>(`/tasks/${taskId}`, { method: "PATCH", body }),

  removeTask: (taskId: string) => apiFetch<void>(`/tasks/${taskId}`, { method: "DELETE" }),

  reorderTasks: (projectId: string, parentTaskId: string | null, orderedIds: string[]) =>
    apiFetch<ProjectTask[]>(`/projects/${projectId}/tasks:reorder`, {
      method: "POST",
      body: { parentTaskId, orderedIds },
    }),
};
