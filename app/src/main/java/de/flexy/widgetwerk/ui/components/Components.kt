package de.flexy.widgetwerk.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.ShapeKind
import de.flexy.widgetwerk.data.WidgetConfig
import de.flexy.widgetwerk.data.WidgetKind
import de.flexy.widgetwerk.glass.Shapes
import de.flexy.widgetwerk.glass.WallpaperSource
import de.flexy.widgetwerk.glass.WidgetGeometry
import de.flexy.widgetwerk.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private class PreviewData(val views: RemoteViews?, val geo: WidgetGeometry, val backdrop: Bitmap?, val margin: Int, val stamp: Long)

/**
 * Echte Widget-Vorschau: dieselben RemoteViews wie auf dem Homescreen,
 * vor dem passenden Ausschnitt des Hintergrundbilds.
 */
@Composable
fun WidgetPreview(
    kind: WidgetKind,
    id: Int?,
    cfg: WidgetConfig,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 260.dp,
    backdropMarginDp: Float = 20f,
) {
    val ctx = LocalContext.current
    val changes by ConfigStore.changes.collectAsState()
    var data by remember { mutableStateOf<PreviewData?>(null) }
    LaunchedEffect(kind, id, cfg, changes) {
        data = withContext(Dispatchers.Default) {
            val geo = WidgetUpdater.previewGeometry(ctx, id, kind)
            val views = runCatching { WidgetUpdater.build(ctx, id ?: -1, kind, cfg, geo, preview = true) }.getOrNull()
            val m = (backdropMarginDp * geo.density).roundToInt()
            PreviewData(views, geo, backdrop(ctx, geo, m, cfg.page), m, System.nanoTime())
        }
    }
    val d = data
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current.density
        if (d == null) {
            Box(Modifier.fillMaxWidth().height(maxHeight / 2).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            return@BoxWithConstraints
        }
        val totalW = d.geo.widthPx + 2 * d.margin
        val totalH = d.geo.heightPx + 2 * d.margin
        val maxW = constraints.maxWidth.toFloat()
        val maxH = maxHeight.value * density
        val scale = min(1f, min(maxW / totalW, maxH / totalH))
        val boxW = (totalW * scale / density).dp
        val boxH = (totalH * scale / density).dp
        val cs = MaterialTheme.colorScheme
        Box(
            Modifier.size(boxW, boxH).clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer))
            ),
            contentAlignment = Alignment.Center,
        ) {
            d.backdrop?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            key(d.stamp) {
                AndroidView(
                    factory = { c ->
                        FrameLayout(c).apply {
                            d.views?.let { rv ->
                                runCatching { rv.apply(c, this) }.getOrNull()?.let { v ->
                                    addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .requiredSize((d.geo.widthPx / density).dp, (d.geo.heightPx / density).dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
        }
    }
}

/** Ausschnitt des Wallpapers rund um die Widget-Position. */
private fun backdrop(ctx: android.content.Context, geo: WidgetGeometry, margin: Int, page: Int): Bitmap? {
    val wall = WallpaperSource.get(ctx) ?: return null
    val k = WallpaperSource.cacheScale(ctx, wall)
    val f = WallpaperSource.regionFor(ctx, wall, de.flexy.widgetwerk.glass.SurfaceRenderer.screenRect(ctx, geo), page)
    val m = margin * k
    val r = Rect((f.left - m).toInt(), (f.top - m).toInt(), (f.right + m).toInt(), (f.bottom + m).toInt())
    if (!r.intersect(0, 0, wall.width, wall.height) || r.width() < 2 || r.height() < 2) return null
    return Bitmap.createBitmap(wall, r.left, r.top, r.width(), r.height())
}

// ---------------------------------------------------------------- Bausteine

@Composable
fun Section(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String? = null, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            valueText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Form-Auswahl mit gezeichneten Vorschaukacheln. */
@Composable
fun ShapePicker(options: List<ShapeKind>, selected: ShapeKind, onSelect: (ShapeKind) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { k ->
            val sel = k == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) cs.primaryContainer else cs.surfaceContainerHighest)
                        .then(if (sel) Modifier.border(2.dp, cs.primary, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable { onSelect(k) }
                        .padding(10.dp),
                ) {
                    val fill = (if (sel) cs.primary else cs.onSurfaceVariant).toArgb()
                    Canvas(Modifier.fillMaxSize()) {
                        val p = Shapes.path(k, RectF(0f, 0f, size.width, size.height), 10.dp.toPx())
                        drawIntoCanvas { it.nativeCanvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }) }
                    }
                }
                Text(k.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(60.dp).padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

fun pct(v: Float) = "${(v * 100).roundToInt()} %"
