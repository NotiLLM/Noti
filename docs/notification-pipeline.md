# Notification pipeline

This doc describes the high-level flow from an Android notification to what users see in the drawer UI.

## Components

- `NotiListenerService`: Android `NotificationListenerService` entry point.
- `NotiRepository`: orchestrates persistence and background-work triggers.
- Room DB (`AppDatabase`) + DAOs:
  - `NotiDrawerDao`: drawer item state (`NotiUnit`).
  - `NotiRecordDao`: individual notification records (`NotiRecord`).
  - `NotiGroupDao`: user-created groups.
  - `NotiActionDao`: user interaction logs.
- `DrawerViewModel`: UI state holder; subscribes to flows.
- `N8nAPIWorker`: WorkManager worker that calls the server webhook(s) and applies returned updates.

## Flow

1. **System notifies** Android of a new notification.
2. `NotiListenerService.onNotificationPosted()` filters out:
   - app’s own notifications
   - ongoing/non-clearable notifications
   - group summaries
   - media style notifications
   - known noisy system notifications
3. `NotiRepository.upsertNotiUnit()` creates or updates a `NotiUnit`.
4. `NotiRepository.insertNotiRecord()` upserts a `NotiRecord`.
5. Repository may schedule background work:
   - task scan
   - task extraction
   - updates to server-side summaries/sort scores
6. UI renders:
   - `DrawerViewModel` collects `getGroupedNotifications(category, appCategory)`.
   - Grouping & sorting rules are applied by `domain/notification/DrawerGrouper`.

## Invariants

- All non-Android business rules should be testable on the JVM.
- Repository methods should remain behavior-preserving (no UI assumptions).
- Worker parsing errors should not corrupt DB state.

