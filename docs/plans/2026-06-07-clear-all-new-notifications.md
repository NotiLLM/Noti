# Clear All Icon beside New Notifications Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add a clear-all/sweep icon to the right side of the `New Notifications` section header so tapping it clears active NotiCards that are not pinned. Also tighten `New` semantics: pinned notification cards must stay in `New` when swiped away, no dismiss path should show the `Moved to History` toast, and new Task/Keep cards shown inside `New` should trigger the same quick preference-learning interactions as cards in their dedicated Tasks/Keep screens.

**Architecture:** Keep deletion semantics in the existing repository path. `DrawerViewModel.deleteAllNotis()` already delegates to `NotiMaintenanceRepository.deleteAllNotis()`, which reads `getActiveNotPinnedKeys()` and dismisses only those keys. The new work is mainly screen-level UI: render a header row in `NotificationsScreen`, enable the button only when at least one displayed active card is clearable, and route the click to the existing ViewModel method. For the `New` tab path, preserve pinned cards explicitly in the swipe-dismiss path, remove the `Moved to History` toast from `archiveNewNotificationCard()`, and pass `PreferenceViewModel` into `NewScreen` so its Task/Keep card delete/edit flows can reuse the same preference-learning calls already used in `RemindersScreen`.

**Tech Stack:** Android, Kotlin, Jetpack Compose, Material 3, Room, Gradle/JVM tests.

---

## Current code facts

- Active cards are rendered in `app/src/main/java/org/muilab/notigpt/ui/notification/screen/NotificationsScreen.kt`; the current bottom-tab `New` surface is `app/src/main/java/org/muilab/notigpt/ui/home/NewScreen.kt`, which also renders new NotiCards via `NotiCard`.
- The current section header is a plain `Text` at `NotificationsScreen.kt:216-221`:
  `stringResource(R.string.ui_notifications_new, activeCount)`.
- Bulk clear already exists: `DrawerViewModel.deleteAllNotis()` in `ui/notification/viewmodel/DrawerViewModel.kt:306-313`.
- It already preserves pinned cards because `NotiMaintenanceRepository.deleteAllNotis()` uses `notiDrawerDao.getActiveNotPinnedKeys()` before dismissing rows and records.
- Drawable assets already exist: `app/src/main/res/drawable/sweep.xml` and `app/src/main/res/drawable/delete_all.xml`.
- `NotiCard` swipe dismiss currently calls `drawerViewModel.archiveNewNotificationCard(notiKey)` from `NotiCard.kt:195`.
- `DrawerViewModel.archiveNewNotificationCard()` currently removes the NotiUnit, refreshes new records, then shows `notifier.showShort("Moved to History")` at `DrawerViewModel.kt:424-429`.
- Opening a NotiCard uses `launchNotificationContent()`, which already skips `NotiListenerService.removeIntents()` for pinned notifications. Do not regress that behavior.
- `NewScreen` renders new Task/Keep suggestions with `ReminderCard`, but currently does not receive `PreferenceViewModel`; `RemindersScreen` does, and triggers preference learning for LLM-generated cards on delete and edit.
- `AppTopBar` already has a sweep action when `showNotificationActions = true`, but `AppScaffold` currently passes `showNotificationActions = false` for the shared top bar. Enabling it only for `New` would require context-aware top-bar state and would make the action less clearly scoped to the `New Notifications` subsection.
- Do not change backend APIs, notification capture, reminder extraction, history paging, or pin behavior.

## Acceptance criteria

- The `New Notifications (N)` row shows a clear-all icon on the right.
- Tapping the icon calls the existing clear-all path and removes only active NotiCards where `isPinned == false`.
- Pinned NotiCards remain visible after clearing.
- Swiping a pinned NotiCard away in `New` must not remove it from `New`; pinning means it stays active/visible until explicitly unpinned or otherwise handled.
- No dismiss/archive path should show the toast text `Moved to History`.
- New Task/Keep cards in `New` trigger quick preference learning the same way their cards do in the Tasks/Keep screens:
  - deleting an LLM-generated card starts the `PreferenceEntryPoint.DELETE` flow;
  - editing an LLM-generated card starts the `PreferenceEntryPoint.EDIT` flow with the before/after item;
  - keep non-LLM/manual cards out of these preference prompts, matching existing screen behavior.
- The icon is disabled or hidden when there are no non-pinned active cards.
- The `All Notifications` history list remains unchanged except that dismissed records may appear as they already do today.
- Sorting/reorder mode still works. If clearing while sorting, the list should not crash or leave stale drag/order state.

---

### Task 0: Preserve pinned cards on single-card swipe dismiss and remove the archive toast

**Objective:** Align single-card dismiss behavior with the meaning of pinning, and remove noisy history toasts for every dismiss/archive case.

**Files:**
- Modify: `app/src/main/java/org/muilab/notigpt/ui/notification/viewmodel/DrawerViewModel.kt`
- Modify only if needed: `app/src/main/java/org/muilab/notigpt/ui/notification/component/card/noticard/NotiCard.kt`

**Step 1: Guard the archive path for pinned cards**

Update `archiveNewNotificationCard(notiKey)` so it checks the current active/new unit before calling `notiRepository.removeNotiUnit(notiKey)`. If the matching unit is pinned, return without removing it and without showing any toast.

Prefer doing the guard inside `DrawerViewModel.archiveNewNotificationCard()` instead of only in `NotiCard`, because this makes every call site safe and keeps the rule near the mutation.

Implementation shape:

```kotlin
fun archiveNewNotificationCard(notiKey: String) {
    viewModelScope.launch {
        val activeUnit = _activeNotiUnits.value.firstOrNull { it.notiKey == notiKey }?.notiUnit
        if (activeUnit?.isPinned == true) return@launch

        notiRepository.removeNotiUnit(notiKey)
        refreshNewNotificationRecords()
    }
}
```

Adjust the exact source list if the implementation should consult `newNotificationUnits` instead of `_activeNotiUnits`, but keep the behavior: pinned stays in `New`.

**Step 2: Remove the `Moved to History` toast**

Delete `notifier.showShort("Moved to History")` from `archiveNewNotificationCard()`.

Do not replace it with another toast or snackbar. The user asked for no toast for all cases.

**Step 3: Verify no duplicate toast remains**

Search the codebase for:

```bash
Moved to History
```

Expected: zero results.

---

### Task 1: Add a tiny pure helper for clearable active-card count

**Objective:** Make the UI enable/disable logic testable without Compose instrumentation.

**Files:**
- Create: `app/src/main/java/org/muilab/notigpt/domain/notification/ClearableNotificationFilter.kt`
- Create: `app/src/test/java/org/muilab/notigpt/domain/notification/ClearableNotificationFilterTest.kt`

**Step 1: Write failing test**

Create `ClearableNotificationFilterTest.kt` with tests for the behavior the header needs:

```kotlin
package org.muilab.notigpt.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import org.muilab.notigpt.model.notifications.NotiDisplayUnit

class ClearableNotificationFilterTest {
    @Test
    fun `counts active unpinned display units as clearable`() {
        val units = listOf(
            fakeDisplayUnit(notiKey = "a", isPinned = false, isDismissed = false),
            fakeDisplayUnit(notiKey = "b", isPinned = true, isDismissed = false),
            fakeDisplayUnit(notiKey = "c", isPinned = false, isDismissed = true),
        )

        assertEquals(1, countClearableActiveNotifications(units))
    }
}
```

Use the existing `NotiUnit`/metadata constructors from nearby tests or add a local helper in the test file. Keep the helper local to the test, not production code.

**Step 2: Verify RED**

Run:

```bash
./gradlew app:testDebugUnitTest --tests org.muilab.notigpt.domain.notification.ClearableNotificationFilterTest
```

Expected: fail because `countClearableActiveNotifications` does not exist.

**Step 3: Implement minimal helper**

Create `ClearableNotificationFilter.kt`:

```kotlin
package org.muilab.notigpt.domain.notification

import org.muilab.notigpt.model.notifications.NotiDisplayUnit

fun countClearableActiveNotifications(units: List<NotiDisplayUnit>): Int {
    return units.count { displayUnit ->
        !displayUnit.notiUnit.isDismissed && !displayUnit.notiUnit.isPinned
    }
}
```

**Step 4: Verify GREEN**

Run the same focused test. Expected: pass.

---

### Task 2: Add the clear-all icon beside the `New Notifications` section header, not the shared top bar

**Objective:** Put the clear-all icon back to the right of `New Notifications`. Prefer section-level placement over top-bar placement.

**Decision:** Add the icon beside the `New Notifications` section in `NewScreen` and any legacy `NotificationsScreen` path that still renders active notifications. Do not re-enable the shared top-bar sweep action for `New` in this plan.

Reasoning:
- The action only applies to NotiCards, not new Task/Keep suggestions, so putting it next to the `New Notifications` section prevents ambiguity.
- The icon can be enabled/disabled from the visible section list (`filteredUnits` / `displayedNotiUnits`) without threading extra state into `AppTopBar`.
- Search/filter behavior is clearer: if the visible `New Notifications` section has no clearable unpinned cards, the section icon is disabled right where the user is looking.
- The top bar is shared across New, Tasks, Keep, and menu screens. Re-enabling notification actions there risks showing a destructive notification action while the user is focused on other content.

Keep the top-bar option as a future alternative only if UD later wants one global notification action. If that happens, add explicit `showClearAllNotificationsAction` and `clearAllEnabled` parameters to `AppTopBar` instead of reusing broad `showNotificationActions`.

**Files:**
- Modify: `app/src/main/java/org/muilab/notigpt/ui/notification/screen/NotificationsScreen.kt`
- Modify: `app/src/main/java/org/muilab/notigpt/ui/home/NewScreen.kt` if that is the active `New` tab path being shipped
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add strings**

Add a content description string, for example:

```xml
<string name="ui_action_clear_unpinned_notifications">Clear unpinned notifications</string>
```

Traditional Chinese:

```xml
<string name="ui_action_clear_unpinned_notifications">清除未釘選通知</string>
```

**Step 2: Update imports**

In `NotificationsScreen.kt`, add only the imports needed for the header row:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import org.muilab.notigpt.domain.notification.countClearableActiveNotifications
```

Adjust imports if Android Studio/ktlint prefers a different order.

**Step 3: Compute clearable count**

Near `displayedNotiUnits`, add:

```kotlin
val clearableActiveCount = remember(displayedNotiUnits) {
    countClearableActiveNotifications(displayedNotiUnits)
}
```

Use `displayedNotiUnits`, not raw history records. This keeps the button scoped to visible active NotiCards.

**Step 4: Replace the header item**

Replace the existing `item { Text(...) }` for `New Notifications` with:

```kotlin
item {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ui_notifications_new, activeCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            enabled = clearableActiveCount > 0,
            onClick = { drawerViewModel.deleteAllNotis() },
        ) {
            Icon(
                painter = painterResource(id = R.drawable.sweep),
                contentDescription = stringResource(R.string.ui_action_clear_unpinned_notifications),
            )
        }
    }
}
```

Default to disabled instead of hidden. It keeps the header stable and makes the unavailable state obvious when all remaining cards are pinned.

For the bottom-tab `NewScreen`, apply this header-row pattern to the `filteredUnits` section currently rendered as `item { SectionTitle("New Notifications", filteredUnits.size) }`. Use `filteredUnits` for the displayed count and clearable count, and call the same `drawerViewModel.deleteAllNotis()` path. Do not add a separate clear implementation in `NewScreen`.

For the legacy `NotificationsScreen`, keep the same section-header approach if that screen is still reachable. If it is no longer reachable, do not spend implementation time polishing that path beyond keeping compilation green.

**Step 5: Verify compile**

Run:

```bash
./gradlew app:compileDebugKotlin
```

Expected: pass.

---

### Task 3: Check sorting-mode interaction and stale order handling

**Objective:** Make sure clearing active unpinned cards does not break reorder state.

**Files:**
- Modify only if needed: `app/src/main/java/org/muilab/notigpt/ui/notification/screen/NotificationsScreen.kt`

**Step 1: Inspect behavior after Task 2**

Reason through this path:

- `deleteAllNotis()` dismisses non-pinned active keys.
- `activeNotiUnits` emits a shorter list.
- `LaunchedEffect(activeNotiUnits, isSortingMode)` already resets `activeOrder` when the key set changes and sorting mode is active.

Expected: no extra production change needed.

**Step 2: If testing shows stale drag/order state, add the smallest guard**

Only if needed, clear drag state before the delete call:

```kotlin
onClick = {
    dragState.clear()
    drawerViewModel.deleteAllNotis()
}
```

Do not add new sorting behavior beyond crash/stale-state prevention.

---

### Task 4: Add quick preference-learning interactions to Task/Keep cards in `New`

**Objective:** Make new Task/Keep suggestion cards behave like Task/Keep cards in their dedicated screens for preference learning.

**Files:**
- Modify: `app/src/main/java/org/muilab/notigpt/ui/common/component/AppScaffold.kt`
- Modify: `app/src/main/java/org/muilab/notigpt/ui/home/NewScreen.kt`

**Step 1: Pass `PreferenceViewModel` into `NewScreen`**

In `AppScaffold`, update the `AppPrimaryTab.New -> NewScreen(...)` call to pass the existing scaffold-level `preferenceViewModel`:

```kotlin
NewScreen(
    drawerViewModel = drawerViewModel,
    reminderViewModel = reminderViewModel,
    scheduledReminderViewModel = scheduledReminderViewModel,
    preferenceViewModel = preferenceViewModel,
    searchQuery = appSearchQuery,
)
```

Add the matching parameter to `NewScreen`:

```kotlin
preferenceViewModel: PreferenceViewModel,
```

Import:

```kotlin
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
```

**Step 2: Mirror list-card delete preference flow**

For both `newTasks` and `newKeep` `ReminderCard(onDelete = ...)` handlers, keep the existing delete, then trigger the same flow used in `RemindersScreen` only for LLM-generated items:

```kotlin
onDelete = {
    reminderViewModel.delete(item.savedItemId)
    if (item.origin.contains("llm")) {
        preferenceViewModel.startFlow(
            entryPoint = PreferenceEntryPoint.DELETE,
            reminder = item,
        )
    }
}
```

Do not trigger the flow for bulk `Delete all` in the `NewSavedItemSectionHeader` confirmation. Bulk delete has less precise intent and should not spam preference prompts unless UD explicitly asks for that later.

**Step 3: Mirror detail-delete preference flow**

In the `ReminderDetailScreen(onDelete = ...)` inside `NewScreen`, capture `editingInitialSnapshot` before deletion. After deleting and clearing local edit state, start the DELETE flow if the deleted snapshot came from LLM:

```kotlin
val deletedItem = editingInitialSnapshot
reminderViewModel.delete(id)
...
if (deletedItem != null && deletedItem.origin.contains("llm")) {
    preferenceViewModel.startFlow(
        entryPoint = PreferenceEntryPoint.DELETE,
        reminder = deletedItem,
    )
}
```

**Step 4: Mirror edit preference flow**

In both `ReminderDetailScreen` save/back paths that persist edits, compare against `editingInitialSnapshot` before clearing it. If the item changed, the base item exists, and the base item came from LLM, call:

```kotlin
preferenceViewModel.startFlow(
    entryPoint = PreferenceEntryPoint.EDIT,
    reminder = updated,
    reminderBefore = base,
)
```

Use the same changed fields as existing `NewScreen.persistEditedItem()` and `RemindersScreen`: title, content, task/keep type, completion state, deadline, and estimated completion time. Do not start EDIT for unchanged back navigation.

**Step 5: Long-press behavior**

If “quick preference interactions” also refers to the existing `RemindersScreen` long-press feedback dialog, add it to `NewScreen` too only if the current `ReminderCard` API already supports `onLongPress` there. Reuse `vm.submitFeedback(...)` and the existing string resources from `RemindersScreen` instead of adding a new UX. If this conflicts with opening/editing behavior in `New`, skip it and note the reason during implementation.

---

### Task 5: Run focused and broad verification

**Objective:** Prove the change did not break existing notification behavior.

**Commands:**

```bash
./gradlew app:testDebugUnitTest --tests org.muilab.notigpt.domain.notification.ClearableNotificationFilterTest
./gradlew app:testDebugUnitTest --tests org.muilab.notigpt.domain.notification.DrawerItemSorterTest
./gradlew app:testDebugUnitTest
./gradlew app:assembleDebug
```

Expected: all pass.

**Manual QA on device/emulator:**

- Start with at least three active NotiCards: two unpinned, one pinned.
- Confirm `New Notifications (3)` shows the sweep icon on the right.
- Tap the icon.
- Confirm the two unpinned cards disappear and the pinned card remains.
- Confirm the icon becomes disabled if no clearable cards remain.
- Swipe a pinned NotiCard in `New`; confirm it remains in `New` and no `Moved to History` toast appears.
- Swipe an unpinned NotiCard in `New`; confirm it leaves `New` and no `Moved to History` toast appears.
- Delete an LLM-generated new Task card from `New`; confirm the DELETE quick preference prompt/snackbar flow appears as it does from the Tasks screen.
- Edit an LLM-generated new Keep card from `New`; confirm the EDIT bottom-sheet flow appears as it does from the Keep screen.
- Confirm deleting/editing manual or non-LLM new cards does not trigger preference prompts.
- Confirm existing top-app-bar search and reorder buttons still work.
- Confirm history still loads under `All Notifications`.

---

## Commit

After verification:

```bash
git add app/src/main/java/org/muilab/notigpt/domain/notification/ClearableNotificationFilter.kt \
        app/src/test/java/org/muilab/notigpt/domain/notification/ClearableNotificationFilterTest.kt \
        app/src/main/java/org/muilab/notigpt/ui/notification/screen/NotificationsScreen.kt \
        app/src/main/java/org/muilab/notigpt/ui/notification/viewmodel/DrawerViewModel.kt \
        app/src/main/java/org/muilab/notigpt/ui/common/component/AppScaffold.kt \
        app/src/main/java/org/muilab/notigpt/ui/home/NewScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        docs/plans/2026-06-07-clear-all-new-notifications.md
git commit -m "feat: add clear all action to notification header"
```

## Non-goals

- Do not change `NotiMaintenanceRepository.deleteAllNotis()` unless a test proves it is not preserving pinned cards.
- Do not remove the top-app-bar sweep icon unless UD explicitly wants only one clear-all entry point.
- Do not change notification history retention, n8n/Dify action sync, reminder extraction, or SavedItem behavior.
