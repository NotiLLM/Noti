# NotiGPT Architecture & Directory Guide

This project uses a **layered Android + Jetpack Compose** structure. It’s a common, "normal" organization in modern Android apps because it separates:

- **UI (Compose) + ViewModels**: rendering and presentation state
- **Domain**: business logic / rules (Android-free when possible)
- **Data**: persistence + network clients + repositories
- **Platform**: small wrappers around Android system APIs (clipboard, toast, MediaStore, etc.)

The goal is to keep code readable, testable, and easy to extend.

---

## Top-level packages (under `org.muilab.notigpt`)

### `ui/`
**What it is:** Everything related to Jetpack Compose UI.

**What goes here:**
- `ui/screen`: high-level screens (page-level composables)
- `ui/component`: reusable UI pieces (composables)
- `ui/viewmodel`: `ViewModel`s + factories (presentation layer)
- `ui/theme`: Material theme definitions
- `ui/utils`: Compose-only utilities

**Rules of thumb:**
- Composables should be **stateless where possible**: take state via parameters, send events via callbacks.
- `ViewModel`s hold state and coordinate repositories/platform.
- Avoid doing network/Room work directly in composables.

### `domain/`
**What it is:** Pure application logic: filtering, grouping, rules, types.

**What goes here:**
- Business rules (e.g., grouping notifications, filter predicates)
- Strongly typed concepts that UI and data can share (e.g., `NotiActionType`)

**What should NOT go here:**
- Android APIs (`Context`, `Intent`, `Toast`, `MediaStore`)
- Room/Retrofit implementations

This makes it easy to unit-test domain logic as plain Kotlin.

### `repository/`
**What it is:** Data orchestration. Repositories coordinate Room, network, and side effects.

**What goes here:**
- Interfaces/implementations to read/write notification state
- Task repository logic

**Notes:**
- Repositories may depend on `database/*` and `database/server/*`.
- They should not depend on Compose UI.

### `database/`
**What it is:** Data sources.

- `database/room`: Room DB + DAOs + converters
- `database/server`: outward communications (Retrofit API client/service and related DTO helpers)
- `database/server/workers`: WorkManager workers that run background jobs that typically call network/repository code

> Naming note: many Android projects call this `data/local` + `data/remote`, or `network/`.
> We keep `database/server` here to minimize churn while remaining understandable.

### `platform/`
**What it is:** Small, testable wrappers around Android/system APIs.

Examples in this project:
- `ClipboardController` (clipboard)
- `UserNotifier` (toast)
- `NotiLogExporter` (MediaStore)

**Why:** ViewModels can depend on these interfaces instead of raw Android classes, making them easier to test and keeping side-effects contained.

### `service/`
Android services, e.g. `NotiListenerService`.

### `receiver/`
Android broadcast receivers, e.g. `BootUpReceiver`.

### `model/`
Shared data models / entities.

- `model/notifications`: notification-related models and DTOs
- `model/features`: task model

### `util/`
Generic helpers/constants shared by multiple layers.

---

## Dependency direction (recommended)

A clean, low-coupling direction looks like:

- `ui/*` → depends on `ui/viewmodel` only (and shared models)
- `ui/viewmodel` → depends on `repository` and `platform`
- `repository` → depends on `database/room` and `database/server` and `model`/`domain`
- `domain` → depends on `model` (optional) but **not Android**
- `platform` → depends on Android APIs

This avoids UI depending directly on persistence/network code.

---

## Where should new code go?

- New composable? → `ui/component` or `ui/screen`
- New screen/page? → `ui/screen`
- New state holder for UI? → `ui/viewmodel`
- New business rule / algorithm? → `domain/…`
- New Room table/DAO? → `database/room`
- New API client/endpoint? → `database/server`
- New background sync job? → `database/server/workers`
- New Android-system side effect wrapper? → `platform/`

---

## Notes

- Some files may currently bend these rules for practicality. If you see Android code inside domain, or DB/network calls inside composables, that’s a good refactor target.
- Gesture-heavy composables (drag/swipe/fling) should be refactored conservatively.

