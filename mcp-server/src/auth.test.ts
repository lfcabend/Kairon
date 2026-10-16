import type { Request } from "express";
import { describe, expect, it } from "vitest";

import { extractBearerToken } from "./auth.js";

function requestWithHeader(value: string | undefined): Request {
  return { header: (name: string) => (name.toLowerCase() === "authorization" ? value : undefined) } as unknown as Request;
}

describe("extractBearerToken", () => {
  it("extracts the token from a well-formed header", () => {
    expect(extractBearerToken(requestWithHeader("Bearer kairon_pat_abc123"))).toBe("kairon_pat_abc123");
  });

  it("is case-insensitive on the Bearer scheme", () => {
    expect(extractBearerToken(requestWithHeader("bearer kairon_pat_abc123"))).toBe("kairon_pat_abc123");
  });

  it("returns null when the header is missing", () => {
    expect(extractBearerToken(requestWithHeader(undefined))).toBeNull();
  });

  it("returns null for a malformed header (wrong scheme)", () => {
    expect(extractBearerToken(requestWithHeader("Basic dXNlcjpwYXNz"))).toBeNull();
  });

  it("returns null for an empty token", () => {
    expect(extractBearerToken(requestWithHeader("Bearer    "))).toBeNull();
  });
});
