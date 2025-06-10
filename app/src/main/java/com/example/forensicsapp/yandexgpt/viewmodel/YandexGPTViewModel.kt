package com.example.forensicsapp.yandexgpt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forensicsapp.yandexgpt.data.api.YandexGPTApi
import com.example.forensicsapp.yandexgpt.data.model.YandexGPTRequest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YandexGPTViewModel : ViewModel() {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://llm.api.cloud.yandex.net/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api by lazy {
        retrofit.create(YandexGPTApi::class.java)
    }

    fun getFormalText(folderId: String, iamToken: String, userText: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val request = YandexGPTRequest(
                    modelUri = "gpt://$folderId/yandexgpt",
                    messages = listOf(
                        YandexGPTRequest.Message(
                            role = "system",
                            text = "Ты помощник криминалиста. Перефразируй текст в официально-деловой стиль."
                        ),
                        YandexGPTRequest.Message(
                            role = "user",
                            text = userText
                        )
                    )
                )

                val response = api.getCompletion(
                    authHeader = "Bearer $iamToken",
                    request = request
                )

                callback(response.result.alternatives[0].message.text)
            } catch (e: Exception) {
                callback("Ошибка: ${e.localizedMessage}")
            }
        }
    }
}
