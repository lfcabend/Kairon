# syntax=docker/dockerfile:1
#
# The MCP server (docs/milestones/M10.5-mcp-server.md) — a small, always-running
# Node/TypeScript service, entirely separate from the main Kairon jar's build.
# No JDK involved at all.
#
# Build context is the repo root: docker build -f docker/mcp-server.Dockerfile -t kairon-mcp-server .

# ---- Stage 1: install deps + compile ---------------------------------------
FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY mcp-server/package.json mcp-server/package-lock.json ./
RUN npm ci
COPY mcp-server/ ./
RUN npm run build

# ---- Stage 2: runtime --------------------------------------------------------
FROM node:24-bookworm-slim AS runtime
# Fixed numeric uid/gid, same convention as docker/Dockerfile, so Kubernetes'
# runAsNonRoot check can verify the user without inspecting the image (must
# match deploy/helm/kairon-mcp/templates/deployment.yaml).
RUN groupadd --system --gid 10001 kairon && \
    useradd --system --uid 10001 --gid 10001 --home /app kairon
WORKDIR /app
COPY mcp-server/package.json mcp-server/package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist/ dist/
USER 10001:10001
EXPOSE 3000
ENV NODE_ENV=production
ENTRYPOINT ["node", "dist/index.js"]
