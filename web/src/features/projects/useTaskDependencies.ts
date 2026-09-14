import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { projectsApi } from "@/lib/api/projects";
import type { CreateTaskDependencyBody } from "@/lib/api/types";

import { projectKeys } from "./projectKeys";

/** Bare array (D7), like `useProjectCategories`. */
export function useTaskDependencies(projectId: string) {
  return useQuery({
    queryKey: projectKeys.dependencies(projectId),
    queryFn: () => projectsApi.getDependencies(projectId),
    enabled: Boolean(projectId),
  });
}

/**
 * No optimistic update: the server is the source of truth for whether an edge
 * is allowed (self/cross-project/duplicate/cycle, D4), so the 400/409 it
 * throws surfaces directly to the caller instead of being pre-validated here.
 */
export function useCreateDependency(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, body }: { taskId: string; body: CreateTaskDependencyBody }) =>
      projectsApi.createDependency(taskId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: projectKeys.dependencies(projectId) });
    },
  });
}

export function useDeleteDependency(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => projectsApi.deleteDependency(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: projectKeys.dependencies(projectId) });
    },
  });
}
