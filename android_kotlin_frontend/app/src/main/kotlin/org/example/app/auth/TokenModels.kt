package org.example.app.auth

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis when access token should be considered expired */
    val accessTokenExpiresAtMs: Long
)
