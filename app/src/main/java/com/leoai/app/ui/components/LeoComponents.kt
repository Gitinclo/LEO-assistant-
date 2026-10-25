package com.leoai.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leoai.app.R
import com.leoai.app.data.LeoMediaAssets
import com.leoai.app.ui.theme.LeoColors

@Composable
fun LeoAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE7B69B),
                        LeoColors.Primary,
                        LeoColors.Secondary,
                        Color(0xFFD6DEC9)
                    ),
                    center = Offset(this.size.width * 0.35f, this.size.height * 0.35f),
                    radius = radius * 1.45f
                )
            )
        }
        AsyncImage(
            model = LeoMediaAssets.AI_AVATAR,
            contentDescription = stringResource(R.string.cd_leo_avatar),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BotanicalBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.08f)
        ) {
            val w = size.width
            val h = size.height
            val stemPath = Path().apply {
                moveTo(w * 0.85f, h * 0.92f)
                cubicTo(w * 0.65f, h * 0.65f, w * 0.78f, h * 0.35f, w * 0.55f, h * 0.08f)
            }
            drawPath(
                path = stemPath,
                color = LeoColors.Secondary,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = LeoColors.Secondary,
                radius = w * 0.16f,
                center = Offset(w * 0.78f, h * 0.28f)
            )
            drawCircle(
                color = LeoColors.Primary,
                radius = w * 0.11f,
                center = Offset(w * 0.62f, h * 0.19f)
            )
        }

        AsyncImage(
            model = LeoMediaAssets.BG_BOTANICAL,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.12f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LeoColors.Bg.copy(alpha = 0.6f))
        )
    }
}

@Composable
fun TypingDotsIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing_dots")

    val alpha1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.35f at 0
                1.0f at 400
                0.35f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.35f at 200
                1.0f at 600
                0.35f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.35f at 400
                1.0f at 800
                0.35f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .padding(start = 36.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("typing-indicator"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(alpha1)
                .clip(CircleShape)
                .background(LeoColors.TextMuted)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(alpha2)
                .clip(CircleShape)
                .background(LeoColors.TextMuted)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(alpha3)
                .clip(CircleShape)
                .background(LeoColors.TextMuted)
        )
    }
}
