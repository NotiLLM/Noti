# UI package (`org.muilab.notigpt.ui`)

This package contains **all Jetpack Compose UI code**.

## Folder guide

- `theme/`: Compose Material theme (Color/Type/Theme)
- `screen/`: top-level screens (page composables)
- `component/`: reusable composables used by screens
- `viewmodel/`: `ViewModel`s and factories used by UI
- `utils/`: Compose-only helpers (Modifier utilities, lifecycle hooks, etc.)

## Notes

- Gesture-heavy composables (swipe/drag/fling) should be refactored conservatively.
- Prefer state hoisting: Composables take state via parameters and emit events via callbacks.

