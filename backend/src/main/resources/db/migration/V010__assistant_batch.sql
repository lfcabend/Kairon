-- Tracks one Anthropic Message Batches API submission per scheduled-sweep
-- firing (docs/milestones/M10-journal-reflection.md D21) — distinguishes "an
-- @Async call is actively running right now" from "submitted as part of a
-- batch, waiting on Anthropic's own timeline", which otherwise both look
-- like assistant_run.status = RUNNING. Scoped to the WEEKLY_SUMMARY/
-- MONTHLY_SUMMARY scheduled sweep only (D17) — batch_id stays null for every
-- synchronously/@Async-dispatched run. No soft delete, matching every other
-- assistant_* table's hard-delete-only convention (DATA_MODEL.md) — it
-- carries no user-identifying content of its own, only bookkeeping.
CREATE TABLE assistant_batch (
    id                  UUID PRIMARY KEY,
    anthropic_batch_id  VARCHAR(100) NOT NULL UNIQUE,
    kind                VARCHAR(30) NOT NULL
                          CHECK (kind IN ('WEEKLY_SUMMARY', 'MONTHLY_SUMMARY')),
    status              VARCHAR(20) NOT NULL
                          CHECK (status IN ('IN_PROGRESS', 'CANCELING', 'ENDED')),
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE assistant_run ADD COLUMN batch_id UUID REFERENCES assistant_batch(id);
CREATE INDEX idx_assistant_run_batch ON assistant_run(batch_id) WHERE batch_id IS NOT NULL;
