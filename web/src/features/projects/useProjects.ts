import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { projectsApi } from "@/lib/api/projects";
import type { CreateProjectBody, PatchProjectBody, Project, ProjectsPage } from "@/lib/api/types";
import { randomId } from "@/lib/id";

import { projectKeys } from "./projectKeys";

export function useProjects(status: string | undefined, categoryId: string | undefined, size: string | undefined, page = 0) {
  return useQuery({
    queryKey: projectKeys.list(status, categoryId, size, page),
    queryFn: () => projectsApi.list({ status, categoryId, size, page }),
  });
}

/** Used only in Priority sort mode (D19) — every non-`ARCHIVED` project, flat, rank-ordered. */
export function useProjectsByPriority(enabled = true) {
  return useQuery({
    queryKey: projectKeys.priorityOrdered(),
    queryFn: () => projectsApi.listByPriority(),
    enabled,
  });
}

export function useProject(id: string) {
  return useQuery({
    queryKey: projectKeys.detail(id),
    queryFn: () => projectsApi.get(id),
    enabled: Boolean(id),
  });
}

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateProjectBody) => projectsApi.create(body),
    onMutate: async (body) => {
      await qc.cancelQueries({ queryKey: projectKeys.priorityOrdered() });
      const previous = qc.getQueryData<Project[]>(projectKeys.priorityOrdered()) ?? [];
      const tempId = `temp-${randomId()}`;
      const maxRank = previous.reduce((m, p) => Math.max(m, p.priorityRank), 0);
      const optimistic: Project = {
        id: tempId,
        categoryId: body.categoryId ?? null,
        name: body.name,
        description: body.description ?? null,
        status: "PLANNING",
        size: body.size ?? null,
        priorityRank: maxRank + 100,
        // Corrected by the server response moments later (onSuccess) — this view
        // (the flat priority list) doesn't group by category, so the real bucket
        // position doesn't matter here the way it would in "Sort by: Custom order".
        categoryRank: 0,
        color: body.color ?? "#6366f1",
        startDate: body.startDate ?? null,
        endDate: body.endDate ?? null,
        actualStart: null,
        actualEnd: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        version: 0,
      };
      qc.setQueryData<Project[]>(projectKeys.priorityOrdered(), [...previous, optimistic]);
      return { previous, tempId };
    },
    onError: (_err, _body, ctx) => {
      if (ctx) qc.setQueryData(projectKeys.priorityOrdered(), ctx.previous);
    },
    onSuccess: (created, _body, ctx) => {
      qc.setQueryData<Project[]>(projectKeys.priorityOrdered(), (list = []) =>
        list.map((p) => (p.id === ctx?.tempId ? created : p)),
      );
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
    },
  });
}

/** Always sends the full current project with the changed field(s) overridden (D15) — never `priorityRank` (D18). */
export function usePatchProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: PatchProjectBody }) => projectsApi.patch(id, body),
    onSuccess: (updated) => {
      qc.setQueryData(projectKeys.detail(updated.id), updated);
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
      qc.invalidateQueries({ queryKey: projectKeys.priorityOrdered() });
    },
  });
}

/**
 * Drag-and-drop category reassignment from the list page: sends the same whole-form
 * `PATCH` as `usePatchProject` (D15) but optimistically moves the card between the
 * cached list's category sections first, since waiting on invalidation would make the
 * drop feel laggy.
 */
export function useMoveProjectCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ project, categoryId }: { project: Project; categoryId: string | null }) =>
      projectsApi.patch(project.id, {
        categoryId,
        name: project.name,
        description: project.description,
        status: project.status,
        size: project.size,
        color: project.color,
        startDate: project.startDate,
        endDate: project.endDate,
        actualStart: project.actualStart,
        actualEnd: project.actualEnd,
        expectedVersion: project.version,
      }),
    onMutate: async ({ project, categoryId }) => {
      await qc.cancelQueries({ queryKey: ["projects", "list"] });
      const previous = qc.getQueriesData<ProjectsPage>({ queryKey: ["projects", "list"] });
      for (const [key, data] of previous) {
        if (!data) continue;
        qc.setQueryData<ProjectsPage>(key, {
          ...data,
          content: data.content.map((p) => (p.id === project.id ? { ...p, categoryId } : p)),
        });
      }
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      ctx?.previous.forEach(([key, data]) => qc.setQueryData<ProjectsPage>(key, data));
    },
    onSuccess: (updated) => {
      qc.setQueryData(projectKeys.detail(updated.id), updated);
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
    },
  });
}

/** Reassigns the slots that held `orderedIds` members, in `orderedIds`' order; every other slot is untouched. */
function applyLocalOrder(content: Project[], orderedIds: string[]): Project[] {
  const included = new Set(orderedIds);
  const slots = content.map((_, i) => i).filter((i) => included.has(content[i].id));
  const byId = new Map(content.map((p) => [p.id, p]));
  const result = [...content];
  slots.forEach((slotIndex, k) => {
    result[slotIndex] = byId.get(orderedIds[k])!;
  });
  return result;
}

/**
 * "Sort by: Custom order" drag-reorder, scoped to one category bucket (D19's
 * global `priorityRank` is untouched by this — see `ProjectService.reorderInCategory`).
 * Optimistic against every cached `["projects","list"]` query, reordering only the
 * slots that belong to this category so other categories' relative order is undisturbed.
 */
export function useReorderProjectsInCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ categoryId, orderedIds }: { categoryId: string | null; orderedIds: string[] }) =>
      projectsApi.reorderInCategory(categoryId, orderedIds),
    onMutate: async ({ orderedIds }) => {
      await qc.cancelQueries({ queryKey: ["projects", "list"] });
      const previous = qc.getQueriesData<ProjectsPage>({ queryKey: ["projects", "list"] });
      for (const [key, data] of previous) {
        if (!data) continue;
        qc.setQueryData<ProjectsPage>(key, { ...data, content: applyLocalOrder(data.content, orderedIds) });
      }
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      ctx?.previous.forEach(([key, data]) => qc.setQueryData<ProjectsPage>(key, data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
    },
  });
}

export function useDeleteProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => projectsApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
      qc.invalidateQueries({ queryKey: projectKeys.priorityOrdered() });
    },
  });
}

/** Optimistic against the flat priority list; invalidates the grouped list too (D20). */
export function useReorderProjects() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (orderedIds: string[]) => projectsApi.reorder(orderedIds),
    onMutate: async (orderedIds) => {
      await qc.cancelQueries({ queryKey: projectKeys.priorityOrdered() });
      const previous = qc.getQueryData<Project[]>(projectKeys.priorityOrdered()) ?? [];
      const rank = new Map(orderedIds.map((id, i) => [id, i]));
      qc.setQueryData<Project[]>(
        projectKeys.priorityOrdered(),
        [...previous].sort((a, b) => (rank.get(a.id) ?? 0) - (rank.get(b.id) ?? 0)),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(projectKeys.priorityOrdered(), ctx.previous);
    },
    onSuccess: (reordered) => {
      qc.setQueryData<Project[]>(projectKeys.priorityOrdered(), reordered);
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
    },
  });
}
