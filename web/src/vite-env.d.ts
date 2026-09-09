/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Overrides the {@link log} seam's level (`debug` | `info` | `warn` | `error`
   * | `silent`). Unset: `debug` in dev, `warn` in production builds.
   */
  VITE_LOG_LEVEL?: "debug" | "info" | "warn" | "error" | "silent";
}
