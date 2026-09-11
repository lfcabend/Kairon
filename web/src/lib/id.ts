/**
 * A random UUID-ish string that works outside secure contexts.
 *
 * `crypto.randomUUID` is only defined for secure contexts (HTTPS or
 * `http://localhost`). Kairon is also served over plain HTTP on a LAN
 * address (the home k3s deploy), where it is `undefined`, so we fall back
 * to `crypto.getRandomValues` (which *is* available on insecure origins)
 * and finally to `Math.random` if even that is missing.
 */
export function randomId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const h = Array.from(b, (x) => x.toString(16).padStart(2, "0"));
    return `${h.slice(0, 4).join("")}-${h.slice(4, 6).join("")}-${h
      .slice(6, 8)
      .join("")}-${h.slice(8, 10).join("")}-${h.slice(10, 16).join("")}`;
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
