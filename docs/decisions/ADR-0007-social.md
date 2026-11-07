# ADR-0007 — P6: mutual friendship and online social reads

Status: accepted for implementation under the owner's P6 instruction, 2026-11-07.
P5 baseline: b2cc8441d3b9e0f226bf8e552a9010c6f94a46ab. No P7 work.

Use the existing PostgreSQL social tables and the shared versioned HTTP API.
No external provider, paid infrastructure, generated production users or data.
Unlike unilateral follows, friendship requires an explicit recipient decision.
An inverse pending request returns 409; it is not implicitly accepted.

A client generates a UUID for a request/comment and uses PUT at that resource.
Exact retries return the existing result; different content or reuse by another
actor conflicts. Terminal requests and comment tombstones cannot be resurrected.
Repeating acceptance after unfriend does not restore friendship. A new invitation
requires a new UUID. Likes use idempotent PUT/DELETE, not a non-idempotent toggle.
Pair advisory locks serialize invite/accept/remove. Writes on a visible checkin
lock its friendship (if any), user/profile and checkin before rechecking ACL;
privacy changes, unfriend, suspension and deletion serialize against those writes.

Search uses display name (minimum 2 characters); private profiles are absent,
friend-only profiles are discoverable only to current friends. To receive a first
invitation, expose the profile publicly. Pending invitation and friendship lists
show the counterpart ID/name needed to manage that relationship, even if their
profile becomes private; they never reveal profile fields/email. Friendship itself
does not override either the profile or checkin visibility gate.

Feed contains current friends' visible checkins in server creation order, with a
UUID tie-breaker. Public profile counts include only checkins visible to the viewer,
not the owner's private diary totals. Reactions/comments by suspended accounts are
excluded. Every list is bounded (1–50) and keyset-paginated with scope-bound cursors.
All requests re-evaluate ACL. Hidden/deleted content is not returned by social
routes; existing owner diary routes retain their P5 semantics and offline cache.

Social routes are separate from cached owner diary routes. Android keeps social
content only in memory while foregrounded, clears on leaving/background/failure,
and revalidates on resume and refresh. Social images use uncached authorized HTTP;
no fallback to persisted profile/catalog images. A revocation cannot retract bytes
already delivered to a screen, but all subsequent requests enforce current rights.
No claim of push delivery or realtime revocation is made; push remains SHOULD.

Migration 008 is additive: search/outbox/feed indexes and a nullable original
comment request hash. P5 rows remain untouched. Roll forward by default; a P5 binary
requires its matching schema readiness manifest, so rollback requires restoring the
pre-upgrade DB backup with media, never rewriting migration checksums.
OpenAPI is generated from actual route schemas and remains Android/iOS-neutral.
