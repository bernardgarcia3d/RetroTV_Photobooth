package com.eventphotobooth.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores only local operational metadata. Media stays in app-private storage and
 * no guest information or cloud access tokens are persisted here.
 */
class LocalSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun saveSession(session: LocalPhotoSession) {
        val sessions = loadSessions().filterNot { it.id == session.id } + session
        preferences.edit()
            .putString(SESSIONS_KEY, JSONArray(sessions.map(::sessionToJson)).toString())
            .apply()
    }

    @Synchronized
    fun updatePrintStatus(sessionId: String, printStatus: PrintStatus) {
        loadSessions().firstOrNull { it.id == sessionId }?.let {
            saveSession(it.copy(printStatus = printStatus))
        }
    }

    fun loadSessions(): List<LocalPhotoSession> {
        val raw = preferences.getString(SESSIONS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(sessionFromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun savePrintJob(job: PrintJob) {
        val jobs = loadPrintJobs().filterNot { it.id == job.id } + job
        preferences.edit()
            .putString(PRINT_JOBS_KEY, JSONArray(jobs.map(::printJobToJson)).toString())
            .apply()
    }

    fun loadPrintJobs(): List<PrintJob> {
        val raw = preferences.getString(PRINT_JOBS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(printJobFromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun sessionToJson(session: LocalPhotoSession) = JSONObject()
        .put("id", session.id)
        .put("capturedAtEpochMs", session.capturedAtEpochMs)
        .put("sourcePhotoPaths", JSONArray(session.sourcePhotoPaths))
        .put("printStripPath", session.printStripPath)
        .put("layoutId", session.layoutId.name)
        .put("printQuantity", session.printQuantity)
        .put("stickers", JSONArray(session.stickerPlacements.map(::stickerToJson)))
        .put("guestPrintBrightness", session.guestPrintBrightness)
        .put("configuration", configurationToJson(session.configuration))
        .put("printStatus", session.printStatus.name)
        .put("uploadStatus", session.uploadStatus.name)
        .put("digitalStripPath", session.digitalStripPath)
        .put("animatedGifPath", session.animatedGifPath)
        .put("borderTemplateId", session.borderTemplateId)
        .put("borderTemplateVersion", session.borderTemplateVersion)
        .put("accessCodeId", session.accessCodeId)
        .put("accessCodePackageId", session.accessCodePackageId)
        .put("accessCodeBoothId", session.accessCodeBoothId)
        .put("paymongoOrderId", session.paymongoOrderId)
        .put("paymongoSessionId", session.paymongoSessionId)

    private fun sessionFromJson(value: JSONObject): LocalPhotoSession {
        val layoutId = value.optString("layoutId").let { raw ->
            runCatching { LayoutId.valueOf(raw) }.getOrDefault(LayoutId.THREE_STRIP)
        }
        return LocalPhotoSession(
            id = value.getString("id"),
            capturedAtEpochMs = value.getLong("capturedAtEpochMs"),
            sourcePhotoPaths = value.getJSONArray("sourcePhotoPaths").toStringList(),
            printStripPath = value.getString("printStripPath"),
            layoutId = layoutId,
            printQuantity = value.optInt("printQuantity", 1).coerceIn(1, 10),
            stickerPlacements = value.optJSONArray("stickers")?.toStickerList(layoutId).orEmpty(),
            guestPrintBrightness = value.optInt("guestPrintBrightness", 50).coerceIn(0, 100),
            configuration = value.optJSONObject("configuration")?.let(::configurationFromJson) ?: BoothConfiguration(),
            printStatus = value.optString("printStatus", PrintStatus.NOT_QUEUED.name).let { raw ->
                runCatching { PrintStatus.valueOf(raw) }.getOrDefault(PrintStatus.NOT_QUEUED)
            },
            uploadStatus = value.optString("uploadStatus", UploadStatus.LOCAL_ONLY.name).let { raw ->
                runCatching { UploadStatus.valueOf(raw) }.getOrDefault(UploadStatus.LOCAL_ONLY)
            },
            digitalStripPath = value.optString("digitalStripPath").takeIf { it.isNotBlank() },
            animatedGifPath = value.optString("animatedGifPath").takeIf { it.isNotBlank() },
            borderTemplateId = value.optString("borderTemplateId").takeIf { it.isNotBlank() },
            borderTemplateVersion = value.optInt("borderTemplateVersion", 0).takeIf { it > 0 },
            accessCodeId = value.optString("accessCodeId").takeIf { it.isNotBlank() },
            accessCodePackageId = value.optString("accessCodePackageId").takeIf { it.isNotBlank() },
            accessCodeBoothId = value.optString("accessCodeBoothId").takeIf { it.isNotBlank() },
            paymongoOrderId = value.optString("paymongoOrderId").takeIf { it.isNotBlank() },
            paymongoSessionId = value.optString("paymongoSessionId").takeIf { it.isNotBlank() },
        )
    }

    private fun printJobToJson(job: PrintJob) = JSONObject()
        .put("id", job.id)
        .put("sessionId", job.sessionId)
        .put("stripPath", job.stripPath)
        .put("quantity", job.quantity)
        .put("status", job.status.name)
        .put("attempts", job.attempts)
        .put("lastError", job.lastError)

    private fun printJobFromJson(value: JSONObject) = PrintJob(
        id = value.getString("id"),
        sessionId = value.getString("sessionId"),
        stripPath = value.getString("stripPath"),
        quantity = value.optInt("quantity", 1).coerceIn(1, 10),
        status = PrintStatus.valueOf(value.getString("status")),
        attempts = value.getInt("attempts"),
        lastError = value.optString("lastError").takeIf { it.isNotBlank() },
    )

    private fun JSONArray.toStringList() = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun stickerToJson(sticker: StickerPlacement) = JSONObject()
        .put("id", sticker.sticker.name)
        .put("artwork", sticker.artwork?.name)
        .put("x", sticker.x.toDouble())
        .put("y", sticker.y.toDouble())
        .put("scale", sticker.scale.toDouble())
        .put("coordinateSpace", "receipt_dots_v1")

    private fun JSONArray.toStickerList(layoutId: LayoutId) = buildList {
        val spec = PhotoLayoutSpecs.forLayout(layoutId)
        for (index in 0 until length()) {
            val value = getJSONObject(index)
            runCatching { StickerId.valueOf(value.getString("id")) }.getOrNull()?.let { sticker ->
                val scale = value.optDouble("scale", 1.0).toFloat().coerceIn(0.5f, 2.5f)
                val artwork = value.optString("artwork").takeIf(String::isNotBlank)
                    ?.let { raw -> runCatching { StickerArtworkId.valueOf(raw) }.getOrNull() }
                val placement = if (value.optString("coordinateSpace") == "receipt_dots_v1") {
                    StickerPlacement(sticker, value.optDouble("x", spec.stickerSafeArea.centerX.toDouble()).toFloat(), value.optDouble("y", spec.stickerSafeArea.centerY.toDouble()).toFloat(), scale, artwork)
                } else {
                    // Sessions written before v1 stored 0..1 screen-relative
                    // coordinates. Convert them once while preserving their
                    // relative position as closely as the new safe area allows.
                    StickerPlacement(sticker, value.optDouble("x", 0.5).toFloat() * 576f, value.optDouble("y", 0.5).toFloat() * spec.defaultCanvasHeight, scale, artwork)
                }
                add(spec.stickerSafeArea.clamp(placement))
            }
        }
    }

    private fun configurationToJson(configuration: BoothConfiguration) = JSONObject()
        .put("venueName", configuration.venueName)
        .put("header", configuration.header)
        .put("logoText", configuration.logoText)
        .put("welcomeLogoPath", configuration.welcomeLogoPath)
        .put("footer", configuration.footer)
        .put("priceMinor", configuration.priceMinor)
        .put("currency", configuration.currency)
        .put("operationMode", configuration.operationMode.name)
        .put("businessPin", configuration.businessPin)
        .put("printPriceTiersMinor", JSONArray(configuration.printPriceTiersMinor))
        .put("enabledLayouts", JSONArray(configuration.enabledLayouts.map(LayoutId::name)))
        .put("primaryColor", configuration.primaryColor)
        .put("backgroundColor", configuration.backgroundColor)
        .put("layoutDesigns", layoutDesignsToJson(configuration.layoutDesigns))
        .put("crtIntensity", configuration.crtIntensity)
        .put("reducedMotion", configuration.reducedMotion)
        .put("reducedGlitch", configuration.reducedGlitch)
        .put("thermalPrintBrightness", configuration.thermalPrintBrightness)
        .put("thermalDitherThreshold", configuration.thermalDitherThreshold)
        .put("receiptBottomFeedLines", configuration.receiptBottomFeedLines)
        .put("receiptTemplate", JSONObject()
            .put("title", configuration.receiptTemplate.title)
            .put("primaryText", configuration.receiptTemplate.primaryText)
            .put("secondaryText", configuration.receiptTemplate.secondaryText)
            .put("postTotalText", configuration.receiptTemplate.postTotalText)
            .put("noteLabel", configuration.receiptTemplate.noteLabel)
            .put("noteText", configuration.receiptTemplate.noteText)
            .put("barcodeCaption", configuration.receiptTemplate.barcodeCaption)
            .put("logoUri", configuration.receiptTemplate.logoUri))
        .put("paymentQrUris", JSONArray(configuration.paymentQrUris))
        .put("deliveryWorkerUrl", configuration.deliveryWorkerUrl)
        .put("deliveryBoothId", configuration.deliveryBoothId)
        .put("deliveryDeviceKey", configuration.deliveryDeviceKey)
        .put("accessCodeSharedKey", configuration.accessCodeSharedKey)

    private fun configurationFromJson(value: JSONObject): BoothConfiguration {
        val legacyPrice = value.optInt("priceMinor", 2500).coerceAtLeast(0)
        fun readPrices(key: String): List<Int>? = value.optJSONArray(key)?.let { prices ->
            buildList {
                for (index in 0 until prices.length()) add(prices.optInt(index, 0).coerceAtLeast(0))
            }
        }?.takeIf { it.isNotEmpty() }
        val legacyPrices = readPrices("printPriceTiersMinor") ?: listOf(legacyPrice, legacyPrice * 2, legacyPrice * 3, legacyPrice * 4)
        val sharedPrices = readPrices("printPriceTiersMinor")
            ?: readPrices("businessPrintPriceTiersMinor")
            ?: readPrices("rentalPrintPriceTiersMinor")
            ?: legacyPrices
        return BoothConfiguration(
        venueName = value.optString("venueName", "Your venue"),
        header = value.optString("header", "PHOTO MOMENT"),
        logoText = value.optString("logoText", "PB"),
        welcomeLogoPath = value.optString("welcomeLogoPath", ""),
        footer = value.optString("footer", "Thanks for visiting"),
        priceMinor = legacyPrice,
        currency = value.optString("currency", "PHP"),
        operationMode = runCatching { BoothOperationMode.valueOf(value.optString("operationMode", BoothOperationMode.BUSINESS.name)) }.getOrDefault(BoothOperationMode.BUSINESS),
        businessPin = value.optString("businessPin", "2580").filter(Char::isDigit).take(12),
        printPriceTiersMinor = sharedPrices,
        enabledLayouts = value.optJSONArray("enabledLayouts")?.let { layouts ->
            buildSet {
                for (index in 0 until layouts.length()) {
                    runCatching { LayoutId.valueOf(layouts.getString(index)) }.getOrNull()?.let(::add)
                }
            }.ifEmpty { LayoutId.entries.toSet() }
        } ?: LayoutId.entries.toSet(),
        primaryColor = value.optLong("primaryColor", 0xFF485A49),
        backgroundColor = value.optLong("backgroundColor", 0xFFF4ECE2),
        layoutDesigns = layoutDesignsFromJson(value.optJSONArray("layoutDesigns")),
        crtIntensity = value.optInt("crtIntensity", 82).coerceIn(0, 100),
        reducedMotion = value.optBoolean("reducedMotion", false),
        reducedGlitch = value.optBoolean("reducedGlitch", false),
        thermalPrintBrightness = value.optInt(
            "thermalPrintBrightness",
            value.optInt("xpD4601bProofBrightness", 50),
        ).coerceIn(0, 100),
        thermalDitherThreshold = value.optInt("thermalDitherThreshold", 120).coerceIn(80, 180),
        receiptBottomFeedLines = value.optInt("receiptBottomFeedLines", 5).coerceIn(2, 10),
        receiptTemplate = value.optJSONObject("receiptTemplate")?.let { template ->
            com.eventphotobooth.app.data.ReceiptTemplate(
                title = template.optString("title", "RECEIPT").ifBlank { "RECEIPT" },
                primaryText = template.optString("primaryText", "YOUR MOMENTS").let { text ->
                    if (text.equals("PHOTO MOMENT", ignoreCase = true)) "YOUR MOMENTS" else text
                },
                secondaryText = template.optString("secondaryText", "KEEP THE MEMORY"),
                postTotalText = template.optString("postTotalText", "THANK YOU FOR VISITING"),
                noteLabel = template.optString("noteLabel", "LEAVE A NOTE HERE:").ifBlank { "LEAVE A NOTE HERE:" },
                noteText = template.optString("noteText", ""),
                barcodeCaption = template.optString("barcodeCaption", "THANK YOU!"),
                logoUri = template.optString("logoUri", ""),
            )
        } ?: com.eventphotobooth.app.data.ReceiptTemplate(),
        paymentQrUris = value.optJSONArray("paymentQrUris")?.let { items -> buildList { for (index in 0 until items.length()) add(items.optString(index)) } } ?: emptyList(),
        deliveryWorkerUrl = value.optString("deliveryWorkerUrl"),
        deliveryBoothId = value.optString("deliveryBoothId"),
        deliveryDeviceKey = value.optString("deliveryDeviceKey"),
        accessCodeSharedKey = value.optString("accessCodeSharedKey"),
    )
    }

    private companion object {
        const val PREFERENCES_NAME = "event_photobooth_local_store"
        const val SESSIONS_KEY = "sessions"
        const val PRINT_JOBS_KEY = "print_jobs"
    }
}
