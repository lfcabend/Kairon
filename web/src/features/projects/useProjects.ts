import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { projectsApi } from "@/lib/api/projects";
import type { CreateProjectBody, PatchProjectBody, Project } from "@/lib/api/types";
import { randomId } from "@/lib/id";

import { projectKeys } from "./projectKeys";

export function useProjects(status: string | undefined, categoryId: string | undefined, size: string | undefined, page = 0) {
  return useQuery({
    queryKey: projectKeys.list(status, categoryId, size, page),
    queryFn: () => projectsApi.list({ status, categoryId, size, page }),
  });
}

/** Used only in Priority sort mode (D19) — every non-`ARCHIVED` project, flat, rank-ordered. */
export function useProjectsByPriority() {
  return useQuery({
    queryKey: projectKeys.priorityOrdered(),
    queryFn: () => projectsApi.listByPriority(),
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
