package com.zaneschepke.wireguardautotunnel.ui.common.animations

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AnimatedFloatIcon(
    inactiveIcon: ImageVector,
    activeIcon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier,
) {

    val transition = updateTransition(isSelected, label = null)
    val scale by
        transition.animateFloat(transitionSpec = { tween(250, easing = FastOutSlowInEasing) }) {
            selected ->
            if (selected) 1.2f else 1f
        }

    val rotation by
        animateFloatAsState(targetValue = 0f, animationSpec = tween(400, easing = LinearEasing))

    Crossfade(targetState = isSelected, animationSpec = tween(250, easing = FastOutSlowInEasing)) {
        selected ->
        Icon(
            imageVector = if (selected) activeIcon else inactiveIcon,
            contentDescription = null,
            modifier = modifier.scale(scale).rotate(rotation),
        )
    }
}
