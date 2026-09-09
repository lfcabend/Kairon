import { create } from "zustand";

import type { UserSummary } from "@/lib/api/types";

/**
 * The session lives only in memory: the access token is never written to
 * localStorage (XSS), and the refresh token is an httpOnly cookie the JS never
 * sees. On a full page load {@link AuthProvider} rebuilds the session by calling
 * the refresh endpoint with that cookie.
 */
interface AuthState {
  accessToken: string | null;
  user: UserSummary | null;
  setSession: (accessToken: string, user: UserSummary) => void;
  clearSession: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  setSession: (accessToken, user) => set({ accessToken, user }),
  clearSession: () => set({ accessToken: null, user: null }),
}));
