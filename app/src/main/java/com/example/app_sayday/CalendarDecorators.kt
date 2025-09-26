package com.sophiegold.app_sayday


import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

class TodayDecorator(
    private val context: Context,
    private val today: CalendarDay
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return day == today
    }

    override fun decorate(view: DayViewFacade) {
        view.setBackgroundDrawable(
            ContextCompat.getDrawable(context, R.drawable.today_background)
        )
        view.addSpan(android.text.style.ForegroundColorSpan(
            ContextCompat.getColor(context, android.R.color.black)
        ))
    }
}

class RecordingDotDecorator(
    private val context: Context,
    private val dates: Collection<CalendarDay>
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return dates.contains(day)
    }

    override fun decorate(view: DayViewFacade) {
        view.addSpan(DotSpan(8f, ContextCompat.getColor(context, android.R.color.holo_red_dark)))
    }
}

class DotSpan(private val radius: Float, private val color: Int) :
    android.text.style.LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        val oldColor = paint.color
        paint.color = color

        val centerX = (left + right) / 2f
        val centerY = bottom + radius + 4f

        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.color = oldColor
    }
}