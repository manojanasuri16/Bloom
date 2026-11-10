package com.bloom.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bloom.app.R

// ---- shared pieces ------------------------------------------------------------------------

private const val MAIN_ACTIVITY = "com.bloom.app.MainActivity"

/** Plain launch of the app (referenced by name so this file never depends on MainActivity). */
private fun mainIntent(context: Context) =
    Intent().setComponent(ComponentName(context.packageName, MAIN_ACTIVITY))

private fun openApp(context: Context): Action = actionStartActivity(mainIntent(context))

/** Launch straight into the log screen via the "open_log" extra. */
private fun openLog(context: Context): Action =
    actionStartActivity(mainIntent(context).putExtra("open_log", true))

private val Ink = ColorProvider(BloomColors.inkLight, BloomColors.inkDark)
private val Muted = ColorProvider(BloomColors.mutedLight, BloomColors.mutedDark)
private val White = androidx.glance.unit.ColorProvider(Color.White)

private fun ts(size: Int, color: androidx.glance.unit.ColorProvider = Ink, bold: Boolean = false, center: Boolean = false) =
    TextStyle(
        color = color, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )

/** Pre-rendered bitmaps for one update pass (built outside composition, shared by all sizes). */
private class Art(val ring: Bitmap, val strip7: Bitmap?, val strip14: Bitmap?)

private fun buildArt(context: Context, s: WidgetState): Art {
    val pal = BitmapPalette.of(context)
    return when (s) {
        is WidgetState.Data -> Art(
            WidgetDraw.ring(pal, s.cycleDay, s.cycleLength, s.phase),
            WidgetDraw.strip(pal, s.week, dates = false, legend = null),
            WidgetDraw.strip(
                pal, s.twoWeeks, dates = true,
                legend = Triple(
                    context.getString(R.string.widget_legend_period),
                    context.getString(R.string.widget_legend_predicted),
                    context.getString(R.string.widget_legend_fertile),
                ),
            ),
        )
        WidgetState.Discreet -> Art(WidgetDraw.discreetRing(pal), null, null)
        // Empty state: an unfilled ring (no cycle day).
        WidgetState.Empty -> Art(WidgetDraw.ring(pal, null, 28, null), null, null)
    }
}

@Composable
private fun Frame(context: Context, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_bg))
            .clickable(openApp(context))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RingImage(art: Art, modifier: GlanceModifier) = Image(
    provider = ImageProvider(art.ring),
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier = modifier,
)

/** Centered ring + one caption; used by every widget for Discreet / Empty / tiny sizes. */
@Composable
private fun SimpleState(context: Context, s: WidgetState, art: Art, compact: Boolean) {
    val (title, sub) = when (s) {
        WidgetState.Discreet -> context.getString(R.string.widget_app_name) to
            if (compact) null else context.getString(R.string.widget_discreet_hint)
        else -> context.getString(R.string.widget_get_started) to null
    }
    val horizontal = !compact && LocalSize.current.width > 200.dp && LocalSize.current.height < 200.dp
    if (horizontal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingImage(art, GlanceModifier.size(64.dp))
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Text(title, style = ts(15, bold = true))
                if (sub != null) Text(sub, style = ts(12, Muted))
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RingImage(art, GlanceModifier.defaultWeight().fillMaxWidth())
            Spacer(GlanceModifier.height(4.dp))
            Text(title, style = ts(if (compact) 11 else 13, bold = true, center = true))
            if (sub != null) Text(sub, style = ts(11, Muted, center = true))
        }
    }
}

// ---- Small (2x2) --------------------------------------------------------------------------

@Composable
private fun SmallBody(context: Context, s: WidgetState, art: Art) {
    val tiny = LocalSize.current.height < 90.dp
    Frame(context) {
        if (s !is WidgetState.Data) { SimpleState(context, s, art, compact = true); return@Frame }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.fillMaxSize()) {
            RingImage(art, GlanceModifier.defaultWeight().fillMaxWidth())
            if (!tiny) {
                s.phase?.let { Text(it.label, style = ts(10, Muted, center = true), maxLines = 1) }
                Text(s.headline, style = ts(12, bold = true, center = true), maxLines = 2)
            }
        }
    }
}

class BloomSmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(57.dp, 57.dp), DpSize(110.dp, 110.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.load(context); val art = buildArt(context, s)
        provideContent { SmallBody(context, s, art) }
    }
}
class BloomSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloomSmallWidget()
}

// ---- Medium (4x2) -------------------------------------------------------------------------

@Composable
private fun MediumBody(context: Context, s: WidgetState, art: Art) {
    val size = LocalSize.current
    if (size.width < 180.dp) { SmallBody(context, s, art); return }
    Frame(context) {
        if (s !is WidgetState.Data) { SimpleState(context, s, art, compact = false); return@Frame }
        val showStrip = size.height >= 100.dp && art.strip7 != null
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxSize()) {
            RingImage(art, GlanceModifier.size(if (size.height >= 100.dp) 76.dp else 60.dp))
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(s.headline, style = ts(15, bold = true), maxLines = 1)
                s.window?.let { Text(it, style = ts(12), maxLines = 1) }
                val phaseLine = s.phaseNote
                    ?: s.phase?.let { context.getString(R.string.widget_phase_line, it.label) }
                phaseLine?.let { Text(it, style = ts(11, Muted), maxLines = 1) }
                if (showStrip) {
                    Spacer(GlanceModifier.height(6.dp))
                    Image(
                        provider = ImageProvider(art.strip7!!),
                        contentDescription = context.getString(R.string.widget_cd_days),
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.fillMaxWidth().height(34.dp),
                    )
                }
            }
        }
    }
}

class BloomMediumWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 57.dp), DpSize(180.dp, 57.dp), DpSize(250.dp, 110.dp))
    )
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.load(context); val art = buildArt(context, s)
        provideContent { MediumBody(context, s, art) }
    }
}
class BloomMediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloomMediumWidget()
}

// ---- Large (4x4) --------------------------------------------------------------------------

@Composable
private fun LargeBody(context: Context, s: WidgetState, art: Art) {
    val size = LocalSize.current
    if (size.height < 200.dp) { MediumBody(context, s, art); return }
    Frame(context) {
        if (s !is WidgetState.Data) { SimpleState(context, s, art, compact = false); return@Frame }
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingImage(art, GlanceModifier.size(72.dp))
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(s.headline, style = ts(17, bold = true), maxLines = 1)
                    s.phase?.let { Text(context.getString(R.string.widget_phase_line, it.label), style = ts(12, Muted), maxLines = 1) }
                    s.phaseNote?.let { Text(it, style = ts(12, Muted), maxLines = 1) }
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            art.strip14?.let {
                Image(
                    provider = ImageProvider(it),
                    contentDescription = context.getString(R.string.widget_cd_days),
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            s.nextPeriod?.let { Text(it, style = ts(12, bold = true), maxLines = 1) }
            s.nextOvulation?.let { Text(it, style = ts(12, Muted), maxLines = 1) }
            Spacer(GlanceModifier.height(8.dp))
            // Pill button -> opens the log screen. Background is a drawable so it is round on every API level.
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.widget_pill))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clickable(openLog(context)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(context.getString(R.string.widget_log_today), style = ts(13, White, bold = true))
                }
            }
        }
    }
}

class BloomLargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp))
    )
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.load(context); val art = buildArt(context, s)
        provideContent { LargeBody(context, s, art) }
    }
}
class BloomLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloomLargeWidget()
}
