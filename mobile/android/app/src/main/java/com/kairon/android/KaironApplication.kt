package com.kairon.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kairon.android.core.auth.TokenStore
import com.kairon.android.core.logging.AppLog
import com.kairon.android.notifications.NotificationChannels
import com.kairon.android.notifications.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

private const val TAG = "UncaughtException"

@HiltAndroidApp
class KaironApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var notificationScheduler: NotificationScheduler

    // Implementing Configuration.Provider is enough — WorkManager's own
    // default (androidx.startup-based) initializer detects it automatically
    // and uses this configuration, no manifest change needed.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        installCrashLogging()
        NotificationChannels.create(this)
        // M11.9 D8 — an app relaunch with an existing session re-enqueues
        // (KEEP policy) the periodic notification check; a fresh login/
        // register enqueues from AuthRepository instead.
        if (tokenStore.signedIn.value) {
            notificationScheduler.enqueue()
        }
    }

    /**
     * The platform's own crash handler already tombstones a fatal exception,
     * but that logcat line is a single unstructured dump. Wrap it so a fatal
     * crash gets the same structured event/fields/origin/cause-chain shape as
     * every handled error this app logs, before handing off to whatever the
     * default handler does next (system crash dialog, process death) —
     * this never suppresses or recovers from the crash, only logs it first.
     */
    private fun installCrashLogging() {
        val platformDefault = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e(TAG, "app.crash", mapOf("thread" to thread.name), throwable = throwable)
            platformDefault?.uncaughtException(thread, throwable)
        }
    }
}
