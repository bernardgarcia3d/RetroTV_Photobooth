package com.eventphotobooth.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LayoutId(
    val displayName: String,
    val photoCount: Int,
    val description: String,
) {
    ONE_PHOTO("One photo", 1, "A full portrait keepsake"),
    THREE_STRIP("Three photos", 3, "Classic vertical strip"),
    FOUR_GRID("Four photos", 4, "Four-photo grid"),
    SIX_GRID("Six photos", 6, "Six-photo grid"),
}

enum class BoothOperationMode(val label: String) {
    RENTAL("Rental mode"),
    BUSINESS("Business mode"),
    CODE_ACCESS("Code access"),
}

data class LayoutDesign(
    val displayName: String,
    val accentColor: Long,
    val backgroundColor: Long = 0xFFFFFFFF,
    val frameColor: Long = 0xFFE1EAE6,
)

data class ReceiptTemplate(
    val title: String = "RECEIPT",
    val primaryText: String = "YOUR MOMENTS",
    val secondaryText: String = "KEEP THE MEMORY",
    val postTotalText: String = "THANK YOU FOR VISITING",
    val noteLabel: String = "LEAVE A NOTE HERE:",
    val noteText: String = "",
    val barcodeCaption: String = "THANK YOU!",
    val logoUri: String = "",
)

fun defaultPrintPriceTiersMinor() = listOf(2500, 4000, 6000, 8000, 10000, 12000, 14000, 16000, 18000, 20000)

private fun defaultLayoutDesigns() = LayoutId.entries.associateWith { layout ->
    LayoutDesign(
        displayName = layout.displayName,
        accentColor = 0xFF485A49,
    )
}

data class BoothConfiguration(
    val venueName: String = "Your venue",
    val header: String = "PHOTO MOMENT",
    val logoText: String = "PB",
    /** App-private normalized image used only on the Your Moment welcome screen. */
    val welcomeLogoPath: String = "",
    val footer: String = "Thanks for visiting",
    val priceMinor: Int = 2500,
    val currency: String = "PHP",
    val operationMode: BoothOperationMode = BoothOperationMode.BUSINESS,
    val businessPin: String = "2580",
    val printPriceTiersMinor: List<Int> = defaultPrintPriceTiersMinor(),
    val enabledLayouts: Set<LayoutId> = LayoutId.entries.toSet(),
    val primaryColor: Long = 0xFF485A49,
    val backgroundColor: Long = 0xFFF4ECE2,
    val layoutDesigns: Map<LayoutId, LayoutDesign> = defaultLayoutDesigns(),
    val crtIntensity: Int = 82,
    val reducedMotion: Boolean = false,
    val reducedGlitch: Boolean = false,
    val thermalPrintBrightness: Int = 50,
    val thermalDitherThreshold: Int = 120,
    val receiptBottomFeedLines: Int = 5,
    val receiptTemplate: ReceiptTemplate = ReceiptTemplate(),
    val paymentQrUris: List<String> = emptyList(),
    val paymentQrTitles: List<String> = emptyList(),
    val deliveryWorkerUrl: String = "",
    val deliveryBoothId: String = "",
    val deliveryDeviceKey: String = "",
    val accessCodeSharedKey: String = "",
) {
    val priceLabel: String
        get() = priceLabelForQuantity(1)

    val maxPrintQuantity: Int
        get() = 10

    val activePrintPriceTiersMinor: List<Int>
        get() = printPriceTiersMinor

    fun priceForQuantity(quantity: Int): Int {
        val safeQuantity = quantity.coerceIn(1, maxPrintQuantity)
        return activePrintPriceTiersMinor.getOrNull(safeQuantity - 1)?.takeIf { it >= 0 }
            ?: (priceMinor * safeQuantity)
    }

    fun priceLabelForQuantity(quantity: Int): String {
        val amount = priceForQuantity(quantity) / 100.0
        return when (currency) {
            "PHP" -> "₱" + "%.2f".format(amount)
            else -> currency + " " + "%.2f".format(amount)
        }
    }

    fun brandSnapshot() = BrandSnapshot(
        organizerName = venueName,
        header = header,
        footer = footer,
        primaryColor = primaryColor,
        backgroundColor = backgroundColor,
    )

    fun layoutDesign(layoutId: LayoutId): LayoutDesign = layoutDesigns[layoutId]
        ?: LayoutDesign(layoutId.displayName, primaryColor)
}

data class StickerPlacement(
    val sticker: StickerId,
    /** Receipt design coordinates in 576-dot width space, never UI-relative percentages. */
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    /** Null retains the original glyph-based stickers from saved sessions. */
    val artwork: StickerArtworkId? = null,
)

enum class StickerId(val label: String, val glyph: String) {
    HEART("Heart", "♥"),
    STAR("Star", "★"),
    SMILE("Smile", "☺"),
    SPARKLE("Sparkle", "✦"),
    LAUGH("Laugh", "😄"),
    COOL("Cool", "😎"),
    KISS("Kiss", "😘"),
    PARTY("Party", "🥳"),
    FIRE("Fire", "🔥"),
    RAINBOW("Rainbow", "🌈"),
    FLOWER("Flower", "🌸"),
    CAMERA("Camera", "📸"),
    COFFEE("Coffee", "☕"),
    CAKE("Cake", "🎂"),
    CROWN("Crown", "👑"),
    HEARTS("Hearts", "💕"),
}

/**
 * Release 1 cache. A backend-published configuration will replace this store
 * in Release 2; this prevents guest flow code from depending on hard-coded UI text.
 */
class LocalBoothConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): BoothConfiguration = preferences.getString(CONFIGURATION_KEY, null)
        ?.let(::configurationFromJson)
        ?: BoothConfiguration()

    fun save(configuration: BoothConfiguration) {
        preferences.edit().putString(CONFIGURATION_KEY, configurationToJson(configuration).toString()).apply()
    }

    fun exportSafe(configuration: BoothConfiguration): String = configurationToJson(configuration)
        .apply {
            remove("deliveryDeviceKey")
            remove("accessCodeSharedKey")
        }
        .toString()

    fun importSafe(value: String, current: BoothConfiguration): BoothConfiguration =
        configurationFromJson(value, current)

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
        .put("receiptTemplate", receiptTemplateToJson(configuration.receiptTemplate))
        .put("paymentQrUris", JSONArray(configuration.paymentQrUris))
        .put("paymentQrTitles", JSONArray(configuration.paymentQrTitles))
        .put("deliveryWorkerUrl", configuration.deliveryWorkerUrl)
        .put("deliveryBoothId", configuration.deliveryBoothId)
        .put("deliveryDeviceKey", configuration.deliveryDeviceKey)
        .put("accessCodeSharedKey", configuration.accessCodeSharedKey)

    private fun configurationFromJson(value: String, preserveSecrets: BoothConfiguration = BoothConfiguration()): BoothConfiguration = runCatching {
        val json = JSONObject(value)
        val legacyPrice = json.optInt("priceMinor", 2500).coerceAtLeast(0)
        fun readPrices(key: String): List<Int>? = json.optJSONArray(key)?.let { prices ->
            buildList {
                for (index in 0 until prices.length()) add(prices.optInt(index, 0).coerceAtLeast(0))
            }
        }?.takeIf { it.isNotEmpty() }
        val legacyPrices = readPrices("printPriceTiersMinor") ?: listOf(legacyPrice, legacyPrice * 2, legacyPrice * 3, legacyPrice * 4)
        val sharedPrices = readPrices("printPriceTiersMinor")
            ?: readPrices("businessPrintPriceTiersMinor")
            ?: readPrices("rentalPrintPriceTiersMinor")
            ?: legacyPrices
        BoothConfiguration(
            venueName = json.optString("venueName", "Your venue"),
            header = json.optString("header", "PHOTO MOMENT"),
            logoText = json.optString("logoText", "PB"),
            welcomeLogoPath = json.optString("welcomeLogoPath", ""),
            footer = json.optString("footer", "Thanks for visiting"),
            priceMinor = legacyPrice,
            currency = json.optString("currency", "PHP"),
            operationMode = runCatching { BoothOperationMode.valueOf(json.optString("operationMode", BoothOperationMode.BUSINESS.name)) }.getOrDefault(BoothOperationMode.BUSINESS),
            businessPin = json.optString("businessPin", "2580").filter(Char::isDigit).take(12),
            printPriceTiersMinor = sharedPrices,
            enabledLayouts = json.optJSONArray("enabledLayouts")?.let { layouts ->
                buildSet {
                    for (index in 0 until layouts.length()) {
                        runCatching { LayoutId.valueOf(layouts.getString(index)) }.getOrNull()?.let(::add)
                    }
                }.ifEmpty { LayoutId.entries.toSet() }
            } ?: LayoutId.entries.toSet(),
            primaryColor = json.optLong("primaryColor", 0xFF485A49),
            backgroundColor = json.optLong("backgroundColor", 0xFFF4ECE2),
            layoutDesigns = layoutDesignsFromJson(json.optJSONArray("layoutDesigns")),
            crtIntensity = json.optInt("crtIntensity", 82).coerceIn(0, 100),
            reducedMotion = json.optBoolean("reducedMotion", false),
            reducedGlitch = json.optBoolean("reducedGlitch", false),
            thermalPrintBrightness = json.optInt(
                "thermalPrintBrightness",
                json.optInt("xpD4601bProofBrightness", 50),
            ).coerceIn(0, 100),
            thermalDitherThreshold = json.optInt("thermalDitherThreshold", 120).coerceIn(80, 180),
            receiptBottomFeedLines = json.optInt("receiptBottomFeedLines", 5).coerceIn(2, 10),
            receiptTemplate = receiptTemplateFromJson(json.optJSONObject("receiptTemplate")),
            paymentQrUris = json.optJSONArray("paymentQrUris")?.let { items -> buildList { for (index in 0 until items.length()) add(items.optString(index)) } } ?: emptyList(),
            paymentQrTitles = json.optJSONArray("paymentQrTitles")?.let { items -> buildList { for (index in 0 until items.length()) add(items.optString(index)) } } ?: emptyList(),
            deliveryWorkerUrl = json.optString("deliveryWorkerUrl", preserveSecrets.deliveryWorkerUrl),
            deliveryBoothId = json.optString("deliveryBoothId", preserveSecrets.deliveryBoothId),
            deliveryDeviceKey = json.optString("deliveryDeviceKey", preserveSecrets.deliveryDeviceKey),
            accessCodeSharedKey = json.optString("accessCodeSharedKey", preserveSecrets.accessCodeSharedKey),
        )
    }.getOrElse { BoothConfiguration() }

    private companion object {
        const val PREFERENCES_NAME = "event_photobooth_configuration"
        const val CONFIGURATION_KEY = "configuration"
    }
}

private fun receiptTemplateToJson(template: ReceiptTemplate) = JSONObject()
    .put("title", template.title)
    .put("primaryText", template.primaryText)
    .put("secondaryText", template.secondaryText)
    .put("postTotalText", template.postTotalText)
    .put("noteLabel", template.noteLabel)
    .put("noteText", template.noteText)
    .put("barcodeCaption", template.barcodeCaption)
    .put("logoUri", template.logoUri)

private fun receiptTemplateFromJson(value: JSONObject?): ReceiptTemplate = ReceiptTemplate(
    title = value?.optString("title", "RECEIPT")?.ifBlank { "RECEIPT" } ?: "RECEIPT",
    primaryText = value?.optString("primaryText", "YOUR MOMENTS")?.let { text ->
        if (text.equals("PHOTO MOMENT", ignoreCase = true)) "YOUR MOMENTS" else text
    } ?: "YOUR MOMENTS",
    secondaryText = value?.optString("secondaryText", "KEEP THE MEMORY") ?: "KEEP THE MEMORY",
    postTotalText = value?.optString("postTotalText", "THANK YOU FOR VISITING") ?: "THANK YOU FOR VISITING",
    noteLabel = value?.optString("noteLabel", "LEAVE A NOTE HERE:")?.ifBlank { "LEAVE A NOTE HERE:" } ?: "LEAVE A NOTE HERE:",
    noteText = value?.optString("noteText", "") ?: "",
    barcodeCaption = value?.optString("barcodeCaption", "THANK YOU!") ?: "THANK YOU!",
    logoUri = value?.optString("logoUri", "") ?: "",
)

fun layoutDesignsToJson(designs: Map<LayoutId, LayoutDesign>) = JSONArray().apply {
    designs.forEach { (layoutId, design) ->
        put(
            JSONObject()
                .put("layoutId", layoutId.name)
                .put("displayName", design.displayName)
                .put("accentColor", design.accentColor)
                .put("backgroundColor", design.backgroundColor)
                .put("frameColor", design.frameColor),
        )
    }
}

fun layoutDesignsFromJson(value: JSONArray?): Map<LayoutId, LayoutDesign> {
    if (value == null) return defaultLayoutDesigns()
    val parsed = buildMap {
        for (index in 0 until value.length()) {
            val item = value.optJSONObject(index) ?: continue
            val layout = runCatching { LayoutId.valueOf(item.optString("layoutId")) }.getOrNull() ?: continue
            put(
                layout,
                LayoutDesign(
                    displayName = item.optString("displayName", layout.displayName).ifBlank { layout.displayName },
                    accentColor = item.optLong("accentColor", 0xFF485A49),
                    backgroundColor = item.optLong("backgroundColor", 0xFFFFFFFF),
                    frameColor = item.optLong("frameColor", 0xFFE1EAE6),
                ),
            )
        }
    }
    return defaultLayoutDesigns() + parsed
}
