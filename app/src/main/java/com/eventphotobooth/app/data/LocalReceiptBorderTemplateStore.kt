package com.eventphotobooth.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tablet-only catalog for responsive receipt borders. Bundled defaults are
 * shipped in the APK as code; operator uploads are copied into app-private
 * storage immediately, so they remain available offline and need no URI grant.
 */
class LocalReceiptBorderTemplateStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val customRoot = File(context.filesDir, CUSTOM_ROOT).apply { mkdirs() }
    private val bundledRoot = File(context.filesDir, BUNDLED_ROOT).apply { mkdirs() }

    @Synchronized
    fun load(): List<ResponsiveBorderTemplate> {
        resetLegacyCatalogIfNeeded()
        seedBundledTemplates()
        return readTemplates().sortedWith(compareBy<ResponsiveBorderTemplate> { it.sortOrder }.thenBy { it.name.lowercase() })
    }

    @Synchronized
    fun save(templates: List<ResponsiveBorderTemplate>) {
        val normalized = templates
            .filter { it.isRenderable() }
            .mapIndexed { index, template -> template.copy(sortOrder = index, updatedAtEpochMs = System.currentTimeMillis()) }
        writeTemplates(normalized)
    }

    @Synchronized
    fun update(template: ResponsiveBorderTemplate): List<ResponsiveBorderTemplate> {
        val updated = load().map { current ->
            if (current.id == template.id) template.copy(updatedAtEpochMs = System.currentTimeMillis()) else current
        }
        save(updated)
        return load()
    }

    @Synchronized
    fun setDefault(templateId: String): List<ResponsiveBorderTemplate> {
        val updated = load().map { template ->
            if (template.id == templateId) {
                template.copy(enabled = true, isDefault = true, updatedAtEpochMs = System.currentTimeMillis())
            } else {
                template.copy(isDefault = false, updatedAtEpochMs = System.currentTimeMillis())
            }
        }
        save(updated)
        return load()
    }

    /** Selects one active artwork for only the requested photo-count layout. */
    @Synchronized
    fun setDefaultForLayout(templateId: String, layoutId: LayoutId): List<ResponsiveBorderTemplate> {
        val updated = load().map { template ->
            if (template.layoutId == layoutId) {
                template.copy(
                    enabled = if (template.id == templateId) true else template.enabled,
                    isDefault = template.id == templateId,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            } else template
        }
        save(updated)
        return load()
    }

    /** Re-enables all four shipped layouts without affecting uploaded artwork. */
    @Synchronized
    fun restoreBundledTemplates(): List<ResponsiveBorderTemplate> {
        val updated = load().map { template ->
            if (template.source == ReceiptBorderSource.BUNDLED) {
                template.copy(enabled = true, updatedAtEpochMs = System.currentTimeMillis())
            } else template
        }.toMutableList()
        LayoutId.entries.forEach { layout ->
            val candidates = updated.filter { it.layoutId == layout && it.enabled }
            if (candidates.none { it.isDefault }) {
                candidates.firstOrNull()?.let { candidate ->
                    val index = updated.indexOfFirst { it.id == candidate.id }
                    if (index >= 0) updated[index] = candidate.copy(isDefault = true)
                }
            }
        }
        save(updated)
        return load()
    }

    /** Removes only an operator-owned directory, never APK-provided defaults. */
    @Synchronized
    fun removeCustom(templateId: String): List<ResponsiveBorderTemplate> {
        val existing = load().firstOrNull { it.id == templateId } ?: return load()
        if (existing.source != ReceiptBorderSource.OPERATOR_CUSTOM) return load()
        val directory = File(customRoot, templateId).canonicalFile
        val rootPath = customRoot.canonicalFile.path + File.separator
        if (directory.path.startsWith(rootPath)) directory.deleteRecursively()
        save(load().filterNot { it.id == templateId })
        return load()
    }

    /** Creates a full editable receipt document from the current receipt copy. */
    @Synchronized
    fun createDocumentTemplate(name: String, receiptTemplate: ReceiptTemplate, layoutId: LayoutId? = null): ResponsiveBorderTemplate {
        val id = UUID.randomUUID().toString()
        val document = defaultReceiptTemplateDocument(
            receiptTemplate,
            id = id,
            name = name.trim().ifBlank { "Custom receipt" }.take(48),
            layoutId = layoutId,
        )
        val template = ResponsiveBorderTemplate(
            id = id,
            name = document.name,
            source = ReceiptBorderSource.OPERATOR_CUSTOM,
            layoutId = layoutId,
            document = document,
            sortOrder = load().size,
        )
        save(load() + template)
        return template
    }

    /** Duplicates either a built-in border or an existing document into an editable template. */
    @Synchronized
    fun duplicateAsDocument(template: ResponsiveBorderTemplate, name: String, receiptTemplate: ReceiptTemplate): ResponsiveBorderTemplate {
        val id = UUID.randomUUID().toString()
        val baseDocument = template.document
            ?: template.asReceiptDocument(receiptTemplate).document
            ?: defaultReceiptTemplateDocument(receiptTemplate)
        val document = baseDocument.copy(
            id = id,
            name = name.trim().ifBlank { "${template.name} copy" }.take(48),
            version = 1,
        )
        val copy = ResponsiveBorderTemplate(
            id = id,
            name = document.name,
            source = ReceiptBorderSource.OPERATOR_CUSTOM,
            layoutId = template.layoutId,
            // Keep the source artwork metadata so the saved copy has a real
            // thumbnail and remains editable/renderable after relaunch.
            originalAssetPath = template.originalAssetPath,
            normalizedAssetPath = template.normalizedAssetPath,
            thumbnailPath = template.thumbnailPath,
            guides = template.guides,
            edgeMode = template.edgeMode,
            document = document,
            sortOrder = load().size,
        )
        save(load() + copy)
        return copy
    }

    /** Imports border artwork into app-private storage. PNG/WebP with transparency
     * are recommended for overlays; JPEG is also accepted for full artwork that
     * will be placed underneath the editable receipt document when duplicated. */
    @Synchronized
    fun importBorder(uri: Uri, requestedName: String, layoutId: LayoutId? = null): ResponsiveBorderTemplate {
        // Read the provider URI once. Some tablet gallery/document providers
        // invalidate a transient URI grant between separate stream opens.
        val originalBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("The selected image could not be opened. Choose it again from Files or Gallery.")
        val bitmap = decodeBorder(originalBytes)
        try {
            validateArtworkForLayout(bitmap, layoutId)
            val id = UUID.randomUUID().toString()
            val directory = File(customRoot, id).apply { mkdirs() }
            check(directory.isDirectory) { "The local template folder could not be created." }

            val extension = extensionFor(context.contentResolver.getType(uri))
            val original = File(directory, "original.$extension")
            FileOutputStream(original).use { output -> output.write(originalBytes) }

            val normalized = File(directory, "normalized.png")
            FileOutputStream(normalized).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "The normalized border could not be saved." }
            }

            val thumbnail = File(directory, "thumbnail.png")
            val thumbnailWidth = 180
            val thumbnailHeight = (bitmap.height * (thumbnailWidth.toFloat() / bitmap.width)).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
            try {
                FileOutputStream(thumbnail).use { output ->
                    check(scaled.compress(Bitmap.CompressFormat.PNG, 90, output)) { "The template preview could not be saved." }
                }
            } finally {
                scaled.recycle()
            }

            val template = ResponsiveBorderTemplate(
                id = id,
                name = requestedName.trim().ifBlank { "Custom border" }.take(48),
                source = ReceiptBorderSource.OPERATOR_CUSTOM,
                layoutId = layoutId,
                originalAssetPath = original.absolutePath,
                normalizedAssetPath = normalized.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
                sortOrder = load().size,
            )
            save(load() + template)
            return template
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeBorder(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ByteArrayInputStream(bytes).use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid image file." }
        check(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_PIXELS) {
            "Choose an image smaller than 20 megapixels."
        }
        return ByteArrayInputStream(bytes).use { BitmapFactory.decodeStream(it) }
            ?: error("The selected image could not be decoded.")
    }

    /**
     * The selected layout owns the photo-window geometry. We can safely accept
     * exports at any resolution, but their paper proportion must match the
     * matching guide so artwork is never stretched onto a different receipt.
     */
    private fun validateArtworkForLayout(bitmap: Bitmap, layoutId: LayoutId?) {
        val layout = layoutId ?: return
        val spec = PhotoLayoutSpecs.forLayout(layout)
        val expectedAspect = 576f / spec.defaultCanvasHeight
        val uploadedAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        check(abs(uploadedAspect - expectedAspect) <= 0.012f) {
            "This ${layout.displayName} template has the wrong receipt proportion. " +
                "Export at 576 × ${spec.defaultCanvasHeight.toInt()} pixels, or the same aspect ratio."
        }
    }

    private fun seedBundledTemplates() {
        val current = readTemplates()
        val existingById = current.associateBy { it.id }
        val bundled = bundledTemplates().map { bundledTemplate ->
            val hydrated = hydrateBundledTemplate(bundledTemplate)
            existingById[hydrated.id]?.takeIf { it.source == ReceiptBorderSource.BUNDLED }?.copy(
                name = hydrated.name,
                builtInStyle = hydrated.builtInStyle,
                bundledAssetPath = hydrated.bundledAssetPath,
                normalizedAssetPath = hydrated.normalizedAssetPath,
                thumbnailPath = hydrated.thumbnailPath,
                layoutId = hydrated.layoutId,
                guides = hydrated.guides,
                edgeMode = hydrated.edgeMode,
                enabled = hydrated.enabled,
                isDefault = hydrated.isDefault,
                sortOrder = hydrated.sortOrder,
                version = hydrated.version,
                document = hydrated.document,
            ) ?: hydrated
        }
        val bundledIds = bundled.map { it.id }.toSet()
        val customs = current.filter { template ->
            template.source == ReceiptBorderSource.OPERATOR_CUSTOM &&
                template.id !in bundledIds
        }
        val merged = bundled + customs
        if (merged != current) writeTemplates(merged)
    }

    /**
     * The operator editor and the previous reusable-border catalog are no
     * longer part of the product flow. Clear that one-time catalog so an
     * already-installed tablet cannot keep showing stale Classic/Queen/
     * Newspaper/Playlist or editable copies after the fixed artwork update.
     */
    private fun resetLegacyCatalogIfNeeded() {
        if (preferences.getInt(CATALOG_VERSION_KEY, 0) >= FIXED_ARTWORK_CATALOG_VERSION) return
        customRoot.deleteRecursively()
        customRoot.mkdirs()
        bundledRoot.deleteRecursively()
        bundledRoot.mkdirs()
        preferences.edit()
            .remove(TEMPLATES_KEY)
            .putInt(CATALOG_VERSION_KEY, FIXED_ARTWORK_CATALOG_VERSION)
            .apply()
    }

    private fun hydrateBundledTemplate(template: ResponsiveBorderTemplate): ResponsiveBorderTemplate {
        val assetPath = template.bundledAssetPath ?: return template
        val directory = File(bundledRoot, template.id).apply { mkdirs() }
        val normalized = File(directory, "normalized.png")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(normalized).use(input::copyTo)
        }

        val bitmap = BitmapFactory.decodeFile(normalized.absolutePath)
            ?: error("Bundled receipt template could not be decoded: ${template.name}")
        try {
            val thumbnail = File(directory, "thumbnail.png")
            val thumbnailWidth = 180
            val thumbnailHeight = (bitmap.height * (thumbnailWidth.toFloat() / bitmap.width))
                .roundToInt()
                .coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
            try {
                FileOutputStream(thumbnail).use { output ->
                    check(scaled.compress(Bitmap.CompressFormat.PNG, 90, output)) {
                        "Bundled template preview could not be saved."
                    }
                }
            } finally {
                scaled.recycle()
            }
            return template.copy(
                normalizedAssetPath = normalized.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun readTemplates(): List<ResponsiveBorderTemplate> {
        val raw = preferences.getString(TEMPLATES_KEY, null) ?: return emptyList()
        return runCatching {
            val items = JSONArray(raw)
            buildList {
                for (index in 0 until items.length()) {
                    templateFromJson(items.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeTemplates(templates: List<ResponsiveBorderTemplate>) {
        preferences.edit().putString(TEMPLATES_KEY, JSONArray(templates.map(::templateToJson)).toString()).apply()
    }

    private fun templateToJson(template: ResponsiveBorderTemplate) = JSONObject()
        .put("id", template.id)
        .put("name", template.name)
        .put("source", template.source.name)
        .put("layoutId", template.layoutId?.name)
        .put("originalAssetPath", template.originalAssetPath)
        .put("normalizedAssetPath", template.normalizedAssetPath)
        .put("thumbnailPath", template.thumbnailPath)
        .put("builtInStyle", template.builtInStyle?.name)
        .put("bundledAssetPath", template.bundledAssetPath)
        .put("guideLeft", template.guides.left)
        .put("guideRight", template.guides.right)
        .put("guideTop", template.guides.top)
        .put("guideBottom", template.guides.bottom)
        .put("edgeMode", template.edgeMode.name)
        .put("enabled", template.enabled)
        .put("isDefault", template.isDefault)
        .put("sortOrder", template.sortOrder)
        .put("version", template.version)
        .put("createdAtEpochMs", template.createdAtEpochMs)
        .put("updatedAtEpochMs", template.updatedAtEpochMs)
        .put("document", template.document?.toJson())

    private fun templateFromJson(value: JSONObject): ResponsiveBorderTemplate? = runCatching {
        val source = ReceiptBorderSource.valueOf(value.getString("source"))
        val style = value.optString("builtInStyle").takeIf { it.isNotBlank() }?.let(BuiltInBorderStyle::valueOf)
        ResponsiveBorderTemplate(
            id = value.getString("id"),
            name = value.optString("name", "Receipt border").ifBlank { "Receipt border" },
            source = source,
            layoutId = value.optString("layoutId").takeIf { it.isNotBlank() }?.let { runCatching { LayoutId.valueOf(it) }.getOrNull() },
            originalAssetPath = value.optString("originalAssetPath").takeIf { it.isNotBlank() },
            normalizedAssetPath = value.optString("normalizedAssetPath").takeIf { it.isNotBlank() },
            thumbnailPath = value.optString("thumbnailPath").takeIf { it.isNotBlank() },
            builtInStyle = style,
            bundledAssetPath = value.optString("bundledAssetPath").takeIf { it.isNotBlank() },
            guides = BorderSliceGuides(
                left = value.optDouble("guideLeft", 0.06).toFloat(),
                right = value.optDouble("guideRight", 0.94).toFloat(),
                top = value.optDouble("guideTop", 0.045).toFloat(),
                bottom = value.optDouble("guideBottom", 0.955).toFloat(),
            ).sanitized(),
            edgeMode = value.optString("edgeMode", BorderEdgeMode.STRETCH.name).let { BorderEdgeMode.valueOf(it) },
            enabled = value.optBoolean("enabled", true),
            isDefault = value.optBoolean("isDefault", false),
            sortOrder = value.optInt("sortOrder", 0),
            version = value.optInt("version", 1),
            createdAtEpochMs = value.optLong("createdAtEpochMs", System.currentTimeMillis()),
            updatedAtEpochMs = value.optLong("updatedAtEpochMs", System.currentTimeMillis()),
            document = receiptTemplateDocumentFromJson(value.optJSONObject("document")),
        )
    }.getOrNull()

    private fun extensionFor(mimeType: String?): String = when (mimeType) {
        "image/webp" -> "webp"
        "image/png" -> "png"
        else -> "image"
    }

    private fun bundledTemplates(): List<ResponsiveBorderTemplate> = listOf(
        fixedArtworkTemplate("builtin-1-test-moments", "Test Moments", LayoutId.ONE_PHOTO, "receipt_templates/builtin_1_test_moments.jpg", 0, true),
        fixedArtworkTemplate("builtin-1-capture-your-story", "Capture Your Story Today", LayoutId.ONE_PHOTO, "receipt_templates/builtin_1_capture_your_story_today.jpg", 1),
        fixedArtworkTemplate("builtin-1-no-bad-days", "No Bad Days", LayoutId.ONE_PHOTO, "receipt_templates/builtin_1_no_bad_days.jpg", 2),

        fixedArtworkTemplate("builtin-3-test-moments", "Test Moments", LayoutId.THREE_STRIP, "receipt_templates/builtin_3_test_moments.jpg", 3, true),
        fixedArtworkTemplate("builtin-3-capture-your-story", "Capture Your Story Today", LayoutId.THREE_STRIP, "receipt_templates/builtin_3_capture_your_story_today.jpg", 4),
        fixedArtworkTemplate("builtin-3-no-bad-days", "No Bad Days", LayoutId.THREE_STRIP, "receipt_templates/builtin_3_no_bad_days.jpg", 5),
        fixedArtworkTemplate("builtin-3-plane-ticket", "Plane Ticket", LayoutId.THREE_STRIP, "receipt_templates/builtin_3_plane_ticket.jpg", 6),

        fixedArtworkTemplate("builtin-4-test-moments", "Test Moments", LayoutId.FOUR_GRID, "receipt_templates/builtin_4_test_moments.jpg", 7, true),
        fixedArtworkTemplate("builtin-4-capture-your-story", "Capture Your Story Today", LayoutId.FOUR_GRID, "receipt_templates/builtin_4_capture_your_story_today.jpg", 8),
        fixedArtworkTemplate("builtin-4-no-bad-days", "No Bad Days", LayoutId.FOUR_GRID, "receipt_templates/builtin_4_no_bad_days.jpg", 9),
        fixedArtworkTemplate("builtin-4-plane-ticket", "Plane Ticket", LayoutId.FOUR_GRID, "receipt_templates/builtin_4_plane_ticket.jpg", 10),

        fixedArtworkTemplate("builtin-6-test-moments", "Test Moments", LayoutId.SIX_GRID, "receipt_templates/builtin_6_test_moments.jpg", 11, true),
        fixedArtworkTemplate("builtin-6-capture-your-story", "Capture Your Story Today", LayoutId.SIX_GRID, "receipt_templates/builtin_6_capture_your_story_today.jpg", 12),
        fixedArtworkTemplate("builtin-6-no-bad-days", "No Bad Days", LayoutId.SIX_GRID, "receipt_templates/builtin_6_no_bad_days.jpg", 13),
        fixedArtworkTemplate("builtin-6-plane-ticket", "Plane Ticket", LayoutId.SIX_GRID, "receipt_templates/builtin_6_plane_ticket.jpg", 14),
    )

    private fun fixedArtworkTemplate(
        id: String,
        name: String,
        layoutId: LayoutId,
        assetPath: String,
        sortOrder: Int,
        isDefault: Boolean = false,
    ) = ResponsiveBorderTemplate(
        id = id,
        name = name,
        source = ReceiptBorderSource.BUNDLED,
        layoutId = layoutId,
        bundledAssetPath = assetPath,
        guides = BorderSliceGuides(left = 0.06f, right = 0.94f, top = 0.045f, bottom = 0.955f),
        isDefault = isDefault,
        sortOrder = sortOrder,
    )

    private companion object {
        const val PREFERENCES_NAME = "event_photobooth_receipt_borders"
        const val TEMPLATES_KEY = "templates"
        const val CUSTOM_ROOT = "receipt-templates/custom"
        const val BUNDLED_ROOT = "receipt-templates/bundled"
        const val MAX_PIXELS = 20_000_000L
        const val CATALOG_VERSION_KEY = "catalogVersion"
        const val FIXED_ARTWORK_CATALOG_VERSION = 2
    }
}
