# M7 — Hardening & prod — implementation plan

Status: **Accepted — implemented.** Companion to [`../ROADMAP.md`](../ROADMAP.md) (M7 acceptance
criteria) and [`../DESIGN.md`](../DESIGN.md) §8 / §11. This is the detailed plan for
the milestone; the roadmap checkboxes are the acceptance test. Follows the structure
and conventions of [`M6-today.md`](M6-today.md).

**Implementation note:** everything in §1's "In" scope is implemented and tested
(D10's `SecurityConfigTest`, `helm lint`/`helm template` against all three
overlays, the full backend suite) except the one-time, human-only setup in §6.3
(Tailscale OAuth client, `KUBECONFIG_XBMC` repo secret, the `xbmc` GitHub
Environment) — `deploy.yml` exists and is reviewed, but cannot actually run a
real deploy until that setup is done by whoever holds repo-admin/Tailscale-admin
access. Q3 is resolved (skip); Q2/Q4/Q5 remain accepted-as-is per their
recommendations below, revisited only if their triggering scenario becomes real.

Unlike M2–M6, M7 adds no product feature — it hardens the deployment path the app
already has, for the environment that already exists: **xbmc**, a single-node home
k3s cluster reached over Tailscale Funnel, deployed today via `task xbmc`
(`deploy/helm/kairon/values-xbmc.yaml`). Several `DESIGN.md` §8 bullets were written
before that real environment existed and assumed a generic "managed cluster with a
real domain" (cert-manager TLS, HPA) — this plan keeps that path alive as a second,
separate overlay (`values-prod.yaml`, D1) for whenever a real managed cluster shows
up, but the actual hardening work this milestone ships against is xbmc's.

Researching this plan also surfaced one live security gap not on the original
roadmap list — D10 — closed here because it's squarely "hardening" and cheap to fix
now, before it's forgotten.

---

## 1. Scope

### In

- **`values-prod.yaml`** (new): a distinct overlay for a hypothetical future
  managed-cluster deployment — external DB, cert-manager TLS annotations, HPA,
  `RollingUpdate` — kept alongside `values-xbmc.yaml`, not replacing it (D1).
- **Flyway as a Helm pre-install/pre-upgrade hook Job**, for every k8s environment
  (local kind, xbmc, and the hypothetical prod) — not prod-only (D4). A new
  `docker/migrator.Dockerfile` (D5) + `templates/migration-job.yaml` (D6).
- **HPA**: `templates/hpa.yaml` + an `autoscaling` values block, off everywhere
  except `values-prod.yaml` (D2).
- **Backups**: `templates/backup-cronjob.yaml` + `templates/backup-pvc.yaml`, a
  `backup` values block, enabled and tuned in `values-xbmc.yaml` — `pg_dump` to a
  local PVC on the node (D7), per the user's explicit call. Off-node replication is
  explicitly out (D8).
- **Secrets stay manual**: no new tool. `deploy/RUNBOOK.md` (new) formalizes the
  `existingSecret` + one-time `kubectl create secret` pattern already in use for
  the DB password, and documents JWT-secret rotation using the reuse-or-generate
  mechanism `templates/secret.yaml` already has (D9).
- **Security fix**: `SecurityConfig`'s `authorizeHttpRequests` chain gets an
  explicit `/actuator/**` deny between the existing `health`/`info` allow and the
  SPA catch-all, closing every other actuator endpoint (`env`, `heapdump`,
  `prometheus`, …) regardless of what `management.endpoints.web.exposure.include`
  is ever set to (D10).
- **Observability/API-docs groundwork**: `micrometer-registry-prometheus` added
  but not yet exposed/wired to a live scraper (D11 — see Out for why);
  `springdoc-openapi` added and made **live and public** on the deployed
  app — Swagger UI and the raw spec, not gated behind auth (D13) — though the
  spec doesn't yet feed a generated web client (see Out).
- **CI**: a new `deploy.yml` workflow, triggered on push to `main` after `verify`
  passes — `package` (build + Trivy-scan the app image and the new migrator image,
  push both to GHCR tagged with the commit sha, publish the OpenAPI spec as a build
  artifact) then `deploy` (join the tailnet via `tailscale/github-action`, `helm
  upgrade --install` against xbmc), targeting a named GitHub Environment (`xbmc`,
  D15) so a manual-approval gate is a settings change later, not a workflow
  rewrite, if ever wanted.
- Tests: a `SecurityConfigTest` proving D10 actually rejects an unlisted actuator
  path even when exposed; manual verification steps for the Job/CronJob (§8) —
  this milestone is infra, not app logic, so it doesn't get the usual
  unit/integration/Playwright pyramid.

### Out (deferred, with the milestone/backlog item that picks it up)

- **A generated web API client** (`openapi-typescript` + orval, per `CLAUDE.md`'s
  stated stack) replacing the hand-written `web/src/lib/api/types.ts`/`*.ts`. The
  spec gets published this milestone (D13); the client swap is a real,
  separately-reviewable frontend migration touching every call site — bundling it
  into an infra-hardening milestone risks rushing both. Its own future milestone
  or standalone task.
- **The web-client "drift check"** named in `ROADMAP.md`'s original M7 bullet —
  there's nothing to drift from until the item above ships. Follows it.
- **SOPS / sealed-secrets / external-secrets.** User's explicit call — the
  existing `existingSecret` pattern is formalized in docs, not replaced (D9).
- **Off-node backup replication.** User's explicit call (local PVC, D7) — the
  single-point-of-failure risk is written down in `RUNBOOK.md` (D8), not solved.
- **Live Prometheus scraping** (a real Prometheus instance, Grafana, exposing
  `/actuator/prometheus` past D10's deny). The dependency and the (closed)
  endpoint exist; nothing is actually wired to a scraper — that's now
  **M12 — Observability: Prometheus & Grafana** (`../ROADMAP.md`), not this
  milestone.
- **A distributed rate-limit backend** (e.g. `bucket4j-redis`) for the in-memory
  `RateLimitFilter`. Only matters once `values-prod.yaml` is deployed with more
  than one replica, which isn't planned. See Q2.
- **`imagePullSecrets` support** in the chart, which would let the GHCR package go
  private. `values-xbmc.yaml`'s existing comment already documents that the
  package must stay public without this. See Q3.
- **Assisted scheduling** (forward pass / critical-path). Already marked "optional
  here or deferred" in `ROADMAP.md`'s M7 bullets; it's a scheduling/UX feature
  unrelated to "hardening & prod," and this milestone has a full scope without it
  (D14). Left as an unscheduled backlog item, not reassigned to another milestone
  number here.
- **A GitHub Environment manual-approval gate** on the new `deploy` job. Ships
  open (auto-deploy on merge, matching how `task xbmc` is used today) with the
  Environment seam in place to add one later without a workflow rewrite (D15, Q5).

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **`values-prod.yaml` is a new, separate overlay** for a hypothetical future managed-cluster deployment (external DB, cert-manager TLS, HPA, `RollingUpdate`), kept alongside — not replacing — `values-xbmc.yaml`, which is hardened in place as the environment that actually runs today. | User's explicit call. `values.yaml`'s own header comment already flagged these as "M7" concerns; xbmc existing first (built in earlier milestones, ahead of this plan) means the original single-`values-prod.yaml` framing in `DESIGN.md` §8.4 needs a second, real-environment track alongside it, not instead of it. |
| D2 | **HPA**: a new `autoscaling` values block (`enabled`, `minReplicas`, `maxReplicas`, `targetCPUUtilizationPercentage`) and `templates/hpa.yaml` (`autoscaling/v2`), `enabled: false` in `values.yaml`/`values-xbmc.yaml`, `enabled: true` in `values-prod.yaml` — which also overrides `deploymentStrategy.type` to `RollingUpdate`. | `DESIGN.md` §8.3 already named `hpa.yaml` as a planned template gated on an autoscaling flag; this milestone builds it. `values.yaml`'s existing `Recreate`-strategy comment explains that choice is about xbmc's single 2-core node fighting itself on a rolling surge, not a general constraint — a real managed cluster (D1) has room for both replicas and a rolling deploy. |
| D3 | **`RateLimitFilter`'s per-instance in-memory buckets are not made distributed this milestone.** Accepted as a known tradeoff of D2: with `replicas > 1`, the same client can get up to `replicas ×` the configured auth-endpoint rate limit, since each pod's `ConcurrentHashMap<String, Bucket>` is independent. | Only matters once `values-prod.yaml` is actually deployed with more than one replica, which isn't planned — adding a distributed backend (`bucket4j-redis` or similar) means standing up Redis for a scenario that doesn't exist yet. See Q2. |
| D4 | **Flyway moves to a Helm pre-install/pre-upgrade hook Job for every k8s environment** (local kind, xbmc, and the hypothetical prod) — not gated to `prod` alone. `templates/configmap.yaml` gets `SPRING_FLYWAY_ENABLED: "false"` unconditionally. `application-local.yml` (host `bootRun`, not a k8s deploy at all) is untouched and keeps self-migrating on boot, same as every milestone so far. | `DESIGN.md` §8.3 already commits to the Job pattern chart-wide ("Migrations run as a Helm pre-upgrade/pre-install hook Job… App itself starts with `spring.flyway.enabled=false` in k8s"), not prod-only. Leaving kind/xbmc on the old app-self-migrates path while only prod uses the Job would mean the Job pattern is untested in every environment that's actually deployed to, and only exercised for real the first time it matters. |
| D5 | **New `docker/migrator.Dockerfile`**: `FROM flyway/flyway:<pinned>-alpine`, `COPY backend/src/main/resources/db/migration/ /flyway/sql/`. Built and pushed alongside the app image (Taskfile + CI, D12), tagged with the same commit-sha scheme. Pin the Flyway CLI version to match whatever Spring Boot's dependency management resolves for `flyway-core`/`flyway-database-postgresql` in `backend/build.gradle.kts` (currently `12.4.0` — not pinned explicitly in `gradle/libs.versions.toml` today since it rides `spring-boot-starter-flyway`'s BOM; note this as something to re-check whenever Spring Boot is bumped). | Reuses the official, well-trodden Flyway CLI image instead of teaching the Spring Boot app a "run Flyway then exit" mode with custom `ApplicationRunner` code — the SQL files are the only real payload the Job needs, and a mismatched CLI version between "the app validates a migration's checksum" and "the Job applies it" is the one thing worth being careful about. |
| D6 | **`templates/migration-job.yaml`**: `helm.sh/hook: pre-install,pre-upgrade` + `helm.sh/hook-delete-policy: before-hook-creation` (so a failed migration attempt doesn't block retrying the next upgrade), using the migrator image (D5) and the same `kairon.jdbcUrl` / `kairon.databaseSecretName` helpers the app's own Deployment already uses (`_helpers.tpl`), translated to Flyway's own env var names (`FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD`). | Matches the existing chart's own conventions for building the JDBC URL and resolving whichever Secret holds the DB password (bundled subchart vs. `existingSecret`) — no new resolution logic, just a second consumer of the same helpers. |
| D7 | **Backups**: `templates/backup-pvc.yaml` + `templates/backup-cronjob.yaml`, gated by a new `backup` values block (`enabled`, `schedule`, `retentionDays`, `pvc.size`) — `enabled: false` in `values.yaml`/`values-prod.yaml`, `enabled: true` in `values-xbmc.yaml` with a schedule and retention sized to its `local-path` StorageClass. A `postgres:<matching major>-alpine` CronJob runs `pg_dump`, writes timestamped gzipped dumps to the PVC, and prunes anything older than `retentionDays` with a one-line `find … -mtime +N -delete`. | User's explicit call: local PVC, not off-node. Scoped to `values-xbmc.yaml` specifically because that's the environment holding real data today; `values-prod.yaml` gets the same template available (gated off by default) for whenever it's real. |
| D8 | **Off-node backup replication is out of scope.** `RUNBOOK.md` documents the resulting single-point-of-failure explicitly (a dead node loses the DB and its backups together) as a known, accepted risk with a named follow-up (an occasional manual copy off the node, or a future CronJob step), not silently glossed over. | User's explicit call. Writing the risk down is cheap and means it's a deliberate choice on record, not a gap someone finds by surprise during an actual incident. |
| D9 | **Secrets stay manual — no SOPS / sealed-secrets / external-secrets.** `deploy/RUNBOOK.md` (new) documents the existing `existingSecret` + one-time `kubectl create secret` pattern (already how `values-xbmc.yaml`'s DB password works) with exact commands, and documents JWT-secret rotation via `templates/secret.yaml`'s existing `lookup`-based reuse-or-generate logic (delete the app Secret, then `helm upgrade` — the chart already handles "reuse if present, else generate," this just writes down how to force a rotation). No chart change — the mechanism already exists; this milestone's job is writing it down. | User's explicit call: a single-operator home lab doesn't need a secrets-management tool on top of "nothing sensitive is ever in git" (already true) — the manual pattern is simplest-viable and already proven (the DB password already works this way). |
| D10 | **Security fix, found during this design pass, not on the original roadmap list**: `SecurityConfig`'s `authorizeHttpRequests` chain ends `.anyRequest().permitAll()` — necessary (it's what lets the SPA's static assets and client-side routes like `/login`, `/today` load; none of them match `/api/v1/**` or the two explicit actuator matchers), but it also means *any* actuator endpoint ever added to `management.endpoints.web.exposure.include` beyond today's `health`/`info` becomes immediately public and unauthenticated on the same origin the public Ingress already routes to — a future values/config-only change (not even a code change) could leak `/actuator/env` (every environment variable, including secrets) or `/actuator/prometheus` to the open internet via the Tailscale Funnel hostname, with nothing to catch it. Fix: insert `.requestMatchers("/actuator/**").denyAll()` between the existing `health`/`info` allow and the final catch-all — closes every other actuator path by default regardless of the exposure list, without touching the SPA fallthrough. | Genuinely in scope for "hardening" even though it predates this plan — it's a latent gap that costs nothing to close now and would be a real, silent leak later. A `SecurityConfigTest` (§8) proves the fix actually rejects an unlisted path even when exposure includes it, not just that the code compiles. |
| D11 | **`micrometer-registry-prometheus` is added as a dependency, but `prometheus` is *not* added to `management.endpoints.web.exposure.include` this milestone**, and D10's `denyAll` keeps `/actuator/prometheus` closed even if a future values override tries to expose it. | Standing up a real Prometheus + Grafana is its own milestone (**M12**, `../ROADMAP.md`) — wiring live scraping half-blind here risks exactly D10's mistake in reverse: shipping something open before there's a real, tested consumer for it. Adding the dependency now means M12 only needs a values + `SecurityConfig` allowlist change, not new backend code. |
| D12 | **CI gets a new `deploy.yml` workflow** (separate from `verify.yml`, which stays PR-gating only), triggered on push to `main` after `verify` passes: a `package` job builds + Trivy-scans (`aquasecurity/trivy-action`, fail on HIGH/CRITICAL) both the app image and the new migrator image (D5), pushes both to GHCR tagged with the commit sha, and publishes the OpenAPI spec (D13) as a build artifact; a `deploy` job (needs `package`) joins the tailnet via `tailscale/github-action` and runs `helm upgrade --install` against xbmc using a `KUBECONFIG_XBMC` repository secret. | User's explicit call: full auto-deploy, not package-only. Splitting into a separate workflow file (rather than adding jobs to `verify.yml`) keeps every PR's CI run from ever building/pushing/deploying anything — only a merge to `main` does, matching the "PRs verify, main ships" split `DESIGN.md` §8.5 already sketched. |
| D13 | **`springdoc-openapi` is added, and both the interactive Swagger UI (`/swagger-ui/index.html`) and the raw spec (`/v3/api-docs`) are deliberately made public** on the deployed app (`SecurityConfig`, §5.1) — same reasoning as `/actuator/info`: it's documentation, not data, so there's no reason to gate it behind auth. The spec is *also* published as a CI artifact (D12) independently of the live endpoint, since CI needs it before that commit's image is even deployed. The hand-written web API client is **not** replaced by a generated one this milestone, so there is no "web client drift check" yet (`ROADMAP.md`'s original M7 bullet named both together). | User's explicit call on the exposure question. Swapping `web/src/lib/api/types.ts` and friends for a generated client (`openapi-typescript` + orval, per `CLAUDE.md`'s stack) touches every API call site in the web app — a real, separately-reviewable migration that deserves its own milestone/task rather than riding along inside infra hardening. Publishing the spec now is still valuable on its own (a stable, versioned, publicly-browsable contract) and is the prerequisite for that migration whenever it happens. |
| D14 | **Assisted scheduling (forward pass / optional critical path) is dropped from M7 entirely**, left as an unscheduled backlog item rather than folded in or reassigned to a new milestone number. | It was already marked "optional here or deferred" in `ROADMAP.md`'s M7 bullets; it's a scheduling/UX feature with nothing to do with "hardening & prod," and this milestone already has a full, coherent infra scope without it. |
| D15 | **The `deploy` CI job (D12) targets a named GitHub Environment (`xbmc`)** with no required reviewers configured — auto-deploys on every merge to `main`, same as `task xbmc` today (manual, but always "just run it," no approval step). | Costs nothing now and means adding a manual-approval gate later — worth reconsidering once this is the user's actual daily-driver app being auto-deployed on every merge — is a GitHub repo-settings change (add required reviewers to the Environment), not a workflow rewrite. See Q5. |

---

## 3. Docker

### 3.1 `docker/migrator.Dockerfile` (new)

```dockerfile
# syntax=docker/dockerfile:1
#
# Runs Flyway migrations as a Helm pre-install/pre-upgrade hook Job
# (deploy/helm/kairon/templates/migration-job.yaml). Version pinned to match
# whatever Spring Boot's dependency management resolves for flyway-core /
# flyway-database-postgresql in backend/build.gradle.kts — re-check this tag
# whenever Spring Boot is bumped (see docs/milestones/M7-hardening-prod.md D5).
FROM flyway/flyway:12.4.0-alpine
COPY backend/src/main/resources/db/migration/ /flyway/sql/
```

No entrypoint override — the official image's default entrypoint is `flyway`, and
the Job (§4.1) supplies `migrate` plus the connection env vars as args/env.

### 3.2 `docker/Dockerfile` — unchanged

The app stops *running* Flyway itself in Kubernetes (D4, via a config flag), not
building it differently — no Dockerfile change.

---

## 4. Helm chart changes

### 4.1 `templates/migration-job.yaml` (new)

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "kairon.fullname" . }}-migrate
  labels:
    {{- include "kairon.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "0"
    "helm.sh/hook-delete-policy": before-hook-creation
spec:
  backoffLimit: 2
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: migrate
          image: {{ include "kairon.migratorImage" . | quote }}
          args: ["migrate"]
          env:
            - name: FLYWAY_URL
              value: {{ include "kairon.jdbcUrl" . | quote }}
            - name: FLYWAY_USER
              value: {{ .Values.database.username | quote }}
            - name: FLYWAY_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "kairon.databaseSecretName" . }}
                  key: SPRING_DATASOURCE_PASSWORD
```

`_helpers.tpl` gains `kairon.migratorImage` mirroring `kairon.image` (D5's own
`migrator.image.repository`/`.tag` values, falling back to the same tag as the app
image when unset — they're always built and pushed together, D12).

### 4.2 `templates/hpa.yaml` (new)

```yaml
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "kairon.fullname" . }}
  labels:
    {{- include "kairon.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "kairon.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
{{- end }}
```

`values.yaml` gains:

```yaml
autoscaling:
  enabled: false
  minReplicas: 1
  maxReplicas: 3
  targetCPUUtilizationPercentage: 70
```

`values-prod.yaml` sets `autoscaling.enabled: true` and
`deploymentStrategy.type: RollingUpdate` (D2). `values-xbmc.yaml` doesn't need to
set anything — the base default (`false`) already matches its single-node reality.

### 4.3 `templates/backup-pvc.yaml` + `templates/backup-cronjob.yaml` (new)

```yaml
{{- if .Values.backup.enabled }}
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: {{ include "kairon.fullname" . }}-backups
  labels:
    {{- include "kairon.labels" . | nindent 4 }}
spec:
  accessModes: ["ReadWriteOnce"]
  resources:
    requests:
      storage: {{ .Values.backup.pvc.size }}
{{- end }}
```

```yaml
{{- if .Values.backup.enabled }}
apiVersion: batch/v1
kind: CronJob
metadata:
  name: {{ include "kairon.fullname" . }}-backup
  labels:
    {{- include "kairon.labels" . | nindent 4 }}
spec:
  schedule: {{ .Values.backup.schedule | quote }}
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: backup
              image: postgres:16-alpine
              env:
                - name: PGHOST
                  value: {{ .Values.database.host | default (printf "%s-postgresql" .Release.Name) | quote }}
                - name: PGPORT
                  value: {{ .Values.database.port | default 5432 | quote }}
                - name: PGDATABASE
                  value: {{ .Values.database.name | quote }}
                - name: PGUSER
                  value: {{ .Values.database.username | quote }}
                - name: PGPASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: {{ include "kairon.databaseSecretName" . }}
                      key: SPRING_DATASOURCE_PASSWORD
              command:
                - sh
                - -c
                - >
                  set -eu;
                  pg_dump | gzip > /backups/kairon-$(date +%Y%m%dT%H%M%S).sql.gz;
                  find /backups -name 'kairon-*.sql.gz' -mtime +{{ .Values.backup.retentionDays }} -delete
              volumeMounts:
                - name: backups
                  mountPath: /backups
          volumes:
            - name: backups
              persistentVolumeClaim:
                claimName: {{ include "kairon.fullname" . }}-backups
{{- end }}
```

`values.yaml` gains:

```yaml
backup:
  enabled: false
  schedule: "0 3 * * *"     # daily 03:00
  retentionDays: 14
  pvc:
    size: 2Gi
```

`values-xbmc.yaml` sets `backup.enabled: true` (a 2Gi `local-path` PVC and 14-day
retention is a sane starting point for this app's data volume; revisit the size
once real dumps exist to measure against).

### 4.4 `templates/configmap.yaml`

Adds one line (D4):

```yaml
  SPRING_FLYWAY_ENABLED: "false"
```

### 4.5 `values-prod.yaml` (new)

```yaml
# Hypothetical future managed-cluster overlay — nothing deploys here today (see
# docs/milestones/M7-hardening-prod.md D1). Fill in the real values (host, DB,
# cert-manager issuer name) once a real cluster exists.

image:
  repository: ghcr.io/lfcabend/kairon
  tag: ""

springProfiles: prod

deploymentStrategy:
  type: RollingUpdate

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70

ingress:
  enabled: true
  className: nginx
  host: kairon.example.com   # placeholder — set the real domain before using this file
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  tls:
    enabled: true

resources:
  requests:
    cpu: 500m
    memory: 640Mi
  limits:
    cpu: "1"
    memory: 1Gi

security:
  existingSecret: kairon-app   # pre-created; see deploy/RUNBOOK.md

postgresql:
  enabled: false

database:
  host: ""                    # set to the managed Postgres host
  name: kairon
  username: kairon
  existingSecret: kairon-db    # pre-created; see deploy/RUNBOOK.md

backup:
  enabled: false               # managed Postgres providers typically handle this themselves
```

### 4.6 `values-xbmc.yaml`

Adds `backup.enabled: true` (D7) plus its schedule/retention if different from the
base defaults; no other change needed — `autoscaling` correctly stays at the base
`false`.

---

## 5. Backend changes

### 5.1 `SecurityConfig.java` (D10, D13)

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**", "/api/v1/ping").permitAll()
        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .requestMatchers("/actuator/**").denyAll()
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().permitAll())
```

Two lines inserted; nothing else in the chain changes. The `/v3/api-docs/**` /
`/swagger-ui/**` line is D13's explicit call: without it, those paths would still
end up reachable through the final `.anyRequest().permitAll()` (needed for the
SPA) purely incidentally, the same way an unlisted actuator path used to before
D10 — writing the matcher out makes "the API docs are deliberately public, same
reasoning as `/actuator/info`" a decision on record instead of an accident of
rule ordering. The Javadoc block above the class gains a bullet for both.

### 5.2 `backend/build.gradle.kts`

```kotlin
implementation("io.micrometer:micrometer-registry-prometheus")   // D11
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui")   // D13
```

Both pinned via Spring Boot's dependency management (no explicit version, same
pattern as the app's other Spring Boot starters); `springdoc-openapi`'s version
does need pinning in `gradle/libs.versions.toml` since it isn't part of the Spring
Boot BOM — check the latest release compatible with Spring Boot 4 / Spring
Framework 7 at implementation time.

### 5.3 `application.yml`

No change to `management.endpoints.web.exposure.include` this milestone (stays
`health,info`) — D11 keeps Prometheus present-but-unexposed on purpose.

---

## 6. CI/CD

### 6.1 `verify.yml` — unchanged

Stays the PR gate (web build/test, backend `gradle build`, helm lint +
kubeconform) — never builds, pushes, or deploys anything.

### 6.2 `.github/workflows/deploy.yml` (new)

Triggered on `push` to `main`, after `verify` would already have passed on the
merged PR (a `workflow_run` trigger keyed off `verify`, or simply relying on
branch protection requiring `verify` to pass before merge — either is fine;
prefer whichever is less workflow-YAML for the same guarantee).

```yaml
jobs:
  package:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    outputs:
      image: ${{ steps.tag.outputs.image }}
      migrator-image: ${{ steps.tag.outputs.migrator-image }}
    steps:
      - uses: actions/checkout@v4
      - id: tag
        run: |
          sha=$(git rev-parse --short=12 HEAD)
          echo "image=ghcr.io/lfcabend/kairon:${sha}" >> "$GITHUB_OUTPUT"
          echo "migrator-image=ghcr.io/lfcabend/kairon-migrator:${sha}" >> "$GITHUB_OUTPUT"
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build & push app image
        uses: docker/build-push-action@v6
        with:
          file: docker/Dockerfile
          push: true
          tags: ${{ steps.tag.outputs.image }}
          build-args: GIT_COMMIT=${{ github.sha }}
      - name: Build & push migrator image
        uses: docker/build-push-action@v6
        with:
          file: docker/migrator.Dockerfile
          push: true
          tags: ${{ steps.tag.outputs.migrator-image }}
      - name: Trivy scan (app image)
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ steps.tag.outputs.image }}
          severity: HIGH,CRITICAL
          exit-code: "1"
      - name: Trivy scan (migrator image)
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ steps.tag.outputs.migrator-image }}
          severity: HIGH,CRITICAL
          exit-code: "1"
      - name: Fetch OpenAPI spec
        run: |
          # Boot the app image briefly against a throwaway Postgres, pull
          # /v3/api-docs.yaml, tear down. (Exact approach TBD at implementation —
          # a docker-run + curl + kill is simplest; a dedicated Gradle task that
          # generates the spec at build time without booting anything is nicer
          # if springdoc supports it out of the box for this Spring Boot version.)
      - uses: actions/upload-artifact@v4
        with:
          name: openapi-spec
          path: openapi.yaml

  deploy:
    needs: package
    runs-on: ubuntu-latest
    environment: xbmc
    steps:
      - uses: actions/checkout@v4
      - uses: tailscale/github-action@v3
        with:
          oauth-client-id: ${{ secrets.TS_OAUTH_CLIENT_ID }}
          oauth-secret: ${{ secrets.TS_OAUTH_SECRET }}
          tags: tag:ci
      - uses: azure/setup-helm@v4
      - run: |
          mkdir -p ~/.kube
          echo "${{ secrets.KUBECONFIG_XBMC }}" | base64 -d > ~/.kube/config
          helm dependency update deploy/helm/kairon
          helm upgrade --install kairon deploy/helm/kairon \
            --kube-context xbmc --namespace kairon --create-namespace \
            -f deploy/helm/kairon/values-xbmc.yaml \
            --set image.tag=$(echo "${{ needs.package.outputs.image }}" | cut -d: -f2) \
            --set migrator.image.tag=$(echo "${{ needs.package.outputs.migrator-image }}" | cut -d: -f2) \
            --wait --timeout 65m
```

(Sketch, not final YAML — exact step versions/actions get pinned at
implementation time, same as every other CI action already in `verify.yml`.)

### 6.3 One-time setup only the user can do

I can't create GitHub repository secrets, GitHub Environments, or a Tailscale
OAuth client myself — these are manual, one-time steps `RUNBOOK.md` documents but
someone with repo-admin/Tailscale-admin access has to actually perform:

1. Create a Tailscale OAuth client (tag `tag:ci` or similar) for the
   `tailscale/github-action` step; store its client id/secret as repo secrets
   (`TS_OAUTH_CLIENT_ID`, `TS_OAUTH_SECRET`).
2. Extract xbmc's kubeconfig (`/etc/rancher/k3s/k3s.yaml` on the node), rewrite
   its `server:` field to the Tailscale hostname (`xbmc.tail9ae0e9.ts.net`), and
   rename its context from k3s's default (`default`) to `xbmc` (`kubectl config
   rename-context default xbmc --kubeconfig <file>`) so `deploy.yml`'s
   `--kube-context xbmc` (matching `Taskfile.yml`'s existing convention)
   resolves correctly. Base64-encode the result, store as the `KUBECONFIG_XBMC`
   repo secret.
3. Create the `xbmc` GitHub Environment (Settings → Environments) — no required
   reviewers to start (D15); add them later if wanted.
4. **Make `ghcr.io/lfcabend/kairon-migrator` public** the first time it's ever
   pushed — GHCR defaults a brand-new container package to **private**, and
   there is no API/CLI flag on the push itself to make it public up front.
   Confirmed the hard way during the real xbmc trial deploy for this milestone:
   the first `task xbmc` run pushed the new package fine, but the
   `kairon-migrate` pre-upgrade hook Job then sat in `ImagePullBackOff` against
   the real cluster (`401 Unauthorized` pulling anonymously) until the package
   was flipped to public by hand at
   `https://github.com/users/lfcabend/packages/container/kairon-migrator/settings`
   → Danger Zone → Change visibility → Public. Because it's a **pre-upgrade**
   hook, this failure mode is safe — Helm blocks the app rollout until the hook
   Job completes, so the previous (working) app version keeps serving traffic
   the whole time — but it does stall the deploy until someone flips the
   toggle. `values-xbmc.yaml`'s existing comment already notes the same
   constraint for the app image; it now applies to the migrator image too, and
   this is a one-time step per package, not per deploy.

---

## 7. `deploy/RUNBOOK.md` (new)

Sections, each a short, copy-pasteable set of commands:

- **Restore from backup** — `kubectl cp` a dump off the backup PVC, `gunzip | psql`
  against a target database (either in place, for disaster recovery, or into a
  scratch database to inspect first).
- **Rotate the JWT secret** — `kubectl delete secret <appSecretName>` then
  `helm upgrade` (the chart's `lookup`-based reuse-or-generate logic mints a new
  one since the old one is gone); note this invalidates every live session.
- **Rotate the DB password** — change it at the database, then update (or
  recreate) the `existingSecret` the chart points at; `helm upgrade` picks it up
  on the next pod restart.
- **Create the required secrets on a fresh cluster** — the exact
  `kubectl create secret generic … --from-literal=SPRING_DATASOURCE_PASSWORD=…`
  command `values-xbmc.yaml`'s own comment already describes, written down as a
  runnable step rather than a comment someone has to go find.
- **Roll back a Helm release** — `helm rollback kairon <revision>`, plus a note
  that the migration Job (D4/D6) does *not* run on rollback (Helm hooks are
  install/upgrade-only) — a schema rollback, if ever needed, is a manual Flyway
  `undo`/hand-written down-migration, same caveat every Flyway-based project has.
- **Read logs** — `kubectl logs`, and how the correlation id (`X-Request-Id`) ties
  a browser-reported issue to a specific server log line via ECS JSON fields.
- **Known risk: backups are on-node** (D8) — stated plainly, with the manual
  off-node copy as a named (not yet automated) mitigation.

---

## 8. Testing

Infra work, not app logic — no new unit/integration/Playwright pyramid. What each
piece gets:

| Item | Verification |
| --- | --- |
| D10 (`SecurityConfig`) | New `SecurityConfigTest` (`@WebMvcTest` or a focused `@SpringBootTest` slice) asserting a request to an actuator path *not* in the `health`/`info` allowlist — e.g. `/actuator/env` — returns `401`/`403` even when a test-only property override adds it to `management.endpoints.web.exposure.include`, proving the fix closes the path rather than merely relying on it never being exposed. |
| D4/D6 (migration Job) | Manual: drop the local kind release's database (or start a fresh one), `task up`, confirm the schema lands via `kubectl logs job/<name>-migrate` before the app pod ever starts, and that the app boots with `SPRING_FLYWAY_ENABLED=false` without re-running migrations itself. |
| D7 (backup CronJob) | Manual: `kubectl create job --from=cronjob/<name>-backup <name>-backup-test`, confirm a `.sql.gz` lands on the PVC and restores cleanly into a scratch database (exercises the Restore section of `RUNBOOK.md` end to end once, not just on paper). |
| D2 (HPA) | `helm template -f values-prod.yaml \| kubeconform` (existing CI job already covers new templates syntactically); no live cluster to exercise scaling against yet — deferred until `values-prod.yaml` has a real target. |
| D12 (`deploy.yml`) | Exercised for real the first time it runs on a merge to `main` — no meaningful way to test a deploy workflow against the actual xbmc cluster without running it. Reviewed carefully before first use instead. |

---

## 9. Task breakdown (suggested order)

1. D10 security fix (`SecurityConfig.java`) + `SecurityConfigTest` — smallest,
   highest-value, no infra dependency on anything else in this list.
2. `docker/migrator.Dockerfile` + a `task migrator-image` Taskfile entry for local
   testing (mirrors `task image`).
3. `templates/migration-job.yaml` + `kairon.migratorImage` helper +
   `configmap.yaml`'s `SPRING_FLYWAY_ENABLED=false`; verify against a dropped
   local kind database via `task up`.
4. `templates/hpa.yaml` + `autoscaling` values block (off everywhere but prod).
5. `values-prod.yaml` (D1) — placeholder host/issuer, real values filled in
   whenever a real managed cluster exists.
6. `templates/backup-pvc.yaml` + `templates/backup-cronjob.yaml` + `backup`
   values block; enable + tune in `values-xbmc.yaml`; manually verify a dump
   lands and restores (§8).
7. `micrometer-registry-prometheus` + `springdoc-openapi` dependencies; confirm
   `/v3/api-docs` responds and `/actuator/prometheus` is still closed (D10/D11
   together — this is the pair the `SecurityConfigTest` from step 1 should also
   cover once the dependency exists).
8. `.github/workflows/deploy.yml` (D12) — package job first, verify images land
   in GHCR with a manual workflow_dispatch run before wiring the deploy job.
9. `deploy/RUNBOOK.md` (§7).
10. The one-time manual setup (§6.3) — done by whoever has repo-admin/Tailscale
    access; not something I can do from here.
11. Docs: tick M7 boxes in `ROADMAP.md` as each lands; reword the M7 bullet list
    itself to match the decisions here (`values-prod.yaml` scoped to the
    hypothetical managed cluster, not xbmc; add the `SecurityConfig` fix as its
    own bullet); update `DESIGN.md` §8 (Flyway job applies chart-wide, not
    prod-only; the actuator lockdown; the spec-published/no-client-yet split);
    update `CLAUDE.md`'s "Current state"; flip this doc's Status to `Accepted`.

---

## 10. Open questions / risks

| # | Question | Recommendation |
| --- | --- | --- |
| ~~Q1~~ | ~~D11 adds the Prometheus dependency and a closed endpoint but doesn't wire a live scraper.~~ | **Resolved** — split into its own milestone, **M12 — Observability: Prometheus & Grafana** (`../ROADMAP.md`), rather than answered inline here. |
| Q2 | D3's per-instance rate limiting only matters once `values-prod.yaml` runs with `replicas > 1` — is that scenario realistic soon, or purely hypothetical alongside the rest of `values-prod.yaml`? | Accept for now — a distributed backend means standing up Redis for a deployment that doesn't exist yet. Revisit if/when `values-prod.yaml` gets a real target. |
| ~~Q3~~ | ~~`values-xbmc.yaml`'s existing comment already notes the GHCR package must stay public since the chart has no `imagePullSecrets` support. Worth adding (small, mechanical: a `Secret` + a `serviceAccount.imagePullSecrets` values field) so the package could go private later?~~ | **Resolved** — user's explicit call: skip for this milestone. Both `ghcr.io/lfcabend/kairon` and the new `ghcr.io/lfcabend/kairon-migrator` package stay public; `imagePullSecrets` support is a backlog item, not scheduled. |
| Q4 | D8's local-only backups are a known single point of failure by the user's own choice. Worth a specific off-node target in mind now (a NAS share, a cheap object-storage bucket), or truly defer until later? | Not blocking — flagged in `RUNBOOK.md` so it's a deliberate, visible choice rather than a gap discovered mid-incident. Revisit once the CronJob has been running long enough to know the real dump size. |
| Q5 | D15 ships `deploy.yml` with no required-reviewer gate on the `xbmc` Environment — auto-deploying to the user's actual daily-driver app on every merge to `main`. Is that the right default, or should the very first version of this workflow ship with a manual-approval step already on, tightened later if it turns out to be unnecessary friction? | Recommend shipping open, matching how `task xbmc` already works today (manual command, no approval step, "just run it") — the Environment seam means adding a gate later is a repo-settings change, not a workflow rewrite, if the first few auto-deploys make it feel too loose. |

---

## 11. Doc updates this milestone produces

- `ROADMAP.md` — reword the M7 bullet list to match the decisions here
  (`values-prod.yaml` explicitly scoped to a hypothetical managed cluster, not
  xbmc; add the `SecurityConfig` actuator-lockdown as its own bullet; note the
  spec-published/no-generated-client split); tick boxes as each item lands.
- `DESIGN.md` §8 — Flyway-as-Job applies to every k8s environment, not just prod
  (§8.3 already implied this but M7 is what makes it literally true); §11 gains a
  line on the actuator lockdown and on Prometheus being present-but-unexposed.
- `CLAUDE.md` "Current state" — once implemented: new `RUNBOOK.md`, the migrator
  image, the `deploy.yml` workflow, the `SecurityConfig` hardening, the
  `springdoc-openapi`/`micrometer-registry-prometheus` dependencies.
- New `deploy/RUNBOOK.md` (§7).
- New `docker/migrator.Dockerfile`, `deploy/helm/kairon/values-prod.yaml`.
- This file — flip `Status: Draft` → `Accepted` once Q1–Q5 are resolved and the
  milestone is implemented.
