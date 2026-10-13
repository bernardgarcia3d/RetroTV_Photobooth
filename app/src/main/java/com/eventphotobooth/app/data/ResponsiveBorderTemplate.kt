package com.eventphotobooth.app.data

/**
 * A decorative overlay for a receipt. Artwork stays separate from photos and
 * editable receipt text so one border can safely adapt to every strip layout.
 */
enum class ReceiptBorderSource {
    BUNDLED,
    OPERATOR_CUSTOM,
}

enum class BorderEdgeMode {
    STRETCH,
    REPEAT,
}

enum class BuiltInBorderStyle {
    NONE,
    CLASSIC,
    HEARTS,
    FILM,
    MIDNIGHT,
}

data class BorderSliceGuides(
    /** Left and right bounds of the flexible middle region, normalized to 0..1. */
    val left: Float = 0.06f,
    val right: Float = 0.94f,
    /** Top and bottom bounds of the flexible middle region, normalized to 0..1. */
    val top: Float = 0.045f,
    val bottom: Float = 0.955f,
) {
    fun sanitized(): BorderSliceGuides {
        val safeLeft = left.coerceIn(0.01f, 0.4f)
        val safeRight = right.coerceIn(safeLeft + 0.12f, 0.99f)
        val safeTop = top.coerceIn(0.01f, 0.4f)
        val safeBottom = bottom.coerceIn(safeTop + 0.12f, 0.99f)
        return BorderSliceGuides(safeLeft, safeRight, safeTop, safeBottom)
    }
}

/** The content-safe rectangle is derived from the nine-slice center. */
data class BorderSafeArea(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
)

data class ResponsiveBorderTemplate(
    val id: String,
    val name: String,
    val source: ReceiptBorderSource,
    /** Null means the artwork is intentionally reusable across every layout. */
    val layoutId: LayoutId? = null,
    /** Original operator file retained locally for later re-editing. */
    val originalAssetPath: String? = null,
    /** Alpha-preserving PNG used by the print renderer. */
    val normalizedAssetPath: String? = null,
    val thumbnailPath: String? = null,
    val builtInStyle: BuiltInBorderStyle? = null,
    val bundledAssetPath: String? = null,
    val guides: BorderSliceGuides = BorderSliceGuides(),
    val edgeMode: BorderEdgeMode = BorderEdgeMode.STRETCH,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val version: Int = 1,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    /** Optional complete receipt document. Null keeps legacy border-only behavior. */
    val document: ReceiptTemplateDocument? = null,
) {
    fun safeArea(): BorderSafeArea {
        if (builtInStyle == BuiltInBorderStyle.NONE) {
            return BorderSafeArea(left = 0f, right = 1f, top = 0f, bottom = 1f)
        }
        val safeGuides = guides.sanitized()
        return BorderSafeArea(
            left = safeGuides.left,
            right = safeGuides.right,
            top = safeGuides.top,
            bottom = safeGuides.bottom,
        )
    }

    fun isRenderable(): Boolean = source == ReceiptBorderSource.BUNDLED ||
        normalizedAssetPath?.isNotBlank() == true ||
        document != null

    fun isUsableForLayout(layout: LayoutId): Boolean = layoutId == null || layoutId == layout
}
