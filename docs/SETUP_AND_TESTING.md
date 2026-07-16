# Setup and testing

## Local setup

- Install JDK 17 and Android SDK/API 37.
- Put the development Firebase file at `app/google-services.json`.
- Use Firebase App Check debug-token registration for debug builds. Release builds use Play Integrity.
- Do not add LAN/cleartext permissions for temporary servers to the production manifest. If local server
  testing returns, use a debug-only manifest and network-security config.
- n8n paths in `app/build.gradle.kts` are compatibility contracts. Change them only with a coordinated
  backend migration and sanitized contract tests.

## Fast checks

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

`lintDebug` is a required gate. A Gradle lock failure under a restricted automation sandbox does not
prove Android Studio is broken; rerun with access to the existing Gradle cache and report the distinction.

## Dependency alignment

Firestore and the Google Tasks client both bring gRPC modules into the runtime graph. Keep the
`io.grpc:grpc-bom` declaration in `app/build.gradle.kts` and upgrade its version as a unit; removing it can
mix `grpc-core` with a different `grpc-api` and crash Firestore during channel initialization. The legacy
`grpc-android` adapter is not managed by newer BOMs, but the core/API versions shown below must match.
After changing Firebase, Google API Client, Google Tasks, or gRPC versions, verify both sides:

```bash
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency io.grpc:grpc-api
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency io.grpc:grpc-core
```

## Database checks

Room exports schemas to `app/schemas/.../AppDatabase/<version>.json`. Commit every new schema. For a
database version bump:

1. Add a forward-only migration in `AppDatabaseMigrations`.
2. Register it in `AppDatabase`.
3. Add a `MigrationTestHelper` test from the previous schema.
4. Compile to generate the new JSON, inspect it, and run the instrumented test on a disposable emulator.

Never use destructive migration fallback for production user data.

## Device matrix

Before Play release, exercise API 29, 30, 31, and current target/API 37. The manual matrix must include:
notification capture, delayed cancellation, content-intent launch, listener rebind/destruction, sign-in,
sign-out, and account switching, review accept/reject/undo, offline outbox retry, scheduled alarms, reboot restoration,
local-history deletion, cloud-data deletion, and English/Traditional Chinese ongoing status text.

Do not install an automation test APK over a phone containing irreplaceable notification history.
