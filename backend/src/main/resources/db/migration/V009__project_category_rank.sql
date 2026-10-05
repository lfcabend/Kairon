-- Per-category manual ordering for projects: lets a user drag-reorder
-- projects within one category's section, independent of priority_rank
-- (V004), which is a deliberately global, cross-category ranking
-- (docs/milestones/M4-projects-core.md D19) and stays that way — this is a
-- second, unrelated manual-order concept, not a replacement. NULL
-- category_id (the "Uncategorized" bucket) ranks like any other bucket.

ALTER TABLE project ADD COLUMN category_rank integer NOT NULL DEFAULT 0;

-- Backfill: seed each (user_id, category_id) bucket in its existing
-- priority_rank order so nothing visibly reshuffles on first load.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, category_id ORDER BY priority_rank ASC
    ) * 100 AS rank
    FROM project
)
UPDATE project p
SET category_rank = ranked.rank
FROM ranked
WHERE p.id = ranked.id;

CREATE INDEX project_category_rank_idx ON project (user_id, category_id, category_rank)
    WHERE deleted_at IS NULL AND status <> 'ARCHIVED';
