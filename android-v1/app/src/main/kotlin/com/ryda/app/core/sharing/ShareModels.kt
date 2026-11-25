package com.ryda.app.core.sharing

enum class ShareFormat { TRANSPARENT_STICKER, MAP_CARD, PHOTO_CARD }

enum class StickerLayout { COMPACT_CORNER, FULL_VERTICAL, HORIZONTAL_STRIP }

enum class MapCardStyle { DARK, LIGHT, MINIMAL_ROUTE, RYDA_BRANDED }

enum class ExportSize(val width: Int, val height: Int) {
    STORY(1080, 1920),
    FEED_PORTRAIT(1080, 1350),
    SQUARE(1080, 1080)
}

enum class TextStyle { LIGHT_ON_DARK, DARK_ON_LIGHT }

data class ShareConfig(
    val format: ShareFormat = ShareFormat.TRANSPARENT_STICKER,
    val layout: StickerLayout = StickerLayout.COMPACT_CORNER,
    val mapCardStyle: MapCardStyle = MapCardStyle.RYDA_BRANDED,
    val exportSize: ExportSize = ExportSize.STORY,
    val textStyle: TextStyle = TextStyle.LIGHT_ON_DARK,
    val showDistance: Boolean = true,
    val showMovingTime: Boolean = true,
    val showAvgSpeed: Boolean = true,
    val showMaxSpeed: Boolean = true,
    val showElevation: Boolean = true,
    val showMotorcycle: Boolean = true,
    val showDate: Boolean = true,
    val trimRouteEnds: Boolean = false,
    val trimMeters: Int = 200
)
