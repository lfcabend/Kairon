# M3 — Daily Journal — implementation plan

Status: **Accepted — implemented.** Companion to [`../ROADMAP.md`](../ROADMAP.md) (M3 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §2.2 / §3.2 / §5, and
[`../DATA_MODEL.md`](../DATA_MODEL.md) (`journal_entry`). This is the detailed
plan for the milestone; the roadmap checkboxes are the acceptance test. Follows
the structure and conventions of [`M2-daily-todo.md`](M2-daily-todo.md) — same
module shape, same hand-rolled mapping, same authz/error rules.

M3 delivers the second product feature: a **daily journal** — multiple markdown
entries per day, edited through a minimal WYSIWYG editor, browsed via a calendar
that marks which days have entries, and searchable by full text.

---

## 1. Scope

### In

- `journal_entry` schema (`V003__journal.sql`) with a generated `content_tsv`
  column + GIN index.
- `journal` backend module: CRUD, `GET /journal?day=`, `GET /journal?from=&to=`,
  `GET /journal/entry-days?from=&to=` (calendar markers), `GET /journal:search?q=`
  (paginated, ranked).
- Web: journal day view at `/journal/:date?` with multiple entries per day,
  add/edit/delete, a minimal **WYSIWYG** editor (bold / italic / heading — no
  raw markdown ever shown to the user, though the wire format stays markdown),
  a calendar-popover date nav with has-entry dots, and a search screen.
- Tests: `@DataJpaTest` + Testcontainers repo tests including full-text search;
  service unit tests; MockMvc controller tests; an `@SpringBootTest` flow test;
  ArchUnit rule for the new module; Vitest component tests; a Playwright happy
  path.

### Out (deferred, with the milestone that picks them up)

- Manual reorder of same-day entries — append-only position order for M3 (D4).
  Revisit if users actually want to reorder a morning-plan/evening-reflection
  pair.
- Journal entry revision history — backlog per `DATA_MODEL.md` §"Deferred".
- Tags — backlog.
- The "Today" screen's `journalPrompt` — M6 `planning` consumes the `journal.api`
  port added here, but the Today screen itself doesn't exist yet.
- Weekly journal reflection (AI) — M10, and only after the mechanical assistant
  features (M8–M9) have proven the plumbing; it is explicitly the feature that
  sends full journal text off the instance.
- Rich embeds — images, tables, links beyond what the minimal toolbar exposes.
  Bold / italic / heading only (D1); extending the toolbar is a small, separate
  change if it turns out to matter.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **Minimal WYSIWYG editor: Tiptap with a deliberately small extension set** (`Document`, `Paragraph`, `Text`, `Bold`, `Italic`, `Heading` at one level, `History`) plus the `tiptap-markdown` extension so the editor serializes to/from plain markdown text for storage. A three-button toolbar: **Bold**, *Italic*, Heading. No raw-markdown mode is ever shown. | User explicitly does not want to type markdown syntax; wants "very basic" formatting kept to a minimum. Tiptap is the standard, actively-maintained choice for this (vs. hand-rolling `contentEditable`/`execCommand`, which is real editor-correctness work, or `react-quill-new`, which stores HTML and would need a markdown conversion step the data model doesn't otherwise need). Storing markdown (not HTML) keeps `content`/`content_tsv` exactly as `DATA_MODEL.md` already specifies. |
| D2 | **Date navigation is a `shadcn` `Calendar` popover** (wraps `react-day-picker`) showing a dot under days that have an entry, alongside the same `‹ › Today` controls as Todo's `DateNav`. A new `JournalDateNav` component, not a reuse of Todo's — Todo's native `<input type="date">` can't render per-day markers. | ROADMAP explicitly calls for "calendar navigation with has-entry markers"; the native date input (fine for Todo, which has no markers) can't do this. |
| D3 | **`GET /journal:search?q=` returns the standard paginated envelope** (`{ content, page, totalElements }`), not a bare array. | Unlike a single day's todo list, search results aren't bounded by construction — DESIGN §5's default list rule applies here; the bare-array exception was justified specifically by a day's small, fully-ordered set (M2 §5 Q1), which doesn't hold for search. |
| D4 | **Entries within a day are append-only** — `position` is assigned on create (`max + 100`, same scheme as `todo_item`) and there is no `:reorder` endpoint or drag UI in M3. | Smaller scope; a day rarely has more than a couple of entries, and forcing an order isn't the point of a journal the way it is for a todo list. Add `:reorder` later if it turns out to matter (same dnd-kit pattern as Todo would apply unchanged). |
| D5 | **Hand-rolled mapping**, following the `todo` precedent (`JournalMapper`, static factory methods), not MapStruct. | Consistency with `identity` and `todo` (M2 D5/Q7); MapStruct is still not on the classpath. |
| D6 | **`journal.api` port is minimal: one method**, `boolean hasEntryForDay(UserId, LocalDate)`. | M6 `planning`'s "Today" screen only needs to know whether to show the journal prompt, not the entry content. A cheap `EXISTS`-style repository query, not a full `forDay` fetch. Extend the port (e.g. for M8's assistant context, which reads journal via its `api` package per DESIGN §13) when that milestone actually needs more — same reasoning as Todo's Q3. |
| D7 | **Search snippets via `ts_headline`.** The search response includes a short highlighted excerpt per hit (`ts_headline('simple', ..., websearch_to_tsquery(...))`), not just the ranked full entries. | One SQL function, meaningfully better search UX (shows *where* the match is) — the kind of "cheap now" addition M2 made for `rollover-preview`. Note: because `content_tsv` indexes the raw markdown text, a snippet can show markdown punctuation (e.g. `**word**`) around a match; acceptable for M3, revisit only if it's actually noisy in practice. |

---

## 3. Data model — `V003__journal.sql`

New migration `backend/src/main/resources/db/migration/V003__journal.sql`.
Columns per [`../DATA_MODEL.md`](../DATA_MODEL.md) → `journal_entry`.

```sql
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
```

Notes:

- `content_tsv` is a **generated, stored** column — Postgres maintains it on every
  insert/update, so the entity does not write to it (mapped `insertable = false,
  updatable = false` if mapped at all; simplest is to leave it off the JPA
  entity entirely and query it only via native SQL in the search repository
  method).
- `mood` is nullable (optional per DESIGN §2.2); the check constraint only fires
  when non-null.
- No partial-index-per-query beyond `user_day_idx` — the entry-days marker query
  and the day/range queries all ride on it; the GIN index is search-only.

---

## 4. Backend

### 4.1 Package layout — `com.kairon.journal`

```
com.kairon.journal
├── api/            JournalApi (port), JournalEntryView            ← other modules depend only on this
├── domain/         JournalEntry (@Entity)
├── repo/           JournalEntryRepository
├── app/            JournalService, JournalMapper, JournalSearchService, JournalProperties
├── config/         JournalConfig
└── web/            JournalController, JournalDtos
```

- **`JournalApi`** (D6): `boolean hasEntryForDay(UserId userId, LocalDate day)`.
  Implemented by `JournalService`. One interface, one method — mirrors how
  `TodoApi` started minimal (M2 §4.1) and was sized to what the next module
  actually needs.

### 4.2 Domain

- **`JournalEntry`** `@Entity` in `journal.domain`, following `TodoItem`
  conventions: `@Id UUID` from `Uuidv7.next()`, `@Version long`,
  `@CreationTimestamp` / `@UpdateTimestamp`, private all-args constructor +
  static factories, behaviour methods rather than setters:
  - `static JournalEntry create(UUID userId, LocalDate day, int position, String title, String content, Integer mood)`
  - `void edit(String title, String content, Integer mood)` — all three
    together (the editor always saves the whole entry; unlike Todo's separate
    `rename`/`editNotes`/`reprioritise`, there's no per-field partial-save UI
    here).
  - `void softDelete(Instant when)`

  `content_tsv` is **not** a mapped field — Postgres computes it; the entity
  never reads or writes it directly (search goes through a native query, §4.3).

### 4.3 Repository — `JournalEntryRepository extends JpaRepository<JournalEntry, UUID>`

```java
List<JournalEntry> findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(UUID userId, LocalDate day);

List<JournalEntry> findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(UUID userId, LocalDate from, LocalDate to);

Optional<JournalEntry> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

boolean existsByUserIdAndDayAndDeletedAtIsNull(UUID userId, LocalDate day);   // backs JournalApi.hasEntryForDay

@Query(value = "SELECT DISTINCT day FROM journal_entry " +
               "WHERE user_id = :userId AND day BETWEEN :from AND :to AND deleted_at IS NULL " +
               "ORDER BY day", nativeQuery = true)
List<LocalDate> findEntryDays(UUID userId, LocalDate from, LocalDate to);      // calendar markers

// Full-text search: native query (tsvector/ts_rank/ts_headline aren't
// expressible via derived queries or JPQL functions), paginated.
@Query(value = """
        SELECT id, day, position, title, mood, created_at, updated_at, version,
               ts_headline('simple', coalesce(title,'') || ' ' || content,
                           websearch_to_tsquery('simple', :q), 'MaxFragments=1') AS snippet,
               ts_rank(content_tsv, websearch_to_tsquery('simple', :q)) AS rank
        FROM journal_entry
        WHERE user_id = :userId AND deleted_at IS NULL
          AND content_tsv @@ websearch_to_tsquery('simple', :q)
        ORDER BY rank DESC
        """,
        countQuery = """
        SELECT count(*) FROM journal_entry
        WHERE user_id = :userId AND deleted_at IS NULL
          AND content_tsv @@ websearch_to_tsquery('simple', :q)
        """,
        nativeQuery = true)
Page<JournalSearchRow> search(UUID userId, String q, Pageable pageable);
```

`JournalSearchRow` is a Spring Data **interface projection** (`getId()`,
`getDay()`, `getPosition()`, `getTitle()`, `getMood()`, `getCreatedAt()`,
`getUpdatedAt()`, `getVersion()`, `getSnippet()`, `getRank()`) — deliberately
excludes full `content` (the snippet is enough for a result row; opening a hit
loads the full entry via the normal `GET /journal/{id}` path... which M3 does
not otherwise expose standalone. **Open question in §9 (Q1): add
`GET /journal/{id}` or have the search screen navigate to the day view and
scroll to the entry instead.**

### 4.4 Application services

**`JournalService`** (`@Service`, methods `@Transactional`; reads
`readOnly = true`). Every method takes a `UserId` and 404s (never 403) on
someone else's row via `ApiException.notFound(...)`, per DESIGN §3.2.

| Method | Notes |
| --- | --- |
| `list(UserId, LocalDate day)` | ordered list for the day. |
| `range(UserId, LocalDate from, LocalDate to)` | flat, day-then-position ordered; guard `to - from ≤ 92 days` (same `range-max-days` config as Todo, own `JournalProperties` key). |
| `entryDays(UserId, LocalDate from, LocalDate to)` | distinct sorted list of days with ≥1 entry, for the calendar popover. |
| `create(UserId, CreateCommand)` | `position = (max position for (user, day)) + 100`, or `100` if empty. |
| `patch(UserId, id, PatchCommand{title?, content?, mood?, expectedVersion?})` | whole-entry edit (D1's editor always saves title+content+mood together); stale `expectedVersion` → `ApiException.conflict` (409). |
| `softDelete(UserId, id)` | sets `deletedAt`. |
| `hasEntryForDay(UserId, LocalDate)` | backs `JournalApi`; delegates to the repository `existsBy...` query. |

**`JournalSearchService`** (`@Service`, `readOnly = true`) — thin wrapper over
`JournalEntryRepository.search(...)`, mapping `JournalSearchRow` → the API's
`JournalSearchHitView`. Kept separate from `JournalService` because it's a
different shape of query (paginated, ranked, projection-based) rather than
because the domain differs.

### 4.5 Web layer

**`JournalController`** `@RestController @RequestMapping("/api/v1")` (same
reasoning as `TodoController` — the class stays at `/api/v1` so
`/journal:search` resolves without Spring's path combiner inserting a slash
before the colon).

**`JournalDtos`** — package-private `final class`, same shape as `TodoDtos`:
request records with Bean Validation, a `JournalEntryResponse.from(...)`
factory, a `JournalSearchHitResponse.from(...)` factory. Controllers never see
the entity (ArchUnit-enforced).

**`JournalConfig`** `@Configuration` in `journal.config` with
`@EnableConfigurationProperties(JournalProperties.class)` —
`kairon.journal.range-max-days` (default 92, mirrors Todo). No look-back-window
equivalent — journal has no rollover concept.

### 4.6 Authorization & errors

Same rules as Todo (M2 §4.6): scope every query by `userId`, missing/foreign
row → 404, stale `expectedVersion` → 409, validation failures →
`application/problem+json` (already global).

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call. `day`, `from`, `to` are
ISO `LocalDate` strings.

### `JournalEntry` response shape

```json
{
  "id": "018f…",
  "day": "2026-09-11",
  "position": 100,
  "title": "Morning plan",
  "content": "**Ship the M3 plan** today.\n\n## Focus\n- write the doc",
  "mood": 4,
  "createdAt": "2026-09-11T07:02:11Z",
  "updatedAt": "2026-09-11T07:02:11Z",
  "version": 0
}
```

| Method & path | Body | Response |
| --- | --- | --- |
| `GET /journal?day=2026-09-11` | — | `JournalEntry[]` (position order). |
| `GET /journal?from=2026-09-01&to=2026-09-14` | — | `JournalEntry[]` (day, then position). |
| `GET /journal/entry-days?from=2026-09-01&to=2026-09-30` | — | `string[]` — ISO dates with ≥1 entry, for calendar markers. |
| `POST /journal` | `{ day, title?, content?, mood? }` | `201` + `JournalEntry`. |
| `PATCH /journal/{id}` | `{ title?, content?, mood?, expectedVersion? }` | `200` + `JournalEntry`; `409` on version mismatch. |
| `DELETE /journal/{id}` | — | `204` (soft delete). |
| `GET /journal:search?q=&page=&size=` | — | `{ content: JournalSearchHit[], page, totalElements }` — ranked by `ts_rank`, `websearch_to_tsquery` syntax (quoted phrases, `-exclude`, `OR`). |

### `JournalSearchHit` response shape

```json
{
  "id": "018f…",
  "day": "2026-09-05",
  "title": "Evening reflection",
  "snippet": "…decided to ship the <b>M3</b> plan before…",
  "mood": 3,
  "createdAt": "2026-09-05T20:14:00Z"
}
```

> **Deviations from `DESIGN.md` §5.1 to ratify:**
> 1. `GET /journal/entry-days` is a **new** endpoint not in the §5.1 sketch,
>    needed for the calendar's has-entry markers (D2). Add it to §5.1.
> 2. `GET /journal:search?q=` gains explicit `page`/`size` params and the
>    paginated envelope (D3) — §5.1 lists it without spelling out the shape.
> 3. §5.1's row also lists `GET /journal/{id}` (standalone fetch by id), which
>    this plan doesn't otherwise need — see the open question in §9 (Q1) about
>    whether to add it now for the search-result-click case.
> 4. §4's "Markdown" tooling row (`react-markdown` + `remark-gfm`;
>    `@uiw/react-md-editor` or textarea) is **superseded** by D1 — Tiptap +
>    `tiptap-markdown`, a true WYSIWYG editor. §4 needs updating.
>
> Called out again in §9 as open items.

---

## 6. Web

### 6.1 New dependencies

| Package | Why | Size note |
| --- | --- | --- |
| `@tiptap/react`, `@tiptap/pm`, `@tiptap/starter-kit` | The WYSIWYG editor core (D1). Only `Bold`, `Italic`, one `Heading` level, plus `History`/`Paragraph`/`Text`/`Document` from StarterKit are wired up — the rest of StarterKit (lists, code blocks, blockquote, etc.) stays unconfigured/disabled. | ~30–40 kB gz for this subset. |
| `tiptap-markdown` | Serializes the ProseMirror doc to/from plain markdown so `content` stays markdown text on the wire, matching `DATA_MODEL.md` unchanged. | small. |
| `react-day-picker` | Backs shadcn's `Calendar` component for `JournalDateNav`'s popover (D2). | ~15 kB gz. No `date-fns` needed — bridge to/from the project's existing `web/src/lib/date.ts` ISO-string helpers with a couple of small `Date ⇄ "YYYY-MM-DD"` conversions, not a new date library (M2 §6.1 made the same call for Todo). |

**No separate markdown-rendering library.** Because the editor round-trips
markdown, read-only display (an entry that isn't being edited) reuses the same
Tiptap instance with `editable={false}` instead of adding `react-markdown` —
one library both edits and displays. Collapsed entry-card previews (before
expanding to read/edit) show a short plain-text snippet, produced by a small
regex that strips markdown punctuation from `content` — not a rendered node
tree, just enough to show "what's in here" in a list.

### 6.2 shadcn/ui components to add

Into `web/src/components/ui/`, following the existing pattern:
`calendar` (pulls `react-day-picker`), `popover` (pulls
`@radix-ui/react-popover`), `toggle` and `toggle-group` (pulls
`@radix-ui/react-toggle{,-group}`) for the editor's Bold/Italic/Heading
buttons.

### 6.3 Routing & app shell

- **`web/src/App.tsx`** gains two routes under the existing `AppLayout`:

```tsx
<Route path="journal/:date?" element={<JournalDayView />} />
<Route path="journal/search" element={<JournalSearchPage />} />
```

- **`AppLayout.tsx`** gains a `NavLink` to `/journal`, next to `Day` and before
  `Account`.

### 6.4 Feature folder — `web/src/features/journal/`

```
features/journal/
├── JournalDayView.tsx      route component: resolves the date, owns the queries
├── JournalDateNav.tsx      ‹ › + long date + "Today" + Calendar popover with entry dots (D2)
├── EntryList.tsx           renders the day's entries + "+ New entry"
├── EntryCard.tsx           collapsed preview (title/snippet/mood) ⇄ expanded editor
├── EntryEditor.tsx         Tiptap WYSIWYG + 3-button toolbar + mood picker (D1)
├── MoodPicker.tsx          five-button 1–5 rating strip, optional (none selected = null)
├── JournalSearchPage.tsx   query input + paginated ranked results with snippets
├── useJournal.ts           day/range/entry-days queries + create/patch/delete mutations
├── useJournalSearch.ts     paginated search query
└── journalKeys.ts          query-key factory
```

### 6.5 Data layer

- **`web/src/lib/api/journal.ts`** — `journalApi` object mirroring `todoApi`.
- **Types** — add `JournalEntry`, `CreateJournalEntryBody`,
  `PatchJournalEntryBody`, `JournalSearchHit`, and
  `JournalSearchPage = { content: JournalSearchHit[]; page: number; totalElements: number }`
  to `web/src/lib/api/types.ts` (hand-written, as today).
- **Query keys** — `journalKeys.day(date)`, `journalKeys.range(from, to)`,
  `journalKeys.entryDays(from, to)`, `journalKeys.search(q, page)`.
- **Hooks**:
  - `useJournalDay(date)` → `journalKeys.day(date)`.
  - `useEntryDays(from, to)` → feeds `JournalDateNav`'s calendar markers; a
    month at a time, refetched as the popover's visible month changes.
  - `useCreateEntry(date)` / `usePatchEntry(date)` / `useDeleteEntry(date)` —
    optimistic, same pattern as Todo's mutations.
  - `useJournalSearch(q, page)` — plain `useQuery`, disabled while `q` is empty.

### 6.6 The editor (D1)

- `EntryEditor` initializes Tiptap with `content` (markdown string) via
  `tiptap-markdown`'s markdown-aware `setContent`, and reads it back with
  `editor.storage.markdown.getMarkdown()` on save.
- Toolbar: three `Toggle` buttons (shadcn `toggle`) — **Bold**
  (`editor.chain().focus().toggleBold().run()`), *Italic* (`toggleItalic`),
  Heading (`toggleHeading({ level: 2 })`) — each reflecting active-mark state
  via `editor.isActive(...)`.
- Save on blur (`editor.on("blur", ...)`), same UX precedent as Todo's notes
  textarea (M2 D3) — no explicit Save button, no autosave-while-typing churn.
- `MoodPicker` sits below the editor: five buttons `1..5`, click to set/toggle
  off, `PATCH`ed together with title/content on the same blur-triggered save
  (all three fields always travel together per D1/§4.2's `edit(...)`).

### 6.7 `JournalDateNav` (D2)

- Same `‹ › Today` row as Todo's `DateNav`, plus a `Calendar` trigger button
  (replacing the native date input) that opens a `Popover` containing shadcn's
  `Calendar`.
- The popover's visible month drives `useEntryDays(monthStart, monthEnd)`; days
  with an entry get a small dot rendered via `Calendar`'s `modifiers` /
  `DayContent` override. Selecting a day calls the same `onNavigate(iso)` prop
  `DateNav` uses, closing the popover.

### 6.8 Interactions

- **Day view** — `EntryList` renders each entry as a collapsed `EntryCard`
  (title or "Untitled", plain-text snippet, mood badge if set); click expands
  it into `EntryEditor` in place. "+ New entry" appends a blank entry, already
  expanded and focused.
- **Empty state** — "Nothing written for this day yet." with "+ New entry"
  focused, mirroring Todo's empty-state pattern.
- **Search** — `JournalSearchPage` has one input (debounced), a result list
  (day, title, `snippet` with the matched terms bolded — `ts_headline`'s `<b>`
  tags rendered directly, not re-parsed as markdown), and pagination controls.
  Clicking a result navigates to `/journal/{day}` (the open question in §9 (Q1)
  covers whether a hit should instead deep-link to the specific entry).

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `JournalEntryRepositoryTest` | `@DataJpaTest` + Testcontainers PG | day query ordering, `dayBetween` range, soft-delete filtering, `entryDays` distinct-day query, `existsBy...` for `hasEntryForDay`. |
| `JournalSearchRepositoryTest` | `@DataJpaTest` + Testcontainers PG | **full-text search** (ROADMAP's explicit ask): `websearch_to_tsquery` phrase/`-exclude`/`OR` semantics, `ts_rank` ordering, `ts_headline` snippet contains the matched term, pagination (`page`/`size`/`totalElements`), scoping to the querying user only, soft-deleted entries excluded. |
| `JournalServiceTest` | plain JUnit 5 + AssertJ, Mockito repo | `create` position = max + 100; foreign-user row → 404; stale `expectedVersion` → 409; mood outside 1–5 rejected. |
| `JournalControllerTest` | `@WebMvcTest(JournalController.class)` + `@Import({SecurityConfig, WebMvcConfig, CurrentUserArgumentResolver})`, `@MockitoBean JournalService`/`JournalSearchService`/`JwtDecoder` | every endpoint's happy path; blank/too-long title → 400; mood out of range → 400; no token → 401 problem+json; 409 body shape; search pagination params. |
| `JournalFlowIntegrationTest` | `@SpringBootTest` + Testcontainers | register → login → `POST /journal` ×2 (same day) → `GET /journal?day=` returns both in position order → `PATCH` one → `GET /journal:search?q=` finds it → `DELETE` → search no longer returns it. |
| `ArchitectureTest` (extend) | ArchUnit | add `journalInternalsArePrivate`: no class outside `com.kairon.journal..` depends on `com.kairon.journal.domain..` / `com.kairon.journal.repo..`. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers (`web/src/test/msw/handlers.ts`) | add `GET/POST/PATCH/DELETE /api/v1/journal*`, `entry-days`, `:search` with an in-memory day/entry store. |
| `JournalDayView.test.tsx` | renders entries for the day; "+ New entry" adds and expands one; edit → blur saves via `usePatchEntry`; delete removes; empty state. |
| `EntryEditor.test.tsx` | Bold/Italic/Heading toggles produce the expected markdown on save; mood picker sets/clears; content round-trips (markdown in → editor → markdown out unchanged for simple cases). |
| `JournalDateNav.test.tsx` | calendar popover renders a dot on days returned by `useEntryDays`; selecting a day calls `onNavigate`; `‹`/`›`/`Today` behave like `DateNav`. |
| `JournalSearchPage.test.tsx` | typing a query lists ranked hits with snippets; pagination controls fetch the next page; clicking a hit navigates to its day. |
| `journal.spec.ts` (Playwright) | log in as the seeded dev user, add an entry, bold some text, save, search for it, find it. |

---

## 8. Task breakdown (suggested order)

1. `V003__journal.sql` + `JournalEntry` + `JournalEntryRepository` +
   `JournalEntryRepositoryTest`.
2. `JournalService` + `JournalApi`/`JournalEntryView` + `JournalDtos` +
   `JournalController` + `JournalConfig`; `JournalServiceTest` +
   `JournalControllerTest` (CRUD, entry-days).
3. Search: native repo query + `JournalSearchRow` projection +
   `JournalSearchService` + controller endpoint;
   `JournalSearchRepositoryTest` + controller cases.
4. `ArchitectureTest` rule + `JournalFlowIntegrationTest`.
5. Web: add deps (`@tiptap/*`, `tiptap-markdown`, `react-day-picker`) + shadcn
   components (`calendar`, `popover`, `toggle`, `toggle-group`).
6. Web: `types.ts` additions, `lib/api/journal.ts`, `journalKeys`,
   query/mutation hooks, MSW handlers.
7. Web: `JournalDayView` + `EntryList` + `EntryCard` (read-only preview first,
   no editing yet); routing + nav link.
8. Web: `EntryEditor` (Tiptap wiring, toolbar, save-on-blur) + `MoodPicker`.
9. Web: `JournalDateNav` (calendar popover + entry-day dots).
10. Web: `JournalSearchPage` + `useJournalSearch`.
11. Web tests + Playwright happy path.
12. Docs: tick the M3 boxes in `ROADMAP.md`; update `DESIGN.md` §4 (markdown
    tooling row), §5.1 (`entry-days`, search pagination shape); mark this plan
    `Accepted`.

---

## 9. Open questions / risks — resolved

All five were resolved by adopting the recommendation as-is; nothing surfaced
during implementation that changed the call.

| # | Question | Resolution |
| --- | --- | --- |
| Q1 | Search results link to the **day** (`/journal/2026-09-05`) vs. a **standalone entry fetch** (`GET /journal/{id}`, then deep-link/scroll to it). The latter needs one more endpoint not otherwise used. | **Day-level navigation, no `GET /journal/{id}`.** `JournalSearchPage` navigates a hit to `/journal/{day}`; revisit only if a day with many entries makes scanning annoying in practice. |
| Q2 | `content_tsv` indexes raw markdown syntax (`**`, `#`, etc.), so a `ts_headline` snippet can show that punctuation around a match (D7). | **Accepted as-is** for M3; revisit only if it's actually noisy once there's real content to search. |
| Q3 | Mood UI: numbered 1–5 buttons vs. emoji/star icons. | **Numbered 1–5 buttons** (`MoodPicker`); swapping the visual treatment later doesn't touch the API. |
| Q4 | `journal.api`'s `hasEntryForDay` vs. a fuller `forDay` view now, to save extending the port twice (once for M6, again for M8's assistant context). | **Minimal port** — `JournalApi.hasEntryForDay(UserId, LocalDate)` only, backed by `existsByUserIdAndDayAndDeletedAtIsNull`. |
| Q5 | Tiptap bundle size / new dependency footprint (`@tiptap/react` + `@tiptap/pm` + `@tiptap/starter-kit` + `tiptap-markdown`) against the project's "keep dependencies minimal" default. | **Justified and added** — `@tiptap/react`, `@tiptap/pm`, `@tiptap/starter-kit` configured down to `Document`/`Paragraph`/`Text`/`Bold`/`Italic`/`Heading`/`History` (`undoRedo` in Tiptap v3's `StarterKit`), plus `tiptap-markdown`, `react-day-picker`, and the `popover`/`toggle`/`toggle-group` shadcn components. |

---

## 10. Doc updates this milestone produces

- `ROADMAP.md` — tick the four M3 checkboxes.
- `DESIGN.md` §2.2 — note the WYSIWYG editor (not raw markdown entry) and the
  calendar-with-markers date nav.
- `DESIGN.md` §4 — replace the `react-markdown`/`@uiw/react-md-editor` markdown
  row with Tiptap + `tiptap-markdown` (D1); add `react-day-picker` alongside
  the existing frontend rows.
- `DESIGN.md` §5.1 — add `GET /journal/entry-days`; spell out `:search`'s
  paginated response shape.
- `DATA_MODEL.md` — already describes `journal_entry`; confirm the DDL matches
  (mood check constraint, generated `content_tsv`).
- This file — flip `Status: Draft` → `Accepted` once Q1–Q5 are resolved.
