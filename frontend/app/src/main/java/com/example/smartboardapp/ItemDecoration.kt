package com.example.smartboardapp

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ItemDecoration(
    private val borderHeight: Int,
    private val borderColor: Int
) : RecyclerView.ItemDecoration() {
    private val paint = Paint()

    init {
        paint.color = borderColor
        paint.style = Paint.Style.FILL
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        // 모든 아이템 위쪽에 여백 추가
        outRect.top = borderHeight

        // 마지막 아이템 아래쪽에 여백 추가
        val position = parent.getChildAdapterPosition(view)
        if (position == parent.adapter?.itemCount?.minus(1)) {
            outRect.bottom = borderHeight
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        val totalItemCount = parent.adapter?.itemCount ?: 0

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams

            // 위쪽 테두리
            val left = child.left
            val right = child.right
            val top = child.top - params.topMargin - borderHeight
            val bottom = child.top - params.topMargin
            c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)

            // 마지막 아이템의 아래쪽 테두리
            val position = parent.getChildAdapterPosition(child)
            if (position == totalItemCount - 1) {
                val bottomTop = child.bottom + params.bottomMargin
                val bottomBottom = bottomTop + borderHeight
                c.drawRect(left.toFloat(), bottomTop.toFloat(), right.toFloat(), bottomBottom.toFloat(), paint)
            }
        }
    }
}