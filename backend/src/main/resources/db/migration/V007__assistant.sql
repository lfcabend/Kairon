-- Assistant runs and their suggested tasks (module: assistant). One
-- assistant_run row per call to the Anthropic API (PENDING -> RUNNING ->
-- SUCCEEDED | FAILED); input_snapshot is exactly what was sent, for user
-- audit (docs/DESIGN.md §13.5). TODO_SUGGESTION runs also produce
-- assistant_suggested_task rows; the other three kinds only ever populate
-- output_markdown (M9/M10) — both are defined in full now so a later
-- milestone needs no migration to widen the kind check
-- (docs/milestones/M8-assistant-foundations.md D2).
-- No soft delete on either table: DELETE is a hard delete that purges the
-- stored input_snapshot, cascading to the run's suggested tasks.
-- See docs/DATA_MODEL.md (assistant_run, assistant_suggested_task).

CREATE TABLE assistant_run (
    id              uuid          PRIMARY KEY,
    user_id         uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind            varchar(30)   NOT NULL,
    status          varchar(20)   NOT NULL,
    period_start    date,
    period_end      date,
    model           varchar(60)   NOT NULL,
    input_snapshot  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    output_markdown text,
    input_tokens    integer,
    output_tokens   integer,
    error           text,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,

    CONSTRAINT assistant_run_kind_check
        CHECK (kind IN ('TODO_SUGGESTION', 'WEEKLY_SUMMARY', 'MONTHLY_SUMMARY', 'JOURNAL_REFLECTION')),
    CONSTRAINT assistant_run_status_check
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX assistant_run_user_kind_created_idx ON assistant_run (user_id, kind, created_at);

CREATE TABLE assistant_suggested_task (
    id                     uuid          PRIMARY KEY,
    run_id                 uuid          NOT NULL REFERENCES assistant_run (id) ON DELETE CASCADE,
    user_id                uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title                  varchar(500)  NOT NULL,
    notes                  text,
    rationale              text,
    suggested_for_day      date,
    estimate_minutes       integer,
    source_project_task_id uuid          REFERENCES project_task (id) ON DELETE SET NULL,
    status                 varchar(20)   NOT NULL,
    accepted_todo_item_id  uuid          REFERENCES todo_item (id) ON DELETE SET NULL,
    position               integer       NOT NULL,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL DEFAULT 0,

    CONSTRAINT assistant_suggested_task_status_check
        CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISMISSED'))
);

CREATE INDEX assistant_suggested_task_run_idx         ON assistant_suggested_task (run_id);
CREATE INDEX assistant_suggested_task_user_status_idx ON assistant_suggested_task (user_id, status);
