# NotiGPT

NotiGPT is an Android app that captures incoming notifications, persists them locally, and powers a Compose “drawer” UI with grouping/sorting plus optional server-side enrichment via WorkManager jobs.

## Architecture (current)

This project is evolving toward a clean-ish layering:

- **UI (Compose)**: `org.muilab.notigpt.view.*`
- **State**: `org.muilab.notigpt.viewModel.*`
- **Repository / orchestration**: `org.muilab.notigpt.repository.*`
- **Persistence**: Room DAOs and entities under `org.muilab.notigpt.database.room.*`
- **Background work**: WorkManager worker under `org.muilab.notigpt.database.server.workers.*`
- **Domain (pure Kotlin)**: `org.muilab.notigpt.domain.*` (new, testable logic)

The goal is to keep logic that doesn’t need Android (grouping/sorting rules, parsing, scoring, etc.) in `domain/` so it can be covered by fast JVM tests.

## Notification pipeline

1. **Capture**: `NotiListenerService` receives a `StatusBarNotification`.
2. **Store**:
   - `NotiRepository.upsertNotiUnit()` stores/updates the drawer item (`NotiUnit`).
   - `NotiRepository.insertNotiRecord()` stores the record (`NotiRecord`).
3. **Schedule enrichment** (optional): repository calls helper functions (e.g. `enqueueTaskScan`, `enqueueTaskExtraction`) which enqueue `N8nAPIWorker` jobs.
4. **Render**: `DrawerViewModel` collects `NotiRepository.getGroupedNotifications()` and the Compose UI renders the drawer.

## Testing strategy

- **JVM unit tests** (`app/src/test`): pure Kotlin logic (e.g. drawer grouping & sorting).
- **Instrumentation tests** (`app/src/androidTest`): Room DAO tests and WorkManager tests (recommended next).

## Contributing

Principles:

- Avoid behavior changes unless explicitly requested.
- Prefer extracting logic into `domain/` with tests over editing UI or DAO code directly.
- Add KDoc when a function encodes business rules.

