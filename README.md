# Kairon

> **καιρός + ἔργον** — *the right task at the right time.*

Kairon is a **personal productivity system**: a place to run your day and plan your
small projects, without the ceremony of a team tool like Jira.

Ancient Greek distinguished *chronos* (χρόνος), clock time that just passes, from
*kairos* (καιρός), the **opportune moment** — the right time to act. `Kairón`
(καιρόν) is its accusative form: *seize the moment*. Paired with *ergon* (ἔργον),
"work" or "task", the name states the product's whole purpose: help you put the
**right task** into the **right moment**.

---

## Core features

| Feature | What it is |
| --- | --- |
| **Daily Todo** | A todo list scoped to a calendar day. Add, reorder, complete, and roll incomplete items forward to the next day. |
| **Daily Journal** | A dated journal. One or more markdown entries per day, navigable by calendar, searchable. |
| **Projects** | Lightweight project management: a project has a planned start/end, a set of tasks with estimates and dependencies, and a **Gantt chart**. Personal scale — no assignees, workflows, or permissions (yet). |
| **Today** | The feature that ties it together: an aggregated view of today's todos plus project tasks whose planned window covers today, so you can decide what this moment is for. |
| **AI assistant** *(later, opt-in)* | Optional Anthropic-powered help: suggests a todo list from your active projects, writes weekly/monthly execution summaries with efficiency tips, and reflects on your weekly journal. Off by default; each feature is opted into separately. |

## Platforms

- **Backend** — Spring Boot REST API (this is the source of truth). Also serves
  the web app.
- **Web** — React + TypeScript single-page app, **built into the backend jar and
  served by it**. One deployable, one origin, no separate web server.
- **Mobile** — native Android (Jetpack Compose) and iOS (SwiftUI) apps, later, talking to the same API.

## Tech stack (summary)

Java 25 · Spring Boot 4 · PostgreSQL 16 · Flyway · Spring Security + JWT ·
React 18 · Vite · TanStack Query · Tailwind + shadcn/ui ·
Anthropic Java SDK (AI assistant, later) ·
Docker · Kubernetes · Helm 3 · GitHub Actions

Full rationale in [`docs/DESIGN.md`](docs/DESIGN.md).

## Repository layout

```
Kairon/
├── docs/                     # design, data model, roadmap, ADRs
├── backend/                  # Spring Boot service (Gradle, Java 25)
├── web/                      # React + Vite + TS SPA (Gradle :web builds it into the backend jar)
├── mobile/                   # Android + iOS apps (later)
├── deploy/helm/kairon/       # Helm chart (app + PostgreSQL dependency)
├── docker/                   # Dockerfile — one image: API + SPA
├── docker-compose.yml        # local Postgres for development
└── .github/workflows/        # CI/CD
```

## Documentation

- [`docs/DESIGN.md`](docs/DESIGN.md) — architecture, modules, API, frontend, deployment.
- [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) — entities, schema, ER diagram.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — milestones M0–M11.
- [`docs/adr/`](docs/adr/) — architecture decision records (0001 stack, 0002 AI assistant).

## Status

Planning / design. No code yet. First milestone (**M0 — walking skeleton**) is
described in the roadmap. The AI assistant is a later phase (**M8–M10**); native
mobile is **M11+**.
