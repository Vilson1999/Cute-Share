package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StyledIconBox(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    backgroundColor: Color = Color(0xFF1E293B),
    iconTint: Color = Color(0xFF38BDF8),
    borderColor: Color = Color(0xFF334155).copy(alpha = 0.6f),
    shape: Shape = RoundedCornerShape(14.dp),
    elevation: Dp = 0.dp,
    gradient: Brush? = null,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true),
            onClick = onClick
        )
    } else Modifier

    val bgModifier = if (gradient != null) {
        Modifier.background(gradient, shape)
    } else {
        Modifier.background(backgroundColor, shape)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape)
            .then(bgModifier)
            .border(1.dp, borderColor, shape)
            .then(clickModifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun CircularIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    backgroundColor: Color = Color(0xFF1E293B),
    iconTint: Color = Color.White,
    gradient: Brush? = null,
    elevation: Dp = 4.dp
) {
    StyledIconBox(
        icon = icon,
        contentDescription = contentDescription,
        size = size,
        iconSize = iconSize,
        backgroundColor = backgroundColor,
        iconTint = iconTint,
        borderColor = iconTint.copy(alpha = 0.25f),
        shape = CircleShape,
        elevation = elevation,
        gradient = gradient,
        onClick = onClick,
        modifier = modifier
    )
}
