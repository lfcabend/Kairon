import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { projectsApi } from "@/lib/api/projects";
import type { CreateProjectCategoryBody, PatchProjectCategoryBody, ProjectCategory } from "@/lib/api/types";
import { randomId } from "@/lib/id";

import { projectKeys } from "./projectKeys";

export function useProjectCategories() {
  return useQuery({
    queryKey: projectKeys.categories(),
    queryFn: () => projectsApi.listCategories(),
  });
}

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateProjectCategoryBody) => projectsApi.createCategory(body),
    onMutate: async (body) => {
      await qc.cancelQueries({ queryKey: projectKeys.categories() });
      const previous = qc.getQueryData<ProjectCategory[]>(projectKeys.categories()) ?? [];
      const tempId = `temp-${randomId()}`;
      const maxPos = previous.reduce((m, c) => Math.max(m, c.position), 0);
      const optimistic: ProjectCategory = {
        id: tempId,
        name: body.name,
        color: body.color ?? "#6366f1",
        position: maxPos + 100,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        version: 0,
      };
      qc.setQueryData<ProjectCategory[]>(projectKeys.categories(), [...previous, optimistic]);
      return { previous, tempId };
    },
    onError: (_err, _body, ctx) => {
      if (ctx) qc.setQueryData(projectKeys.categories(), ctx.previous);
    },
    onSuccess: (created, _body, ctx) => {
      qc.setQueryData<ProjectCategory[]>(projectKeys.categories(), (list = []) =>
        list.map((c) => (c.id === ctx?.tempId ? created : c)),
      );
    },
  });
}

export function usePatchCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: PatchProjectCategoryBody }) =>
      projectsApi.patchCategory(id, body),
    onMutate: async ({ id, body }) => {
      await qc.cancelQueries({ queryKey: projectKeys.categories() });
      const previous = qc.getQueryData<ProjectCategory[]>(projectKeys.categories()) ?? [];
      qc.setQueryData<ProjectCategory[]>(
        projectKeys.categories(),
        previous.map((c) => (c.id === id ? { ...c, ...body } : c)),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(projectKeys.categories(), ctx.previous);
    },
    onSuccess: (updated) => {
      qc.setQueryData<ProjectCategory[]>(projectKeys.categories(), (list = []) =>
        list.map((c) => (c.id === updated.id ? updated : c)),
      );
    },
  });
}

/** Deleting a category also invalidates the project list, since it changes projects' `categoryId`. */
export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => projectsApi.removeCategory(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: projectKeys.categories() });
      const previous = qc.getQueryData<ProjectCategory[]>(projectKeys.categories()) ?? [];
      qc.setQueryData<ProjectCategory[]>(
        projectKeys.categories(),
        previous.filter((c) => c.id !== id),
      );
      return { previous };
    },
    onError: (_err, _id, ctx) => {
      if (ctx) qc.setQueryData(projectKeys.categories(), ctx.previous);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["projects", "list"] });
      qc.invalidateQueries({ queryKey: projectKeys.priorityOrdered() });
    },
  });
}

export function useReorderCategories() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (orderedIds: string[]) => projectsApi.reorderCategories(orderedIds),
    onMutate: async (orderedIds) => {
      await qc.cancelQueries({ queryKey: projectKeys.categories() });
      const previous = qc.getQueryData<ProjectCategory[]>(projectKeys.categories()) ?? [];
      const rank = new Map(orderedIds.map((id, i) => [id, i]));
      qc.setQueryData<ProjectCategory[]>(
        projectKeys.categories(),
        [...previous].sort((a, b) => (rank.get(a.id) ?? 0) - (rank.get(b.id) ?? 0)),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(projectKeys.categories(), ctx.previous);
    },
    onSuccess: (reordered) => {
      qc.setQueryData<ProjectCategory[]>(projectKeys.categories(), reordered);
    },
  });
}
