# Google Play release checklist

## Build and identity

- Increase `versionCode` for every upload and set a user-facing `versionName`.
- Keep signing keys outside Git; configure release signing through protected environment/local properties.
- Build and smoke-test an Android App Bundle: `./gradlew bundleRelease`.
- Decide whether to enable R8/minification only after release rules and a signed smoke test are ready.
- Verify Firebase Play Integrity App Check and production SHA certificates.

## Required product inputs

- A hosted privacy-policy URL, policy version, contact details, and effective date.
- Final disclosure/consent copy in English and Traditional Chinese.
- Data Safety answers covering notification access, generated content, n8n/Gemini processing,
  Firestore, Crashlytics, App Check, backups, deletion, and account behavior.
- Store listing, screenshots using synthetic content, support contact, and account-deletion instructions.

Service must not be gated by invented placeholder legal text. Add the mandatory versioned consent screen
only when the real policy inputs exist. Invitation codes/waitlists/admin entitlement are deferred to the
future authenticated GCP backend and must be enforced server-side on every LLM request.

## Pre-upload gates

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease
```

Also run the migration/device matrix in [SETUP_AND_TESTING.md](SETUP_AND_TESTING.md), verify no raw
notification content appears in Firestore or logs, and manually exercise notification-listener disclosure,
battery behavior, account switching, deletion, and backup expectations.
