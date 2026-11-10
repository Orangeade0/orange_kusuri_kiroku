package app.okusurikiroku.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 端末の設定でアニメーションが無効にされていないか。 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** 押している間、少しだけ縮む。カードやボタンに手応えを出す。 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 0 から [progress] まで滑らかに伸びる円形の進み具合。中央に [content] を置ける。 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progressRing",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

private class ConfettiPiece(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val round: Boolean,
)

private const val CONFETTI_SECONDS = 2.6f

/**
 * 紙吹雪。[trigger] の値が変わるたびに、[origin]（画面に対する割合）から1回はじける。
 * タッチは受け取らないので、画面の上に重ねて置ける。
 */
@Composable
fun ConfettiBurst(trigger: Int, colors: List<Color>, modifier: Modifier = Modifier, origin: Offset = Offset(0.5f, 0.22f)) {
    if (trigger == 0) return
    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) {
        val random = Random(trigger)
        List(110) {
            ConfettiPiece(
                // 上向きを中心に扇形に飛ばす
                angle = (-PI / 2 + (random.nextDouble() - 0.5) * PI * 1.5).toFloat(),
                speed = 520f + random.nextFloat() * 900f,
                size = 6f + random.nextFloat() * 7f,
                color = colors[random.nextInt(colors.size)],
                spin = (random.nextFloat() - 0.5f) * 4f,
                round = random.nextInt(3) == 0,
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween((CONFETTI_SECONDS * 1000).toInt(), easing = LinearEasing))
    }
    val t = progress.value
    if (t >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val seconds = t * CONFETTI_SECONDS
        // 空気抵抗で減速しながら飛び、重力で落ちる
        val drag = 2.2f
        val travel = (1f - exp(-drag * seconds)) / drag
        val gravity = 620f * density
        val alpha = (1f - (t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val startX = size.width * origin.x
        val startY = size.height * origin.y
        for (piece in pieces) {
            val x = startX + cos(piece.angle) * piece.speed * density * travel
            val y = startY + sin(piece.angle) * piece.speed * density * travel + 0.5f * gravity * seconds * seconds
            val side = piece.size * density
            val color = piece.color.copy(alpha = alpha)
            if (piece.round) {
                drawCircle(color, radius = side / 2, center = Offset(x, y))
            } else {
                rotate(degrees = piece.spin * seconds * 360f, pivot = Offset(x, y)) {
                    drawRect(color, topLeft = Offset(x - side / 2, y - side / 4), size = Size(side, side / 2))
                }
            }
        }
    }
}
