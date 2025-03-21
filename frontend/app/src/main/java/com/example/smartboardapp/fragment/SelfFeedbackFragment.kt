package com.example.smartboardapp.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.load
import com.example.smartboardapp.ApiService
import com.example.smartboardapp.R
import com.example.smartboardapp.RetrofitClient
import com.example.smartboardapp.data.FeedbackRequest
import com.example.smartboardapp.data.TrainDetails
import com.example.smartboardapp.databinding.FragmentAfterTrainBinding
import com.example.smartboardapp.databinding.FragmentSelfFeedbackBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger


class SelfFeedbackFragment : Fragment() {

    private lateinit var binding: FragmentSelfFeedbackBinding
    private var selfFeedback: String? = null
    private var trainId: Int? = null
    private var trainTitle: String? = null
    private var trainTime: String? = null
    private var totalTime: String? = null
    private var totalBalanceTime: String? = null
    private var copPattern: String? = null
    private var totalUnbalanceCount: String? = null
    private var horizontalBalanceRatio: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            trainId = it.getString("trainId")?.toInt()
            trainTitle = it.getString("trainTitle")
            trainTime = it.getString("trainTime")
            totalTime = it.getString("totalTime")
            totalBalanceTime = it.getString("totalBalanceTime")
            copPattern = it.getString("copPattern")
            totalUnbalanceCount = it.getString("totalUnbalanceCount")
            horizontalBalanceRatio = it.getString("horizontalBalanceRatio")
            selfFeedback = it.getString("selfFeedback")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSelfFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 전달받은 SelfFeedback 설정
        updateUI()

        // 확인 버튼 클릭 이벤트
        binding.btnUpdateCheck.setOnClickListener {
            val updatedFeedback = binding.tvSelfFeedback.text.toString()
            if (trainId != null) {
                updateFeedback(trainId!!, updatedFeedback)
                navigateToAfterTrainFragment()
            } else {
                Log.e("피드백", "Train ID가 없습니다.")
            }
        }
    }

    private fun updateUI() {
        binding.tvTrainTitle.text = trainTitle
        binding.tvTrainTime.text = trainTime
        binding.tvTotalTime.text = totalTime
        binding.tvTotalBalanceTime.text = totalBalanceTime
        copPattern.let { imageUrl ->
            binding.ivCopPattern.load(imageUrl)
        }
        horizontalBalanceRatio.let { imageUrl ->
            binding.ivHorizontalBalanceRatio.load(imageUrl)
        }
        binding.tvSelfFeedback.setText(selfFeedback)
    }

    private fun updateFeedback(id: Int, selfFeedback: String) {
        val apiService: ApiService = RetrofitClient.getApiService()
        val feedbackRequest = FeedbackRequest(id, selfFeedback)
        val call = apiService.sendFeedback(feedbackRequest)

        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("AfterTrainFragment", "Success")
                } else {
                    Log.e("AfterTrainFragment", "Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("AfterTrainFragment", "API Call Failed", t)
            }
        })
    }

    private fun navigateToAfterTrainFragment() {
        // Fragment 간 데이터 전달
        val bundle = Bundle().apply {
            putString("trainId", trainId.toString())
        }
        val fragment = AfterTrainFragment().apply {
            arguments = bundle
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment) // `fragment_container`는 현재 Fragment가 포함된 컨테이너 ID
            .addToBackStack(null) // 뒤로 가기 지원
            .commit()
    }


}