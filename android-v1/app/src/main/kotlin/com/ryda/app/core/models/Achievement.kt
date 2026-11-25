package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Achievement(
    val id: String,
    val key: String,
    val category: String,
    val title: String,
    val description: String,
    @SerialName("target_value") val targetValue: Double,
    val unit: String,
    @SerialName("icon_name") val iconName: String,
    @SerialName("progress_value") val progressValue: Double = 0.0,
    @SerialName("is_unlocked") val isUnlocked: Boolean = false,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
    @SerialName("progress_percent") val progressPercent: Int = 0
)

@Serializable
data class AchievementsResponse(
    @SerialName("unlocked_count") val unlockedCount: Int,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("completion_rate_percent") val completionRatePercent: Int,
    val achievements: List<Achievement>
)

/**
 * Maps backend icon_name values to asset SVG filenames.
 * Returns null when no mapping exists so callers can show a Material icon fallback.
 *
 * Backend icon_name values come from Achievement.icon_name in Django (e.g. "military_tech",
 * "two_wheeler", "map", "groups"). Each is mapped to the correct numbered SVG in assets/badges/.
 */
fun getBadgeAssetUrl(iconName: String): String? {
    val clean = iconName.lowercase().trim()
    val mappedFile = if (clean.endsWith(".svg")) {
        clean
    } else {
        when (clean) {
            // Distance achievements
            "two_wheeler", "first_milestone", "emoji_events", "ride_first", "first_ride" -> "01_ignition.svg"
            "directions_bike", "ride_5", "getting_started"                              -> "02_finding_your_rhythm.svg"
            "ride_25", "regular_rider"                                                  -> "03_the_regular.svg"
            "ride_50", "dedicated_rider"                                                -> "04_ride_devotee.svg"
            "star", "rides_100", "ride_100", "century_rider"                            -> "05_the_centurion.svg"
            "workspace_premium", "rides_500", "ride_250", "ride_collector"              -> "06_two_wheel_legend.svg"
            "speed", "rides_10", "distance_50", "first_50"                              -> "07_first_fifty.svg"
            "sports_motorsports", "rides_50", "distance_100", "the_100_club"            -> "08_the_100_club.svg"
            "distance_500", "half_a_thousand"                                           -> "09_five_hundred_strong.svg"
            "distance_1000", "thousand_kilometres"                                      -> "10_the_grand_thousand.svg"
            "military_tech", "distance_5000km", "distance_5000", "five_thousand"        -> "11_five_thousand_strong.svg"
            "fitness_center", "distance_10000km", "distance_10000", "ten_thousand"      -> "12_ten_thousand_strong.svg"

            // Exploration & Seasonality
            "map", "state_explorer", "explore", "weekend_regular"                       -> "13_weekend_regular.svg"
            "bedtime", "night_rider", "season_rider"                                    -> "14_season_rider.svg"
            "wb_sunny", "season_rider", "back_on_road", "back_on_the_road"              -> "15_back_on_the_road.svg"
            "two_wheel_stable"                                                          -> "16_two_wheel_stable.svg"
            "terrain", "country_explorer", "garage_explorer"                            -> "17_garage_explorer.svg"
            "long_haul"                                                                 -> "18_long_haul.svg"

            // Community & Social
            "share", "first_impression", "community_first_post"                         -> "19_first_impression.svg"
            "ride_storyteller", "community_storyteller"                                 -> "20_ride_storyteller.svg"
            "first_connection", "community_first_follow"                                -> "21_first_connection.svg"
            "chat_bubble", "conversation_starter", "community_comments"                 -> "22_conversation_starter.svg"
            "forum", "community_voice"                                                  -> "23_community_voice.svg"
            "photo_camera", "shared_experience", "community_ride_posts"                 -> "24_shared_experience.svg"

            // Clubs & Rallies
            "groups", "group", "club_member", "club_contributor"                        -> "25_club_member.svg"
            "club_contributor"                                                          -> "26_club_contributor.svg"
            "celebration", "rally_debut"                                                -> "27_rally_debut.svg"
            "event", "rally_regular"                                                    -> "28_rally_regular.svg"
            "flag", "club_founder", "lock"                                              -> "29_club_founder.svg"
            "emoji_people", "rally_host"                                                -> "30_rally_host.svg"

            else -> null
        }
    }
    return if (mappedFile != null) "file:///android_asset/badges/$mappedFile" else null
}
