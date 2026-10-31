package com.denkasir.data.remote.api

import com.denkasir.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface DenKasirApi {

    @POST("api/v1/auth/login/json")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<LoginResponseDto>

    @POST("api/v1/sync/push")
    suspend fun pushSync(
        @Header("Authorization") token: String,
        @Body payload: SyncPushRequestDto
    ): Response<SyncPushResponseDto>

    @POST("api/v1/sync/pull")
    suspend fun pullSync(
        @Header("Authorization") token: String,
        @Body payload: SyncPullRequestDto
    ): Response<SyncPullResponseDto>

    @GET("health")
    suspend fun checkHealth(): Response<Map<String, String>>
}
