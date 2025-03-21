package com.example.smartboardapp.fragment

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartboardapp.ApiService
import com.example.smartboardapp.ItemDecoration
import com.example.smartboardapp.R
import com.example.smartboardapp.RetrofitClient
import com.example.smartboardapp.data.Train
import com.example.smartboardapp.trainListAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger
import java.util.Calendar


class TrainFragment : Fragment() {

    private lateinit var trainRecyclerView: RecyclerView
    private lateinit var trainAdapter: trainListAdapter
    private lateinit var dayButtons: List<Button>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Fragment의 레이아웃을 Inflate
        return inflater.inflate(R.layout.fragment_train, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 요일 버튼들 초기화
        dayButtons = listOf(
            view.findViewById(R.id.btn_monday),  // 월
            view.findViewById(R.id.btn_tuesday), // 화
            view.findViewById(R.id.btn_wednesday), // 수
            view.findViewById(R.id.btn_thursday), // 목
            view.findViewById(R.id.btn_friday), // 금
            view.findViewById(R.id.btn_saturday), // 토
            view.findViewById(R.id.btn_sunday)  // 일
        )

        // 오늘 요일에 해당하는 버튼 선택
        highlightToday()

        // 오늘 날짜를 기본으로 가져오기
        val todayDate = getTodayDate()
        Log.d("훈련", todayDate)
        getTrainData(todayDate)

        // 버튼 클릭 이벤트 설정
        dayButtons.forEach { button ->
            button.setOnClickListener {
                val selectedDate = getDateForButton(button)
                getTrainData(selectedDate)
                highlightButton(button)  // 클릭한 버튼 강조
            }
        }

        // RecyclerView 초기화
        trainRecyclerView = view.findViewById(R.id.rv_train)

        // 아이템 테두리 추가
        val borderHeight = 4 // 테두리 두께 (px)
        val borderColor = Color.parseColor("#E1E3E8") // 테두리 색상
        trainRecyclerView.addItemDecoration(ItemDecoration(borderHeight, borderColor))

        // 훈련 시작 버튼 초기화
        val btnTrainStart = view.findViewById<Button>(R.id.btn_train_start)

        // 클릭 리스너 설정
        btnTrainStart.setOnClickListener {
            // TrainStartFragment로 이동
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, TrainStartFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    // 오늘 날짜를 YY/MM/DD 형식으로 반환
    private fun getTodayDate(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // 월은 0부터 시작
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year/$month/$day"
    }

    // 요일 버튼에 맞는 날짜를 계산하여 YY/MM/DD 형식으로 반환
    private fun getDateForButton(button: Button): String {
        val calendar = Calendar.getInstance()
        // 요일 인덱스를 월요일(0)부터 시작하도록 조정
        val todayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 월요일(0) ~ 일요일(6)
        val selectedIndex = dayButtons.indexOf(button)

        // 클릭한 버튼이 현재 날짜와 몇 일 차이 나는지 계산
        val daysDifference = selectedIndex - todayIndex
        calendar.add(Calendar.DAY_OF_MONTH, daysDifference)

        // 연, 월, 일을 포맷팅하여 반환
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year/$month/$day"
    }


    // Retrofit을 사용하여 해당 날짜의 훈련 데이터를 가져옴
    private fun getTrainData(date: String) {
        val apiService: ApiService = RetrofitClient.getApiService()
        val call = apiService.getTrainData(date)

        call.enqueue(object : Callback<List<Train>> {
            override fun onResponse(call: Call<List<Train>>, response: Response<List<Train>>) {
                if (response.isSuccessful) {
                    val trainList = response.body() ?: emptyList()
                    Log.d("훈련", trainList.toString())
                    // RecyclerView에 데이터 설정
                    trainAdapter = trainListAdapter(ArrayList(trainList)) { trainId ->
                        // TrainAfterFragment로 이동하며 trainId를 전달
                        val bundle = Bundle().apply {
                            putString("trainId", trainId.toString())
                        }
                        val fragment = AfterTrainFragment().apply {
                            arguments = bundle
                        }
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.frame_layout, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    trainRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                    trainRecyclerView.adapter = trainAdapter
                } else {
                    // 에러 처리
                    Log.e("훈련", "Failed to load data: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<List<Train>>, t: Throwable) {
                // 네트워크 실패 처리
                Log.e("훈련", "Error: ${t.message}", t)
            }
        })
    }

    private fun highlightToday() {
        val calendar = Calendar.getInstance()
        val todayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 2 // 월요일이 0
        if (todayIndex in 0..6) {
            highlightButton(dayButtons[todayIndex])
        }
    }

    private fun highlightButton(selectedButton: Button) {
        dayButtons.forEach { button ->
            button.setBackgroundResource(R.drawable.button_border) // 기본 테두리
        }
        selectedButton.setBackgroundResource(R.drawable.button_selected_border) // 선택된 버튼 테두리
    }
}