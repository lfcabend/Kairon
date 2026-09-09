-- Daily todo items (module: todo). One item belongs to one user and one calendar
-- day (interpreted in the user's timezone; the server stores whatever `day` the
-- client sends). See docs/DATA_MODEL.md (todo_item) and docs/DESIGN.md §2.1.

CREATE TABLE todo_item (
    id                     uuid         PRIMARY KEY,
    user_id                uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day                    date         NOT NULL,
    title                  varchar(500) NOT NULL,
    notes                  text,
    status                 varchar(20)  NOT NULL DEFAULT 'OPEN',
    priority               smallint     NOT NULL DEFAULT 0,
    position               integer      NOT NULL,
    estimate_minutes       integer,
    source_project_task_id uuid,        -- FK added in V004 when project_task exists
    rolled_over_from_id     uuid        REFERENCES todo_item (id) ON DELETE SET NULL,
    completed_at           timestamptz,
    deleted_at             timestamptz,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    version                bigint       NOT NULL DEFAULT 0,

    CONSTRAINT todo_item_status_check   CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    CONSTRAINT todo_item_priority_check CHECK (priority BETWEEN 0 AND 3)
);

CREATE INDEX todo_item_user_day_idx ON todo_item (user_id, day) WHERE deleted_at IS NULL;
CREATE INDEX todo_item_source_task_idx ON todo_item (source_project_task_id);
