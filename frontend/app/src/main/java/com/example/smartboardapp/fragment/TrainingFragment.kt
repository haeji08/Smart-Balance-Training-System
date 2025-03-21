package com.example.smartboardapp.fragment

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.smartboardapp.ApiService
import com.example.smartboardapp.R
import com.example.smartboardapp.RetrofitClient
import com.example.smartboardapp.data.EndTrainDetailsRequest
import com.example.smartboardapp.data.Sensor
import com.example.smartboardapp.data.StartTimeRequest
import com.example.smartboardapp.data.StartTrainingResponse
import com.example.smartboardapp.data.SustainTimeRequest
import com.example.smartboardapp.data.TrainingResponse
import com.example.smartboardapp.databinding.FragmentTrainingBinding
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

class TrainingFragment : Fragment() {

    private lateinit var binding: FragmentTrainingBinding
    private var startTime: Long = 0
    private var sustainTime: Long = 0
    private var handler: Handler = Handler()
    private var timerRunnable: Runnable? = null
    private lateinit var pollingHandler: Handler
    private lateinit var pollingRunnable: Runnable
    private lateinit var sensorValueHandler: Handler
    private lateinit var sensorValueRunnable: Runnable
    private lateinit var scatterChart: ScatterChart
    private var trainId: Int = 0
    private var radiusStatusList: MutableList<String> = mutableListOf()
    private var isMeasuring = false
    private var unbalance_count: Int = 0
    private var startTrainingTime: Long = 0
    // MediaPlayer 객체를 전역 변수로 선언
    private var leftAlertPlayer: MediaPlayer? = null
    private var rightAlertPlayer: MediaPlayer? = null


    private var runnable: Runnable = object : Runnable {
        override fun run() {
            // 경과된 시간 계산
            val elapsedMillis = System.currentTimeMillis() - startTrainingTime
            val timeFormatted = formatTime(elapsedMillis)

            // tv_timer 텍스트를 업데이트
            binding.tvTimer.text = timeFormatted

            // 1초마다 다시 실행
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // ViewBinding을 사용하여 레이아웃을 바인딩
        binding = FragmentTrainingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scatterChart = binding.lcCopPattern
        setupChart()
        startTraining()

        // 타이머 시작
        startTrainingTime = System.currentTimeMillis() // 한국 시간 기준으로 시작 시간 기록
        handler.post(runnable) // 훈련 타이머 실행

        // 훈련 종료 로직 추가 필요 - 센서에서 5초 이상 값이 안들어오면 훈련 종료하도록 수정 필요
//        startSensorValueCheck()

        // 훈련 종료 버튼을 누르면 훈련 종료
        binding.btnTrainFinish.setOnClickListener{
            endTraining()
        }
    }
    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnable) // 훈련 타이머 정지
        stopPolling()
        // MediaPlayer 해제
        // MediaPlayer 해제
        leftAlertPlayer?.release()
        leftAlertPlayer = null
        rightAlertPlayer?.release()
        rightAlertPlayer = null
    }

    private fun setupChart() {
        // X축 설정
        val xAxis: XAxis = scatterChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.axisMinimum = -1f // -1부터
        xAxis.axisMaximum = 1f // 1까지
        xAxis.granularity = 1f // 라벨 간격
        xAxis.setDrawGridLines(true)

        // Y축 설정
        val yAxisLeft: YAxis = scatterChart.axisLeft
        yAxisLeft.axisMinimum = -1f
        yAxisLeft.axisMaximum = 1f
        yAxisLeft.granularity = 1f
        yAxisLeft.setDrawGridLines(true)

        scatterChart.axisRight.isEnabled = false // 우측 Y축 비활성화
        scatterChart.description.isEnabled = false // 설명 비활성화
        scatterChart.legend.isEnabled = false // 범례 비활성화

        scatterChart.setTouchEnabled(false) // 터치 비활성화
        scatterChart.invalidate() // 초기화
    }


    private fun startTraining() {
        val apiService = RetrofitClient.getApiService()

        // 훈련 시작 시간 기록 (한국 시간 기준)
        val trainStartTime = System.currentTimeMillis()
        val trainStartTimeFormatted = formatTimeToKST(trainStartTime)
        val startTimeRequest = StartTimeRequest(trainStartTimeFormatted)

        val call = apiService.startTraining(startTimeRequest)
        call.enqueue(object : Callback<StartTrainingResponse> {
            override fun onResponse(call: Call<StartTrainingResponse>, response: Response<StartTrainingResponse>) {
                if (response.isSuccessful) {
                    trainId = response.body()?.trainId!!
                    startPolling(trainId.toBigInteger())
                    Log.d("TrainFragment", "Training started with ID: $trainId")
                } else {
                    Log.e("TrainFragment", "Failed to start training")
                }
            }

            override fun onFailure(call: Call<StartTrainingResponse>, t: Throwable) {
                Log.e("TrainFragment", "Error: ${t.message}")
            }
        })
    }

    private fun startPolling(id : BigInteger) {
        val apiService = RetrofitClient.getApiService()
        pollingHandler = Handler()
        pollingRunnable = object : Runnable {
            override fun run() {
                val call = apiService.getTrainingData(id)
                call.enqueue(object : Callback<TrainingResponse> {
                    override fun onResponse(call: Call<TrainingResponse>, response: Response<TrainingResponse>) {
                        if (response.isSuccessful) {
                            val sensor = response.body()?.sensor
                            if (sensor != null) {
                                compareSensorValues(sensor)
                            }
                            // 체중 이동 패턴 실시간 그래프
                            val copPattern = response.body()?.copPattern
                            Log.d("Polling", copPattern.toString())
                            if (copPattern != null) {
                                updateChart(copPattern.x_cop, copPattern.y_cop)
                            }
                            // 균형 유지 시간 실시간 표시
                            val isInside = response.body()?.isInside ?: "NO"
                            calculateSustainTime(id, isInside)
                        }
                    }
                    override fun onFailure(call: Call<TrainingResponse>, t: Throwable) {
                        Log.e("Polling", "Error: ${t.message}")
                    }
                })
                // 0.5초마다 반복
                pollingHandler.postDelayed(this, 1000)
            }
        }
        pollingHandler.post(pollingRunnable)
    }

    private fun updateChart(xCop: Float, yCop: Float) {
        val entries = listOf(Entry(xCop, yCop)) // 새로운 데이터

        val dataSet = ScatterDataSet(entries, "Center of Pressure").apply {
            color = Color.RED
            setDrawValues(false) // 값 표시 비활성화
            setScatterShape(ScatterChart.ScatterShape.CIRCLE) // 점 모양
            scatterShapeSize = 23f // 점 크기
        }

        scatterChart.data = ScatterData(dataSet) // 새 데이터 적용
        scatterChart.invalidate() // 그래프 갱신
    }

    private fun calculateSustainTime(id: BigInteger, isInside: String) {
        // 최근 3개의 상태 업데이트
        radiusStatusList.add(isInside)
        if (radiusStatusList.size > 3) {
            radiusStatusList.removeAt(0)
        }
        Log.d("TrainingFragment", radiusStatusList.toString())

        // "YES" 상태가 3번 연속되면 측정을 시작
        if (!isMeasuring && radiusStatusList.size == 3 && radiusStatusList.all { it == "YES" }) {
            if (startTime == 0L) {
                // 처음 타이머 시작 시점만 기록
                isMeasuring = true
                startTime = System.currentTimeMillis()
                startTimer()
                Log.d("TrainingFragment", "Started measuring sustain time at: $startTime")
            }
        }
        // 상태가 "NO"로 변경되면 측정 종료
        else if (isMeasuring && isInside == "NO") {
            sustainTime = ((System.currentTimeMillis() - startTime) / 1000)
            Log.d("TrainingFragment", "Sustain time calculated: $sustainTime seconds")
            sendSustainTimeToServer(id, sustainTime)
            unbalance_count++
            resetTimer()
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                // 현재 시간을 계산하여 TextView 업데이트
                val currentTime = (System.currentTimeMillis() - startTime)
                val formatMSTime = formatMSTime(currentTime)
                binding.tvSustainTime.text = formatMSTime
                // 1초 후 다시 실행
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun resetTimer() {
        // 타이머 상태와 startTime 초기화
        isMeasuring = false
        sustainTime = 0
        startTime = 0
        // 타이머 멈추기
        timerRunnable?.let { handler.removeCallbacks(it) }
        binding.tvSustainTime.text = "00 : 00" // 초기화 시 0으로 표시
    }

    private fun sendSustainTimeToServer(id: BigInteger, time: Long) {
        val apiService: ApiService = RetrofitClient.getApiService()
        val sustainTimeRequest = SustainTimeRequest(id, time)
        val call = apiService.sendSustainTime(sustainTimeRequest)

        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("TrainingFragment", "Success")
                } else {
                    Log.e("TrainingFragment", "Error: ${response.message()} ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("TrainingFragment", "API Call Failed", t)
            }
        })
    }

    // 센서 값을 주기적으로 체크하여 훈련 종료 상태 확인
//    private fun startSensorValueCheck() {
//        sensorValueHandler = Handler()
//        sensorValueRunnable = object : Runnable {
//            override fun run() {
//                val apiService = RetrofitClient.getApiService()
//                val call = apiService.checkTrainStatus()
//                call.enqueue(object : Callback<Map<String, String>> {
//                    override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
//                        if (response.isSuccessful) {
//                            val message = response.body()?.get("message")
//                            if (message == "훈련이 종료되었습니다.") {
//                                // 훈련 종료 후 AfterTrainFragment로 이동
//                                endTraining()
//                            }
//                        }
//                    }
//
//                    override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
//                        Log.e("Polling", "Error: ${t.message}")
//                    }
//                })
//                sensorValueHandler.postDelayed(this, 1000) // 1초마다 서버에 요청 보내기
//            }
//        }
//        sensorValueHandler.post(sensorValueRunnable)
//    }

    private fun compareSensorValues(sensor: Sensor) {
        val leftSum = sensor.s1 + sensor.s2 
        val rightSum = sensor.s3 + sensor.s4

        if (leftSum > rightSum) {
            // 왼쪽으로 치우침
            Log.d("Sensor", "왼쪽으로 치우쳤다")
            playSound("left")
        } else if (leftSum < rightSum) {
            // 오른쪽으로 치우침
            Log.d("Sensor", "오른쪽으로 치우쳤다")
            playSound("right")
        }
    }

    private fun playSound(direction: String) {
        // 기존 플레이어가 사용 중이면 해제
        leftAlertPlayer?.release()
        rightAlertPlayer?.release()

        if (direction == "left") {
            leftAlertPlayer = MediaPlayer.create(requireContext(), R.raw.left)
            leftAlertPlayer?.setOnCompletionListener {
                leftAlertPlayer?.release()
                leftAlertPlayer = null
            }
            leftAlertPlayer?.start()
        } else if (direction == "right") {
            rightAlertPlayer = MediaPlayer.create(requireContext(), R.raw.right)
            rightAlertPlayer?.setOnCompletionListener {
                rightAlertPlayer?.release()
                rightAlertPlayer = null
            }
            rightAlertPlayer?.start()
        }
    }

    private fun endTraining() {
        // 훈련 종료 시간 기록 (한국 시간 기준)
        val endTime = System.currentTimeMillis()
        val endTimeFormatted = formatTimeToKST(endTime)

        // 진행 중인 타이머가 있다면 강제로 종료 및 sustainTime 전송
        if (isMeasuring) {
            sustainTime = ((System.currentTimeMillis() - startTime) / 1000)
            Log.d("TrainingFragment", "Training ended. Final sustain time: $sustainTime seconds")
            sendSustainTimeToServer(trainId.toBigInteger(), sustainTime)
            resetTimer()
        }

        // 훈련 종료 시간을 서버로 전달
        sendEndTrainDetailsToServer(trainId.toBigInteger(), endTimeFormatted)

        // 일정 시간 대기 후 데이터를 가져오기
        Handler(Looper.getMainLooper()).postDelayed({
        }, 2000) // 2초 대기

        // AfterTrainFragment로 이동
        val bundle = Bundle().apply {
            putString("trainId", trainId.toString())
        }

        val afterTrainFragment = AfterTrainFragment().apply {
            arguments = bundle
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, afterTrainFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun sendEndTrainDetailsToServer(id: BigInteger, endTime: String) {
        val apiService: ApiService = RetrofitClient.getApiService()
        val endTrainDetailsRequest = EndTrainDetailsRequest(id, endTime, unbalance_count)
        val call = apiService.sendEndTrainDetails(endTrainDetailsRequest)

        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("TrainingFragment", "End Time Success")
                } else {
                    Log.e("TrainingFragment", "Error: ${response.message()} ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("TrainingFragment", "API Call Failed", t)
            }
        })
    }

    private fun stopPolling() {
        // 기존 pollingHandler 중단
        if (::pollingHandler.isInitialized) {
            pollingHandler.removeCallbacks(pollingRunnable)
        }

        // sensorValueHandler 중단
        if (::sensorValueHandler.isInitialized) {
            sensorValueHandler.removeCallbacks(sensorValueRunnable)
        }
    }

    // 한국 시간(KST)으로 포맷팅
    private fun formatTimeToKST(timeMillis: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return dateFormat.format(timeMillis)
    }

    private fun formatTime(elapsedMillis: Long): String {
        val hours = (elapsedMillis / 1000) / 3600
        val minutes = (elapsedMillis / 1000 % 3600) / 60
        val seconds = (elapsedMillis / 1000) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatMSTime(elapsedMillis: Long): String {
        val minutes = (elapsedMillis / 1000 % 3600) / 60
        val seconds = (elapsedMillis / 1000) % 60
        return String.format("%02d : %02d", minutes, seconds)
    }
}
