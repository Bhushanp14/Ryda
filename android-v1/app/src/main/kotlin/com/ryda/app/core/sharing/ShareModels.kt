package com.ryda.app.core.sharing

enum class SvgLayoutTemplate(
    val title: String,
    val viewBoxWidth: Int,
    val viewBoxHeight: Int
) {
    HERO_DISTANCE("Hero Distance", 420, 470),
    HORIZONTAL_BANNER("Horizontal Banner", 740, 190),
    MINIMALIST_STORY("Minimalist Story", 460, 440),
    SPLIT_PANEL("Split Panel", 620, 380),
    CLASSIC_CARD("Classic Card", 420, 500)
}

enum class GraphicStyle(val title: String) {
    TRANSPARENT("Transparent"),
    MAP_BACKGROUND("Map (Plain Roads)")
}

enum class ExportFormat(
    val title: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    PNG("PNG", "png", "image/png", "Transparent image saved to Photos / Gallery"),
    SVG("SVG", "svg", "image/svg+xml", "Vector graphic saved to Downloads / Files")
}

enum class ColorTheme(
    val primaryHex: String,
    val secondaryHex: String,
    val bgHex: String
) {
    RYDA_ORANGE("#FF6B35", "#00E5FF", "#0E0F12"),
    RYDA_CYAN("#00E5FF", "#FF6B35", "#0E0F12"),
    FOREST_GREEN("#10B981", "#00E5FF", "#0E0F12"),
    HIGH_VIS_LIME("#CCFF00", "#00E5FF", "#0E0F12"),
    PLAIN_WHITE("#FFFFFF", "#FF6B35", "#0E0F12"),
    PLAIN_BLACK("#101114", "#FF6B35", "#F0F2F5")
}

data class ShareConfig(
    val template: SvgLayoutTemplate = SvgLayoutTemplate.HERO_DISTANCE,
    val style: GraphicStyle = GraphicStyle.TRANSPARENT,
    val colorTheme: ColorTheme = ColorTheme.RYDA_ORANGE,
    val startLocationName: String = "",
    val destinationName: String = "",
    val showMaxSpeed: Boolean = true
)
