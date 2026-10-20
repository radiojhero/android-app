package com.radiojhero.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

private val DefaultSkeletonShape = RoundedCornerShape(4.dp)

/** Replaces the content with a pulsing placeholder while [visible] is true. */
fun Modifier.skeleton(visible: Boolean, shape: Shape = DefaultSkeletonShape): Modifier =
    if (!visible) this else composed {
        val color = MaterialTheme.colorScheme.surfaceVariant
        val transition = rememberInfiniteTransition(label = "Skeleton")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "SkeletonAlpha",
        )

        drawWithContent {
            drawOutline(shape.createOutline(size, layoutDirection, this), color, alpha = alpha)
        }
    }

