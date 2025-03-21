package com.example.smartboardapp.data

import java.math.BigInteger

data class Train(
    val id: BigInteger,
    val date: String,  // 훈련 날짜 (MM/DD 형식)
    val startTime: String,  // 훈련 시작 시간 (HH:mm:ss 형식)
    val endTime: String  // 훈련 종료 시간 (HH:mm:ss 형식)
)
