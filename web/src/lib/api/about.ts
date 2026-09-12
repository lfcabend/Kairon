import type { AboutInfo } from "./types";

/**
 * `/actuator/info`, not `/api/v1/...` — it's public (SecurityConfig permits it)
 * and outside the versioned API, so this bypasses `apiFetch` the same way
 * PingCard's raw ping call does.
 */
export const aboutApi = {
  info: async (): Promise<AboutInfo> => {
    const res = await fetch("/actuator/info");
    if (!res.ok) {
      throw new Error(`about info failed: HTTP ${res.status}`);
    }
    return (await res.json()) as AboutInfo;
  },
};
