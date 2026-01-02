# Platform package (`org.muilab.notigpt.platform`)

This package contains **small wrappers around Android/system APIs**.

## Why this exists

ViewModels and domain logic become cleaner and more testable when they depend on small interfaces instead of directly on Android framework classes.

## Examples in this project

- `ClipboardController`: copy text to clipboard
- `UserNotifier`: show short user messages (Toast)
- `NotiLogExporter`: write exported logs to Documents via MediaStore

## Guidance

- Keep interfaces small.
- Prefer one responsibility per interface.
- Provide Android implementations here and inject them from factories.

