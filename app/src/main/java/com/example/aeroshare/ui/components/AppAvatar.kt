package com.example.aeroshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aeroshare.data.model.AvatarPresets

@Composable
fun AppAvatar(
    avatarId: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val avatar = AvatarPresets.getById(avatarId)
    val icon = when (avatar.iconName) {
        "send" -> Icons.Default.Send
        "waves" -> Icons.Default.Waves
        "bolt" -> Icons.Default.Bolt
        "flight" -> Icons.Default.Flight
        "flare" -> Icons.Default.Flare
        "grain" -> Icons.Default.Grain
        "pets" -> Icons.Default.Pets
        "shield" -> Icons.Default.Shield
        "blur_on" -> Icons.Default.BlurOn
        else -> Icons.Default.AllInclusive
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(avatar.primaryColor, avatar.secondaryColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = avatar.name,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
