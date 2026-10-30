package com.goldrate.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import kotlin.math.max


/**
 * Draws the rate history as a smoothed line chart with a gradient area fill.
 *
 * The chart is rendered in the app process and handed to the launcher as a bitmap. Android 17
 * (API 37) caps the total bitmap payload of a RemoteViews update at `6 * displayWidth *
 * displayHeight` bytes, so the size is clamped against that budget before drawing.
 *
 * The bitmap carries **no text**. A launcher reports the widget's minimum size, not the size it
 * actually gave the widget, so this drawing is always stretched to fit the real area - and text
 * drawn into the bitmap would be stretched with it. The axis labels and the day labels are
 * therefore laid out as regular views at a fixed size, and only the curve is painted here.
 */
object RateChart {

    /**
     * The maximum byte budget for a single RemoteViews bitmap.
     *
     * `system_server` computes the overall cap as `6 * width * height` of the boot-time display.
     * Half of it is left free for any other bitmap the update may carry.
     */
    fun memoryCapBytes(context: Context): Long {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toLong().coerceAtLeast(1L)
        val height = metrics.heightPixels.toLong().coerceAtLeast(1L)
        return 6L * width * height
    }

    /**
     * Renders the chart.
     *
     * @param values the series, oldest first. A `null` is a day the publisher did not publish:
     *   the column keeps its place on the time axis but carries no point. Every point sits at
     *   the centre of an equal column, which is where the layout puts the day labels, so the
     *   two stay aligned at any scale.
     */
    fun render(
        context: Context,
        values: List<Double?>,
        widthPx: Int,
        heightPx: Int,
        lineColor: Int,
        fillColor: Int,
        gridColor: Int,
    ): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)

        // Never exceed the RemoteViews bitmap budget.
        val budget = memoryCapBytes(context) / 2
        var w = width
        var h = height
        while (w.toLong() * h.toLong() * 4L > budget && (w > 1 || h > 1)) {
            if (w >= h) w /= 2 else h /= 2
            w = w.coerceAtLeast(1)
            h = h.coerceAtLeast(1)
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = value * density

        // The value labels and the day labels live in the layout, so the drawing needs no room
        // for them - only a little for the ticks under the baseline.
        val bottomGutter = dp(4f)
        val plotLeft = 0f
        val plotRight = w.toFloat()
        val plotTop = 0f
        val plotBottom = h - bottomGutter

        val plotWidth = max(1f, plotRight - plotLeft)
        val plotHeight = max(1f, plotBottom - plotTop)

        // Gridlines first, so the series sits on top of them.
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
            color = gridColor
        }
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = plotTop + plotHeight * i / gridCount
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
        }

        if (values.isEmpty()) {
            canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, gridPaint)
            return bitmap
        }

        // The days the publisher did not publish keep their column but carry no point.
        val known = values.filterNotNull()
        if (known.isEmpty()) {
            canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, gridPaint)
            return bitmap
        }

        // Scale the series into the plot area, with a little headroom on both ends.
        val minValue = known.min()
        val maxValue = known.max()
        val span = (maxValue - minValue).takeIf { it > 0.0 }
            ?: (maxValue * 0.02).takeIf { it > 0.0 }
            ?: 1.0
        val low = minValue - span * 0.18
        val high = maxValue + span * 0.18

        val points = values.mapIndexedNotNull { index, value ->
            if (value == null || value.isNaN()) return@mapIndexedNotNull null
            // Every point sits at the centre of an equal column, which is exactly where the
            // layout puts the day labels.
            val x = plotLeft + plotWidth * (index + 0.5f) / values.size
            val ratio = ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)
            val y = plotBottom - ratio * plotHeight
            PointF(x, y)
        }

        // Area fill under the curve.
        if (points.size >= 2) {
            val area = Path()
            area.addPath(smoothPath(points))
            area.lineTo(points.last().x, plotBottom)
            area.lineTo(points.first().x, plotBottom)
            area.close()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f,
                    plotTop,
                    0f,
                    plotBottom,
                    fillColor,
                    fillColor and 0x00FFFFFF,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawPath(area, fillPaint)
        }

        // The line itself.
        if (points.size >= 2) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = lineColor
            }
            canvas.drawPath(smoothPath(points), linePaint)
        }

        // Marker for the latest value, and for a single-point series.
        val last = points.last()
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
        canvas.drawCircle(last.x, last.y, dp(3f), dotPaint)
        val dotInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(last.x, last.y, dp(1.3f), dotInner)

        // The baseline the points sit on, with a tick under every point. The ticks mark the
        // days; the labels for them are regular views in the layout.
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, gridPaint)
        for (point in points) {
            canvas.drawLine(
                point.x,
                plotBottom,
                point.x,
                plotBottom + dp(3f),
                gridPaint,
            )
        }

        return bitmap
    }

    /** Catmull-Rom smoothing expressed as cubic beziers, for the soft curve in the mock-up. */
    private fun smoothPath(points: List<PointF>): Path {
        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return path
        }
        for (i in 0 until points.size - 1) {
            val p0 = points[if (i == 0) 0 else i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]
            val c1 = PointF(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = PointF(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        return path
    }
}
