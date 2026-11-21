package ua.guitar.archive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

val MascotPhrases = listOf(
    "Я тебе сильно кохаю!",
    "Ти моя принцеса!",
    "Як справи, моє сонце?",
    "Ти моя квіточка.",
    "Сама красива дівчина, яка в мене є.",
    "Посміхнись, ти найкраща.",
)

private val HeartColors = listOf(
    Color(0xFFFF6B9D), Color(0xFFFF8FB1), Color(0xFFE91E63), Color(0xFFFFB3C6), Color(0xFFFF5C8A),
)
private val BubbleColor = Color(0xFFFFE4EF)
private val BubbleText = Color(0xFF6A2450)

private class Heart(
    val id: Int,
    val angle: Float,
    val distance: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val duration: Int,
)

private val MascotWidth = 96.dp
private val MascotHeight = 114.dp

/** Котик у куті головного екрана: тихенько гойдається, а від натискання підстрибує, пускає сердечка й каже щось миле. */
@Composable
fun Mascot(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Спокійне «дихання» та легке погойдування.
    val idle = rememberInfiniteTransition(label = "idle")
    val bob by idle.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val sway by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway",
    )

    val jump = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    val hearts = remember { mutableStateListOf<Heart>() }
    var nextHeartId by remember { mutableIntStateOf(0) }
    var bubbleVisible by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf(MascotPhrases.first()) }
    var lastIndex by remember { mutableIntStateOf(-1) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun onTap() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            jump.snapTo(0f)
            jump.animateTo(26f, tween(170, easing = FastOutSlowInEasing))
            jump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
        scope.launch {
            squash.snapTo(0.88f)
            squash.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f))
        }
        repeat(8) {
            hearts += Heart(
                id = nextHeartId++,
                // Вліво й угору — котик стоїть біля правого краю екрана.
                angle = Math.toRadians(Random.nextDouble(-205.0, -55.0)).toFloat(),
                distance = Random.nextInt(55, 115).toFloat(),
                size = Random.nextInt(14, 26).toFloat(),
                color = HeartColors.random(),
                spin = Random.nextInt(-30, 30).toFloat(),
                duration = Random.nextInt(900, 1400),
            )
        }
        var i: Int
        do {
            i = Random.nextInt(MascotPhrases.size)
        } while (i == lastIndex && MascotPhrases.size > 1)
        lastIndex = i
        phrase = MascotPhrases[i]
        bubbleVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(3800)
            bubbleVisible = false
        }
    }

    Column(modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = bubbleVisible,
            modifier = Modifier.zIndex(1f), // бульбашка поверх сердечок
            enter = fadeIn() + scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(0.85f, 1f)) +
                slideInVertically { it / 3 },
            exit = fadeOut() + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(0.85f, 1f)),
        ) {
            SpeechBubble(phrase)
        }
        Box(Modifier.size(MascotWidth, MascotHeight)) {
            Image(
                painterResource(R.drawable.mascot),
                contentDescription = "Котик",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = -(jump.value + bob * 3f) * density.density
                        rotationZ = sway * 2f
                        scaleY = squash.value * (1f + bob * 0.015f)
                        scaleX = 2f - squash.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::onTap,
                    ),
            )
            // Сердечка вилітають з-за котика й тануть.
            hearts.forEach { h ->
                key(h.id) {
                    HeartParticle(h) { hearts.remove(h) }
                }
            }
        }
    }
}

@Composable
private fun HeartParticle(h: Heart, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(h.duration, easing = LinearOutSlowInEasing))
        onDone()
    }
    val density = LocalDensity.current
    val p = progress.value
    // Старт — біля голови котика.
    val cx = with(density) { (MascotWidth / 2).toPx() }
    val cy = with(density) { (MascotHeight * 0.35f).toPx() }
    val dist = with(density) { h.distance.dp.toPx() } * p
    val rise = with(density) { 18.dp.toPx() } * p * p
    val half = with(density) { (h.size / 2).dp.toPx() }
    Icon(
        Icons.Rounded.Favorite,
        contentDescription = null,
        tint = h.color,
        modifier = Modifier
            .offset {
                IntOffset(
                    (cx + cos(h.angle) * dist - half).roundToInt(),
                    (cy + sin(h.angle) * dist - rise - half).roundToInt(),
                )
            }
            .size(h.size.dp)
            .graphicsLayer {
                val pop = if (p < 0.2f) p / 0.2f else 1f
                scaleX = 0.5f + 0.6f * pop
                scaleY = 0.5f + 0.6f * pop
                rotationZ = h.spin * p
                alpha = if (p < 0.6f) 1f else (1f - (p - 0.6f) / 0.4f)
            },
    )
}

@Composable
private fun SpeechBubble(text: String) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 2.dp)) {
        Surface(
            color = BubbleColor,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 230.dp),
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                label = "phrase",
            ) { t ->
                Text(
                    t,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = BubbleText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Хвостик бульбашки — показує на котика.
        Canvas(Modifier.padding(end = 40.dp).size(16.dp, 9.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width * 0.55f, size.height)
                close()
            }
            drawPath(path, BubbleColor)
        }
    }
}
