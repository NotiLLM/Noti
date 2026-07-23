# Data, privacy, and deletion

## Storage matrix

| Data | Room/device backup | n8n processing | Firestore |
|---|---|---|---|
| Raw notification title/body/person/messages | Yes | Sent when required for extraction | Never |
| Source notification record IDs/counts | Yes | Yes | Yes, association metadata only |
| Generated tasks, keeps, and subitems | Yes | Yes | Yes |
| Complete generated proposals | Yes | Generated there | Yes |
| Proposal decision state | Yes | May inform later runs | Yes |
| Account/preferences/context metadata | Yes | As required | Yes |
| Invitation-code redemption state, entitlement flag | No (SharedPreferences cache only) | Never | Yes |
| Per-LLM-call raw token counts (input/output/thinking/total), stage, model | No | Reflected via response header only | Yes |
| 6-hour notification-count/character-count rollups | No | Never | Yes |

HTTP logging is `BASIC` in debug and disabled in release. Do not log payload bodies, generated content,
Firebase tokens, or Firestore document bodies. Crashlytics collection is disabled in debug.

## Firestore convergence

Saved-item writes/deletes and generated-proposal decisions create an account-scoped `firestore_outbox`
row containing only an entity ID and operation kind. `FirestoreOutboxWorker` runs with a network
constraint, checks the current Firebase UID, re-reads content from Room, and retries failures. A newer
operation replaces an older one for the same entity, and conditional completion prevents a slow old
request from deleting a newer queued mutation.

The temporary n8n server accepts Firebase ID-token and App Check headers on a fail-open basis. A future
GCP service must verify both and derive the UID server-side; client-supplied `userId` is not authentication.
This fail-open posture is an accepted risk specific to invitation-code enforcement (see
`plans/3-invitation-and-llm-usage.md`), not something waiting on that future GCP migration.

## Invitation entitlement and LLM usage observability

`entitlements/{uid}`, `invitationCodes/{code}`, `usageLogs`, and `notificationUsage` are separate
top-level Firestore collections (see `firestore/firestore.rules`), kept out of the `users/{uid}` subtree
so their narrow rules cannot be widened by that document's broad owner-read/write rule. `usageLogs`
stores only raw provider-reported token counts (never prompt/response text); `notificationUsage` stores
only a count and a character total per 6-hour window, never notification content. Cost is computed later,
offline, by `firestore/usage_report.py` — never shipped as a pricing table in the app or n8n.

## User-visible deletion

- **Clear notification history**: local-only; removes raw notification history and dependent local
  processing metadata. Generated tasks/keeps remain.
- **Delete cloud data**: deletes generated item/proposal/user documents first, then clears corresponding
  local generated state after Firestore confirms. Raw notification history remains unless separately cleared.
- **Account switch**: the current Firebase session remains active while the user chooses whether to
  retain device notification history or start clean and while the Google account chooser is open.
  Cancelling the chooser changes no identity or data. A successfully selected different account applies
  the chosen local-history policy, replaces account-owned generated state, and restores that account.
- **Sign out**: clears the Firebase and Credential Manager sessions but does not delete local or cloud
  data. The cached UID is blank while signed out, preventing account-scoped sync until the user signs in
  again. The previous UID is retained separately only so a later different-account sign-in can require the
  account-switch confirmation above.

Firestore parent deletion does not cascade subcollections, so deletion explicitly removes known item,
legacy raw-notification, and generated-proposal documents before deleting parent documents.

## Backup caveat

`allowBackup=true` and the existing backup rule files are intentionally unchanged pending a product
decision. Android Auto Backup/device transfer is Google-managed off-device backup, not merely a local
copy, and may include the Room database. Do not claim raw history is device-bound across backup/restore
until backup exclusions or encryption policy are explicitly approved and implemented.

The app allows screenshots. Repository screenshots and documentation must use synthetic content.
