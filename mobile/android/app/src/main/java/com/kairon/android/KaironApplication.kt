package com.kairon.android

import android.app.Application
import com.kairon.android.core.logging.AppLog
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "UncaughtException"

@HiltAndroidApp
class KaironApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogging()
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
