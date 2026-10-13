package com.eventphotobooth.app.data

/**
 * Curated image-backed stickers sourced from the three guest-facing sticker
 * sheets. Keeping crop geometry here lets the editor, receipt preview and
 * printer use the very same artwork.
 */
enum class StickerSheet(
    val drawableName: String,
    val columns: Int,
    val rows: Int,
    val left: Float,
    val top: Float,
    val cellWidth: Float,
    val cellHeight: Float,
    val horizontalGap: Float,
    val verticalGap: Float,
) {
    ROMANCE("sticker_sheet_romance", 4, 5, 0.03f, 0.03f, 0.21f, 0.19f, 0.035f, 0.01f),
    EXPRESSIONS("sticker_sheet_expression", 5, 4, 0.02f, 0.14f, 0.18f, 0.17f, 0.02f, 0.03f),
    RETRO("sticker_sheet_retro", 4, 5, 0.02f, 0.02f, 0.22f, 0.18f, 0.025f, 0.02f),
}

enum class StickerArtworkId(
    val label: String,
    val sheet: StickerSheet,
    val row: Int,
    val column: Int,
) {
    STRAWBERRY("Strawberry", StickerSheet.ROMANCE, 0, 0),
    CLOVER("Clover", StickerSheet.ROMANCE, 0, 1),
    FLOWER_BOUQUET("Flower bouquet", StickerSheet.ROMANCE, 0, 2),
    MOON("Moon", StickerSheet.ROMANCE, 0, 3),
    SHOOTING_STAR("Shooting star", StickerSheet.ROMANCE, 1, 0),
    RINGS("Rings", StickerSheet.ROMANCE, 1, 2),
    CAMERA_ART("Camera", StickerSheet.ROMANCE, 2, 1),
    BOW("Bow", StickerSheet.ROMANCE, 4, 3),

    WINK("Wink", StickerSheet.EXPRESSIONS, 0, 0),
    HEART_EYES("Heart eyes", StickerSheet.EXPRESSIONS, 0, 1),
    KISS_FACE("Kiss", StickerSheet.EXPRESSIONS, 0, 2),
    TONGUE_OUT("Silly", StickerSheet.EXPRESSIONS, 0, 3),
    THUMBS_UP("Thumbs up", StickerSheet.EXPRESSIONS, 1, 1),
    PARTY_POPPER("Party", StickerSheet.EXPRESSIONS, 1, 4),
    CROWN_ART("Crown", StickerSheet.EXPRESSIONS, 2, 3),
    SPARKLES_ART("Sparkles", StickerSheet.EXPRESSIONS, 3, 0),

    PEACE_HANDS("Peace", StickerSheet.RETRO, 0, 0),
    POLAROID("Polaroid", StickerSheet.RETRO, 0, 2),
    FILM("Film", StickerSheet.RETRO, 0, 3),
    BOOMBOX("Boombox", StickerSheet.RETRO, 1, 2),
    ROLLER_SKATE("Roller skate", StickerSheet.RETRO, 1, 3),
    ROSE("Rose", StickerSheet.RETRO, 2, 2),
    PLANET("Planet", StickerSheet.RETRO, 3, 2),
    SUNGLASSES("Sunglasses", StickerSheet.RETRO, 4, 3),
}

fun StickerArtworkId.within(sheet: StickerSheet): Boolean = this.sheet == sheet
