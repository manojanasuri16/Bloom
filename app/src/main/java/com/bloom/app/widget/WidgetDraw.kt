package com.bloom.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.bloom.app.domain.Phase
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Palette shared by bitmaps and Glance colours. Hex values come from the Bloom design. */
object BloomColors {
    const val ROSE = 0xFFD9788F.toInt()
    const val LAVENDER = 0xFFB9A6E3.toInt()
    const val DEEP_LAVENDER = 0xFF8E73D1.toInt()
    const val BLUSH = 0xFFE9A8B8.toInt()

    val inkLight = Color(0xFF2B2230); val inkDark = Color(0xFFF5EEF3)
    val mutedLight = Color(0xFF8A7F90); val mutedDark = Color(0xFFA89DAE)

    fun phase(p: Phase?): Int = when (p) {
        Phase.MENSTRUAL -> ROSE
        Phase.FOLLICULAR -> LAVENDER
        Phase.FERTILE, Phase.OVULATION -> DEEP_LAVENDER
        Phase.LUTEAL -> BLUSH
        null -> LAVENDER
    }
}

/** Resolved-at-render-time colours for bitmaps (bitmaps cannot follow the theme by themselves). */
class BitmapPalette(val night: Boolean) {
    val ink = (if (night) BloomColors.inkDark else BloomColors.inkLight).toArgbInt()
    val muted = (if (night) BloomColors.mutedDark else BloomColors.mutedLight).toArgbInt()
    val surface = if (night) 0xFF251D2C.toInt() else 0xFFFFFFFF.toInt()

    private fun Color.toArgbInt(): Int {
        fun c(f: Float) = (f * 255f + 0.5f).toInt()
        return (c(alpha) shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
    }

    companion object {
        fun of(context: Context) = BitmapPalette(
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        )
    }
}

private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

object WidgetDraw {
    private const val RING_PX = 300  // 300x300 ARGB_8888 = ~350 KB

    /** Cycle progress ring with the cycle-day number in the middle. */
    fun ring(pal: BitmapPalette, cycleDay: Int?, cycleLength: Int, phase: Phase?): Bitmap {
        val size = RING_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.10f
        val inset = stroke / 2f + 4f
        val rect = RectF(inset, inset, size - inset, size - inset)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        p.color = withAlpha(pal.muted, 70)
        c.drawArc(rect, 0f, 360f, false, p)
        if (cycleDay != null && cycleLength > 0) {
            val progress = (cycleDay.toFloat() / cycleLength).coerceIn(0.02f, 1f)
            // Small angular gap at the start/end so rounded caps never merge into a closed ring.
            val sweep = (360f * progress).coerceAtMost(350f)
            p.color = BloomColors.phase(phase)
            c.drawArc(rect, -90f + 5f, sweep - 5f, false, p)
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.ink; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = size * if (cycleDay >= 100) 0.28f else 0.38f
            }
            val y = size / 2f - (t.descent() + t.ascent()) / 2f
            c.drawText(cycleDay.toString(), size / 2f, y, t)
        }
        return bmp
    }

    /** Neutral ring of dots for discreet mode: carries no information at all. */
    fun discreetRing(pal: BitmapPalette): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val r = size / 2f - 20f
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            p.color = if (i % 2 == 0) BloomColors.LAVENDER else BloomColors.BLUSH
            c.drawCircle(size / 2f + r * cos(a).toFloat(), size / 2f + r * sin(a).toFloat(), 14f, p)
        }
        p.color = withAlpha(pal.muted, 90)
        c.drawCircle(size / 2f, size / 2f, 14f, p)
        return bmp
    }

    /**
     * Dot strip. [cells] are laid out in rows of 7. When [dates] is true each dot carries its day
     * of month (large widget); otherwise a weekday initial sits above a plain dot (medium).
     */
    fun strip(pal: BitmapPalette, cells: List<DayCell>, dates: Boolean, legend: Triple<String, String, String>?): Bitmap {
        val w = 420
        val colW = w / 7f
        val rowH = if (dates) 86f else 76f
        val rows = (cells.size + 6) / 7
        val legendH = if (legend != null) 40f else 0f
        val h = (rows * rowH + legendH).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val radius = min(colW / 2f - 8f, if (dates) 27f else 22f)

        cells.forEachIndexed { i, cell ->
            val cx = (i % 7) * colW + colW / 2f
            var top = (i / 7) * rowH
            if (!dates) {
                // Weekday initial above the dot.
                txt.color = pal.muted; txt.textSize = 22f
                c.drawText(cell.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), cx, top + 22f, txt)
                top += 28f
            }
            val cy = top + radius + 6f
            drawDot(c, fill, line, pal, cell, cx, cy, radius)
            if (dates) {
                txt.textSize = 26f
                txt.color = when (cell.kind) {
                    DayKind.PERIOD, DayKind.OVULATION -> 0xFFFFFFFF.toInt()
                    DayKind.FERTILE -> 0xFF2B2230.toInt()
                    else -> if (cell.isToday) pal.ink else pal.muted
                }
                c.drawText(cell.date.dayOfMonth.toString(), cx, cy - (txt.descent() + txt.ascent()) / 2f, txt)
            }
        }

        if (legend != null) {
            val y = h - 18f
            txt.textSize = 22f; txt.color = pal.muted; txt.textAlign = Paint.Align.LEFT
            var x = 8f
            listOf(
                Triple(BloomColors.ROSE, false, legend.first),
                Triple(BloomColors.ROSE, true, legend.second),
                Triple(BloomColors.LAVENDER, false, legend.third),
            ).forEach { (col, outlined, label) ->
                if (outlined) {
                    fill.color = withAlpha(col, 70); c.drawCircle(x + 9f, y - 7f, 9f, fill)
                    line.color = col; line.strokeWidth = 3f; c.drawCircle(x + 9f, y - 7f, 9f, line)
                } else {
                    fill.color = col; c.drawCircle(x + 9f, y - 7f, 9f, fill)
                }
                c.drawText(label, x + 26f, y, txt)
                x += 26f + txt.measureText(label) + 26f
            }
        }
        return bmp
    }

    private fun drawDot(
        c: Canvas, fill: Paint, line: Paint, pal: BitmapPalette, cell: DayCell,
        cx: Float, cy: Float, r: Float,
    ) {
        when (cell.kind) {
            DayKind.PERIOD -> { fill.color = BloomColors.ROSE; c.drawCircle(cx, cy, r, fill) }
            DayKind.PREDICTED -> {
                fill.color = withAlpha(BloomColors.ROSE, 70); c.drawCircle(cx, cy, r, fill)
                line.color = BloomColors.ROSE; line.strokeWidth = 4f; c.drawCircle(cx, cy, r - 2f, line)
            }
            DayKind.FERTILE -> { fill.color = BloomColors.LAVENDER; c.drawCircle(cx, cy, r, fill) }
            DayKind.OVULATION -> { fill.color = BloomColors.DEEP_LAVENDER; c.drawCircle(cx, cy, r, fill) }
            DayKind.NONE -> { fill.color = withAlpha(pal.muted, 50); c.drawCircle(cx, cy, r * 0.8f, fill) }
        }
        if (cell.isToday) {
            // Highlight: ink-coloured halo around today.
            line.color = pal.ink; line.strokeWidth = 4f
            c.drawCircle(cx, cy, r + 6f, line)
        }
    }
}
