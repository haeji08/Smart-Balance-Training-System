package com.example.smartboardapp.data

import java.math.BigInteger

data class EndTrainDetailsRequest(
    val trainId: BigInteger,
    val endTime: String,
    val unbalanceCount: Int
)
