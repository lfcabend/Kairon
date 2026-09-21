# ADR 0001 — Architecture and stack

- Status: **Accepted**
- Date: 2026-08-28
- Deciders: project owner

## Context

Kairon is a personal productivity tool (daily todo, daily journal, small
projects with a Gantt chart). It needs a backend that a web app uses now and
native Android/iOS apps use later. It will be containerised and deployed to
Kubernetes with Helm. It is built and maintained by one developer.

Four decisions were taken up front (see the project brief):

1. **Multi-user with registration** — not a single hard-coded account.
2. **PostgreSQL** as the database.
3. **Monorepo** for backend, web, mobile, and deployment.
4. First milestone focuses on **Daily Todo + Journal** end to end.

## Decision

### Backend — modular monolith, Spring Boot, Java 25

One Spring Boot 4 service (Spring Framework 7), Java 25 (current LTS), Gradle (Kotlin DSL). Internally organised
**package-by-feature** (`identity`, `todo`, `journal`, `projects`, `planning`,
plus a `common` shared kernel). Modules talk only through each other's public
`api` package; boundaries enforced by an ArchUnit test.

Persistence: Spring Data JPA / Hibernate ORM 7 on PostgreSQL 18, schema managed by
**Flyway** (plain versioned SQL) rather than Liquibase — see the alternatives
table. Security: Spring Security 7 with JWT access tokens + rotating opaque
refresh tokens, Argon2id password hashing. API documented with springdoc-openapi;
the generated spec drives client generation.

### Frontend — React SPA

React 18 + TypeScript + Vite. TanStack Query for server state, Zustand for the
little client state there is, React Router, React Hook Form + Zod, Tailwind +
shadcn/ui. Gantt via `gantt-task-react` (MIT) initially. API client generated
from the OpenAPI spec.

`web/` stays a self-contained codebase but is **not deployed separately**: a
Gradle `:web` subproject builds it into the backend jar, which serves the static
SPA (with a client-side-routing fallback) alongside the REST API. One image, one
deployment, one origin — no nginx, no CORS, no runtime API-URL config. The dev
loop still uses the Vite dev server with a `/api` proxy.

### Mobile — later, native

Native Android (Jetpack Compose) and iOS (SwiftUI), added at M11+ (was M8+
before ADR 0002 inserted the AI-assistant milestones), consuming the same API
via generated clients. Schema carries `updated_at` / soft-delete columns now so
an offline sync endpoint can be added without migration pain.

### Deployment — Docker + Kubernetes + Helm

One multi-stage Dockerfile (node build → gradle build → temurin JRE) producing a
**single image** that serves both the API and the SPA. A **single Helm
application chart** with PostgreSQL as a chart *dependency* (Bitnami subchart for
local, external DB in prod) deploys one backend workload; the ingress routes the
whole host to it. Flyway runs as a Helm pre-upgrade hook Job. Local target is a
kind/minikube cluster; prod is a managed cluster with ingress-nginx +
cert-manager. CI/CD on GitHub Actions, image to GHCR.

### Repository — monorepo

```
backend/  web/  mobile/  deploy/  docker/  docs/
```

## Alternatives considered

| Area | Rejected option | Why |
| --- | --- | --- |
| Backend shape | Microservices from the start | No scaling or team reason to pay the operational cost for a personal tool. The module seams leave the door open. |
| Backend shape | Plain layered monolith (package-by-layer) | Feature modules keep related code together and make a future split cheaper. |
| Language | Kotlin backend | Fine choice; Java 25 (current LTS) chosen for the owner's familiarity and the widest example/library base. Revisit is low-cost. |
| DB | SQLite/H2 file | Awkward in Kubernetes (needs a PV, no clean HA), weaker concurrency and full-text search. Postgres is easy to run in a container and gives FTS for the journal. |
| Migrations | Liquibase | Its main edge — database-agnostic changesets and free per-changeset rollback — does not apply: Kairon targets Postgres only, uses Postgres-specific DDL (`tsvector`, GIN, partial indexes) that would become raw `<sql>` blocks anyway, and the Helm pre-upgrade migration Job + rolling deploys favour expand/contract and roll-forward over `rollback`. Flyway's folder of numbered `.sql` files is the lighter model for a solo maintainer. Revisit if a second target DB or context-conditional migrations are needed. |
| Auth | External OIDC (Keycloak/Authelia) | Heaviest footprint for a personal tool; adds a second stateful service to operate. Self-issued JWT is enough; can move to OIDC later since the app is already token-based. |
| Auth | Single hard-coded user | Explicitly rejected in the brief — sharing one instance with family/friends should not need a migration. |
| Frontend UI | MUI / Ant Design | Prefer owning lightweight components (shadcn/ui) over a large opinionated dependency. |
| Web hosting | Separate nginx container serving the SPA | Rejected for simplification. Embedding the build in the backend jar gives one deployable, one origin (no CORS, no runtime base-URL injection, genuinely first-party auth cookie), and a simpler ingress. At personal scale the JVM's static-asset serving cost is negligible. Trade-offs accepted: a frontend-only change needs a backend redeploy, web and API cannot scale or roll independently, and the backend build needs Node. `web/` stays isolated so this is reversible. |
| Gantt | dhtmlx-gantt | GPL / commercial licensing. `gantt-task-react` is MIT; custom SVG is the fallback. |
| Helm | Umbrella chart of subcharts | Only one deployable app; a single chart with a Postgres dependency is simpler. Umbrella reconsidered if more services appear. |
| Repo | Polyrepo | More CI wiring and contract-sync overhead for a solo developer; monorepo keeps the API contract and generated clients in lockstep. |

## Consequences

- The backend is one process — and one image — to build, test, run, and deploy;
  the SPA ships inside it. Testcontainers gives real-Postgres tests without extra
  infrastructure.
- Frontend-only changes still go through a backend build and redeploy, and the
  backend build depends on a Node toolchain. Accepted for the simplicity of a
  single deployable.
- ArchUnit failures are the guardrail that keeps the "modular" in modular
  monolith; without discipline it degrades to a big ball of mud.
- Generated API clients mean a spec change can break the web build on purpose —
  that is the contract check.
- Choosing Postgres commits us to running a stateful workload (or paying for a
  managed one) in every environment.
- Deferring mobile means the offline/sync design is only sketched; the schema
  hedges (soft deletes, `updated_at`, client-generatable UUIDv7 ids) but the
  sync endpoint is unproven until M11.
