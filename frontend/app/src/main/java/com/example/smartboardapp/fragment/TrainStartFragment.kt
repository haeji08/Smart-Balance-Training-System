package com.example.smartboardapp.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.smartboardapp.ApiService
import com.example.smartboardapp.R
import com.example.smartboardapp.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TrainStartFragment : Fragment() {
    private var statusHandler: Handler? = null
    private var statusRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_train_start, container, false)

        // TextView 참조
        val textView: TextView = view.findViewById(R.id.tv_train_start)

        // 원문 텍스트
        val text = "훈련을 시작하기 위해 \n5초 동안 밸런스보드에 두 발로 서주세요"
        val spannableString = SpannableString(text)

        // 색상 지정
        val blueColor = ContextCompat.getColor(requireContext(), R.color.custom_blue)
        val redColor = ContextCompat.getColor(requireContext(), R.color.custom_red)

        // "5초" 부분 파란색
        val blueStart = text.indexOf("5초")
        val blueEnd = blueStart + 2
        spannableString.setSpan(
            ForegroundColorSpan(blueColor),
            blueStart,
            blueEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // "두 발로" 부분 빨간색
        val redStart = text.indexOf("두 발로")
        val redEnd = redStart + 4
        spannableString.setSpan(
            ForegroundColorSpan(redColor),
            redStart,
            redEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // TextView에 SpannableString 적용
        textView.text = spannableString

//        // 5초 후에 TrainingFragment로 이동
//        Handler().postDelayed({
//            startTraining()
//        }, 5000) // 5초 딜레이

        // 훈련 시작 요청 보내기
        startTrainRequest()
        // 훈련 시작 상태 확인 (주기적으로 확인)
        checkTrainStartStatus()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPolling() // 페이지 나갈 때 폴링 중단
    }

    // 훈련 시작 요청 보내기
    private fun startTrainRequest() {
        val apiService = RetrofitClient.getApiService()
        val call = apiService.readyTrain()

        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    // 훈련 시작 준비 완료
                    Log.d("TrainStart", "훈련 시작 조건 확인중")
                } else {
                    Log.e("TrainStart", "훈련 시작 준비 요청 실패")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("TrainStart", "서버 요청 실패", t)
            }
        })
    }

    // 훈련 상태 주기적으로 확인
    private fun checkTrainStartStatus() {
        statusHandler = Handler(Looper.getMainLooper())
        val apiService = RetrofitClient.getApiService()
        statusRunnable = object : Runnable {
            override fun run() {
                apiService.checkTrainStatus().enqueue(object : Callback<Map<String, String>> {
                    override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                        if (response.isSuccessful) {
                            val message = response.body()?.get("message")
                            Log.d("TrainStart", message.toString())
                            if (message == "훈련이 시작되었습니다.") {
                                // 훈련 시작 메시지를 받으면 TrainingFragment로 이동
                                stopPolling() // 폴링 중지
                                startTraining()
                            }
                        }
                    }

                    override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                        Log.e("TrainStart", "훈련 상태 확인 실패", t)
                    }
                })
                statusHandler?.postDelayed(this, 1000)  // 1초마다 서버에 요청 보내기
            }
        }
        statusHandler?.post(statusRunnable!!)
    }


    // 훈련을 시작하는 함수
    private fun startTraining() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.frame_layout, TrainingFragment())
        transaction.addToBackStack(null)
        transaction.commit()
    }

    // 폴링 중지
    private fun stopPolling() {
        statusHandler?.removeCallbacks(statusRunnable!!)
        statusHandler = null
        statusRunnable = null
    }
}