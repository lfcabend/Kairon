-- M10.5: a new long-lived personal-access-token (PAT) mechanism, distinct
-- from the web/Android refresh token, for the MCP server to forward verbatim
-- on every request (docs/milestones/M10.5-mcp-server.md D2). No soft delete —
-- a revoked token is marked revoked_at, never deleted, so "who had access and
-- when" stays auditable; a PAT has no rotation-family concept (unlike
-- refresh_token), so there's no family id either.
CREATE TABLE personal_access_token (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES app_user(id),
    name          VARCHAR(100) NOT NULL,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_personal_access_token_user
    ON personal_access_token (user_id) WHERE revoked_at IS NULL;

-- Purely descriptive/audit: which path produced this run. Defaults 'API' so
-- every existing row needs no backfill (docs/milestones/M10.5-mcp-server.md D10).
ALTER TABLE assistant_run ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'API'
    CHECK (source IN ('API', 'MCP'));
