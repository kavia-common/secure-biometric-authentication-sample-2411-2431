package org.example.app.net

import retrofit2.http.GET

interface DemoApiService {
    @GET("get")
    suspend fun get(): String
}
