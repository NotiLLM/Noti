# Known issues and deferred work

- The temporary n8n backend does not yet verify Firebase ID/App Check tokens or derive UID server-side.
  Android attaches headers opportunistically; this is readiness work, not backend authentication.
- `allowBackup=true` remains unresolved because Google-managed backup may transfer raw Room history.
- Privacy-policy consent is not enabled because no final hosted policy/version/contact details exist.
- Invitation-code entitlement is enforced today via Firestore Security Rules and a client-side
  redemption transaction (see `plans/3-invitation-and-llm-usage.md` and the root-level
  `firestore/firestore.rules`), not a GCP backend — that migration remains a separate future
  decision. n8n webhooks themselves stay unauthenticated by an accepted-risk decision in that
  plan. Waitlists and admin bypass are not implemented.
- Some older ViewModels/repositories still construct dependencies manually. Hilt owns the primary app,
  listener, worker, database, and drawer/deletion paths; migrate remaining classes when touched.
- `data.repository.reminder` and `ui.reminder` are historical mixed packages. Class names distinguish
  saved items from scheduled reminders; future moves should split them into `saveditem` and
  `scheduledreminder` without changing behavior.
- Google Tasks export still uses deprecated Google Sign-In APIs and needs a separately tested auth migration.
- Exact OEM notification-listener survival cannot be guaranteed; maintain the tested recovery paths.
- Instrumented migrations compile but still require a disposable emulator/device to execute.
