import type { IssuePersonalAccessTokenBody, IssuedPersonalAccessToken, PersonalAccessToken } from "./types";

import { apiFetch } from "./client";

/** Thin wrapper over `/me/personal-access-tokens`, mirroring `assistantApi`/`todoApi`. */
export const personalAccessTokensApi = {
  list: () => apiFetch<PersonalAccessToken[]>("/me/personal-access-tokens"),

  issue: (body: IssuePersonalAccessTokenBody) =>
    apiFetch<IssuedPersonalAccessToken>("/me/personal-access-tokens", { method: "POST", body }),

  revoke: (id: string) => apiFetch<void>(`/me/personal-access-tokens/${id}`, { method: "DELETE" }),
};
