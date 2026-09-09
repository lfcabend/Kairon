import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAuthStore } from "@/features/auth/authStore";

/**
 * Gate for authenticated routes. By the time this renders {@link AuthProvider}
 * has already attempted a silent refresh, so a null token means "really signed
 * out" — send them to /login, remembering where they were headed.
 */
export function ProtectedRoute() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const location = useLocation();

  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
