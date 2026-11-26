package com.ryda.app.core.location

import com.google.android.libraries.navigation.Navigator

enum class NavigationSoundMode(
    val label: String,
    val shortLabel: String
) {
    SOUND_ON("Sound On", "Sound"),
    ALERTS_ONLY("Alerts Only", "Alerts"),
    MUTED("Muted", "Muted");

    fun toAudioGuidance(): Int = when (this) {
        SOUND_ON -> Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE
        ALERTS_ONLY -> Navigator.AudioGuidance.VOICE_ALERTS_ONLY
        MUTED -> Navigator.AudioGuidance.SILENT
    }

    companion object {
        fun fromStored(value: String?): NavigationSoundMode =
            entries.firstOrNull { it.name == value } ?: SOUND_ON
    }
}
