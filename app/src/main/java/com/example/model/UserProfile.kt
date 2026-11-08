package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

data class UserProfile(
    val name: String = "My Device",
    @DrawableRes val avatarRes: Int = R.drawable.avatar_1_bunny,
    val isCreated: Boolean = false
)
