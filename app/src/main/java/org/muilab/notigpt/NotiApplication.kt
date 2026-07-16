package org.muilab.notigpt

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.muilab.notigpt.work.FirestoreOutboxWork

/**
 * Process-level Hilt container for Noti.
 *
 * Hilt generates the application component before Android creates activities, services, receivers,
 * or workers. App-scoped dependencies are declared in `di`; feature classes request only what they
 * use through constructors or Android entry-point fields.
 */
@HiltAndroidApp
class NotiApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Lets WorkManager ask Hilt to construct workers with their declared dependencies. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // App Check is advisory while n8n is temporary; the future GCP server will verify this
        // token. Debug builds use Firebase's registered debug-token flow for emulator/local work.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            },
        )

        // Debug logs and local failures must not leave the development environment via Crashlytics.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // Safe after process death: the request contains no user content and the worker reads the
        // current signed-in UID before replaying account-scoped Room operations.
        FirestoreOutboxWork.enqueue(this)
    }
}
