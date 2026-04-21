package com.sova.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class AnswerOverlayView(context: Context) : View(context) {

    private var box: Rect? = null
    private var label: String = ""
    private val orderMarks = mutableListOf<Pair<Rect, Int>>()

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(255, 40, 40)
        strokeWidth = 6f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(120, 255, 40, 40)
        strokeWidth = 14f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 0, 0, 0)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 136)
        textSize = 36f
        isFakeBoldText = true
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }
    private val numBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 170, 85)
    }

    fun show(rect: Rect, text: String) {
        box = rect
        label = text
        orderMarks.clear()
        invalidate()
    }

    fun showOrder(marks: List<Pair<Rect, Int>>) {
        box = null
        label = ""
        orderMarks.clear()
        orderMarks.addAll(marks)
        invalidate()
    }

    fun clear() {
        box = null
        label = ""
        orderMarks.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        box?.let { r ->
            canvas.drawRect(r, glowPaint)
            canvas.drawRect(r, boxPaint)
            if (label.isNotEmpty()) {
                val pad = 10f
                val tw = textPaint.measureText(label)
                val th = textPaint.textSize
                val bx = r.left.toFloat()
                val by = (r.top - th - pad * 2).coerceAtLeast(0f)
                canvas.drawRect(bx, by, bx + tw + pad * 2, by + th + pad * 2, bgPaint)
                canvas.drawText(label, bx + pad, by + th + pad / 2, textPaint)
            }
        }

        for ((r, n) in orderMarks) {
            val cx = (r.left - 40).coerceAtLeast(10).toFloat()
            val cy = r.centerY().toFloat()
            canvas.drawCircle(cx, cy, 28f, numBgPaint)
            val s = n.toString()
            val w = numPaint.measureText(s)
            canvas.drawText(s, cx - w / 2, cy + 14f, numPaint)
        }
    }
}
