package org.example.app

import android.app.Application
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
        tokenStore = TokenStore(applicationContext)
        authManager = AuthManager(applicationContext, tokenStore)
        apiClient = ApiClient(authManager)
    }
}
