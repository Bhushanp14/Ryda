package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RiderTag(
    val id: String,
    val name: String,
    @SerialName("unlock_type") val unlockType: String, // "free" or "achievement"
    @SerialName("linked_achievement_id") val linkedAchievementId: String? = null,
    @SerialName("icon_name") val iconName: String,
    @SerialName("unlock_requirement") val unlockRequirement: String? = null,
    @SerialName("is_unlocked") val isUnlocked: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = false
) {
    companion object {
        val ALL_TAGS = listOf(
            // 8 Freely available tags
            RiderTag("weekend_rider", "Weekend Rider", "free", null, "31_tag_weekend_rider.svg", "Available to all riders", true),
            RiderTag("city_rider", "City Rider", "free", null, "32_tag_city_rider.svg", "Available to all riders", true),
            RiderTag("solo_rider", "Solo Rider", "free", null, "33_tag_solo_rider.svg", "Available to all riders", true),
            RiderTag("group_rider", "Group Rider", "free", null, "34_tag_group_rider.svg", "Available to all riders", true),
            RiderTag("daily_commuter", "Daily Commuter", "free", null, "35_tag_daily_commuter.svg", "Available to all riders", true),
            RiderTag("touring_soul", "Touring Soul", "free", null, "36_tag_touring_soul.svg", "Available to all riders", true),
            RiderTag("adventure_seeker", "Adventure Seeker", "free", null, "37_tag_adventure_seeker.svg", "Available to all riders", true),
            RiderTag("classic_soul", "Classic Soul", "free", null, "38_tag_classic_soul.svg", "Available to all riders", true),

            // 7 Achievement-unlocked tags
            RiderTag("weekend_wanderer", "Weekend Wanderer", "achievement", "weekend_regular", "41_earned_distance_junkie.svg", "Unlocked by earning Weekend Regular achievement"),
            RiderTag("road_tripper", "Road Tripper", "achievement", "long_haul", "43_earned_long_haul_legend.svg", "Unlocked by earning Long Haul achievement"),
            RiderTag("road_veteran", "Road Veteran", "achievement", "ride_100", "39_earned_road_veteran.svg", "Unlocked by earning Century Rider achievement"),
            RiderTag("club_regular", "Club Regular", "achievement", "rally_regular", "42_earned_iron_rider.svg", "Unlocked by earning Rally Regular achievement"),
            RiderTag("community_voice", "Community Voice", "achievement", "community_voice", "23_community_voice.svg", "Unlocked by earning Community Voice achievement"),
            RiderTag("founding_rider", "Founding Rider", "achievement", "club_founder", "40_earned_club_founder.svg", "Unlocked by earning Club Founder achievement"),
            RiderTag("garage_explorer", "Garage Explorer", "achievement", "garage_explorer", "17_garage_explorer.svg", "Unlocked by earning Garage Explorer achievement")
        )
    }
}
