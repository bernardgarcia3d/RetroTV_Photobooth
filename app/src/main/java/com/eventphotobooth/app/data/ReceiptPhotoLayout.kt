package com.eventphotobooth.app.data

/** A photo destination in the editor/print coordinate system. */
data class ReceiptPhotoRegion(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Photo slots are a layout preview, not individually authored objects. Their
 * combined bounds define the reserved photo area; the selected guest layout
 * divides that area into the same 1/3/4/6 arrangement used by printing.
 */
fun ReceiptTemplateDocument.photoRegions(layoutId: LayoutId): List<ReceiptPhotoRegion> {
    val slots = elements.filter { it.type == ReceiptElementType.PHOTO_SLOT }
    val rawLeft = slots.minOfOrNull { it.x } ?: 18f
    val top = slots.minOfOrNull { it.y } ?: 360f
    val rawRight = slots.maxOfOrNull { it.x + it.width } ?: (designWidth - 18f)
    val bottom = slots.maxOfOrNull { it.y + it.height } ?: (top + 594f)
    val inset = photoSideInset.coerceIn(0f, ((rawRight - rawLeft) / 2f - 20f).coerceAtLeast(0f))
    val left = rawLeft + inset
    val right = rawRight - inset
    val areaWidth = (right - left).coerceAtLeast(40f)
    val areaHeight = (bottom - top).coerceAtLeast(40f)
    val gap = minOf(12f, areaWidth / 28f, areaHeight / 28f)
    return PhotoLayoutSpecs.forLayout(layoutId).photoRegions(
        left = left,
        top = top,
        width = areaWidth,
        height = areaHeight,
        gap = gap,
    )
}
