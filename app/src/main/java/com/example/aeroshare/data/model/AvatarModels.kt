package com.example.aeroshare.data.model

import androidx.compose.ui.graphics.Color

data class AvatarOption(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val iconName: String
)

object AvatarPresets {
    val options = listOf(
        AvatarOption("avatar_1", "Cosmic Pulse", Color(0xFF0284C7), Color(0xFF38BDF8), "send"),
        AvatarOption("avatar_2", "Cyber Wave", Color(0xFF0D9488), Color(0xFF2DD4BF), "waves"),
        AvatarOption("avatar_3", "Electric Bolt", Color(0xFFEAB308), Color(0xFFFACC15), "bolt"),
        AvatarOption("avatar_4", "Neon Falcon", Color(0xFF6366F1), Color(0xFF818CF8), "flight"),
        AvatarOption("avatar_5", "Solar Flare", Color(0xFFF97316), Color(0xFFFB923C), "flare"),
        AvatarOption("avatar_6", "Quantum Apex", Color(0xFF8B5CF6), Color(0xFFA78BFA), "grain"),
        AvatarOption("avatar_7", "Polar Fox", Color(0xFFEC4899), Color(0xFFF472B6), "pets"),
        AvatarOption("avatar_8", "Terra Shield", Color(0xFF10B981), Color(0xFF34D399), "shield"),
        AvatarOption("avatar_9", "Hyper Nova", Color(0xFF06B6D4), Color(0xFF67E8F9), "blur_on"),
        AvatarOption("avatar_10", "Aero Prism", Color(0xFF3B82F6), Color(0xFF93C5FD), "all_inclusive")
    )

    fun getById(id: String?): AvatarOption {
        return options.firstOrNull { it.id == id } ?: options.first()
    }
}
