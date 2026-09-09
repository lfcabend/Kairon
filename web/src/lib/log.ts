/**
 * The app's logging seam — a thin wrapper over `console` with a level gate, so
 * diagnostic chatter stays out of production builds while warnings and errors
 * always surface. Prefer `log.*` over calling `console.*` directly.
 *
 * The level resolves from `VITE_LOG_LEVEL` (`debug` | `info` | `warn` | `error`
 * | `silent`) when it is set, otherwise `debug` in dev and `warn` in production
 * builds. Error reporting to a backend collector (GlitchTip / Sentry) can hang
 * off `log.error` later without touching call sites.
 */
type Level = "debug" | "info" | "warn" | "error" | "silent";

const ORDER: Record<Level, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
  silent: 100,
};

function resolveLevel(): Level {
  const configured = import.meta.env.VITE_LOG_LEVEL as string | undefined;
  if (configured && configured in ORDER) {
    return configured as Level;
  }
  return import.meta.env.DEV ? "debug" : "warn";
}

function emit(level: Exclude<Level, "silent">, args: readonly unknown[]): void {
  if (ORDER[level] < ORDER[resolveLevel()]) {
    return;
  }
  console[level](...args);
}

export const log = {
  debug: (...args: unknown[]): void => emit("debug", args),
  info: (...args: unknown[]): void => emit("info", args),
  warn: (...args: unknown[]): void => emit("warn", args),
  error: (...args: unknown[]): void => emit("error", args),
};
