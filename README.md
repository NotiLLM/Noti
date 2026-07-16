# Noti

Noti is a single-module Android application that captures notification history on the device,
uses a staged LLM pipeline to propose tasks and keeps, lets the user review those proposals, and
synchronizes generated content across signed-in devices. Scheduled reminders are a separate
feature: they are Android alarm notifications that can point to a saved item or notification.

## Start here

1. [Architecture](docs/ARCHITECTURE.md) — layers, runtime flow, Hilt, and significant file roles.
2. [Data and privacy](docs/DATA_AND_PRIVACY.md) — what is local, what reaches Firestore/n8n, and deletion.
3. [Notification pipeline](docs/notification-pipeline.md) — capture, delayed shade cancellation, and recovery.
4. [Extraction pipeline](docs/extraction-pipeline.md) — current n8n contract and staged review flow.
5. [Setup and testing](docs/SETUP_AND_TESTING.md) — local configuration and verification commands.
6. [Release checklist](docs/RELEASE.md) and [known issues](docs/KNOWN_ISSUES.md).

## Build

Requirements: JDK 17 and the Android SDK used by `compileSdk = 37`. Keep Firebase configuration in
`app/google-services.json`; never commit private signing keys or server credentials.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Instrumented migration and DAO tests need an emulator or test device:

```bash
./gradlew connectedDebugAndroidTest
```

The app supports Android 10/API 29 and later. Newer notification metadata is read behind runtime
API checks, so keeping API 29 does not disable Android 12+ features on newer phones.

## Non-negotiable data boundary

Raw notification titles, messages, and bodies remain in the local Room database. Firestore stores
generated task/keep content, generated proposals and decision state, minimal source record IDs, and
account metadata; it must not store source notification snapshots. The temporary n8n server still
receives content needed for generation. Its workflow paths are compatibility contracts and are not
edited from this Android repository.
