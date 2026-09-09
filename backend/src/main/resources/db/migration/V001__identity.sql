-- Identity schema. Every user-owned row in later migrations carries a user_id
-- referencing this table. See docs/DATA_MODEL.md (app_user).
--
-- M0 introduces app_user only. The refresh_token table and the rest of the auth
-- flow are appended to this migration in M1 (the project is pre-release, so
-- extending V001 in place is fine — no deployed database exists to re-checksum).

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
