package org.muilab.notigpt.ui.notification.action

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Platform boundary for opening a notification's PendingIntent or app fallback.
 *
 * Keep Android launch flags, activity options, and settings fallback here so notification cards can ask for an
 * app launch without knowing PendingIntent failure modes.
 */
object NotificationLauncher {

    fun launchPendingIntentOrFallback(
        context: Context,
        pendingIntent: PendingIntent?,
        packageName: String,
        logTag: String = "NotificationLauncher",
    ) {
        if (pendingIntent != null) {
            try {
                pendingIntent.send(context, 0, null, null, null, null, activityOptionsBundle())
                return
            } catch (e: PendingIntent.CanceledException) {
                Log.w(logTag, "PendingIntent canceled/expired. Falling back.")
            } catch (t: Throwable) {
                Log.w(logTag, "PendingIntent send failed. Falling back.", t)
            }
        }

        // Fallback: launch app, or open app details.
        if (!launchApp(context, packageName)) {
            openAppDetails(context, packageName, logTag)
        }
    }

    private fun activityOptionsBundle(): android.os.Bundle? {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    val options = ActivityOptions.makeBasic()
                    // Android 14+ background start mode
                    if (Build.VERSION.SDK_INT >= 35) {
                        options.pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    } else {
                        options.pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                    options.toBundle()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> ActivityOptions.makeBasic().toBundle()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun openAppDetails(context: Context, packageName: String, logTag: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(logTag, "Unable to open app details for $packageName", t)
        }
    }
}

