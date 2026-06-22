package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NimMessage(
    @Json(name = "role") val role: String, // "system", "user", "assistant"
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class NimChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<NimMessage>,
    @Json(name = "temperature") val temperature: Float,
    @Json(name = "top_p") val topP: Float,
    @Json(name = "max_tokens") val maxTokens: Int = 1024,
    @Json(name = "stream") val stream: Boolean = false,
    @Json(name = "frequency_penalty") val frequencyPenalty: Float = 0.0f,
    @Json(name = "presence_penalty") val presencePenalty: Float = 0.0f
)

@JsonClass(generateAdapter = true)
data class NimChoice(
    @Json(name = "index") val index: Int,
    @Json(name = "message") val message: NimMessage,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class NimChatResponse(
    @Json(name = "id") val id: String?,
    @Json(name = "model") val model: String?,
    @Json(name = "choices") val choices: List<NimChoice>
)
