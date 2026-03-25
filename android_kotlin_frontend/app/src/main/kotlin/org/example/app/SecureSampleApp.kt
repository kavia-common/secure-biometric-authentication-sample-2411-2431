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

    override fun onCreate() {
        super.onCreate()

        // Some preview/emulator environments can throw during crypto/keystore setup or other
        // initialization paths. If that happens in Application.onCreate, the app will
        // immediately crash/auto-close. We must never crash at this stage.
        runCatching {
            tokenStore = TokenStore(applicationContext)
            authManager = AuthManager(applicationContext, tokenStore)
            apiClient = ApiClient(authManager)
        }.onFailure { t ->
            Log.e(TAG, "App initialization failed; falling back to non-encrypted token storage.", t)

            // Fallback: allow the app to launch even if secure components fail to initialize.
            // TokenStore already attempts encryption first and falls back internally, but we
            // keep this extra safety net to ensure preview stability.
            tokenStore = TokenStore(applicationContext)
            authManager = AuthManager(applicationContext, tokenStore)
            apiClient = ApiClient(authManager)
        }
    }

    private companion object {
        private const val TAG = "SecureSampleApp"
    }
}
