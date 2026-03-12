package com.example.fixator.yandexgpt.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixator.yandexgpt.data.api.YandexGPTApi
import com.example.fixator.yandexgpt.data.model.YandexGPTRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class YandexGPTViewModel : ViewModel() {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://llm.api.cloud.yandex.net/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)  // GPT может думать долго
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api by lazy {
        retrofit.create(YandexGPTApi::class.java)
    }

    // Храним Job чтобы можно было отменить при закрытии Activity
    private var currentJob: Job? = null

    /**
     * Отправляет текст в YandexGPT для формализации.
     *
     * @param folderId  ID каталога Yandex Cloud
     * @param apiKey    API-ключ сервисного аккаунта
     * @param userText  Исходный текст от пользователя
     * @param onResult  Callback: (text, isError)
     */
    fun getFormalText(
        folderId: String,
        apiKey: String,
        userText: String,
        onResult: (text: String, isError: Boolean) -> Unit
    ) {
        // Отменяем предыдущий запрос если пользователь нажал повторно
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            try {
                val request = YandexGPTRequest(
                    modelUri = "gpt://$folderId/yandexgpt-lite/latest",
                    messages = listOf(
                        YandexGPTRequest.Message(
                            role = "system",
                            text = "Ты помощник криминалиста. Перефразируй текст в официально-деловой стиль, " +
                                    "сохрани все факты, не добавляй ничего от себя."
                        ),
                        YandexGPTRequest.Message(
                            role = "user",
                            text = userText
                        )
                    )
                )

                val response = api.getCompletion(
                    authHeader = "Api-Key $apiKey",
                    folderId = folderId,
                    request = request
                )

                val alternatives = response.result.alternatives
                if (alternatives.isEmpty()) {
                    onResult("Модель не вернула результат. Попробуйте ещё раз.", true)
                    return@launch
                }

                val resultText = alternatives[0].message.text
                if (resultText.isBlank()) {
                    onResult("Получен пустой ответ от модели.", true)
                } else {
                    onResult(resultText, false)
                }

            } catch (e: HttpException) {
                // Разбираем HTTP-ошибки отдельно для понятных сообщений
                val message = when (e.code()) {
                    400 -> "Ошибка запроса (400): проверьте folderId и формат данных"
                    401 -> "Ошибка авторизации (401): неверный или устаревший API-ключ"
                    403 -> "Нет доступа (403): проверьте роль сервисного аккаунта (ai.languageModels.user)"
                    429 -> "Превышен лимит запросов (429): подождите немного"
                    500 -> "Ошибка сервера Яндекса (500): попробуйте позже"
                    else -> "HTTP ошибка ${e.code()}: ${e.message()}"
                }
                onResult(message, true)

            } catch (e: UnknownHostException) {
                onResult("Нет подключения к интернету", true)

            } catch (e: SocketTimeoutException) {
                onResult("Превышено время ожидания ответа. Попробуйте ещё раз.", true)

            } catch (e: Exception) {
                onResult("Неизвестная ошибка: ${e.localizedMessage}", true)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("YandexGPT", "HTTP ${e.code()}: $errorBody")  // <- смотри Logcat
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
