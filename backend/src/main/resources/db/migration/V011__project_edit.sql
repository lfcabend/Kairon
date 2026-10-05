-- Adds PROJECT_EDIT to assistant_run.kind (M8's V007/M8.5's V008 didn't
-- anticipate this value either) and the assistant_suggested_project_edit
-- table: one row per PROJECT_EDIT run, scoped to the existing project it
-- targets, the whole proposed diff stored as jsonb (PROPOSED -> ACCEPTED |
-- DISMISSED). Unlike assistant_suggested_project, accepting never creates a
-- new project — it mutates the one project_id this row already targets, so
-- there's no accepted_project_id column (docs/milestones/
-- M9.5-ai-project-editing.md D2/D3). No soft delete: DELETE (cascaded from
-- assistant_run) is a hard delete. See docs/DATA_MODEL.md
-- (assistant_suggested_project_edit).

ALTER TABLE assistant_run DROP CONSTRAINT assistant_run_kind_check;
ALTER TABLE assistant_run ADD CONSTRAINT assistant_run_kind_check
    CHECK (kind IN ('TODO_SUGGESTION', 'WEEKLY_SUMMARY', 'MONTHLY_SUMMARY',
                     'JOURNAL_REFLECTION', 'PROJECT_GENERATION', 'PROJECT_EDIT'));

CREATE TABLE assistant_suggested_project_edit (
    id          uuid          PRIMARY KEY,
    run_id      uuid          NOT NULL REFERENCES assistant_run (id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    project_id  uuid          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    diff        jsonb         NOT NULL,
    status      varchar(20)   NOT NULL,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT assistant_suggested_project_edit_status_check
        CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISMISSED'))
);

CREATE UNIQUE INDEX assistant_suggested_project_edit_run_idx    ON assistant_suggested_project_edit (run_id);
CREATE INDEX assistant_suggested_project_edit_user_status_idx   ON assistant_suggested_project_edit (user_id, status);
CREATE INDEX assistant_suggested_project_edit_project_idx       ON assistant_suggested_project_edit (project_id);
