import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { personalAccessTokensApi } from "@/lib/api/personalAccessTokens";
import type { IssuePersonalAccessTokenBody } from "@/lib/api/types";

const personalAccessTokenKeys = {
  list: ["personalAccessTokens", "list"] as const,
};

/** The caller's own personal access tokens — generate/list/revoke (M10.5, Account "Developer access"). */
export function usePersonalAccessTokens() {
  return useQuery({
    queryKey: personalAccessTokenKeys.list,
    queryFn: personalAccessTokensApi.list,
  });
}

export function useIssuePersonalAccessToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: IssuePersonalAccessTokenBody) => personalAccessTokensApi.issue(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: personalAccessTokenKeys.list }),
  });
}

export function useRevokePersonalAccessToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => personalAccessTokensApi.revoke(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: personalAccessTokenKeys.list }),
  });
}
