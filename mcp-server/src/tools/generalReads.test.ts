import { afterEach, describe, expect, it, vi } from "vitest";

import { registerGeneralReadTools } from "./generalReads.js";
import { fakeMcpServer } from "./testHarness.js";

describe("generalReads tools", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("list_todos calls GET /todo with the day query param", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([{ id: "t1" }]), { status: 200 }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const { server, tools } = fakeMcpServer();
    registerGeneralReadTools(server, "pat-123");

    const result = await tools.get("list_todos")!.handler({ day: "2026-09-21" });

    const calledUrl: URL = (fetchMock.mock.calls[0] as unknown[])[0] as URL;
    expect(calledUrl.pathname).toContain("/todo");
    expect(calledUrl.searchParams.get("day")).toBe("2026-09-21");
    expect(JSON.parse(result.content[0].text)).toEqual([{ id: "t1" }]);
  });

  it("get_project calls GET /projects/:id", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: "p1" }), { status: 200 }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const { server, tools } = fakeMcpServer();
    registerGeneralReadTools(server, "pat-123");

    await tools.get("get_project")!.handler({ projectId: "p1" });

    const calledUrl: URL = (fetchMock.mock.calls[0] as unknown[])[0] as URL;
    expect(calledUrl.pathname).toContain("/projects/p1");
  });

  it("surfaces an unauthenticated/unauthorized Kairon response as an isError result", async () => {
    globalThis.fetch = vi.fn(
      async () => new Response(JSON.stringify({ status: 401, detail: "Authentication required." }), { status: 401 }),
    ) as unknown as typeof fetch;

    const { server, tools } = fakeMcpServer();
    registerGeneralReadTools(server, "bad-token");

    const result = await tools.get("list_projects")!.handler({});

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Authentication required.");
  });
});
