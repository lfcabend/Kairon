-- Dependencies between project tasks (module: projects). An edge points
-- predecessor -> successor; both tasks must belong to the same project
-- (service-enforced, docs/milestones/M5-gantt-dependencies.md D4) and the
-- service rejects any edge that would create a cycle (D3). No soft delete —
-- deleting a task hard-deletes its edges (D5).
-- See docs/DATA_MODEL.md (task_dependency) and docs/DESIGN.md §7.

CREATE TABLE task_dependency (
    id             uuid        PRIMARY KEY,
    predecessor_id uuid        NOT NULL REFERENCES project_task (id) ON DELETE CASCADE,
    successor_id   uuid        NOT NULL REFERENCES project_task (id) ON DELETE CASCADE,
    type           varchar(2)  NOT NULL DEFAULT 'FS',
    lag_days       integer     NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL,

    CONSTRAINT task_dependency_type_check CHECK (type IN ('FS', 'SS', 'FF', 'SF')),
    CONSTRAINT task_dependency_not_self_check CHECK (predecessor_id <> successor_id),
    CONSTRAINT task_dependency_uk UNIQUE (predecessor_id, successor_id)
);

CREATE INDEX task_dependency_predecessor_idx ON task_dependency (predecessor_id);
CREATE INDEX task_dependency_successor_idx   ON task_dependency (successor_id);
