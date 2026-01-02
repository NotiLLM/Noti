# Domain package (`org.muilab.notigpt.domain`)

The **domain layer** holds business logic and rules that should be mostly independent of Android.

## What belongs here

- Business rules (filtering, grouping, sorting decisions)
- Type-safe concepts shared across the app (e.g. action enums)
- Pure Kotlin logic that is easy to unit test

## What should not be here

- Android framework types (`Context`, `Intent`, `Toast`, `MediaStore`, etc.)
- Compose UI
- Room / Retrofit implementations

If domain logic needs data, it should be passed in as plain Kotlin types.

