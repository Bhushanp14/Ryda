package com.ryda.app.core.models

import java.util.UUID

data class Motorcycle(
    val id: String = UUID.randomUUID().toString(),
    val brand: String,
    val model: String,
    val variant: String? = null,
    val year: Int = 2024,
    val engineCc: Int = 450,
    val nickname: String? = null,
    val isPrimary: Boolean = false,
    val imageUri: String? = null
) {
    val displayName: String
        get() = if (!nickname.isNullOrBlank()) "$nickname ($brand $model)" else "$brand $model"
}
