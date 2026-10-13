package com.eventphotobooth.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The kinds of objects an operator can place on an 80 mm receipt. */
enum class ReceiptElementType {
    TEXT,
    LOGO,
    IMAGE,
    LINE,
    SPACER,
    PHOTO_SLOT,
    BARCODE,
}

enum class ReceiptTextAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/**
 * A single design object in printer coordinates. The document is authored at
 * 576 dots wide so the editor and the thermal renderer share one coordinate
 * system. Height is intentionally fixed for the editor canvas; the renderer
 * scales it uniformly when a different photo count changes the final height.
 */
data class ReceiptElement(
    val id: String = UUID.randomUUID().toString(),
    val type: ReceiptElementType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String = "",
    val assetUri: String = "",
    val fontSize: Float = 26f,
    val color: Long = 0xFF14191B,
    val bold: Boolean = false,
    val alignment: ReceiptTextAlignment = ReceiptTextAlignment.CENTER,
    val photoIndex: Int = 0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val zIndex: Int = 0,
) {
    companion object
}

data class ReceiptTemplateDocument(
    val id: String,
    val name: String,
    val designWidth: Float = 576f,
    val designHeight: Float = 1496f,
    val backgroundColor: Long = 0xFFFFFFFF,
    /** Horizontal inset reserved for border artwork beside the photo area. */
    val photoSideInset: Float = 36f,
    val elements: List<ReceiptElement> = emptyList(),
    val version: Int = 1,
) {
    fun sortedElements(): List<ReceiptElement> = elements.sortedBy { it.zIndex }

    fun withElement(element: ReceiptElement): ReceiptTemplateDocument = copy(
        elements = elements + element.copy(zIndex = elements.maxOfOrNull { it.zIndex }?.plus(1) ?: 0),
    )

    fun replaceElement(updated: ReceiptElement): ReceiptTemplateDocument = copy(
        elements = elements.map { if (it.id == updated.id) updated else it },
    )

    fun removeElement(elementId: String): ReceiptTemplateDocument = copy(
        elements = elements.filterNot { it.id == elementId },
    )

    fun hasRequiredElements(): Boolean = elements.any { it.type == ReceiptElementType.PHOTO_SLOT } &&
        elements.any { it.type == ReceiptElementType.BARCODE }
}

fun defaultReceiptTemplateDocument(
    template: ReceiptTemplate,
    id: String = UUID.randomUUID().toString(),
    name: String = "Custom receipt",
    layoutId: LayoutId? = null,
): ReceiptTemplateDocument {
    val layoutSpec = layoutId?.let(PhotoLayoutSpecs::forLayout)
    val photoTop = 360f
    val photoGapX = if (layoutSpec == null) 6f else 12f
    val photoGapY = 12f
    val photoColumns = layoutSpec?.columns ?: 2
    val photoRows = layoutSpec?.rows ?: 2
    val photoWidth = ((540f - photoGapX * (photoColumns - 1)) / photoColumns).coerceAtLeast(1f)
    val photoHeight = layoutSpec?.let { photoWidth / it.photoAspectRatio } ?: 312f
    val photoSectionHeight = photoRows * photoHeight + photoGapY * (photoRows - 1)
    val footerTop = if (layoutSpec == null) 1010f else photoTop + photoSectionHeight + 14f
    val designHeight = if (layoutSpec == null) 1496f else footerTop + 486f
    val elements = buildList {
        add(
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                x = 18f,
                y = 14f,
                width = 300f,
                height = 34f,
                text = template.title.uppercase(),
                fontSize = 26f,
                bold = true,
                alignment = ReceiptTextAlignment.LEFT,
                locked = true,
            ),
        )
        add(
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                x = 398f,
                y = 14f,
                width = 160f,
                height = 34f,
                text = "NO: {receipt_number}",
                fontSize = 18f,
                alignment = ReceiptTextAlignment.RIGHT,
                locked = true,
            ),
        )
        if (template.logoUri.isNotBlank()) {
            add(
                ReceiptElement(
                    type = ReceiptElementType.LOGO,
                    x = 200f,
                    y = 42f,
                    width = 176f,
                    height = 138f,
                    assetUri = template.logoUri,
                ),
            )
        }
        add(
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                x = 28f,
                y = 188f,
                width = 520f,
                height = 72f,
                text = template.primaryText,
                fontSize = 52f,
                bold = true,
            ),
        )
        add(
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                x = 28f,
                y = 258f,
                width = 520f,
                height = 34f,
                text = template.secondaryText,
                fontSize = 26f,
            ),
        )
        add(ReceiptElement( type = ReceiptElementType.LINE, x = 18f, y = 296f, width = 540f, height = 2f, locked = true ))
        add(
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                x = 28f,
                y = 304f,
                width = 520f,
                height = 34f,
                text = "DATE: {date}",
                fontSize = 26f,
                locked = true,
            ),
        )
        add(ReceiptElement( type = ReceiptElementType.LINE, x = 18f, y = 350f, width = 540f, height = 2f, locked = true ))
        List(layoutSpec?.photoCount ?: 4) { index ->
            val row = index / photoColumns
            val column = index % photoColumns
            add(
                ReceiptElement(
                    type = ReceiptElementType.PHOTO_SLOT,
                    x = 18f + column * (photoWidth + photoGapX),
                    y = photoTop + row * (photoHeight + photoGapY),
                    width = photoWidth,
                    height = photoHeight,
                    photoIndex = index,
                    locked = true,
                ),
            )
        }
        add(ReceiptElement( type = ReceiptElementType.LINE, x = 18f, y = footerTop, width = 540f, height = 2f, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.TEXT, x = 18f, y = footerTop + 10f, width = 180f, height = 34f, text = "TOTAL:", fontSize = 22f, bold = true, alignment = ReceiptTextAlignment.LEFT, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.TEXT, x = 378f, y = footerTop + 10f, width = 180f, height = 34f, text = "{total}", fontSize = 22f, bold = true, alignment = ReceiptTextAlignment.RIGHT, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.TEXT, x = 28f, y = footerTop + 52f, width = 520f, height = 34f, text = template.postTotalText, fontSize = 26f, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.LINE, x = 18f, y = footerTop + 90f, width = 540f, height = 2f, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.SPACER, x = 28f, y = footerTop + 100f, width = 520f, height = 190f, text = "Writing space" ))
        add(ReceiptElement( type = ReceiptElementType.TEXT, x = 28f, y = footerTop + 298f, width = 520f, height = 34f, text = template.noteLabel.uppercase(), fontSize = 26f, locked = true ))
        if (template.noteText.isNotBlank()) add(ReceiptElement( type = ReceiptElementType.TEXT, x = 28f, y = footerTop + 330f, width = 520f, height = 24f, text = template.noteText, fontSize = 15f ))
        add(ReceiptElement( type = ReceiptElementType.LINE, x = 110f, y = footerTop + 350f, width = 356f, height = 2f, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.BARCODE, x = 30f, y = footerTop + 365f, width = 516f, height = 58f, locked = true ))
        add(ReceiptElement( type = ReceiptElementType.TEXT, x = 28f, y = footerTop + 440f, width = 520f, height = 38f, text = template.barcodeCaption, fontSize = 30f, bold = true, locked = true ))
    }
    return ReceiptTemplateDocument(id = id, name = name, designHeight = designHeight, elements = elements)
}

/** Creates the document shell for an artwork-first border. */
private fun artworkBorderDocument(
    id: String,
    name: String,
    assetPath: String,
    layoutId: LayoutId? = null,
): ReceiptTemplateDocument {
    val layoutSpec = layoutId?.let(PhotoLayoutSpecs::forLayout)
    val designHeight = layoutSpec?.defaultCanvasHeight ?: 1496f
    val photoLeft = layoutSpec?.artworkPhotoLeft ?: 40f
    val photoTop = layoutSpec?.artworkPhotoTop ?: 240f
    val photoWidth = layoutSpec?.artworkPhotoWidth ?: 496f
    val photoHeight = layoutSpec?.artworkPhotoHeight ?: 880f
    val image = ReceiptElement(
        type = ReceiptElementType.IMAGE,
        x = 0f,
        y = 0f,
        width = 576f,
        height = designHeight,
        assetUri = assetPath,
        text = "$name artwork",
        locked = true,
        zIndex = -100,
    )
    val photoArea = ReceiptElement(
        type = ReceiptElementType.PHOTO_SLOT,
        x = photoLeft,
        y = photoTop,
        width = photoWidth,
        height = photoHeight,
        locked = true,
    )
    // The template artwork owns the physical paper length. Omitting this
    // value falls back to ReceiptTemplateDocument's old 1496px canvas and
    // prints a long blank tail below otherwise-correct artwork.
    return ReceiptTemplateDocument(
        id = id,
        name = name,
        designHeight = designHeight,
        // The JPEG author owns the visible margin. Its layout profile defines
        // the complete intended photo area, so artwork templates do not receive
        // the legacy editable-template inset.
        photoSideInset = 0f,
        elements = listOf(image, photoArea),
    )
}

/**
 * Converts a legacy border catalog entry into a complete document on demand.
 * Image-backed borders are artwork-first; Classic remains the only built-in
 * that uses the configurable receipt text document.
 */
fun ResponsiveBorderTemplate.asReceiptDocument(receiptTemplate: ReceiptTemplate): ResponsiveBorderTemplate {
    // Bundled artwork is layout-specific. Rebuild its document every time so a
    // document persisted by an older build (usually the legacy 1496px canvas)
    // can never make a short strip print at the wrong height.
    if (source == ReceiptBorderSource.BUNDLED && layoutId != null) {
        val path = normalizedAssetPath?.takeIf(String::isNotBlank)
        if (path != null) {
            return copy(document = artworkBorderDocument(id = id, name = name, assetPath = path, layoutId = layoutId))
        }
    }
    if (document != null) return this
    // Classic is the configurable legacy receipt. Keep it on the dynamic
    // renderer so its print height follows the selected photo layout.
    if (builtInStyle == BuiltInBorderStyle.NONE) return this
    val complete = normalizedAssetPath?.takeIf(String::isNotBlank)?.let { path ->
        artworkBorderDocument(id = id, name = name, assetPath = path, layoutId = layoutId)
    } ?: defaultReceiptTemplateDocument(receiptTemplate, id = id, name = name, layoutId = layoutId)
    return copy(document = complete)
}

fun ReceiptElement.toJson() = JSONObject()
    .put("id", id)
    .put("type", type.name)
    .put("x", x)
    .put("y", y)
    .put("width", width)
    .put("height", height)
    .put("text", text)
    .put("assetUri", assetUri)
    .put("fontSize", fontSize)
    .put("color", color)
    .put("bold", bold)
    .put("alignment", alignment.name)
    .put("photoIndex", photoIndex)
    .put("visible", visible)
    .put("locked", locked)
    .put("zIndex", zIndex)

fun ReceiptElement.Companion.fromJson(value: JSONObject): ReceiptElement? = runCatching {
    ReceiptElement(
        id = value.optString("id").ifBlank { UUID.randomUUID().toString() },
        type = ReceiptElementType.valueOf(value.optString("type", ReceiptElementType.TEXT.name)),
        x = value.optDouble("x", 0.0).toFloat(),
        y = value.optDouble("y", 0.0).toFloat(),
        width = value.optDouble("width", 100.0).toFloat().coerceAtLeast(1f),
        height = value.optDouble("height", 30.0).toFloat().coerceAtLeast(1f),
        text = value.optString("text"),
        assetUri = value.optString("assetUri"),
        fontSize = value.optDouble("fontSize", 26.0).toFloat().coerceIn(8f, 160f),
        color = value.optLong("color", 0xFF14191B),
        bold = value.optBoolean("bold", false),
        alignment = runCatching { ReceiptTextAlignment.valueOf(value.optString("alignment", ReceiptTextAlignment.CENTER.name)) }.getOrDefault(ReceiptTextAlignment.CENTER),
        photoIndex = value.optInt("photoIndex", 0).coerceAtLeast(0),
        visible = value.optBoolean("visible", true),
        locked = value.optBoolean("locked", false),
        zIndex = value.optInt("zIndex", 0),
    )
}.getOrNull()

fun ReceiptTemplateDocument.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("designWidth", designWidth)
    .put("designHeight", designHeight)
    .put("backgroundColor", backgroundColor)
    .put("photoSideInset", photoSideInset)
    .put("elements", JSONArray(elements.map { it.toJson() }))
    .put("version", version)

fun receiptTemplateDocumentFromJson(value: JSONObject?): ReceiptTemplateDocument? = value?.let { source -> runCatching {
    val elements = source.optJSONArray("elements")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                ReceiptElement.fromJson(array.optJSONObject(index))?.let(::add)
            }
        }
    }.orEmpty()
    ReceiptTemplateDocument(
        id = source.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = source.optString("name", "Custom receipt").ifBlank { "Custom receipt" },
        designWidth = source.optDouble("designWidth", 576.0).toFloat().coerceAtLeast(320f),
        designHeight = source.optDouble("designHeight", 1496.0).toFloat().coerceAtLeast(320f),
        backgroundColor = source.optLong("backgroundColor", 0xFFFFFFFF),
        photoSideInset = source.optDouble("photoSideInset", 36.0).toFloat().coerceIn(0f, 120f),
        elements = elements,
        version = source.optInt("version", 1).coerceAtLeast(1),
    )
}.getOrNull() }
