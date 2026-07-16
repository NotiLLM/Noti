# Hilt in Noti

Hilt is the dependency-injection layer generated at compile time. It answers one practical question:
when an Activity, service, ViewModel, or worker asks for a repository, who constructs it and how long
does that instance live?

## Construction path

```mermaid
flowchart LR
    App[NotiApplication @HiltAndroidApp] --> Graph[SingletonComponent]
    Module[AppModule @Module] --> Graph
    Graph --> DB[AppDatabase @Singleton]
    DB --> Repo[NotiRepository @Singleton]
    Repo --> VM[DrawerViewModel @HiltViewModel]
    Repo --> Service[NotiListenerService @AndroidEntryPoint]
    Graph --> Factory[HiltWorkerFactory]
    Factory --> Workers[@HiltWorker classes]
```

`@HiltAndroidApp` generates the process component. `@Module` plus `@Provides` describes objects that
cannot use an injected constructor, such as Room. `@Inject constructor` is preferred for classes the
project owns. `@Singleton` means one instance per app process—not permanent storage. Room and
WorkManager provide durability across process death.

## Entry-point annotations

- `@AndroidEntryPoint`: Android constructs the class, then Hilt supplies fields. Used by
  `MainActivity` and `NotiListenerService`.
- `@HiltViewModel`: Hilt constructs the ViewModel and its constructor dependencies.
- `@HiltWorker` + `@AssistedInject`: WorkManager supplies `Context` and `WorkerParameters`; Hilt
  supplies database/repository dependencies.
- `@ApplicationContext`: requests the process-safe application context, never an Activity context.

`NotiApplication` implements `Configuration.Provider` and supplies `HiltWorkerFactory`. Removing that
connection compiles but causes injected workers to fail at runtime, so treat it as part of the worker contract.

## Adding a dependency

1. Prefer `class X @Inject constructor(...)`.
2. If construction calls a builder/static factory, add one focused provider in `di/AppModule.kt`.
3. Choose the narrowest lifetime. Add `@Singleton` only for process-wide, thread-safe objects.
4. Request the interface/type from the consumer constructor; do not call `AppDatabase.getInstance()`
   in newly migrated ViewModels or workers.
5. Compile after changing the graph. Hilt/KSP errors usually identify a missing binding or an invalid scope.

## Testing and failures

Pure repository/ViewModel tests should pass fakes directly where possible. Hilt instrumented tests can
replace bindings with `@TestInstallIn` modules. Common failures are a missing `@AndroidEntryPoint`,
requesting an unbound interface, using two scopes on one binding, exposing an internal constructor type,
or registering a worker without `HiltWorkerFactory`.

Generated Hilt source is under `app/build/generated`; never edit or commit it.
