# Notification capture pipeline

## Flow

1. Android binds `NotiListenerService` after the user grants notification-listener access.
2. Posted notifications pass through `NotificationFilter`. The app ignores its own ongoing status,
   non-actionable noise, and other explicitly filtered categories.
3. `NotiRepository` updates one `NotiUnit` drawer row and appends a `NotiRecord` content snapshot.
4. The source notification's content/delete `PendingIntent`s are cached in memory when available.
5. Per-key WorkManager extraction is debounced so a burst becomes one staged processing run.
6. The app posts its ongoing status using only notifications from the configured recent X-hour
   window and current task/keep counts awaiting review.
7. For a live accepted notification, the original system notification is cancelled after the
   configured delay. This intentional late cancellation leaves urgent content briefly actionable.

## Local and cloud data

`NotiRecord` contains raw title/body/person fields and stays in Room. Firestore receives only generated
content and source record IDs/counts. Clearing local notification history removes drawer records,
actions, LLM thread state, journals, and raw reminder references without deleting generated items.

## Listener recovery

- `onListenerDisconnected` calls the framework `requestRebind` API.
- `onStartCommand` preserves redelivery and the existing component re-enable recovery behavior.
- `onDestroy` schedules a short elapsed-realtime alarm. `NotiListenerRestartReceiver` requests a
  framework rebind; it does not directly start a background service.
- `onListenerConnected` cancels the fallback alarm and hydrates currently active notifications.

Android controls the listener process and no app can promise literal 24/7 execution. Do not remove a
recovery path without API 29/30/31/current testing and comparable physical-device evidence.
