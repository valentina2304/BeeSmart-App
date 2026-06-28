package com.example.beesmart.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beesmart.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Honey = Color(0xFFE89611)
private val HoneyDeep = Color(0xFFB87308)
private val Ink = Color(0xFF1A1410)
private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private const val STARTUP_SPLASH_DELAY_MS = 900L

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    val hiveScale = remember { Animatable(0.6f) }
    val hiveTy = remember { Animatable(-26f) }
    val hiveAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { hiveAlpha.animateTo(1f, tween(300)) }
        launch {
            hiveScale.animateTo(1f, keyframes {
                durationMillis = 850
                0.6f at 0 using Overshoot
                1.06f at 470
                0.98f at 640
                1f at 850
            })
        }
        launch {
            hiveTy.animateTo(0f, keyframes {
                durationMillis = 850
                -26f at 0 using Overshoot
                6f at 470
                -2f at 640
                0f at 850
            })
        }
        delay(STARTUP_SPLASH_DELAY_MS)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val ring1 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing)),
        label = "ring1"
    )
    val ring2 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(1400, easing = LinearOutSlowInEasing),
            initialStartOffset = StartOffset(900)
        ),
        label = "ring2"
    )

    val beeT by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bee1"
    )
    val beeT2 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bee2"
    )

    Surface {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to Color(0xFFFFF9E0),
                        0.55f to Color(0xFFFCE9BA),
                        1f to Color(0xFFF4C75D)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {

                    Ring(progress = ring1)
                    Ring(progress = ring2)

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = hiveScale.value
                                scaleY = hiveScale.value
                                translationY = hiveTy.value.dp.toPx()
                                alpha = hiveAlpha.value
                            }
                            .size(132.dp)
                            .shadow(16.dp, RoundedCornerShape(34.dp))
                            .clip(RoundedCornerShape(34.dp))
                            .background(Color(0xFFFFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.sx_misc_splash_app_name),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(124.dp)
                        )
                    }

                    Image(
                        painter = painterResource(R.drawable.bee),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(38.dp)
                            .graphicsLayer {
                                translationX = beeT * 14.dp.toPx()
                                translationY = -beeT * 10.dp.toPx()
                                rotationZ = 8f - beeT * 14f
                            }
                    )

                    Image(
                        painter = painterResource(R.drawable.bee),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(30.dp)
                            .graphicsLayer {
                                translationX = -beeT2 * 12.dp.toPx()
                                translationY = -beeT2 * 8.dp.toPx()
                                rotationZ = -10f + beeT2 * 18f
                                alpha = 0.9f
                            }
                    )
                }

                Spacer(Modifier.height(18.dp))
                RiseIn(delayMillis = 500) {
                    Text(
                        stringResource(R.string.sx_misc_splash_app_name),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink
                    )
                }
                Spacer(Modifier.height(4.dp))
                RiseIn(delayMillis = 700) {
                    Text(
                        stringResource(R.string.sx_misc_splash_tagline),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HoneyDeep
                    )
                }

                Spacer(Modifier.height(30.dp))
                RiseIn(delayMillis = 900) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(3) { i -> LoadingDot(index = i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Ring(progress: Float) {
    val scale = 0.4f + progress * 1.4f
    val alpha = if (progress < 0.4f) (progress / 0.4f) * 0.6f
    else (1f - (progress - 0.4f) / 0.6f) * 0.6f
    Box(
        modifier = Modifier
            .size(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .border(2.dp, Honey, CircleShape)
    )
}

@Composable
private fun RiseIn(delayMillis: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        a.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * 14.dp.toPx()
        }
    ) { content() }
}

@Composable
private fun LoadingDot(index: Int) {
    val infinite = rememberInfiniteTransition(label = "dot$index")
    val a by infinite.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 180)
        ),
        label = "dotA$index"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { alpha = a }
            .clip(CircleShape)
            .background(HoneyDeep)
    )
}
