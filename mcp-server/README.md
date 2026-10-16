# kairon-mcp-server

A standalone MCP server bridging a user's own Claude Code session to Kairon's
assistant features, so the reasoning happens under their own Claude
subscription instead of the instance's paid Anthropic API key. See
[`docs/milestones/M10.5-mcp-server.md`](../docs/milestones/M10.5-mcp-server.md)
for the full design.

It is a thin, stateless REST client over Kairon's existing and newly-added
API — it holds no credential of its own. Every request to this server must
carry `Authorization: Bearer <token>` (a personal access token, PAT); that
exact token is forwarded verbatim to Kairon's backend for every call made
while handling that one request. Nothing is cached, nothing is stored —
restart the server or scale it to ten replicas and nothing changes, because
there is no server-side session to lose.

## Contents

- [Step 1 — generate a personal access token](#step-1--generate-a-personal-access-token)
- [Step 2 — connect a client](#step-2--connect-a-client)
  - [Claude Code CLI](#claude-code-cli-recommended)
  - [Manual `.mcp.json` config](#manual-mcpjson-config)
  - [Claude Desktop](#claude-desktop)
  - [A mobile/cloud Claude surface](#a-mobilecloud-claude-surface-eg-claude-code-on-a-phone)
- [Step 3 — verify the connection](#step-3--verify-the-connection)
- [Using it](#using-it)
- [Revoking access](#revoking-access)
- [Troubleshooting](#troubleshooting)
- [Security notes](#security-notes)
- [Local development](#local-development)
- [Deployment](#deployment)
- [Testing](#testing)

---

## Step 1 — generate a personal access token

1. Log into the Kairon web app.
2. Go to **Account** → **Developer access**.
3. Give the token a name that tells you where it's used (e.g. "Claude Code
   — laptop", "Claude Code — phone") — you'll want this later if you ever
   need to tell which device is which before revoking one.
4. Click **Generate token**.
5. **Copy the raw value immediately.** It's shown exactly once, in a
   monospace block with a "Copy" button. Kairon never shows it again — the
   token list only ever displays the name, creation date, last-used date,
   and expiry/revoked status on every later visit.

The raw value looks like `kairon_pat_<random-characters>`. Treat it like a
password: it's long-lived (no default expiry — revoking it is the control,
same posture as a GitHub PAT) and, within its fixed scope, lets whoever holds
it read your todos/journal/projects and submit assistant suggestions on your
behalf.

**What a PAT can and can't do** (enforced in the backend, not by this
server): it can call `GET` on `todo`/`journal`/`projects`, and any of the ten
`/api/v1/assistant/mcp/**` endpoints this server's tools wrap. It **cannot**
change your account, write a todo/journal/project directly, or call any
other endpoint — including the *existing* paid-API assistant endpoints and
the accept/dismiss endpoints. A wrong or hallucinated suggestion from a PAT
session still has to go through the same accept/dismiss review as everything
else.

## Step 2 — connect a client

The server is reachable at `https://xbmc.tail9ae0e9.ts.net/mcp` (the real
deployed instance) or `http://localhost:3000/mcp` (local dev, see
[Local development](#local-development)). Pick whichever section below
matches your client.

### Claude Code CLI (recommended)

This is the one path that's actually been run against the real server and
confirmed working end to end.

```sh
claude mcp add --transport http kairon https://xbmc.tail9ae0e9.ts.net/mcp \
  --header "Authorization: Bearer kairon_pat_xxxxxxxxxxxxxxxx"
```

- `--transport http` is required — this server speaks MCP's Streamable HTTP
  transport, not stdio.
- `--header` can be repeated for more headers if you ever need to; here one
  is enough.
- Add `--scope user` if you want this connection available in every project
  on this machine rather than just the one you ran the command from (default
  scope is `local`, i.e. this one project only, recorded outside any file
  you'd commit). `--scope project` writes it into a shared, committable
  `.mcp.json` instead — don't use that scope here, since it would put your
  raw token in a file version control can see. If you do want a
  project-shared config, use the `${VAR}`-expansion approach in the next
  section instead.

Claude Code will *not* expand a `${VAR}`-style reference passed directly to
`--header` on this command line — your shell expands `$VARNAME` (not
`${VAR}`) before Claude Code ever sees it, which is a shell feature, not a
Claude Code one. If you want to avoid typing the raw token on the command
line at all, export it first and let your shell substitute it:

```sh
export KAIRON_PAT="kairon_pat_xxxxxxxxxxxxxxxx"
claude mcp add --transport http kairon https://xbmc.tail9ae0e9.ts.net/mcp \
  --header "Authorization: Bearer $KAIRON_PAT"
```

### Manual `.mcp.json` config

If you're wiring this into a project's checked-in MCP config (e.g. this
repo's own `.mcp.json`) rather than using `claude mcp add`, the shape is:

```json
{
  "mcpServers": {
    "kairon": {
      "type": "http",
      "url": "https://xbmc.tail9ae0e9.ts.net/mcp",
      "headers": {
        "Authorization": "Bearer ${KAIRON_PAT}",
        "Accept": "application/json, text/event-stream"
      }
    }
  }
}
```

Here, unlike the CLI `--header` flag, Claude Code *does* expand
`${KAIRON_PAT}` itself, reading it from your own environment at connect
time — so the raw token never has to be written into the file. Set it before
starting a session:

```sh
export KAIRON_PAT="kairon_pat_xxxxxxxxxxxxxxxx"
```

This is exactly why a `.mcp.json` committed to a repo is safe to share: the
file carries a reference, never the secret. The explicit `Accept` header
above isn't strictly required by every client, but some Streamable HTTP
clients need it spelled out to negotiate the SSE half of the connection
correctly — harmless to include either way.

If the variable is unset, Claude Code sends the literal string `${KAIRON_PAT}`
as the header value rather than failing fast — which will come back as a
401 from Kairon's backend (see [Troubleshooting](#troubleshooting)), not a
clear "you forgot to set this" error. Double-check `echo $KAIRON_PAT` before
asking why nothing works.

### Claude Desktop

Desktop's own "Connectors" UI only supports OAuth-based remote servers
today — there's no field in that UI for a static bearer header. Two options
if you want this server available there anyway:

1. **Edit Desktop's config file directly.** It accepts the same `.mcp.json`
   shape shown above (`type: "http"`, `url`, `headers`). Location:
   - macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - Windows: `%APPDATA%\Claude\claude_desktop_config.json`

   Unlike the CLI path, double-check whether your installed Desktop version
   actually expands `${VAR}` references in this file before relying on it —
   if it doesn't, you'd have to write the raw token into this file directly,
   which is worth knowing before you do it.

2. **Use an `mcp-remote` proxy.** `mcp-remote` is a small local stdio server
   that forwards to a remote HTTP/SSE MCP server, injecting whatever headers
   you configure — the standard workaround for a client (like Desktop) that
   only speaks stdio or only supports OAuth for remote servers natively. Not
   verified against this specific server by this project — if you go this
   route, confirm it against `mcp-remote`'s own current docs rather than
   this README, since that tool's own interface can change independently of
   Kairon.

### A mobile/cloud Claude surface (e.g. Claude Code on a phone)

This is the scenario the whole MCP server exists for, and it's also the one
genuinely unverified piece of this setup — flagged honestly, not glossed
over (see
[the milestone plan's Q7](../docs/milestones/M10.5-mcp-server.md) for the
full writeup). What's known:

- The Claude Code **CLI**'s static-header approach (above) is confirmed
  working.
- claude.ai's own Connectors UI — which is what a mobile/cloud Claude surface
  uses for an unlisted, self-hosted connector — supports static-header auth
  for some accounts, but as a beta-gated feature; whether it's available on
  any given account isn't something this README can promise. If it isn't
  available, the only remaining path is full OAuth (dynamic client
  registration, discovery, redirect) — this server does **not** implement
  that today.
- Whether a cloud Claude Code session (opened from a phone browser against a
  clone of this repo) inherits the repo's own checked-in `.mcp.json` the same
  way the local CLI does is undocumented either way, and would sidestep the
  Connectors-UI question entirely if it turns out to work.

If you're trying this for the first time: start from the CLI on a laptop to
confirm the server itself is reachable and the PAT works (Step 3 below),
*then* try the mobile/cloud surface — that way a failure there is clearly
about the mobile/cloud auth path specifically, not about the server.

## Step 3 — verify the connection

From the Claude Code CLI:

```sh
claude mcp list
```

Each configured server shows a status icon — `✔ Connected`,
`✘ Failed to connect`, or `! Needs authentication`. For more detail on one
server specifically:

```sh
claude mcp get kairon
```

Inside a running Claude Code session, `/mcp` shows the same list and lets
you re-authenticate or reconnect a specific server without leaving the
session. The first time a project-scoped `.mcp.json` server is used in a
session, Claude Code will also prompt you to approve it — say yes, or the
server stays disabled for that session.

A healthy connection should list all sixteen tools once you ask Claude to
show them (or call `list_projects` as a quick smoke test — it needs no
arguments and will 401 immediately if the token is wrong, long before it
would ever touch anything assistant-related).

## Using it

Ask Claude Code something like:

> Using the kairon tools, look at my open project tasks and suggest a few
> todos for today.

A reasonable session: call `get_todo_suggestion_context` first, read what it
returns, then call `submit_todo_suggestions` with your proposed list. The
tool descriptions themselves say this explicitly (every `submit_*` tool's
description tells the calling model to base its answer only on the matching
`get_*_context` call's output) — but the review step is enforced
server-side regardless of what the model does: every `submit_*` call creates
a `PROPOSED` suggestion (or, for summary/reflection, a run with no
accept/dismiss step of its own), never a direct write. **You still review
and accept or dismiss every suggestion from the normal Kairon web UI**,
exactly as if it had come from the paid Anthropic API path — nothing a
Claude Code session does through this server can create real data on its
own.

Full tool list:

- `get_todo_suggestion_context` / `submit_todo_suggestions`
- `get_project_plan_context` / `submit_project_plan`
- `get_project_edit_context` / `submit_project_edit`
- `get_summary_context` / `submit_summary`
- `get_journal_reflection_context` / `submit_journal_reflection`
- `list_todos`, `list_journal_entries`, `list_projects`, `get_project`,
  `list_project_tasks`, `list_project_dependencies` (general, read-only —
  thin wrappers over Kairon's existing REST endpoints)

Each `get_*_context`/`submit_*` pair also respects whatever you've opted
into on Kairon's Account page — if a feature's checkbox is off there, the
matching tool call 403s with exactly the same message you'd see trying the
equivalent thing in the web UI.

## Revoking access

Account → **Developer access** → **Revoke** next to the token, with a
confirm step. The very next call made with that token 401s — nothing needs
restarting on either side, since the backend does a real lookup per request
rather than caching anything.

If you lose access to the web UI, revoke directly (needs a normal login
session's JWT, not the PAT itself — a PAT can never manage PATs):

```sh
curl -X DELETE https://xbmc.tail9ae0e9.ts.net/kairon/api/v1/me/personal-access-tokens/<id> \
  -H "Authorization: Bearer <a normal JWT>"
```

`<id>` is shown in the Developer Access token list (not the raw token value,
which Kairon doesn't keep).

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `claude mcp list` shows `✘ Failed to connect` | Check the URL is reachable at all first. `/healthz` is **not** reachable from outside the cluster — the ingress only routes the `/mcp` path prefix to this server, so `curl https://xbmc.tail9ae0e9.ts.net/healthz` will 404 even when the server is perfectly healthy; that's expected, not a bug. From outside, `curl -i https://xbmc.tail9ae0e9.ts.net/mcp` getting *any* HTTP response (even a 4xx) rules out a network/deploy problem — a connection failure or timeout means the problem is before the application at all. For a definitive health check, use the in-cluster `/healthz` probe in `deploy/RUNBOOK.md`'s "Debug connectivity" section instead. |
| Every tool call 401s | Token typo'd, expired, or revoked. Re-check with `claude mcp get kairon` to see exactly what header value was actually sent — whitespace or a missing `kairon_pat_` prefix is the usual culprit. |
| `.mcp.json` works for one teammate but not another | Each of you needs your own `KAIRON_PAT` exported locally — a `${VAR}`-style reference in a shared, checked-in `.mcp.json` only works if *your own* shell has that variable set; it's never shared between people. |
| A tool call 403s with a "You haven't enabled X in Settings" message | Working as intended — go flip that feature's checkbox on in Account first. |
| A `submit_*` call 404s on a project | The `projectId` you gave isn't yours (or doesn't exist) — same as the web UI would 404 on it. |
| Everything was working, now nothing is | Check whether the token was revoked (Account → Developer access) before assuming a server problem. |
| Connecting from Claude Desktop does nothing | See [Claude Desktop](#claude-desktop) above — its own Connectors UI can't send a static header at all; you need the manual-config or `mcp-remote` path. |

## Security notes

- This server never sees a long-lived credential of its own — read
  [the top of this README](#kairon-mcp-server) again if that's not clear;
  it's the single most important property of this design.
- A PAT's scope is fixed in the backend's own `SecurityConfig`, not
  per-token — there's no "grant more/less" dial on a token itself, only
  revoke.
- Treat a PAT exactly like a password. It's long-lived by default (no
  expiry unless you set one at creation) specifically so revocation is a
  deliberate, visible action — not because it's meant to be shared or
  checked into anything.
- Never put a raw token directly in a committed `.mcp.json`. Use the
  `${VAR}` form and keep the actual value in your own shell/exported
  environment, a local `.env` file that's gitignored, or your OS keychain —
  whatever your own setup already uses for other secrets.

## Local development

```sh
npm install
KAIRON_BASE_URL=http://localhost:8080/kairon npm run dev
```

The server listens on `:3000` (override with `PORT`) and exposes one
endpoint, `POST /mcp` (MCP's Streamable HTTP transport, stateless mode — no
session id, no GET/SSE stream, no DELETE). `GET /healthz` is a plain
liveness check, no auth required. To point a local Claude Code CLI at it
instead of the real deployment, swap the URL in Step 2 for
`http://localhost:3000/mcp` — the PAT itself still has to be a real one
generated against whichever backend `KAIRON_BASE_URL` points at.

## Deployment

Dockerized (`../docker/mcp-server.Dockerfile`) and deployed as its own Helm
release (`../deploy/helm/kairon-mcp/`) into the same k8s namespace as the
main `kairon` chart, reachable at `/mcp` on the same Tailscale Funnel
hostname the web app already uses. See
[`../deploy/RUNBOOK.md`](../deploy/RUNBOOK.md) for the operator-side tasks
(creating/rotating secrets for the rest of the stack — this server needs
none of its own — and debugging connectivity from inside the cluster).

## Testing

```sh
npm test
```

Vitest, with `fetch` mocked and a real `@modelcontextprotocol/sdk`
`McpServer`/`Client` pair wired over in-memory transports for the tool
registration/validation tests — no real network call, no real Kairon
instance needed anywhere in the suite.
