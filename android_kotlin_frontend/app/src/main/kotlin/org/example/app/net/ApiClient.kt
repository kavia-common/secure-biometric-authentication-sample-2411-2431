package org.example.app.net

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.example.app.auth.AuthManager
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class ApiClient(private val authManager: AuthManager) {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()

        // If not logged in, proceed without auth header; call may fail.
        val tokenResult = runBlocking { authManager.getValidAccessToken() }
        val reqWithAuth = tokenResult.getOrNull()?.let { token ->
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } ?: original

        val response = chain.proceed(reqWithAuth)

        // If unauthorized, attempt one refresh and retry once.
        if (response.code == 401) {
            response.close()
            val refreshed = runBlocking { authManager.getValidAccessToken() }
            val refreshedToken = refreshed.getOrNull()
            if (refreshedToken != null) {
                val retry: Request = original.newBuilder()
                    .header("Authorization", "Bearer $refreshedToken")
                    .build()
                return@Interceptor chain.proceed(retry)
            }
        }

        response
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logger)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            // A harmless public endpoint used for demo "API call"
            .baseUrl("https://httpbin.org/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    val service: DemoApiService by lazy { retrofit.create(DemoApiService::class.java) }
}
