-- Projects and their tasks (module: projects). A project belongs to one user
-- and may be grouped under a project_category (D12); project_task.parent_task_id
-- gives an at-most-2-level breakdown, enforced in the service
-- (docs/milestones/M4-projects-core.md D5/§4.4), not the schema.
-- See docs/DATA_MODEL.md (project_category, project, project_task) and
-- docs/DESIGN.md §2.3.

CREATE TABLE project_category (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name         varchar(100)  NOT NULL,
    color        varchar(7)    NOT NULL DEFAULT '#6366f1',
    position     integer       NOT NULL,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,

    CONSTRAINT project_category_color_check CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT project_category_user_name_uk UNIQUE (user_id, name)
);

CREATE INDEX project_category_user_position_idx ON project_category (user_id, position);

CREATE TABLE project (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category_id  uuid          REFERENCES project_category (id) ON DELETE SET NULL,
    name         varchar(200)  NOT NULL,
    description  text,
    status       varchar(20)   NOT NULL DEFAULT 'PLANNING',
    size         varchar(2),
    priority_rank integer      NOT NULL,
    color        varchar(7)    NOT NULL DEFAULT '#6366f1',
    start_date   date,
    end_date     date,
    actual_start date,
    actual_end   date,
    deleted_at   timestamptz,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,

    CONSTRAINT project_status_check CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'DONE', 'ARCHIVED')),
    CONSTRAINT project_size_check   CHECK (size IS NULL OR size IN ('XS', 'S', 'M', 'L', 'XL')),
    CONSTRAINT project_color_check  CHECK (color ~ '^#[0-9a-fA-F]{6}$')
);

CREATE INDEX project_user_status_idx  ON project (user_id, status)        WHERE deleted_at IS NULL;
CREATE INDEX project_category_idx    ON project (category_id)            WHERE deleted_at IS NULL;
CREATE INDEX project_priority_rank_idx ON project (user_id, priority_rank) WHERE deleted_at IS NULL AND status <> 'ARCHIVED';

CREATE TABLE project_task (
    id                uuid          PRIMARY KEY,
    project_id        uuid          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    parent_task_id    uuid          REFERENCES project_task (id) ON DELETE CASCADE,
    name              varchar(300)  NOT NULL,
    description       text,
    status            varchar(20)   NOT NULL DEFAULT 'TODO',
    is_milestone      boolean       NOT NULL DEFAULT false,
    planned_start     date,
    planned_end       date,
    estimate_hours    numeric(6,2),
    actual_hours      numeric(6,2),
    progress_percent  smallint      NOT NULL DEFAULT 0,
    position          integer       NOT NULL,
    deleted_at        timestamptz,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,

    CONSTRAINT project_task_status_check   CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    CONSTRAINT project_task_progress_check CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT project_task_estimate_check CHECK (estimate_hours IS NULL OR estimate_hours >= 0),
    CONSTRAINT project_task_actual_check   CHECK (actual_hours IS NULL OR actual_hours >= 0)
);

CREATE INDEX project_task_project_idx ON project_task (project_id)     WHERE deleted_at IS NULL;
CREATE INDEX project_task_parent_idx  ON project_task (parent_task_id) WHERE deleted_at IS NULL;

-- M2 left this as a bare uuid because project_task didn't exist yet (M2 §3).
ALTER TABLE todo_item
    ADD CONSTRAINT todo_item_source_project_task_fk
    FOREIGN KEY (source_project_task_id) REFERENCES project_task (id) ON DELETE SET NULL;
