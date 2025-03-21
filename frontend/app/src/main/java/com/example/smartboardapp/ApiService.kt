package com.example.smartboardapp

import com.example.smartboardapp.data.FeedbackRequest
import com.example.smartboardapp.data.StartTrainingResponse
import com.example.smartboardapp.data.SustainTimeRequest
import com.example.smartboardapp.data.Train
import com.example.smartboardapp.data.TrainDetails
import com.example.smartboardapp.data.TrainingResponse
import com.example.smartboardapp.data.EndTrainDetailsRequest
import com.example.smartboardapp.data.StartTimeRequest
import com.example.smartboardapp.data.TrainingStatusResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.math.BigInteger

interface ApiService {
    @GET("/api/train")  // 서버에서 훈련 데이터를 받을 경로
    fun getTrainData(@Query("date") date: String): Call<List<Train>>  // 날짜를 파라미터로 전달

    @GET("/api/train/details")
    fun getTrainDetails(@Query("id") id: BigInteger): Call<TrainDetails>

    @POST("/api/train/feedback")
    fun sendFeedback(@Body feedback: FeedbackRequest): Call<Void>  // 피드백 데이터를 요청 본문에 포함하여 전송

    @POST("/api/train/ready")
    fun readyTrain(): Call<Void>  // 훈련 시작 요청

    @GET("/api/train/check")
    fun checkTrainStatus(): Call<Map<String, String>>  // 훈련 상태 확인

    @POST("/api/train/start")
    fun startTraining(@Body startTime: StartTimeRequest): Call<StartTrainingResponse>

    @GET("/api/training")
    fun getTrainingData(@Query("id") id: BigInteger): Call<TrainingResponse>

    @POST("/api/training/sustainTime")
    fun sendSustainTime(@Body sustainTime: SustainTimeRequest): Call<Void>

    @POST("/api/training/endTrainDetails")
    fun sendEndTrainDetails(@Body endTrainDetails: EndTrainDetailsRequest): Call<Void>

    @GET("/api/train/status")
    fun getTrainingStatus(@Query("id") trainId: BigInteger): Call<TrainingStatusResponse>

}