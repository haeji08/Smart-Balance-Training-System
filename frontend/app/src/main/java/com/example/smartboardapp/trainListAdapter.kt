package com.example.smartboardapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartboardapp.data.Train
import java.math.BigInteger

class trainListAdapter(val trainList : ArrayList<Train>, private val onItemClick: (BigInteger) -> Unit) : RecyclerView.Adapter<trainListAdapter.CustomViewHolder>(){

    // onCreate 비슷한 역할, xml을 붙여줌
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): trainListAdapter.CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return CustomViewHolder(view)
    }

    // view binding
    override fun onBindViewHolder(holder: trainListAdapter.CustomViewHolder, position: Int) {
        holder.date.text = trainList.get(position).date
        holder.startTime.text = trainList.get(position).startTime
        holder.endTime.text = trainList.get(position).endTime

        // 아이템 클릭 이벤트 설정
        holder.itemView.setOnClickListener {
            onItemClick(trainList[position].id)
        }
    }

    // list 개수
    override fun getItemCount(): Int {
        return trainList.size
    }

    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val date = itemView.findViewById<TextView>(R.id.tv_date)
        val startTime = itemView.findViewById<TextView>(R.id.tv_start_time)
        val endTime = itemView.findViewById<TextView>(R.id.tv_end_time)
    }
}