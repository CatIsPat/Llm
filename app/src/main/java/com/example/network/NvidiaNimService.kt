package com.example.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface NvidiaNimService {
    @POST
    suspend fun getChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: NimChatRequest
    ): Response<NimChatResponse>

    @retrofit2.http.Streaming
    @POST
    suspend fun getChatCompletionStreaming(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: NimChatRequest
    ): Response<okhttp3.ResponseBody>

    @retrofit2.http.GET
    suspend fun getModels(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): Response<okhttp3.ResponseBody>

    companion object {
        private var instance: NvidiaNimService? = null

        fun getInstance(): NvidiaNimService {
            return instance ?: synchronized(this) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
                
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(45, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val requestBuilder = original.newBuilder()
                            // Custom popular Mobile Chrome User-Agent to bypass any blockades/firewalls
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .header("Accept", "application/json")
                            .header("Cache-Control", "no-cache")
                        val request = requestBuilder.build()
                        chain.proceed(request)
                    }
                    .addInterceptor(logging)
                    .build()

                val retrofit = Retrofit.Builder()
                    // Dummy baseURL, overridden dynamically by @Url
                    .baseUrl("https://dummy.api.nvidia.com/") 
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                val created = retrofit.create(NvidiaNimService::class.java)
                instance = created
                created
            }
        }
    }
}
