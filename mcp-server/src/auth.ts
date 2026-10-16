import type { Request } from "express";

/**
 * Reads `Authorization: Bearer <token>` off the incoming HTTP request.
 * Returns `null` if missing or malformed — the caller (index.ts) 401s
 * before any tool handler runs (D1-auth). No token is ever stored anywhere;
 * this is called fresh for every request.
 */
export function extractBearerToken(req: Request): string | null {
  const header = req.header("authorization");
  if (!header) return null;
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  if (!match) return null;
  const token = match[1].trim();
  return token.length > 0 ? token : null;
}
