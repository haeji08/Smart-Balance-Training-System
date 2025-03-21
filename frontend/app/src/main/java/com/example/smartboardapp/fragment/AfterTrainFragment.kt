package com.example.smartboardapp.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.load
import com.example.smartboardapp.ApiService
import com.example.smartboardapp.R
import com.example.smartboardapp.RetrofitClient
import com.example.smartboardapp.data.TrainDetails
import com.example.smartboardapp.data.TrainingStatusResponse
import com.example.smartboardapp.databinding.FragmentAfterTrainBinding
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AfterTrainFragment : Fragment() {

    private lateinit var binding: FragmentAfterTrainBinding
    private var trainId: String = ""
    private var isPolling = false
    private var copPatternUrl : String = ""
    private var horizontalBalanceRatioUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 훈련 시작 시간과 종료 시간을 받음 (Bundle로 전달받음)
        arguments?.let {
            trainId = it.getString("trainId", "")
        }

        Log.d("훈련 후", trainId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAfterTrainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 훈련 ID로 상태 확인
        if (trainId.isNotEmpty()) {
            isPolling = true
            checkTrainingStatus()
        }

        // 이미지 버튼 클릭 이벤트
        binding.ibUpdateRequest.setOnClickListener {
            navigateToSelfFeedbackFragment()
        }
    }

    private fun checkTrainingStatus() {
        if (!isPolling) return

        showLoading(true) // 로딩 화면 표시

        val apiService = RetrofitClient.getApiService()
        val call = apiService.getTrainingStatus(trainId.toBigInteger())
        call.enqueue(object : Callback<TrainingStatusResponse> {
            override fun onResponse(call: Call<TrainingStatusResponse>, response: Response<TrainingStatusResponse>) {
                if (response.isSuccessful) {
                    val isGraphReady = response.body()?.isGraphReady ?: false
                    if (isGraphReady) {
                        showLoading(false) // 로딩 화면 숨기기
                        // 작업이 완료된 경우 데이터를 가져오기
                        fetchTrainDetails(trainId.toBigInteger())
                    } else {
                        // 작업이 완료되지 않은 경우 일정 시간 후 다시 요청
                        Handler(Looper.getMainLooper()).postDelayed({
                            checkTrainingStatus()
                        }, 2000) // 2초 후 다시 호출
                    }
                } else {
                    Log.e("AfterTrainFragment", "Failed to check status: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<TrainingStatusResponse>, t: Throwable) {
                Log.e("AfterTrainFragment", "API call failed", t)
            }
        })
    }

    private fun fetchTrainDetails(id: BigInteger) {
        val apiService: ApiService = RetrofitClient.getApiService()
        val call = apiService.getTrainDetails(id)

        call.enqueue(object : Callback<TrainDetails> {
            override fun onResponse(call: Call<TrainDetails>, response: Response<TrainDetails>) {
                if (response.isSuccessful) {
                    val trainDetails = response.body()
                    Log.d("AfterTrainFragment", trainDetails.toString())
                    trainDetails?.let {
                        updateUI(it)
                    }
                } else {
                    Log.e("AfterTrainFragment", "Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<TrainDetails>, t: Throwable) {
                Log.e("AfterTrainFragment", "API Call Failed", t)
            }
        })
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.tvLoading.visibility = View.VISIBLE
            binding.scrollView.visibility = View.GONE // 다른 UI를 숨김
        } else {
            binding.tvLoading.visibility = View.GONE
            binding.scrollView.visibility = View.VISIBLE
        }
    }

    private fun updateUI(details: TrainDetails) {
        binding.tvTrainTitle.text = details.date
        binding.tvTrainTime.text = "${details.startTime} ~ ${details.endTime}"
        val totalMinutes = calculateDurationInMinutes(details.startTime, details.endTime)
        binding.tvTotalTime.text = totalMinutes
        val balanceTimeText = formatTimeToMinutesAndSeconds(details.totalBalanceSustainTime ?: 0)
        binding.tvTotalBalanceTime.text = balanceTimeText
        binding.tvSelfFeedback.text = details.selfFeedback

        // Coil로 이미지 로드
        copPatternUrl = details.copPattern.toString()
        copPatternUrl.let { imageUrl ->
            binding.ivCopPattern.load(imageUrl)
        }

        horizontalBalanceRatioUrl = details.horizontalBalanceRatio.toString()
        horizontalBalanceRatioUrl.let { imageUrl ->
            binding.ivHorizontalBalanceRatio.load(imageUrl)
        }
    }

    private fun calculateDurationInMinutes(startTime: String, endTime: String): String {
        try {
            // "HH:mm" 형식의 시간 문자열을 파싱
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val startDate = timeFormat.parse(startTime)
            val endDate = timeFormat.parse(endTime)

            // 시간 차이 계산 (밀리초 단위)
            val differenceInMillis = endDate.time - startDate.time
            Log.d("AfterTrainFragment", differenceInMillis.toString())

            val minutes = (differenceInMillis / 1000 % 3600) / 60
            val seconds = (differenceInMillis / 1000) % 60

            // 밀리초를 분 단위로 변환
            return if (minutes.toInt() == 0) {
                "${seconds}초"
            } else {
                "${minutes}분 ${seconds}초"
            }
        } catch (e: Exception) {
            Log.e("AfterTrainFragment", "시간 계산 중 오류 발생", e)
        }
        return "" // 오류 시 "" 반환
    }

    private fun formatTimeToMinutesAndSeconds(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return if (minutes == 0) {
            "${seconds}초"
        } else {
            "${minutes}분 ${seconds}초"
        }
    }

    private fun navigateToSelfFeedbackFragment() {
        // Fragment 간 데이터 전달
        val bundle = Bundle().apply {
            putString("trainId", trainId)
            putString("trainTitle", binding.tvTrainTitle.text.toString())
            putString("trainTime", binding.tvTrainTime.text.toString())
            putString("totalTime", binding.tvTotalTime.text.toString())
            putString("totalBalanceTime", binding.tvTotalBalanceTime.text.toString())
            putString("copPattern", copPatternUrl)
            putString("horizontalBalanceRatio", horizontalBalanceRatioUrl)
            putString("selfFeedback", binding.tvSelfFeedback.text.toString())
        }
        val fragment = SelfFeedbackFragment().apply {
            arguments = bundle
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment) // `fragment_container`는 현재 Fragment가 포함된 컨테이너 ID
            .addToBackStack(null) // 뒤로 가기 지원
            .commit()
    }

}