import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";

import { projectsApi } from "@/lib/api/projects";
import type { CreateProjectTaskBody, PatchProjectTaskBody, ProjectTask } from "@/lib/api/types";
import { randomId } from "@/lib/id";

import { projectKeys } from "./projectKeys";

const byPosition = (a: ProjectTask, b: ProjectTask) => a.position - b.position;

/**
 * Every `/planning/today` query, for any date — invalidated after any task
 * create/patch/delete, since each can change which tasks are due/overdue
 * (Today's `dueProjectTasks`). The default 30s `staleTime` (queryClient.ts)
 * otherwise leaves Today showing a stale snapshot if you edit a task's dates
 * elsewhere and switch back within that window.
 */
function invalidateTodayViews(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["planning", "today"] });
}

/** Fetches the whole project's tree (D1) and assembles it into levels for the tree/board views. */
export function useProjectTasks(projectId: string) {
  const query = useQuery({
    queryKey: projectKeys.tasks(projectId),
    queryFn: () => projectsApi.listTasks(projectId),
    enabled: Boolean(projectId),
  });

  const tasks = query.data?.content ?? [];
  const { topLevel, childrenByParentId } = useMemo(() => {
    const top: ProjectTask[] = [];
    const children = new Map<string, ProjectTask[]>();
    for (const task of tasks) {
      if (task.parentTaskId == null) {
        top.push(task);
      } else {
        const list = children.get(task.parentTaskId) ?? [];
        list.push(task);
        children.set(task.parentTaskId, list);
      }
    }
    top.sort(byPosition);
    for (const list of children.values()) list.sort(byPosition);
    return { topLevel: top, childrenByParentId: children };
  }, [tasks]);

  return { ...query, tasks, topLevel, childrenByParentId };
}

export function useCreateTask(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateProjectTaskBody) => projectsApi.createTask(projectId, body),
    onMutate: async (body) => {
      await qc.cancelQueries({ queryKey: projectKeys.tasks(projectId) });
      const previous = qc.getQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
      );
      const siblings = (previous?.content ?? []).filter((t) => t.parentTaskId === (body.parentTaskId ?? null));
      const maxPos = siblings.reduce((m, t) => Math.max(m, t.position), 0);
      const tempId = `temp-${randomId()}`;
      const optimistic: ProjectTask = {
        id: tempId,
        projectId,
        parentTaskId: body.parentTaskId ?? null,
        name: body.name,
        description: body.description ?? null,
        status: "TODO",
        isMilestone: body.isMilestone ?? false,
        plannedStart: body.plannedStart ?? null,
        plannedEnd: body.plannedEnd ?? null,
        estimateHours: body.estimateHours ?? null,
        actualHours: null,
        progressPercent: 0,
        position: maxPos + 100,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        version: 0,
      };
      qc.setQueryData(projectKeys.tasks(projectId), {
        content: [...(previous?.content ?? []), optimistic],
        page: previous?.page ?? 0,
        totalElements: (previous?.totalElements ?? 0) + 1,
      });
      return { previous, tempId };
    },
    onError: (_err, _body, ctx) => {
      if (ctx?.previous) qc.setQueryData(projectKeys.tasks(projectId), ctx.previous);
    },
    onSuccess: (created, _body, ctx) => {
      qc.setQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
        (data) =>
          data && {
            ...data,
            content: data.content.map((t) => (t.id === ctx?.tempId ? created : t)),
          },
      );
      invalidateTodayViews(qc);
    },
  });
}

/** Whole-form (D15), for the same reason as `usePatchProject`. */
export function usePatchTask(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: PatchProjectTaskBody }) => projectsApi.patchTask(id, body),
    onMutate: async ({ id, body }) => {
      await qc.cancelQueries({ queryKey: projectKeys.tasks(projectId) });
      const previous = qc.getQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
      );
      const { expectedVersion: _expectedVersion, ...fields } = body;
      qc.setQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
        (data) =>
          data && {
            ...data,
            content: data.content.map((t) => (t.id === id ? { ...t, ...fields } : t)),
          },
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(projectKeys.tasks(projectId), ctx.previous);
    },
    onSuccess: (updated) => {
      qc.setQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
        (data) => data && { ...data, content: data.content.map((t) => (t.id === updated.id ? updated : t)) },
      );
      invalidateTodayViews(qc);
    },
  });
}

export function useDeleteTask(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => projectsApi.removeTask(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: projectKeys.tasks(projectId) });
      invalidateTodayViews(qc);
    },
  });
}

export function useReorderTasks(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ parentTaskId, orderedIds }: { parentTaskId: string | null; orderedIds: string[] }) =>
      projectsApi.reorderTasks(projectId, parentTaskId, orderedIds),
    onMutate: async ({ orderedIds }) => {
      await qc.cancelQueries({ queryKey: projectKeys.tasks(projectId) });
      const previous = qc.getQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
      );
      const rank = new Map(orderedIds.map((id, i) => [id, (i + 1) * 100]));
      qc.setQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
        (data) =>
          data && {
            ...data,
            content: data.content.map((t) => (rank.has(t.id) ? { ...t, position: rank.get(t.id)! } : t)),
          },
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(projectKeys.tasks(projectId), ctx.previous);
    },
    onSuccess: (reordered) => {
      qc.setQueryData<{ content: ProjectTask[]; page: number; totalElements: number }>(
        projectKeys.tasks(projectId),
        (data) => {
          if (!data) return data;
          const byId = new Map(reordered.map((t) => [t.id, t]));
          return { ...data, content: data.content.map((t) => byId.get(t.id) ?? t) };
        },
      );
    },
  });
}
