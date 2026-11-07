# P6 social API and local acceptance

All endpoints require the existing bearer session. 120 requests/user/minute,
body limits and standard JSON errors remain enforced. No public server is needed.
Run the P5 local PostgreSQL/SMTP stack, then apply all migrations including 008.
Use existing `.env` privately; never copy it into reports or Git.

```sh
cd backend
npm ci --ignore-scripts --no-fund --no-audit
npm run build
node --env-file=../.env dist/src/db/migrate.js
node --env-file=../.env --test dist/test/social.test.js
```

The test creates an isolated PostgreSQL database, upgrades P5 rows, registers two
accounts over HTTP, reads real SMTP messages from local Mailpit, verifies email,
logs in and exercises actual social API. Only test catalog fixtures are synthetic;
account/social operations use the actual application. Temporary DB/media are removed
at test end. No external recipients, fake HTTP responses or production fixtures.

| Route | Semantics |
|---|---|
| GET /v1/users?q=... | Search visible profiles by display name, >=2 characters |
| GET /v1/users/:id | Existing P3 field-level profile privacy |
| GET /v1/users/:id/relationship | Own friendship and pending request IDs |
| GET /v1/friends | Own mutual friends, bounded pages |
| GET /v1/friend-requests | Own incoming/outgoing pending requests |
| PUT /v1/friend-requests/:clientUuid | `{userId}`; stable UUID for uncertain retries |
| PUT /v1/friend-requests/:id/decision | `{status: accepted/rejected/cancelled}`; recipient accepts/rejects, sender cancels |
| DELETE /v1/friends/:userId | Idempotent mutual removal |
| GET /v1/feed | Current friends' visible checkins/photos/counts |
| GET /v1/users/:id/checkins | Viewer-visible profile content |
| GET /v1/users/:id/statistics | Viewer-visible total/distinct/rated/average; inaccessible profile=404 |
| GET /v1/social/checkins/:id | Visible detail, likeCount/liked/commentCount |
| PUT/DELETE /v1/social/checkins/:id/like | Set/unset own unique like |
| GET /v1/social/checkins/:id/comments | Visible comments, descending server creation date |
| PUT /v1/social/checkins/:id/comments/:clientUuid | `{body}`; 1–2000 nonblank characters, exact retry |
| DELETE /v1/social/checkins/:id/comments/:commentId | Own comment only, idempotent tombstone |

Lists use limit=1…50 (default20), nextCursor or null. Cursors are bound to viewer,
endpoint and search filters; changed scopes/malformed cursors return400. Feed is
ordered by server creation timestamp/UUID, independent of editable occurredAt.
Android's next-page action replaces the previous page and revalidates server access;
refresh returns to the first page. It does not append stale private content.

Profiles start private. Set profile visibility to public to be discoverable for a
first invitation; after accepting, friends-only profile/checkins remain restricted
to current mutual friends. A private profile hides its checkins even if a checkin
itself is public. Identity in your own request/friend list remains available for
relationship management; other profile fields require normal ACL. Suspended users
are omitted. A new first request cannot target an invisible private profile.

Direct checkin/media/comments reads return404 after hide/delete/revocation. The
owner-only P5 diary and its encrypted cache are unchanged. Social views/photos have
no persisted offline cache; foreground refresh and resume fetch current state.
No background polling, push integration, paid service or public hosting introduced.
FR-068 extended reports/blocking remain SHOULD deferred; FR-090 moderation workflow
is still assigned to P7/P8 in the approved requirements matrix.

Android acceptance: SocialIntegrationTest in the existing P3AcceptanceSuite. The
test registers two real local accounts, uses HTTP/SQL/SMTP and actual Compose UI,
checks shared photos/like/comment/accept/remove, privacy revocation and radio-off.
P6 screenshots are generated alongside earlier evidence. Physical TalkBack and
RU VPS acceptance need their respective devices/infrastructure; do not infer PASS.


SocialRetentionTest uses the actual Activity/ViewModel scope: a typed unsent comment
survives recreation and an explicit fresh entry resets it; logout clears the scope.
The local container drill (`backend/test/runtime-check.mjs`) additionally checks
social UUIDs/feed/counts after API+PostgreSQL restart and isolated SQL/media restore.
Do not run acceptance against production or external mail recipients.
