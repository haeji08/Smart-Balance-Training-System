package com.example.smartboardapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:5001" // Flask 서버 주소 (실제 IP로 변경)

    // Retrofit 인스턴스를 초기화하고, API 인터페이스를 반환하는 메서드
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())  // Gson을 사용하여 JSON을 객체로 변환
            .build()
    }

    // ApiService 객체 반환
    fun getApiService(): ApiService = retrofit.create(ApiService::class.java)
}