import { config } from "./config.js";

/** A Kairon RFC 7807 `application/problem+json` error body. */
interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}

/**
 * Thrown for any non-2xx Kairon response, or a network failure reaching
 * Kairon at all — the one error shape every tool handler catches and turns
 * into an MCP tool error (the third client to parse this RFC 7807 shape,
 * after web's `client.ts` and Android's `ProblemDetail`/`AppError`, M11.7).
 */
export class KaironApiError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = "KaironApiError";
    this.status = status;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
}

/**
 * Calls one Kairon `/api/v1/**` endpoint with the caller's own bearer token
 * — never a server-wide credential (D1-auth). Every tool handler threads
 * its current request's token through to here; nothing is cached, and
 * nothing here ever reads a token from {@link config} or any module-level
 * variable.
 */
export async function kaironFetch<T>(bearerToken: string, path: string, opts: RequestOptions = {}): Promise<T> {
  const url = new URL(`${config.kaironBaseUrl}/api/v1${path}`);
  for (const [key, value] of Object.entries(opts.query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }

  let res: Response;
  try {
    res = await fetch(url, {
      method: opts.method ?? "GET",
      headers: {
        Authorization: `Bearer ${bearerToken}`,
        ...(opts.body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
  } catch (err) {
    throw new KaironApiError(
      `Could not reach the Kairon backend at ${config.kaironBaseUrl}: ${(err as Error).message}`,
    );
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  const data = text.length > 0 ? (JSON.parse(text) as unknown) : undefined;

  if (!res.ok) {
    const problem = data as ProblemDetail | undefined;
    const fieldErrors = problem?.errors?.map((e) => `${e.field}: ${e.message}`).join("; ");
    const detail = [problem?.detail, fieldErrors].filter(Boolean).join(" — ") || res.statusText;
    throw new KaironApiError(detail, res.status);
  }

  return data as T;
}
