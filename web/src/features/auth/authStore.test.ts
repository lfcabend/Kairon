import { expect, test } from "vitest";

import { useAuthStore } from "./authStore";
import { testUser } from "@/test/msw/handlers";

test("setSession stores the token and user; clearSession wipes both", () => {
  useAuthStore.getState().setSession("access-xyz", testUser);
  expect(useAuthStore.getState().accessToken).toBe("access-xyz");
  expect(useAuthStore.getState().user).toEqual(testUser);

  useAuthStore.getState().clearSession();
  expect(useAuthStore.getState().accessToken).toBeNull();
  expect(useAuthStore.getState().user).toBeNull();
});
