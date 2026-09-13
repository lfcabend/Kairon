import { useAuthStore } from "@/features/auth/authStore";
import { randomId } from "@/lib/id";
import { log } from "@/lib/log";

import type { AuthResponse, ProblemDetail } from "./types";

const BASE = `${import.meta.env.BASE_URL}api/v1`;

/** Correlation id echoed to the backend as `X-Request-Id`; joins browser and server logs. */
function newRequestId(): string {
  return randomId();
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  /** Attach the bearer access token. Default true; set false for /auth/* routes. */
  auth?: boolean;
}

/**
 * The one fetch wrapper. Adds the bearer token and the refresh cookie
 * (`credentials: "include"`), parses `application/problem+json` into an
 * {@link ApiError}, and on a 401 does a single-flight refresh + one retry before
 * giving up and clearing the session.
 */
export async function apiFetch<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const first = await rawFetch(path, opts);

  if (first.status === 401 && opts.auth !== false && !path.startsWith("/auth/")) {
    log.debug(`401 on ${path} — attempting token refresh`);
    const refreshed = await ensureRefreshed();
    if (!refreshed) {
      log.warn(`Refresh failed after 401 on ${path} — clearing session`);
      useAuthStore.getState().clearSession();
      return toResult<T>(first);
    }
    return toResult<T>(await rawFetch(path, opts));
  }

  return toResult<T>(first);
}

async function rawFetch(path: string, opts: RequestOptions): Promise<Response> {
  const headers = new Headers();
  const token = useAuthStore.getState().accessToken;
  if (opts.auth !== false && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (opts.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  const requestId = newRequestId();
  headers.set("X-Request-Id", requestId);
  const method = opts.method ?? "GET";
  log.debug(`→ ${method} ${path}`, { requestId });
  const res = await fetch(BASE + path, {
    method,
    headers,
    credentials: "include",
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  log.debug(`← ${res.status} ${method} ${path}`, { requestId });
  return res;
}

let refreshInFlight: Promise<boolean> | null = null;

function ensureRefreshed(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = tryRefreshSession().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/**
 * Exchanges the refresh cookie for a new access token. Used both by the 401
 * interceptor and by {@link AuthProvider} to bootstrap a session on page load.
 */
export async function tryRefreshSession(): Promise<boolean> {
  let res: Response;
  try {
    res = await fetch(`${BASE}/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "X-Request-Id": newRequestId() },
    });
  } catch (err) {
    log.warn("Refresh request errored", err);
    return false;
  }
  if (!res.ok) {
    log.debug(`Refresh rejected with ${res.status}`);
    return false;
  }
  const data = (await res.json()) as AuthResponse;
  useAuthStore.getState().setSession(data.accessToken, data.user);
  return true;
}

async function toResult<T>(res: Response): Promise<T> {
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    return undefined as T;
  }
  const text = await res.text();
  const data = text ? (JSON.parse(text) as unknown) : undefined;
  if (!res.ok) {
    const problem = data as ProblemDetail | undefined;
    throw new ApiError(res.status, problem?.detail ?? res.statusText, problem);
  }
  return data as T;
}
