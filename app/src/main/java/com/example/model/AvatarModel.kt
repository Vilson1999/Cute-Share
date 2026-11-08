package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

data class CuteAvatar(
    val id: Int,
    val name: String,
    @DrawableRes val drawableRes: Int,
    val glowColor: Color
)

object CartoonAvatars {
    val list: List<CuteAvatar> = listOf(
        CuteAvatar(
            id = 1,
            name = "Cosmic Bunny",
            drawableRes = R.drawable.avatar_1_bunny,
            glowColor = Color(0xFF38BDF8)
        ),
        CuteAvatar(
            id = 2,
            name = "Cyber Panda",
            drawableRes = R.drawable.avatar_2_panda,
            glowColor = Color(0xFFA855F7)
        ),
        CuteAvatar(
            id = 3,
            name = "Neon Fox",
            drawableRes = R.drawable.avatar_3_fox,
            glowColor = Color(0xFFF97316)
        ),
        CuteAvatar(
            id = 4,
            name = "Astro Robot",
            drawableRes = R.drawable.avatar_4_robot,
            glowColor = Color(0xFF06B6D4)
        ),
        CuteAvatar(
            id = 5,
            name = "Aviator Penguin",
            drawableRes = R.drawable.avatar_5_penguin,
            glowColor = Color(0xFF0EA5E9)
        ),
        CuteAvatar(
            id = 6,
            name = "Mystic Dragon",
            drawableRes = R.drawable.avatar_6_dragon,
            glowColor = Color(0xFF8B5CF6)
        ),
        CuteAvatar(
            id = 7,
            name = "Starry Kitten",
            drawableRes = R.drawable.avatar_7_cat,
            glowColor = Color(0xFFEAB308)
        ),
        CuteAvatar(
            id = 8,
            name = "Chill Koala",
            drawableRes = R.drawable.avatar_8_koala,
            glowColor = Color(0xFF10B981)
        ),
        CuteAvatar(
            id = 9,
            name = "Spark Hamster",
            drawableRes = R.drawable.avatar_9_hamster,
            glowColor = Color(0xFFF59E0B)
        ),
        CuteAvatar(
            id = 10,
            name = "Cozy Bear",
            drawableRes = R.drawable.avatar_10_bear,
            glowColor = Color(0xFFEC4899)
        )
    )

    fun getAvatar(index: Int): CuteAvatar {
        val safeIndex = (index.coerceAtLeast(0)) % list.size
        return list[safeIndex]
    }
}
