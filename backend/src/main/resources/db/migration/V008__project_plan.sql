-- Adds PROJECT_GENERATION to assistant_run.kind (M8's V007 only anticipated
-- the four kinds already named in docs/DATA_MODEL.md at the time) and the
-- assistant_suggested_project table: one row per PROJECT_GENERATION run, the
-- whole proposed plan stored as jsonb (PROPOSED -> ACCEPTED | DISMISSED).
-- Unlike assistant_suggested_task, individual tasks/dependencies inside the
-- plan have no server-tracked lifecycle of their own — the whole project is
-- created in one shot on accept (docs/milestones/M8.5-project-generation.md D2).
-- No soft delete: DELETE (cascaded from assistant_run) is a hard delete.
-- See docs/DATA_MODEL.md (assistant_suggested_project).

ALTER TABLE assistant_run DROP CONSTRAINT assistant_run_kind_check;
ALTER TABLE assistant_run ADD CONSTRAINT assistant_run_kind_check
    CHECK (kind IN ('TODO_SUGGESTION', 'WEEKLY_SUMMARY', 'MONTHLY_SUMMARY',
                     'JOURNAL_REFLECTION', 'PROJECT_GENERATION'));

CREATE TABLE assistant_suggested_project (
    id                  uuid          PRIMARY KEY,
    run_id              uuid          NOT NULL REFERENCES assistant_run (id) ON DELETE CASCADE,
    user_id             uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    plan                jsonb         NOT NULL,
    status              varchar(20)   NOT NULL,
    accepted_project_id uuid          REFERENCES project (id) ON DELETE SET NULL,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT assistant_suggested_project_status_check
        CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISMISSED'))
);

CREATE UNIQUE INDEX assistant_suggested_project_run_idx        ON assistant_suggested_project (run_id);
CREATE INDEX assistant_suggested_project_user_status_idx ON assistant_suggested_project (user_id, status);
