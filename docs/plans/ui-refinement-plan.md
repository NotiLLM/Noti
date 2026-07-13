# UI Refinement Plan — Native Android, Apple-calibre polish

**Status:** approved direction, ready for execution
**Written:** 2026-07-13 (post main-interface redesign, commit `24cce29`)
**Executor notes:** designed to be executed phase-by-phase, each phase independently buildable and shippable. Read the whole document before starting Phase 1; the Feature Preservation Contract (§9) applies to every phase.

---

## 1. Design brief

The goal is **an Android app that feels as polished, thoughtful, and cohesive as the best Apple apps while remaining unmistakably native to Android.**

- **Interaction model: Android.** Material 3 + Compose foundation, system back / predictive back, Material components (snackbars, FABs, chips, sheets, menus), Android gestures, TalkBack accessibility, optional dynamic color.
- **Visual language: Apple-influenced restraint.** Generous whitespace, typography-driven hierarchy, hairline dividers, *minimal container tinting*, quiet chrome, content forward.
- **M3 Expressive: selectively.** The project is on `material3 1.5.0-alpha20`, which ships the Expressive APIs. Use Expressive *motion* (spring-based feedback on presses, toggles, swipes, expansion) and isolated components (`LoadingIndicator`) where they add life. Do **not** adopt the Expressive *aesthetic* wholesale (no giant shape morphs, no bold tonal blocking, no FlexibleBottomAppBar).

### Product context the design must serve

This app is the user's **primary notification surface** — notifications are removed from the system drawer. Two coequal primary needs:
1. **Extracted items** (Tasks/Keep) for quick understanding and task management.
2. **Original notifications** for verification and micro-tasks (e.g. replying) that don't warrant extraction.

Secondary: preference elicitation (chat), settings, notification history. Scheduled (alarm) reminders sit between primary and secondary — see §10 Open item.

### Settled decisions (do not relitigate)

- **IA is settled**: home portal + explicit back stack + hamburger drawer for secondary sections ([AppDestinations.kt](../../app/src/main/java/org/muilab/notigpt/ui/common/navigation/AppDestinations.kt), [AppScaffold.kt](../../app/src/main/java/org/muilab/notigpt/ui/common/component/AppScaffold.kt)). No bottom bar. Polish, don't restructure.
- **Semantic color layer stays** ([SemanticColor.kt](../../app/src/main/java/org/muilab/notigpt/ui/theme/SemanticColor.kt)): Tasks/Keep identity and overdue/due-soon urgency keep fixed meanings independent of dynamic color. What changes is *how much surface area* those colors paint (§3.4).
- **Card interactions may be redesigned, but zero feature loss** across NotiCard, Review cards, and Task/Keep (Reminder) cards. §9 is the binding inventory.
- **The 2026-07-13 swipe-intent fix is sacred**: angle-only horizontal intent (`absX > absY*1.05`) in [NotiCardSwipe.kt](../../app/src/main/java/org/muilab/notigpt/ui/notification/component/card/noticard/elements/NotiCardSwipe.kt), and drawer `gesturesEnabled = drawerState.isOpen`. Any change touching these must re-verify slow horizontal swipes still work.

---

## 2. Current-state audit (what's wrong, concretely)

| # | Issue | Where |
|---|-------|-------|
| 1 | No edge-to-edge; deprecated `window.statusBarColor`; XML theme is light-only `Theme.Material.Light.NoActionBar` → white flash on dark-mode launch, no splash screen | [Theme.kt:135](../../app/src/main/java/org/muilab/notigpt/ui/theme/Theme.kt), `res/values/themes.xml`, [MainActivity.kt](../../app/src/main/java/org/muilab/notigpt/MainActivity.kt) |
| 2 | `Toast`s everywhere (≈10 call sites incl. ViewModels) where snackbars belong | RemindersScreen, NotificationHistoryScreen, PreferenceViewModel, NotiFeedbackDropdown, MainActivity, UserControlPanel; `ToastUserToaster` |
| 3 | No screen transitions — destinations hard-switch in a `when`; no predictive-back animation | [AppScaffold.kt:320-377](../../app/src/main/java/org/muilab/notigpt/ui/common/component/AppScaffold.kt) |
| 4 | Corner radii ad-hoc: 6/8/12/14/16/20 dp scattered; `MaterialTheme.shapes` only used by NotiCard | HomeScreen, ReviewScreen, ReminderCard, badges |
| 5 | Margins inconsistent: screens use 16 dp, NotiCard uses 20 dp; card inner paddings vary 10–20 dp | NotiCard vs ReminderCard vs Home |
| 6 | Ad-hoc `FontWeight.SemiBold/Bold` overrides instead of type-scale variants | HomeScreen, ReviewScreen |
| 7 | Hardcoded colors: `Color.Red` drawer badge, pin blue `Color(76,139,245)`, black/white rim alphas on NotiCard | AppDrawerContent:68, NotiCardOverlayButtons:140, NotiCard:212 |
| 8 | NotiCard visual clutter: outline border (1 dp, 3 dp when sorted) *plus* a second rim border | NotiCard:118, 212-213 |
| 9 | Long-press `AlertDialog` as card options menu — undiscoverable, non-idiomatic | NotiCardOptionsDialog, NotificationHistoryScreen:279 |
| 10 | Settings screen is ad-hoc rows: no `ListItem`, radio row aligned with a `top = 12.dp` text hack, export options expand inline | [SettingScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/settings/SettingScreen.kt) |
| 11 | Drawer reuses the `task` icon for Tasks, Reminders, *and* Preferences; no header; no section labels | [AppDrawerContent.kt](../../app/src/main/java/org/muilab/notigpt/ui/common/component/AppDrawerContent.kt) |
| 12 | `RemindersScreen.kt` is a 2,353-line monolith (screen + card + detail editor + 4 dialogs) — unsafe to edit | [RemindersScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/reminder/screen/RemindersScreen.kt) |
| 13 | Gesture surfaces (swipe, drag-expand, review fling) have no TalkBack custom actions | NotiCard, ReviewCardStack |
| 14 | Motion is scattered `tween(200/250)`; no shared spec; haptics only in ReminderCard | throughout |
| 15 | Review stack uses `LocalConfiguration.current.screenWidthDp` (deprecated pattern) and tween-based fling | ReviewCardStack |

---

## 3. Phase 1 — Foundations (tokens, theme, platform hygiene)

Everything later depends on this phase. No visual redesign of screens yet; this phase makes the vocabulary exist.

### 3.1 Shape scale
Define in `Theme.kt` via `MaterialTheme(shapes = …)`:
- `extraSmall` 6 dp (badges, tiny chips) · `small` 10 dp (chips, inline surfaces) · `medium` 14 dp (list cards) · `large` 18 dp (hero cards, sheets) · `extraLarge` 26 dp (dialogs, review cards).
Replace **every** literal `RoundedCornerShape(n.dp)` in `ui/` with a `MaterialTheme.shapes` reference. Grep check must return zero literals outside `theme/` when done.

### 3.2 Spacing scale
New file `ui/theme/Dimens.kt`: 4-pt grid tokens —
`ScreenHPadding = 16.dp`, `CardOuterVGap = 6.dp`, `CardInnerPadding = 16.dp`, `SectionTopGap = 28.dp`, `SectionHeaderBottomGap = 8.dp`, `InlineGap = 8.dp`, `ElementGap = 12.dp`.
Unify: NotiCard outer horizontal margin 20 dp → `ScreenHPadding`; all list screens share identical gutters. "Generous whitespace" = raise vertical rhythm (section gaps 28 dp, card inner padding 16 dp) rather than shrinking content.

### 3.3 Typography usage rules
Keep the existing full M3 scale in [Type.kt](../../app/src/main/java/org/muilab/notigpt/ui/theme/Type.kt) (system font, CJK fallback). Add two app-level styles to `Type.kt` instead of scattered overrides:
- `Typography.titleMediumEmphasized`-equivalent: extend via extension vals (e.g. `val Typography.cardTitle` = titleMedium @ SemiBold) — then **delete every ad-hoc `fontWeight =` override in screens** and reference the named styles.
Rules to apply in later phases: screen/section titles = title styles; card titles = `cardTitle`; metadata (time, counts) = `labelMedium` in `onSurfaceVariant`; body previews = `bodyMedium`. Numbers in stat boxes = `headlineMedium` (not Large+Bold).

### 3.4 Color usage policy — "restrained tinting"
The single biggest Apple-restraint lever. Policy (enforced screen-by-screen in later phases):
- **Neutral surfaces by default.** Cards: `surfaceContainerLow` (lists) / `surfaceBright` (NotiCard ok). Full tinted-container fills (`primaryContainer`, `taskContainer`…) are reserved for **at most one hero element per screen** (e.g. the home Review row).
- **Semantics move from fills to accents**: colored icon-in-circle, colored count text, 3 dp leading accent bar, colored chip *outline/label* — on neutral card bodies. (Apple Reminders pattern: gray boxes, colored icon disc + big count.)
- **Dividers**: `outlineVariant` hairlines (0.5–1 dp), used sparingly; prefer whitespace.
- **Kill hardcoded colors**: drawer badge → `MaterialTheme.colorScheme.error`; pin-active tint → `colorScheme.primary`; NotiCard rim hack → delete (single hairline border instead, §6.1).
- Urgency (`overdue`/`dueSoon`) keeps container fills **only inside DueChip**, nowhere larger.

### 3.5 Edge-to-edge, splash, dark launch
- `enableEdgeToEdge()` in `MainActivity.onCreate`; delete the `window.statusBarColor` SideEffect in `Theme.kt`; handle insets via `Scaffold` paddings + `WindowInsets` on the drawer sheet, top bar, sheets, and IME where needed.
- `themes.xml`: replace with a `Theme.SplashScreen`-parented DayNight theme (androidx core-splashscreen), `postSplashScreenTheme` → a DayNight NoActionBar theme, dark `windowBackground` in `values-night/`. Add app icon as splash mark.
- Verify: launch in dark mode has no white flash; status/nav bars are transparent; content draws behind system bars with correct insets on API 29 (gesture nav + 3-button).

### 3.6 Motion spec
New file `ui/theme/Motion.kt`:
- `MotionSpecs.settle` (spring, medium damping) for offset/expansion settles; `MotionSpecs.flyOff` for review commit; `MotionSpecs.fadeThrough` for screen transitions; `MotionSpecs.quick` (150–200 ms) for alpha-only.
- Replace scattered `tween(200)/tween(250)` in NotiCard, ReviewCardStack, AppTopBar with these tokens. Springs for anything the finger touches; tweens only for pure fades. If adopting `MaterialExpressiveTheme` + `MotionScheme` proves low-friction on this alpha, prefer that as the source of the spring tokens; otherwise hand-rolled `spring()` specs are fine — decide once, in this file.

### 3.7 Haptics
New `ui/common/feedback/Haptics.kt`: semantic wrappers (`confirmToggle`, `commitSwipe`, `thresholdCross`, `longPress`). Wire in later phases: NotiCard swipe commit, review commit + threshold cross, checkbox/star/archive toggles (already partially in ReminderCard — route through the helper), drag-to-reorder pickup.

### 3.8 Feedback unification (Toast → Snackbar)
- Add an app-scoped `SnackbarHostState` owned by `AppScaffold` (one already exists for preference events — generalize it): a small `AppMessageBus` (SharedFlow of message events) that ViewModels emit into; `AppScaffold` collects and shows snackbars.
- Convert all UI-layer `Toast.makeText` call sites (§2 item 2). `ToastUserToaster` gets a snackbar-backed implementation (`UserToaster` interface stays).
- Keep genuine system-context toasts only where the app UI may not be visible (none currently qualify except possibly service-level code — out of UI scope).

**Phase-1 verification:** `./gradlew :app:assembleDebug :app:testDebugUnitTest` green; app launches edge-to-edge light+dark; no visual regressions beyond intended (screens still render with old layouts on new tokens).

---

## 4. Phase 2 — App shell (top bar, drawer, transitions, search)

### 4.1 Screen transitions + predictive back
- Wrap the destination `when` in `AppScaffold` in `AnimatedContent` keyed on `(menuScreen, currentDest)`: push = slide-in-from-end + fade; pop = reverse; menu screens = fade-through (`MotionSpecs.fadeThrough`).
- Replace the plain `BackHandler` with `PredictiveBackHandler` so the pop transition scrubs with the back gesture (Android-native polish; this is exactly the "unmistakably Android" part).
- Verify `android:enableOnBackInvokedCallback="true"` in the manifest.

### 4.2 Top bar
- Home root: switch to `LargeTopAppBar` ("Noti" large title collapsing to small on scroll) — Material-native component, Apple-large-title feel. Pushed screens keep small `TopAppBar` + back arrow.
- Scroll edge treatment: keep `containerColor = surface` and add a hairline bottom divider that fades in when content is scrolled (instead of tonal `scrolledContainerColor` tint) — restrained chrome.
- Search stays the in-bar expandable pattern; restyle the field in [SearchBar.kt](../../app/src/main/java/org/muilab/notigpt/ui/common/component/SearchBar.kt): borderless, `bodyLarge`, subtle placeholder, clear (×) button, auto-focus with IME on expand.

### 4.3 Drawer
[AppDrawerContent.kt](../../app/src/main/java/org/muilab/notigpt/ui/common/component/AppDrawerContent.kt):
- Header: app name (+ signed-in account email, small, `onSurfaceVariant`).
- Distinct icons: Tasks `task`, Keep `keep`/bookmark, Reminders `schedule`/alarm icon, Preferences a chat/tune icon, History `history`, Settings `settings`. Add drawable assets if missing.
- Section structure: Home · **Collections** (Tasks, Keep) · divider · **More** (Reminders, Preferences, History, Settings), with `labelMedium` section headers.
- Badges: counts as plain `Text` badge (current style) but the Reminders badge uses `colorScheme.error` via `Badge()` defaults — no `Color.Red`.
- `NavigationDrawerItem` with `NavigationDrawerItemDefaults.ItemPadding`; drawer sheet gets proper status-bar inset after §3.5.

### 4.4 Scaffold container
- `containerColor`: keep a subtle canvas/card separation — `surfaceContainerLowest`-on-cards over `surface` canvas *or* current `surfaceDim`; pick one and apply globally (recommend: canvas `surface`, cards `surfaceContainerLow`, NotiCard `surfaceBright` in dark / `surfaceContainerLowest` in light). Document the choice in `Color.kt`'s header comment.

**Verification:** navigate through all destinations — transitions run both directions, predictive back scrubs, drawer opens only via hamburger (regression check!), search works on the 4 searchable screens, badges correct.

---

## 5. Phase 3 — Home screen

[HomeScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/home/HomeScreen.kt). Layout order stays: Review row → Notifications → Saved. Restyle per §3.4:

- **Review row** — the screen's one hero: keeps `primaryContainer` fill, `shapes.large`, icon + two-line text + count badge. Slightly larger padding (20 dp), count in `titleMedium`.
- **Notification category rows** (Communication / Content): neutral `surfaceContainerLow` cards; app-icon preview lines get 2 lines max with per-sender counts (as now); unread count becomes a **filled small badge** (primary or neutral) right-aligned; whole row min-height ≥ 72 dp for touch comfort. These rows are the fast path to the primary notification surface — visually calm but first-class.
- **Smart-filter grid**: Apple Reminders treatment — neutral `surfaceContainerLow` boxes; each gets a **small colored icon disc** (task/keep/dueSoon/neutral accents) top-left, big count `headlineMedium` top-right, label + type-breakdown bottom. Kills 4 different tinted fills, preserves color semantics in the discs/counts. Undetermined keeps its full-width row, neutral.
- **Section headers**: `titleSmall`+`onSurfaceVariant` (as now) but with §3.2 rhythm (28 dp top gap).
- Home scrolls under the collapsing large-title bar (§4.2).

**Verification:** counts match drawer badges; tap targets all navigate; light/dark/dynamic-color all render (semantic discs must stay fixed under dynamic color).

---

## 6. Phase 4 — Notification surfaces (NotiCard, category pages, history)

### 6.1 NotiCard visual reskin (gesture logic untouched)
[NotiCard.kt](../../app/src/main/java/org/muilab/notigpt/ui/notification/component/card/noticard/NotiCard.kt) + `elements/`:
- **One border, not three looks**: drop the rim hack (`Color.White/Black` alphas) and the outline border; use `surfaceBright` (dark) / `surfaceContainerLowest` (light) with a single 0.5–1 dp `outlineVariant` hairline. Sort-position highlight (`borderWidth 3.dp`) → replace with a `primary` hairline + slight scale (existing `scaleValue` already does 1.02 on drag).
- Outer margin → `ScreenHPadding` (16 dp); vertical gap between cards 6 dp; inner padding from `Dimens`.
- Header hierarchy: app icon, then title (`cardTitle`), secondary title `bodyMedium`/`onSurfaceVariant`, timestamp `labelMedium` right-aligned, summary `bodyMedium` (keep the collapse-threshold-driven summary/title swap logic exactly as is).
- Pin state: pinned tint = `colorScheme.primary` (kill `Color(76,139,245)`); pinned cards may also show a tiny pin glyph next to the timestamp.
- Expanded records: hairline dividers between records, sender `labelLarge`, content `bodyMedium`, time `labelSmall`; keep anchored-drag + fling behavior and the measured-anchors logic untouched.

### 6.2 NotiCard options: dialog → bottom sheet (feature-preserving)
Replace `NotiCardOptionsDialog` (AlertDialog of TextButtons) with a `ModalBottomSheet` of `ListItem`s: Pin/Unpin · Extract task · Create scheduled reminder (when available). Long-press still opens it; **additionally** add a small overflow affordance inside the expanded state or keep long-press as the only entry — but the sheet itself must list every current action (§9.1). The commented-out "dismiss" action stays out (it was deliberately removed).

### 6.3 Swipe visuals
Background action panel ([NotiCardActions.kt](../../app/src/main/java/org/muilab/notigpt/ui/notification/component/card/noticard/elements/NotiCardActions.kt)): restyle as icon-in-circle buttons on a neutral background strip; alpha ramp stays. Add `Haptics.commitSwipe` when the archive threshold commits. **Do not touch** `NotiCardSwipe.kt` intent logic.

### 6.4 Accessibility on gestures
Add `Modifier.semantics { customActions = … }` on the card root: Archive, Pin/Unpin, Expand/Collapse, Extract task, Create reminder — so TalkBack users can invoke everything swipe/drag provides. Same for review cards (§8) with Approve/Reject/Open details.

### 6.5 Category screens + history
- [NotiCategoryScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/notification/screen/NotiCategoryScreen.kt): filter chips (All / last-24h) restyled per token pass; empty states via `EmptyState`; the top-bar **Clear-all** action gets an **undo snackbar** (route through §3.8 bus) instead of silent execution — verify `clearVisibleNotis` supports restore, otherwise keep a confirm dialog. Zero data-loss risk allowed here.
- [NotificationHistoryScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/notification/screen/NotificationHistoryScreen.kt): Grouped/Timeline chips stay; the long-press AlertDialog (Copy / Extract) → bottom sheet or `DropdownMenu`, same items; toasts → snackbars.
- `AutoControlBar` / `UserControlPanel` / `DevControlPanel` (dev-facing strips): minimal restyle only (switch rows via `ListItem`), lowest priority.

**Verification:** every §9.1 feature exercised on device; slow horizontal swipe still archives; expand-drag and pin-button exclusion zones still work; TalkBack reads and performs custom actions.

---

## 7. Phase 5 — Saved items (split, then redesign)

### 7.0 Pre-refactor (its own PR, zero behavior change)
Split [RemindersScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/reminder/screen/RemindersScreen.kt) (2,353 lines):
- `reminder/screen/RemindersScreen.kt` — list screen + chips row + FAB + wiring only
- `reminder/component/ReminderCard.kt` — card (+ `CardDoDatePickerDialog`, `DoDateBottomButton`, `ReminderActionChip`)
- `reminder/screen/ReminderDetailScreen.kt` — detail editor (+ its pickers, header menu)
- `reminder/component/ExportConfirmationDialog.kt`
Keep signatures; move-only. Verify build + unit tests + a manual list/edit/save round-trip before any restyle.

### 7.1 ReminderCard (Task/Keep card) reskin
All features preserved (§9.2). Restyle:
- Neutral `surfaceContainerLow`, `shapes.medium`, 0.5 dp hairline; 3 dp `sectionAccent` leading bar stays (it *is* the restrained semantic marker).
- Bottom icon row is crowded (star / schedule / export / delete + do-date). Consolidate: keep **do-date chip + star** inline; move **set-reminder, export, delete** into a trailing overflow `DropdownMenu` (⋮) on the bottom row. Delete keeps its confirm dialog for reviewed items. If testing shows the overflow hurts (heavy daily actions), fall back to current 4-icon row with tightened spacing — either way all five actions remain reachable in ≤ 2 taps.
- New/Updated badge: `labelSmall` on `secondaryContainer` (as now) but shape `extraSmall`.
- Completed task: strikethrough + `onSurfaceVariant` title (add the dimming).

### 7.2 Detail editor
Currently a bespoke full-screen column. Restyle toward an iOS-grouped-list feel with Material parts:
- Top bar: back, borderless title field (as now, restyled), ⋮ menu (Regenerate / Delete) stays a `DropdownMenu`.
- Body groups as **cards of grouped rows** (`surfaceContainerLow`, hairline dividers): Type+Completed · Deadline (date/time rows) · Do-date (date/time/Someday chip/clear) · Note · LLM action chips · Sub-tasks · Reminder+Export+Share chips · Change history · Related notifications.
- Date/time pickers stay M3 `DatePickerDialog`/TimePicker.
- Keep the save-on-back contract (`onBack(buildUpdated())`) exactly.

### 7.3 List screen
- Filter chips row: standard `FilterChip` styling from token pass; the chip "flash" affordance stays.
- FAB stays (Android-native), colored by collection accent as now but per §3.4 (container = accent container is acceptable here as the screen's hero action).
- Selection mode, drag-reorder, smart-filter variants unchanged behaviorally.

**Verification:** the §9.2 + §9.3 checklists on device; Google Tasks/Calendar export + share still work; sub-task flows intact; save-on-back verified.

---

## 8. Phase 6 — Review flow

[ReviewScreen.kt](../../app/src/main/java/org/muilab/notigpt/ui/review/ReviewScreen.kt), [ReviewCardStack.kt](../../app/src/main/java/org/muilab/notigpt/ui/review/component/ReviewCardStack.kt), [ReviewDetailSheet.kt](../../app/src/main/java/org/muilab/notigpt/ui/review/component/ReviewDetailSheet.kt):
- Stack mechanics stay; swap `tween` fly-off/settle for `MotionSpecs.flyOff`/`settle` springs; add `Haptics.thresholdCross` when drag crosses commit threshold and `commitSwipe` on commit. Replace `LocalConfiguration.screenWidthDp` with `BoxWithConstraints`/`onSizeChanged` width.
- Card: `shapes.extraLarge`, neutral surface, kill the 1 dp outline+shadow combo (pick shadow only, 2–4 dp); APPROVE/REJECT stamps keep semantic colors.
- Bottom bar: reject/approve `FilledIconButton`s grow slightly (56 dp) with spring press feedback; "Approve all" stays a `TextButton` between them.
- Detail `ModalBottomSheet` restyle per tokens; approve/reject actions inside it stay.
- Empty state + filter chips per shared components.

**Verification:** §9.4 checklist; undo snackbar works for both approve and reject; revert-on-reject for Updated items re-verified.

---

## 9. Feature Preservation Contract (binding for every phase)

Every item below must work after each phase that touches its surface. Interaction *style* may change (e.g. dialog → sheet); capability may not disappear.

### 9.1 NotiCard
| Feature | Current entry point |
|---|---|
| Open notification's content intent + mark accessed/dismissed | tap card (`accessAndDismissNotification`) |
| Archive card | horizontal swipe (direction per Settings), full card surface minus expand/pin buttons |
| Reveal background actions: hide-actions, extract task | partial swipe |
| Pin / Unpin | overlay pin button; also in options menu |
| Expand / collapse records | drag the expand chevron (anchored drag + fling) or tap it |
| Summary ↔ latest-record title swap at collapse threshold | automatic |
| Options: Pin/Unpin, Extract task, Create scheduled reminder | long-press → dialog (→ sheet in §6.2) |
| Reorder in sorting mode via drag handle (long-press) | sorting mode drag handle |
| Sort-position highlight | thicker border (→ new highlight §6.1) |
| Multi-record expanded list w/ per-record display | expansion area |
| Swipe direction setting honored | Settings → swipeDeleteLeft |

### 9.2 ReminderCard (Task/Keep)
Complete (checkbox) / Archive (keep) toggle with haptics · expand/collapse content (chevron) · title strikethrough when completed · New/Updated badge until acknowledged · DueChip deadline urgency / "no deadline" text · content preview 2-line cap · LLM action buttons (link/copy chips, horizontal scroll) · inline sub-task list (toggle/click/edit/delete/export each sub-task) · do-date bottom button + picker (concrete date / Someday / clear; 09:00 default, preserves time-of-day) · star toggle · create scheduled reminder · export chooser (Google Tasks / Calendar) · delete (direct for new-like, confirm dialog otherwise) · long-press feedback flow · selection mode (checkbox, selected border) · left accent bar per section · open detail on tap.

### 9.3 Detail editor
Editable title · type toggle Task/Keep (FilterChips) · completed checkbox · deadline date+time pickers · do-date date+time + Someday chip + clear · note editor · LLM action chips · sub-tasks section (add/toggle/edit/delete/export) · create scheduled reminder chip · export Google Tasks / Google Calendar chips (+ sign-in flow when signed out) · share · change history section · related notifications (expandable, loading state, open-notification buttons, surrounding context load) · regenerate (⋮) · delete (⋮) · acknowledge-review · save-on-back (system back and arrow).

### 9.4 Review flow
Swipe right approve / left reject (reject NEW = soft delete; reject UPDATED = revert pending LLM edit) · tap opens detail sheet with approve/reject · filter chips (All / new-tasks / updated-tasks / new-keeps / updated-keeps, count labels, only non-zero shown) · bottom bar reject / approve-all / approve · undo snackbar (single step) · change-summary line on Updated cards · stacked depth preview (3 cards) · empty state.

### 9.5 Screen-level
Top-bar search (History, Reminders lists, NotiList, SavedList) · clear-all (NotiList, scoped, pinned excluded) · time-window chips (All/24h) · create-reminder-from-notification (records snapshot) · scheduled reminders: mark-seen, reschedule, cancel · history: grouped/timeline modes, copy, manual extract · preference chat: full flow incl. proposed-action cards, conflicts banner, active-preferences panel, user-context panel · settings: dynamic color toggle (activity recreate), swipe direction, extraction language picker w/ search, data export w/ options · sign-in screen · dev/user control panels.

---

## 10. Open item (needs a product decision, not blocking)

**Scheduled (alarm) reminders on Home.** User is unsure whether these are primary-but-intrusive or secondary. Low-risk proposal to implement behind the decision: when `dueUnseenReminderCount > 0`, show a slim tappable strip on Home between the Review row and Notifications ("N reminders due", `dueSoonContainer` accent per §3.4 accent rules) → opens the Reminders screen. Zero cost when count is 0. **Ask the user before building; skip otherwise.**

---

## 11. Execution order, sizing, and verification

| Phase | Scope | Size | Gate |
|---|---|---|---|
| 1 | Foundations: shapes, dimens, type rules, color policy plumbing, edge-to-edge+splash, motion+haptics helpers, snackbar bus | L | build+tests; dark-launch check; no white flash |
| 2 | Shell: transitions, predictive back, LargeTopAppBar, drawer, search restyle | M | all-destination nav sweep; drawer gesture regression |
| 3 | Home restyle | M | counts/nav sweep, 3 theme modes |
| 4 | NotiCard + category/history | L | §9.1 + §9.5 device pass; slow-swipe regression |
| 5 | Saved items: 7.0 split (own PR) → card + detail + list restyle | XL | §9.2/§9.3 device pass; export flows live |
| 6 | Review flow | M | §9.4 device pass |
| 7 | Settings rebuild (ListItem groups), Preferences chat restyle, ScheduledReminders restyle, SignIn polish, EmptyState/DueChip final pass | M | §9.5 remainder |

Rules for the executor:
- One phase per PR (Phase 5 = two PRs: split, then restyle). Build + `testDebugUnitTest` per PR (JBR `JAVA_HOME` per project memory).
- After each phase, run the app and exercise the phase's §9 checklist on device/emulator — gesture code cannot be verified by compilation.
- Never edit: `NotiCardSwipe.kt` intent math, drawer `gesturesEnabled` logic, ViewModel/repository business logic (UI-event plumbing additions are fine), the n8n contract, DB layer.
- If an alpha `material3` API needed for a restyle is broken/missing, fall back to the stable-API equivalent and note it in the PR description; never downgrade the library mid-plan.
- Strings: every new user-visible string goes to `strings.xml` + `values-zh-rTW/strings.xml`.

---

## 12. Explicit non-goals

- No bottom navigation bar; no Navigation-Compose migration (ad-hoc back stack is settled).
- No iOS control mimicry (no Cupertino switches, no centered iOS titles, no swipe-back-from-edge custom nav).
- No feature additions beyond §10 (if approved) and undo-on-clear-all (§6.5).
- No changes to extraction pipeline, sync, notifications listener, or data model.
