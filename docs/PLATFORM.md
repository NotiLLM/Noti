# Android platform boundaries

- `service/`: framework-owned long-lived notification listener.
- `receiver/`: short boot, alarm, seen-action, and listener-rebind callbacks.
- `work/`: deferred work that must survive process death and obey Android background limits.
- `data/export` and `ui/common/clipboard`: narrow wrappers for MediaStore and clipboard side effects.

Framework callbacks should do minimal parsing and delegate to a repository. Do not create unmanaged
application-lifetime coroutine scopes in UI or receivers. WorkManager workers use Hilt assisted
construction; receivers must finish quickly or explicitly use `goAsync()` with bounded work.
