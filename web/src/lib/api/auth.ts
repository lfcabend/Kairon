import { apiFetch } from "./client";
import type { AuthResponse, LoginBody, Me, RegisterBody, UpdateMeBody } from "./types";

export const authApi = {
  register: (body: RegisterBody) =>
    apiFetch<AuthResponse>("/auth/register", { method: "POST", body, auth: false }),

  login: (body: LoginBody) =>
    apiFetch<AuthResponse>("/auth/login", { method: "POST", body, auth: false }),

  /** Cookie-authenticated; no bearer needed. */
  logout: () => apiFetch<void>("/auth/logout", { method: "POST", auth: false }),

  logoutAll: () => apiFetch<void>("/auth/logout-all", { method: "POST" }),

  me: () => apiFetch<Me>("/me"),

  updateMe: (body: UpdateMeBody) => apiFetch<Me>("/me", { method: "PATCH", body }),
};
