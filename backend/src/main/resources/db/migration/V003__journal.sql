-- Daily journal entries (module: journal). Multiple entries may belong to the
-- same (user_id, day); position orders them within the day (append-only, D4).
-- content is markdown, produced by the web WYSIWYG editor (D1) — never shown
-- to the user as raw markdown. See docs/DATA_MODEL.md (journal_entry) and
-- docs/DESIGN.md §2.2.

CREATE TABLE journal_entry (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day          date          NOT NULL,
    position     integer       NOT NULL,
    title        varchar(200),
    content      text          NOT NULL DEFAULT '',
    mood         smallint,
    content_tsv  tsvector      GENERATED ALWAYS AS
                                  (to_tsvector('simple', coalesce(title, '') || ' ' || content)) STORED,
    deleted_at   timestamptz,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,

    CONSTRAINT journal_entry_mood_check CHECK (mood BETWEEN 1 AND 5)
);

CREATE INDEX journal_entry_user_day_idx  ON journal_entry (user_id, day) WHERE deleted_at IS NULL;
CREATE INDEX journal_entry_content_tsv_idx ON journal_entry USING GIN (content_tsv);
