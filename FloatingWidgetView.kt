package com.bydmate.app.ui.widget

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.domain.BatteryThresholds
import com.bydmate.app.domain.calculator.Trend
import com.bydmate.app.ui.components.socColor as componentsSocColor
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.SocYellow
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * v3.2 — 单行长条（720 × 113 dp），供 15.6 寸 pad 状态栏使用。
 *
 * 两行结构：
 *   Row 1（主）：SOC% · 续航 km · 能耗趋势（左） / 行程时长 · 行程距离（右）
 *   Row 2（辅）：车内温度 · 12V 电压
 *
 * 相比 v3.1 已删减：km/%、电池温度、混动油箱/燃油续航显示。
 * voltage12v 仍传入，既用于显示也参与 [widgetStatus] 边框颜色判断。
 */
@Composable
fun FloatingWidgetView(
    soc: Int?,
    rangeKm: Double?,
    consumption: Double?,
    trend: Trend,
    sessionStartedAt: Long?,
    tripDistanceKm: Double?,
    insideTemp: Int?,
    batTemp: Int?,          // 保留签名，不显示
    voltage12v: Double?,    // 显示 + 参与边框状态判断
    alpha: Float,
    scaleFactor: Float = 1.0f,
    listening: Boolean = false,
    kind: VehicleKind = VehicleKind.EV,
    fuelPercent: Int? = null,
    fuelRangeKm: Int? = null,
    onDrag: ((dx: Float, dy: Float) -> Unit)? = null,
) {
    val status = widgetStatus(soc, voltage12v)
    val borderColor = when (status) {
        Status.OK -> AccentGreen
        Status.WARN -> SocYellow
        Status.CRIT -> SocRed
        Status.NO_DATA -> TextMuted.copy(alpha = 0.4f)
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density * scaleFactor,
        fontScale = baseDensity.fontScale,
    )

    // 语音监听时蛇形流光边框；否则静态边框。
    val borderModifier = if (listening) {
        val phase = rememberSnakePhase()
        Modifier.drawBehind { drawSnakeBorder(color = borderColor, phase = phase) }
    } else {
        Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp))
    }

    // 起步 300m 内抑制趋势（与 v3.1 逻辑一致）。
    val effectiveTrend = if (
        sessionStartedAt != null &&
        (tripDistanceKm ?: 0.0) < TRIP_DISTANCE_TREND_THRESHOLD_KM
    ) Trend.NONE else trend

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Column(
            modifier = Modifier
                .alpha(alpha.coerceIn(0.3f, 1.0f))
                .size(
                    width = WidgetButtonLayout.PANEL_WIDTH_DP.dp,
                    height = WidgetButtonLayout.PANEL_HEIGHT_DP.dp,
                )
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .background(CardSurface, RoundedCornerShape(10.dp))
                .then(borderModifier)
                .pointerInput(Unit) {
                    if (onDrag != null) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ---- 第一行：核心信息 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EnergyGroup(soc, rangeKm, consumption, effectiveTrend)
                TripGroup(sessionStartedAt, tripDistanceKm)
            }

            // ---- 分隔线 ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TextMuted.copy(alpha = 0.22f)),
            )

            // ---- 第二行：辅助信息 ----
            HealthGroup(insideTemp, voltage12v)
        }
    }
}

internal const val TRIP_DISTANCE_TREND_THRESHOLD_KM = 0.3

// ---- Snake border（语音监听流光） ----

private const val SNAKE_PERIOD_MS = 1200
private const val SNAKE_FRACTION = 0.35f
private val SNAKE_STROKE_WIDTH = 1.5.dp
private val SNAKE_CORNER_RADIUS = 10.dp

@Composable
private fun rememberSnakePhase(): Float {
    val transition = rememberInfiniteTransition(label = "widgetSnake")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(SNAKE_PERIOD_MS, easing = LinearEasing)),
        label = "widgetSnakePhase",
    )
    return phase
}

private fun DrawScope.drawSnakeBorder(color: Color, phase: Float) {
    val strokeWidthPx = SNAKE_STROKE_WIDTH.toPx()
    val cornerPx = SNAKE_CORNER_RADIUS.toPx()
    val path = Path().apply {
        addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx)))
    }
    val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
    val length = measure.length

    drawPath(path, color = color.copy(alpha = 0.25f), style = Stroke(width = strokeWidthPx))

    val (start, end) = snakeSegment(phase, length, SNAKE_FRACTION)
    val segment = Path()
    if (end >= start) {
        measure.getSegment(start, end, segment, true)
    } else {
        measure.getSegment(start, length, segment, true)
        measure.getSegment(0f, end, segment, true)
    }
    drawPath(segment, color = color, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))
}

internal fun snakeSegment(phase: Float, length: Float, fraction: Float): Pair<Float, Float> {
    if (length <= 0f) return 0f to 0f
    val start = phase.mod(1f) * length
    val segmentLength = fraction * length
    val rawEnd = start + segmentLength
    val end = if (rawEnd > length) rawEnd - length else rawEnd
    return start to end
}

// ---- 三组内容 ----

@Composable
private fun EnergyGroup(
    soc: Int?,
    rangeKm: Double?,
    consumption: Double?,
    trend: Trend,
) {
    val trendColor = when (trend) {
        Trend.DOWN -> AccentGreen
        Trend.UP -> SocYellow
        Trend.FLAT -> TextPrimary
        Trend.NONE -> TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = soc?.let { "$it%" } ?: "—",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = socColor(soc),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = rangeKm?.let { "~${"%.0f".format(it)}" } ?: "~—",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.widget_unit_km),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        )
        Spacer(Modifier.width(14.dp))
        if (trend != Trend.NONE) {
            Icon(
                imageVector = when (trend) {
                    Trend.DOWN -> Icons.Outlined.TrendingDown
                    Trend.UP -> Icons.Outlined.TrendingUp
                    else -> Icons.Outlined.TrendingFlat
                },
                contentDescription = null,
                tint = trendColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = consumption?.let { "%.1f".format(it) } ?: "—",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (trend == Trend.NONE) TextMuted else trendColor,
        )
    }
}

@Composable
private fun TripGroup(sessionStartedAt: Long?, tripDistanceKm: Double?) {
    val context = LocalContext.current
    val durationText by produceState(
        initialValue = formatDurationShort(context, sessionStartedAt),
        sessionStartedAt,
    ) {
        while (true) {
            value = formatDurationShort(context, sessionStartedAt)
            delay(15_000L)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconText(Icons.Outlined.Schedule, durationText)
        Spacer(Modifier.width(16.dp))
        IconText(Icons.Outlined.Route, formatTripKm(context, tripDistanceKm))
    }
}

@Composable
private fun HealthGroup(insideTemp: Int?, voltage12v: Double?) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconText(Icons.Outlined.DirectionsCar, insideTemp?.let { "$it°" } ?: "—")
        Spacer(Modifier.width(24.dp))
        IconText(
            Icons.Outlined.Bolt,
            voltage12v?.let { context.getString(R.string.widget_voltage_value, it) } ?: "—",
        )
    }
}

/** 图标灰色，数值白色，14sp。 */
@Composable
private fun IconText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        )
    }
}

// ---- 格式化 ----

internal fun formatDurationShort(context: Context, sessionStartedAt: Long?): String {
    if (sessionStartedAt == null) return "—"
    val elapsed = System.currentTimeMillis() - sessionStartedAt
    val totalMin = (elapsed / 60_000L).toInt().coerceAtLeast(0)
    val hours = totalMin / 60
    val minutes = totalMin % 60
    return if (hours > 0) {
        context.getString(R.string.widget_duration_hours, hours, minutes)
    } else {
        context.getString(R.string.widget_duration_minutes, minutes)
    }
}

internal fun formatTripKm(context: Context, km: Double?): String {
    if (km == null || km.isNaN() || km.isInfinite() || km < 0.0) return "—"
    return context.getString(R.string.widget_trip_km_value, km)
}

// ---- 状态模型（边框颜色），保持不变 ----

internal enum class Status { OK, WARN, CRIT, NO_DATA }

internal fun widgetStatus(soc: Int?, v12: Double?): Status {
    if (soc == null && v12 == null) return Status.NO_DATA
    val socStatus = when {
        soc == null -> Status.NO_DATA
        soc < BatteryThresholds.SOC_CRIT -> Status.CRIT
        soc < BatteryThresholds.SOC_WARN -> Status.WARN
        else -> Status.OK
    }
    val vStatus = when {
        v12 == null -> Status.OK
        v12 < BatteryThresholds.V12_CRIT -> Status.CRIT
        v12 < BatteryThresholds.V12_WARN -> Status.WARN
        else -> Status.OK
    }
    return listOf(socStatus, vStatus)
        .filter { it != Status.NO_DATA }
        .maxByOrNull { severity(it) }
        ?: Status.NO_DATA
}

private fun severity(s: Status): Int = when (s) {
    Status.NO_DATA -> -1
    Status.OK -> 0
    Status.WARN -> 1
    Status.CRIT -> 2
}

private fun socColor(soc: Int?): Color =
    if (soc == null) TextMuted else componentsSocColor(soc)