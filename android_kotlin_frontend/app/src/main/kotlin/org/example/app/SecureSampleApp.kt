package org.example.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import org.example.app.auth.AuthManager
import org.example.app.auth.TokenStore
import org.example.app.diagnostics.CrashMarker
import org.example.app.net.ApiClient

class SecureSampleApp : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var authManager: AuthManager
        private set

    /**
     * API client is intentionally nullable and lazily created to avoid preview/emulator startup
     * crashes due to networking stack initialization in hosted preview environments.
     */
    var apiClient: ApiClient? = null
        private set

    /**
     * If non-null, the app had to fall back due to an initialization failure.
     * MainActivity can use this to show a non-crashing "safe mode" screen in preview.
     */
    @Volatile
    var initFailureMessage: String? = null
        private set

    /**
     * When true, the app avoids fragile startup initialization paths (keystore/biometric/network).
     * This is meant for hosted preview environments where logs are not visible and early crashes
     * would otherwise make the app appear to "not run".
     */
    @Volatile
    var isPreviewSafeMode: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()

        // IMPORTANT:
        // Capture the current default handler BEFORE replacing it.
        // If we capture it after setDefaultUncaughtExceptionHandler(), we'd capture ourselves and
        // create an infinite recursion on any crash (white screen -> immediate exit).
        val previousDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // We keep a reference to our handler so we can compare identity safely.
        // Also include a simple recursion guard to prevent re-entrancy if delegation triggers us again.
        var handlingCrash = false
        lateinit var ourHandler: Thread.UncaughtExceptionHandler

        // As an additional safety net in preview environments, capture uncaught exceptions
        // and persist a short message so the next launch can show a screen instead of "silent exit".
        ourHandler = Thread.UncaughtExceptionHandler { thread, t ->
            // Best-effort marker write; never throw from the exception handler.
            runCatching { CrashMarker.writeStartupCrashMarker(this, t) }
            recordInitFailureIfEmpty(t)

            // Prevent infinite recursion if something causes this handler to be invoked again.
            if (handlingCrash) return@UncaughtExceptionHandler
            handlingCrash = true

            // Delegate to the prior handler to preserve default behavior outside preview.
            // Only delegate when it is a different instance than our handler.
            val delegated = if (previousDefaultHandler != null && previousDefaultHandler !== ourHandler) {
                runCatching { previousDefaultHandler.uncaughtException(thread, t) }.isSuccess
            } else {
                false
            }

            // Preview stability guarantee:
            // In preview-safe mode we must never explicitly terminate the process from here.
            // Hosted preview runtimes interpret explicit kills/exits as an "auto-close" and you
            // won't get a chance to see DiagnosticActivity on relaunch.
            if (isPreviewSafeMode) {
                return@UncaughtExceptionHandler
            }

            // Outside preview-safe mode we preserve prior/default behavior as much as possible:
            // delegate first; if we cannot delegate, terminate to avoid undefined state.
            if (!delegated) {
                runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
                runCatching { kotlin.system.exitProcess(10) }
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(ourHandler)

        // Determine safe mode using manifest meta-data; default is OFF unless explicitly enabled.
        // We enable it in AndroidManifest.xml for preview stability.
        isPreviewSafeMode = runCatching {
            val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ai.metaData?.getBoolean(META_PREVIEW_SAFE_MODE, false) ?: false
        }.getOrDefault(false)

        // Some preview/emulator environments can throw during crypto/keystore setup or other
        // initialization paths. If that happens in Application.onCreate, the app will
        // immediately crash/auto-close. We must never crash at this stage.
        val initResult = runCatching {
            tokenStore = TokenStore(applicationContext)
            authManager = AuthManager(applicationContext, tokenStore)

            // Defer network client initialization in safe mode.
            if (!isPreviewSafeMode) {
                apiClient = ApiClient(authManager)
            }
        }

        if (initResult.isFailure) {
            val t = initResult.exceptionOrNull()
            recordInitFailureIfEmpty(t)
            Log.e(TAG, "App initialization failed; running in safe mode.", t)

            // Absolute last-resort fallback: initialize only local components.
            runCatching {
                tokenStore = TokenStore(applicationContext)
                authManager = AuthManager(applicationContext, tokenStore)
                apiClient = null
                isPreviewSafeMode = true
            }.onFailure { t2 ->
                Log.e(TAG, "Safe-mode initialization also failed; app will show error UI.", t2)
            }
        }
    }

    // PUBLIC_INTERFACE
    fun recordInitFailureIfEmpty(t: Throwable?) {
        /**
         * Records an initialization failure message if one hasn't already been recorded.
         * This is used by Activity-level crash guards so preview environments show a UI
         * rather than auto-exiting without logs.
         */
        if (initFailureMessage != null) return
        initFailureMessage = t?.message ?: t?.javaClass?.simpleName ?: "Unknown init error"
        isPreviewSafeMode = true
    }

    /**
     * Lazily create the API client when needed (and only when not in preview-safe mode).
     */
    fun getOrCreateApiClient(): ApiClient? {
        if (isPreviewSafeMode) return null
        return apiClient ?: runCatching { ApiClient(authManager) }
            .onSuccess { apiClient = it }
            .getOrNull()
    }

    private companion object {
        private const val TAG = "SecureSampleApp"
        private const val META_PREVIEW_SAFE_MODE = "org.example.app.PREVIEW_SAFE_MODE"
    }
}
