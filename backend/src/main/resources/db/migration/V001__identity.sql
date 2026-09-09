-- Identity schema. Every user-owned row in later migrations carries a user_id
-- referencing this table. See docs/DATA_MODEL.md (app_user, refresh_token).
--
-- M0 introduced app_user; M1 appends refresh_token in place (the project is
-- pre-release, so extending V001 is fine — no deployed database to re-checksum).

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    email         varchar(320) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name  varchar(80)  NOT NULL,
    timezone      varchar(64)  NOT NULL DEFAULT 'UTC',
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    preferences   jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT app_user_status_check CHECK (status IN ('ACTIVE', 'PENDING', 'DISABLED'))
);

-- Email is the login identifier, stored lower-cased; unique case-folded.
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email));

-- Opaque refresh tokens, stored only as a SHA-256 hash. Rotated on every use; a
-- presented token that is already revoked means theft, and the whole family_id is
-- revoked. `logout` revokes one row, `logout-all` revokes every row for the user.
CREATE TABLE refresh_token (
    id         uuid         PRIMARY KEY,
    user_id    uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash varchar(64)  NOT NULL,
    family_id  uuid         NOT NULL,
    user_agent varchar(255),
    expires_at timestamptz  NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz  NOT NULL
);

CREATE UNIQUE INDEX refresh_token_hash_key    ON refresh_token (token_hash);
CREATE INDEX        refresh_token_user_id_idx ON refresh_token (user_id);
CREATE INDEX        refresh_token_family_idx  ON refresh_token (family_id);
