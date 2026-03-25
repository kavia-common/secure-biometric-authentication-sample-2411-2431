package org.example.app.auth

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthManager(
    context: Context,
    private val tokenStore: TokenStore
) {
    private val appContext = context.applicationContext

    // Simple app-lock state: when true, user must re-auth with biometrics to continue.
    @Volatile
    var isLocked: Boolean = false
        private set

    private val refreshMutex = Mutex()

    fun isLoggedIn(): Boolean = tokenStore.load() != null

    fun currentTokens(): TokenPair? = tokenStore.load()

    fun lockApp() {
        // Lock only if a session exists
        if (isLoggedIn()) {
            isLocked = true
        }
    }

    fun unlockApp() {
        isLocked = false
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        // Demo login: generate tokens locally and "expire soon" to exercise refresh.
        // In a real app, call your backend here.
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Username/password required"))
        }

        val now = System.currentTimeMillis()
        val pair = TokenPair(
            accessToken = "access_${now}",
            refreshToken = "refresh_${now}",
            accessTokenExpiresAtMs = now + 60_000L // 60 seconds
        )
        tokenStore.save(pair)
        isLocked = false
        return Result.success(Unit)
    }

    fun logout() {
        tokenStore.clear()
        isLocked = false
    }

    fun isAccessTokenExpired(bufferMs: Long = 10_000L): Boolean {
        val tokens = tokenStore.load() ?: return true
        val now = System.currentTimeMillis()
        return now + bufferMs >= tokens.accessTokenExpiresAtMs
    }

    /**
     * Ensures a valid access token is available.
     * If needed, performs a refresh (simulated) and stores updated tokens.
     */
    suspend fun getValidAccessToken(): Result<String> {
        val tokens = tokenStore.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        if (!isAccessTokenExpired()) return Result.success(tokens.accessToken)

        return refreshMutex.withLock {
            // Re-check after obtaining lock (another caller may have refreshed)
            val afterLock = tokenStore.load()
                ?: return@withLock Result.failure(IllegalStateException("Not logged in"))
            if (!isAccessTokenExpired()) return@withLock Result.success(afterLock.accessToken)

            refreshAccessToken(afterLock)
        }
    }

    private suspend fun refreshAccessToken(current: TokenPair): Result<String> {
        // Demo refresh: rotate access token + extend expiry.
        // In a real app, call refresh endpoint with refresh token.
        val now = System.currentTimeMillis()
        if (!current.refreshToken.startsWith("refresh_")) {
            // Simulate invalid refresh token -> session invalidation
            logout()
            return Result.failure(IllegalStateException("Session expired. Please login again."))
        }

        val newPair = TokenPair(
            accessToken = "access_${now}",
            refreshToken = current.refreshToken,
            accessTokenExpiresAtMs = now + 5 * 60_000L // 5 minutes
        )
        tokenStore.save(newPair)
        return Result.success(newPair.accessToken)
    }
}
