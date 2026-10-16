/**
 * Server-wide, non-secret configuration only — there is no per-user
 * credential to configure here (D1-auth): every request carries its own
 * caller's bearer token, forwarded verbatim by {@link kaironClient}.
 */
export const config = {
  /**
   * The Kairon backend's base URL, including its `/api/v1` mount point's
   * parent context path. In-cluster this is the Service DNS name (D1-chart,
   * e.g. `http://kairon.kairon.svc.cluster.local:8080/kairon`); for local
   * dev it defaults to the host-forwarded backend.
   */
  kaironBaseUrl: (process.env.KAIRON_BASE_URL ?? "http://localhost:8080/kairon").replace(/\/+$/, ""),

  /** The port this server listens on inside its container (D1). */
  port: Number(process.env.PORT ?? 3000),
};
