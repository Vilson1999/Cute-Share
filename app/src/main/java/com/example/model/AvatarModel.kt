package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

data class CuteAvatar(
    val id: Int,
    val name: String,
    @param:DrawableRes val drawableRes: Int,
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
        ),
        CuteAvatar(
            id = 11,
            name = "Sunny Shiba",
            drawableRes = R.drawable.avatar_shiba,
            glowColor = Color(0xFFF59E0B)
        ),
        CuteAvatar(
            id = 12,
            name = "Brave Lion",
            drawableRes = R.drawable.avatar_lion,
            glowColor = Color(0xFFEF4444)
        ),
        CuteAvatar(
            id = 13,
            name = "Happy Otter",
            drawableRes = R.drawable.avatar_otter,
            glowColor = Color(0xFF14B8A6)
        ),
        CuteAvatar(
            id = 14,
            name = "Magic Duck",
            drawableRes = R.drawable.avatar_duck,
            glowColor = Color(0xFFEAB308)
        ),
        CuteAvatar(
            id = 15,
            name = "Lucky Frog",
            drawableRes = R.drawable.avatar_frog,
            glowColor = Color(0xFF22C55E)
        ),
        CuteAvatar(
            id = 16,
            name = "Snow Seal",
            drawableRes = R.drawable.avatar_seal,
            glowColor = Color(0xFF38BDF8)
        ),
        CuteAvatar(
            id = 17,
            name = "Cheeky Monkey",
            drawableRes = R.drawable.avatar_monkey,
            glowColor = Color(0xFFF97316)
        ),
        CuteAvatar(
            id = 18,
            name = "Gentle Elephant",
            drawableRes = R.drawable.avatar_elephant,
            glowColor = Color(0xFF64748B)
        ),
        CuteAvatar(
            id = 19,
            name = "Rainbow Tiger",
            drawableRes = R.drawable.avatar_tiger,
            glowColor = Color(0xFFF43F5E)
        ),
        CuteAvatar(
            id = 20,
            name = "Cosmic Giraffe",
            drawableRes = R.drawable.avatar_giraffe,
            glowColor = Color(0xFFEA580C)
        ),
        CuteAvatar(
            id = 21,
            name = "Night Owl",
            drawableRes = R.drawable.avatar_owl,
            glowColor = Color(0xFF6366F1)
        ),
        CuteAvatar(
            id = 22,
            name = "Playful Puppy",
            drawableRes = R.drawable.avatar_puppy,
            glowColor = Color(0xFF3B82F6)
        )
    )

    fun getAvatar(index: Int): CuteAvatar {
        val safeIndex = (index.coerceAtLeast(0)) % list.size
        return list[safeIndex]
    }

    fun avatarIndexOf(drawableRes: Int): Int {
        val idx = list.indexOfFirst { it.drawableRes == drawableRes }
        return if (idx >= 0) idx else 0
    }

    fun resolveAvatarRes(rawValue: String?, indexHint: String? = null, fallbackSeed: String = ""): Int {
        indexHint?.toIntOrNull()?.let { idx ->
            if (idx in list.indices) return list[idx].drawableRes
        }
        rawValue?.toIntOrNull()?.let { value ->
            if (list.any { it.drawableRes == value }) return value
            if (value in list.indices) return list[value].drawableRes
        }
        if (fallbackSeed.isNotEmpty()) {
            val idx = (fallbackSeed.hashCode() and 0x7FFFFFFF) % list.size
            return list[idx].drawableRes
        }
        return list.first().drawableRes
    }
}
