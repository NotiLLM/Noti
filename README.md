# NotiGPT

NotiGPT is an Android app that captures incoming notifications, persists them locally, and powers a Compose “drawer” UI with grouping/sorting plus optional server-side enrichment via WorkManager jobs.

## Architecture (current)

This project is evolving toward a clean-ish layering:

- **UI (Compose)**: `org.muilab.notigpt.ui.*`
- **State**: `org.muilab.notigpt.ui.viewmodel.*`
- **Repository / orchestration**: `org.muilab.notigpt.repository.*`
- **Local persistence**: Room DAOs and entities under `org.muilab.notigpt.database.room.*`
- **Remote n8n integration**: Retrofit clients and WorkManager handlers under `org.muilab.notigpt.data.remote.n8n.*`
- **Domain (pure Kotlin)**: `org.muilab.notigpt.domain.*`
- **Platform (Android wrappers)**: `org.muilab.notigpt.platform.*`

See additional docs:
- `docs/ARCHITECTURE.md`
- `docs/UI.md`
- `docs/DOMAIN.md`
- `docs/PLATFORM.md`

## Notification pipeline

1. **Capture**: `NotiListenerService` receives a `StatusBarNotification`.
2. **Store**:
   - `NotiRepository.upsertNotiUnit()` stores/updates the drawer item (`NotiUnit`).
   - `NotiRepository.insertNotiRecord()` stores the record (`NotiRecord`).
3. **Schedule enrichment** (optional): repository calls helper functions (e.g. `enqueueTaskScan`, `enqueueTaskExtraction`) which enqueue `N8nAPIWorker` jobs in `data.remote.n8n`.
4. **Render**: `DrawerViewModel` collects `NotiRepository.getGroupedNotifications()` and the Compose UI renders the drawer.

## Firebase setup

Firebase/Firestore sync is kept enabled. Do not commit credentials. Put your local Firebase config at:

```text
app/google-services.json
```

The Google Services Gradle plugin reads this file during Android builds.

## Testing strategy

- **JVM unit tests** (`app/src/test`): pure Kotlin logic (e.g. drawer grouping & sorting).
- **Instrumentation tests** (`app/src/androidTest`): Room DAO tests and WorkManager tests (recommended next).

## Contributing

Principles:

- Avoid behavior changes unless explicitly requested.
- Prefer extracting logic into `domain/` with tests over editing UI or DAO code directly.
- Add KDoc when a function encodes business rules.
