package com.example.smartboardapp.data

import java.math.BigInteger

data class TrainDetails(
    val id: BigInteger,
    val date: String,
    val startTime: String,
    val endTime: String,
    val totalBalanceSustainTime: Int,
    val copPattern: String?,
    val totalUnbalanceCount: Int?,
    val horizontalBalanceRatio: String?,
    val selfFeedback: String?
)
