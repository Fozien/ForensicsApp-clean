package com.example.fixator.yandexgpt.data.api

import com.example.fixator.yandexgpt.data.model.YandexGPTRequest
import com.example.fixator.yandexgpt.data.model.YandexGPTResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface YandexGPTApi {
    @POST("foundationModels/v1/completion")
    suspend fun getCompletion(
        @Header("Authorization") authHeader: String,
        @Header("x-folder-id") folderId: String,
        @Body request: YandexGPTRequest
    ): YandexGPTResponse
}
