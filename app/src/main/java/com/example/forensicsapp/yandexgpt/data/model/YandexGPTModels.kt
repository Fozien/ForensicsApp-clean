package com.example.forensicsapp.yandexgpt.data.model

data class YandexGPTRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions = CompletionOptions(),
    val messages: List<Message>
) {
    data class CompletionOptions(
        val stream: Boolean = false,
        val temperature: Double = 0.6,
        val maxTokens: String = "2000"
    )

    data class Message(
        val role: String, // "system" или "user"
        val text: String
    )
}

data class YandexGPTResponse(
    val result: Result
) {
    data class Result(
        val alternatives: List<Alternative>,
        val usage: Usage
    )

    data class Alternative(
        val message: Message,
        val status: String
    ) {
        data class Message(
            val role: String,
            val text: String
        )
    }

    data class Usage(
        val inputTextTokens: String,
        val completionTokens: String,
        val totalTokens: String
    )
}
