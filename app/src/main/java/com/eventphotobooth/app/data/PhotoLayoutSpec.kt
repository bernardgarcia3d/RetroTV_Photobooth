package com.eventphotobooth.app.data

/**
 * The single geometry contract shared by capture guidance, review, templates,
 * previews, and print output. Coordinates returned by [photoRegions] are in
 * the caller's design coordinate system.
 */
data class PhotoLayoutSpec(
    val layoutId: LayoutId,
    val photoCount: Int,
    /** Width / height of each final photo window. */
    val photoAspectRatio: Float,
    val columns: Int,
    val rows: Int,
    val dimOutsideCaptureFrame: Boolean,
    /** Used by layout-aware receipt documents when no custom height is set. */
    val recommendedPhotoHeight: Float,
    val defaultCanvasHeight: Float,
    /** Two operator-authored artwork zones sit above the photo block. */
    val operatorTopAreaCount: Int,
    /** Complete artwork-owned photo area, in 576-dot receipt coordinates. */
    val artworkPhotoLeft: Float,
    val artworkPhotoTop: Float,
    val artworkPhotoWidth: Float,
    val artworkPhotoHeight: Float,
    /**
     * Complete printable receipt region for guest stickers. It intentionally
     * includes the operator-authored header and footer; only the physical
     * paper edge is protected when a glyph is clamped.
     */
    val stickerSafeArea: StickerSafeArea,
) {
    fun photoRegions(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        gap: Float = 12f,
    ): List<ReceiptPhotoRegion> {
        val safeWidth = width.coerceAtLeast(1f)
        val safeHeight = height.coerceAtLeast(1f)
        val safeGap = gap.coerceIn(0f, minOf(safeWidth, safeHeight) / 4f)
        val cellWidth = ((safeWidth - safeGap * (columns - 1)) / columns).coerceAtLeast(1f)
        val naturalCellHeight = cellWidth / photoAspectRatio
        val availableCellHeight = ((safeHeight - safeGap * (rows - 1)) / rows).coerceAtLeast(1f)
        // Every layout keeps its declared photo-window ratio. If the artwork
        // reserves more vertical space than the windows need, center the
        // complete composition instead of stretching/cropping the windows.
        val cellHeight = naturalCellHeight.coerceAtMost(availableCellHeight)
        val contentHeight = rows * cellHeight + safeGap * (rows - 1)
        val verticalOffset = ((safeHeight - contentHeight) / 2f).coerceAtLeast(0f)
        return List(photoCount) { index ->
            val row = index / columns
            val column = index % columns
            ReceiptPhotoRegion(
                x = left + column * (cellWidth + safeGap),
                y = top + verticalOffset + row * (cellHeight + safeGap),
                width = cellWidth,
                height = cellHeight,
            )
        }
    }
}

data class StickerSafeArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun clamp(placement: StickerPlacement): StickerPlacement {
        // A sticker glyph is drawn at a text baseline. Reserve a small
        // physical edge buffer so its visible pixels do not clip at the
        // paper boundary when it is scaled up. Header and footer are valid.
        val baseRadius = if (placement.artwork == null) 36f else 52f
        val radius = baseRadius * placement.scale.coerceIn(0.5f, 2.5f)
        val minimumX = (left + radius).coerceAtMost(right)
        val maximumX = (right - radius).coerceAtLeast(minimumX)
        val minimumY = (top + radius).coerceAtMost(bottom)
        val maximumY = (bottom - radius).coerceAtLeast(minimumY)
        return placement.copy(
            x = placement.x.coerceIn(minimumX, maximumX),
            y = placement.y.coerceIn(minimumY, maximumY),
            scale = placement.scale.coerceIn(0.5f, 2.5f),
        )
    }
}

object PhotoLayoutSpecs {
    val onePhoto = PhotoLayoutSpec(
        layoutId = LayoutId.ONE_PHOTO,
        photoCount = 1,
        photoAspectRatio = 4f / 5f,
        columns = 1,
        rows = 1,
        dimOutsideCaptureFrame = true,
        recommendedPhotoHeight = 675f,
        defaultCanvasHeight = 1256f,
        operatorTopAreaCount = 2,
        artworkPhotoLeft = 18f,
        artworkPhotoTop = 320f,
        artworkPhotoWidth = 540f,
        artworkPhotoHeight = 675f,
        stickerSafeArea = StickerSafeArea(left = 0f, top = 0f, right = 576f, bottom = 1256f),
    )
    val threePhoto = PhotoLayoutSpec(
        layoutId = LayoutId.THREE_STRIP,
        photoCount = 3,
        photoAspectRatio = 16f / 9f,
        columns = 1,
        rows = 3,
        dimOutsideCaptureFrame = false,
        recommendedPhotoHeight = 304f,
        defaultCanvasHeight = 1500f,
        operatorTopAreaCount = 2,
        artworkPhotoLeft = 18f,
        artworkPhotoTop = 320f,
        artworkPhotoWidth = 540f,
        artworkPhotoHeight = 936f,
        stickerSafeArea = StickerSafeArea(left = 0f, top = 0f, right = 576f, bottom = 1500f),
    )
    val fourPhoto = PhotoLayoutSpec(
        layoutId = LayoutId.FOUR_GRID,
        photoCount = 4,
        photoAspectRatio = 4f / 5f,
        columns = 2,
        rows = 2,
        dimOutsideCaptureFrame = true,
        recommendedPhotoHeight = 330f,
        defaultCanvasHeight = 1252f,
        operatorTopAreaCount = 2,
        artworkPhotoLeft = 18f,
        artworkPhotoTop = 320f,
        artworkPhotoWidth = 540f,
        artworkPhotoHeight = 672f,
        stickerSafeArea = StickerSafeArea(left = 0f, top = 0f, right = 576f, bottom = 1252f),
    )
    val sixPhoto = PhotoLayoutSpec(
        layoutId = LayoutId.SIX_GRID,
        photoCount = 6,
        photoAspectRatio = 4f / 5f,
        columns = 2,
        rows = 3,
        dimOutsideCaptureFrame = true,
        recommendedPhotoHeight = 330f,
        defaultCanvasHeight = 1566f,
        operatorTopAreaCount = 2,
        artworkPhotoLeft = 18f,
        artworkPhotoTop = 320f,
        artworkPhotoWidth = 540f,
        artworkPhotoHeight = 1014f,
        stickerSafeArea = StickerSafeArea(left = 0f, top = 0f, right = 576f, bottom = 1566f),
    )

    fun forLayout(layoutId: LayoutId): PhotoLayoutSpec = when (layoutId) {
        LayoutId.ONE_PHOTO -> onePhoto
        LayoutId.THREE_STRIP -> threePhoto
        LayoutId.FOUR_GRID -> fourPhoto
        LayoutId.SIX_GRID -> sixPhoto
    }
}
