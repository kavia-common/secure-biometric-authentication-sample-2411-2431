package org.example.app

import android.app.Application
import android.util.Log
import org.example.app.auth.AuthManager
import org.example.app.auth.TokenStore
import org.example.app.net.ApiClient

class SecureSampleApp : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var authManager: AuthManager
        private set

    lateinit var apiClient: ApiClient
        private set

    /**
     * If non-null, the app had to fall back due to an initialization failure.
     * MainActivity can use this to show a non-crashing "safe mode" screen in preview.
     */
    @Volatile
    var initFailureMessage: String? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // Some preview/emulator environments can throw during crypto/keystore setup or other
        // initialization paths. If that happens in Application.onCreate, the app will
        // immediately crash/auto-close. We must never crash at this stage.
        val initResult = runCatching {
            tokenStore = TokenStore(applicationContext)
            authManager = AuthManager(applicationContext, tokenStore)
            apiClient = ApiClient(authManager)
        }

        if (initResult.isFailure) {
            val t = initResult.exceptionOrNull()
            initFailureMessage = t?.message ?: t?.javaClass?.simpleName ?: "Unknown init error"
            Log.e(TAG, "App initialization failed; running in safe mode.", t)

            // Absolute last-resort fallback: initialize to something valid so the activity
            // doesn't crash due to uninitialized lateinit properties.
            //
            // TokenStore already falls back internally if EncryptedSharedPreferences fails,
            // so in most cases this will succeed. If it still fails, we swallow the error
            // and keep initFailureMessage set so the UI can display the problem.
            runCatching {
                tokenStore = TokenStore(applicationContext)
                authManager = AuthManager(applicationContext, tokenStore)
                apiClient = ApiClient(authManager)
            }.onFailure { t2 ->
                Log.e(TAG, "Safe-mode initialization also failed; app will show error UI.", t2)
            }
        }
    }

    private companion object {
        private const val TAG = "SecureSampleApp"
    }
}
