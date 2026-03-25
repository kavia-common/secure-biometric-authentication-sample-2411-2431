package org.example.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(tokenPair: TokenPair) {
        prefs.edit()
            .putString(KEY_ACCESS, tokenPair.accessToken)
            .putString(KEY_REFRESH, tokenPair.refreshToken)
            .putLong(KEY_ACCESS_EXPIRY, tokenPair.accessTokenExpiresAtMs)
            .apply()
    }

    fun load(): TokenPair? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val expiry = prefs.getLong(KEY_ACCESS_EXPIRY, 0L)
        if (expiry <= 0L) return null
        return TokenPair(access, refresh, expiry)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "secure_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ACCESS_EXPIRY = "access_expiry_ms"
    }
}
