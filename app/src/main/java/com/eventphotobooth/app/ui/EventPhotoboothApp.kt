package com.eventphotobooth.app.ui

import com.eventphotobooth.app.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Bitmap.Config
import android.media.AudioManager
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.eventphotobooth.app.capture.CameraCaptureController
import com.eventphotobooth.app.data.AccessCodeClient
import com.eventphotobooth.app.data.GeneratedAccessBatch
import com.eventphotobooth.app.data.RedeemedAccessCode
import com.eventphotobooth.app.delivery.CloudDeliveryClient
import com.eventphotobooth.app.delivery.DeliveryLinks
import com.eventphotobooth.app.data.BoothConfiguration
import com.eventphotobooth.app.data.BoothOperationMode
import com.eventphotobooth.app.digital.LocalGifRenderer
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.LayoutDesign
import com.eventphotobooth.app.data.LocalBrandingAssetStore
import com.eventphotobooth.app.data.PhotoLayoutSpecs
import com.eventphotobooth.app.data.LocalBoothConfigurationStore
import com.eventphotobooth.app.data.LocalPhotoSession
import com.eventphotobooth.app.data.LocalReceiptBorderTemplateStore
import com.eventphotobooth.app.data.LocalSessionStore
import com.eventphotobooth.app.data.OperatorPinStore
import com.eventphotobooth.app.data.PrintJob
import com.eventphotobooth.app.data.PrintStatus
import com.eventphotobooth.app.data.ReceiptTemplate
import com.eventphotobooth.app.data.ResponsiveBorderTemplate
import com.eventphotobooth.app.data.asReceiptDocument
import com.eventphotobooth.app.data.StickerId
import com.eventphotobooth.app.data.StickerArtworkId
import com.eventphotobooth.app.data.StickerPlacement
import com.eventphotobooth.app.data.StickerSheet
import com.eventphotobooth.app.printer.DiscoveredUsbDevice
import com.eventphotobooth.app.printer.LocalPrintQueue
import com.eventphotobooth.app.printer.UsbPrinterDiscovery
import com.eventphotobooth.app.render.StripRenderer
import com.eventphotobooth.app.render.drawReceiptStickers
import com.eventphotobooth.app.render.drawStickerSafeArea
import com.eventphotobooth.app.render.loadStickerArtwork
import com.eventphotobooth.app.media.GalleryMediaExporter
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed interface BoothScreen {
    data object Welcome : BoothScreen
    data object Title : BoothScreen
    data object Payment : BoothScreen
    data class BusinessPin(val printQuantity: Int) : BoothScreen
    data object CodeAccess : BoothScreen
    data object LayoutSelection : BoothScreen
    /** Live camera framing before the guest deliberately starts the countdown. */
    data class CaptureReady(
        val layoutId: LayoutId,
        val existingPhotos: List<File> = emptyList(),
        val retakeIndex: Int? = null,
    ) : BoothScreen
    data class Countdown(val layoutId: LayoutId, val seconds: Int, val captureNumber: Int, val photoCount: Int) : BoothScreen
    data class Review(val photos: List<File>) : BoothScreen
    data class BorderSelection(val photos: List<File>) : BoothScreen
    data class Stickers(val photos: List<File>) : BoothScreen
    data class Processing(val photos: List<File>, val layoutId: LayoutId) : BoothScreen
    data class PrintingAnimation(val session: LocalPhotoSession, val printStatus: PrintStatus, val printMessage: String?, val deliveryLinks: DeliveryLinks? = null) : BoothScreen
    data class Complete(val session: LocalPhotoSession, val printStatus: PrintStatus, val printMessage: String?, val deliveryLinks: DeliveryLinks? = null) : BoothScreen
    data class DigitalDelivery(val session: LocalPhotoSession, val deliveryLinks: DeliveryLinks? = null) : BoothScreen
    data object OperatorGate : BoothScreen
    data object OperatorSettings : BoothScreen
    data object ProvisioningSettings : BoothScreen
    data class Failure(val message: String) : BoothScreen
}

@Composable
fun EventPhotoboothApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStore = remember { LocalSessionStore(context.applicationContext) }
    val configurationStore = remember { LocalBoothConfigurationStore(context.applicationContext) }
    val borderTemplateStore = remember { LocalReceiptBorderTemplateStore(context.applicationContext) }
    val pinStore = remember { OperatorPinStore(context.applicationContext) }
    val printQueue = remember { LocalPrintQueue(sessionStore) }
    val printerDiscovery = remember { UsbPrinterDiscovery(context.applicationContext) }
    val renderer = remember { StripRenderer() }
    val gifRenderer = remember { LocalGifRenderer() }
    val galleryMediaExporter = remember { GalleryMediaExporter(context.applicationContext.contentResolver) }
    val deliveryClient = remember { CloudDeliveryClient() }
    val cameraController = remember { CameraCaptureController(context.applicationContext) }
    val countdownTone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85) }
    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(Unit) {
        onDispose {
            countdownTone.release()
            shutterSound.release()
        }
    }
    var configuration by remember { mutableStateOf(configurationStore.load()) }
    var borderTemplates by remember { mutableStateOf(borderTemplateStore.load()) }
    var selectedLayout by remember { mutableStateOf(LayoutId.THREE_STRIP) }
    var selectedBorderTemplateId by remember {
        mutableStateOf(borderTemplates.firstOrNull { it.isDefault && it.enabled }?.id ?: borderTemplates.firstOrNull { it.enabled }?.id)
    }
    val renderableTemplates = borderTemplates.filter { it.enabled && it.isRenderable() }
    // Once a layout has dedicated artwork, hide legacy/global documents from
    // the guest flow. Otherwise an old 1496px receipt can still be selected
    // and leave a large blank tail below the 1-photo artwork.
    val layoutTemplates = renderableTemplates.filter { it.layoutId == selectedLayout }
    val activeBorderTemplates = if (layoutTemplates.isNotEmpty()) {
        layoutTemplates
    } else {
        renderableTemplates.filter { it.isUsableForLayout(selectedLayout) }
    }
    // Prefer an exact layout template over a legacy/global template. A global
    // template may still be present in the operator catalog, but it must never
    // silently replace the 1/3/4/6-specific artwork and canvas geometry.
    val selectedBorderTemplate = activeBorderTemplates.firstOrNull {
        it.layoutId == selectedLayout && it.id == selectedBorderTemplateId
    } ?: activeBorderTemplates.firstOrNull {
        it.layoutId == selectedLayout && it.isDefault
    } ?: activeBorderTemplates.firstOrNull {
        it.layoutId == selectedLayout
    } ?: activeBorderTemplates.firstOrNull {
        it.layoutId == null && it.id == selectedBorderTemplateId
    } ?: activeBorderTemplates.firstOrNull { it.layoutId == null && it.isDefault }
        ?: activeBorderTemplates.firstOrNull()
    val selectedReceiptDocumentTemplate = selectedBorderTemplate?.asReceiptDocument(configuration.receiptTemplate)
    val accessCodeClient = remember(configuration.accessCodeSharedKey, configuration.deliveryBoothId) {
        AccessCodeClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, configuration.accessCodeSharedKey, configuration.deliveryBoothId.ifBlank { "booth-local" })
    }
    var printQuantity by remember { mutableStateOf(1) }
    var stickers by remember { mutableStateOf<List<StickerPlacement>>(emptyList()) }
    var guestBrightness by remember { mutableStateOf(50f) }
    var redeemedAccessCode by remember { mutableStateOf<RedeemedAccessCode?>(null) }
    var screen by remember { mutableStateOf<BoothScreen>(BoothScreen.Welcome) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun beginCapture(
        captureLayout: LayoutId,
        existingPhotos: List<File> = emptyList(),
        retakeIndex: Int? = null,
    ) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            scope.launch {
                runCatching {
                    require(retakeIndex == null || retakeIndex in 0 until captureLayout.photoCount) {
                        "The selected photo is not part of this layout."
                    }
                    val sessionDirectory = existingPhotos.firstOrNull()?.parentFile
                        ?: File(context.filesDir, "sessions/${UUID.randomUUID()}").apply { mkdirs() }
                    val workingPhotos = MutableList<File?>(captureLayout.photoCount) { index -> existingPhotos.getOrNull(index) }
                    val indexesToCapture = retakeIndex?.let(::listOf) ?: (0 until captureLayout.photoCount).toList()
                    indexesToCapture.forEach { index ->
                        for (seconds in COUNTDOWN_SECONDS downTo 1) {
                            screen = BoothScreen.Countdown(captureLayout, seconds, index + 1, captureLayout.photoCount)
                            countdownTone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                            delay(1_000)
                        }
                        shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                        workingPhotos[index] = cameraController.capture(File(sessionDirectory, "source-${index + 1}.jpg"))
                    }
                    workingPhotos.map { requireNotNull(it) { "The capture session is incomplete." } }
                }.onSuccess { photos -> screen = BoothScreen.Review(photos) }
                    .onFailure { error -> screen = BoothScreen.Failure(error.message ?: "The camera could not capture this session.") }
            }
        }
    }

    fun prepareCapture(
        captureLayout: LayoutId,
        existingPhotos: List<File> = emptyList(),
        retakeIndex: Int? = null,
    ) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            screen = BoothScreen.CaptureReady(captureLayout, existingPhotos, retakeIndex)
        }
    }

    val createPrintSession: (List<File>) -> Unit = { sourcePhotos ->
        scope.launch {
            screen = BoothScreen.Processing(sourcePhotos, selectedLayout)
            runCatching {
                withContext(Dispatchers.Default) {
                    val sessionId = UUID.randomUUID().toString()
                    val receiptTimestampMs = System.currentTimeMillis()
                    val outputDirectory = File(context.filesDir, "completed/$sessionId").apply { mkdirs() }
                    val printStrip = renderer.renderPrintStrip(
                        sourcePhotos = sourcePhotos,
                        brand = configuration.brandSnapshot(),
                        layoutId = selectedLayout,
                        stickers = stickers,
                        outputFile = File(outputDirectory, "print-strip.png"),
                        receiptTemplate = configuration.receiptTemplate,
                        receiptNumber = sessionId.hashCode().toUInt().toString().takeLast(6).padStart(6, '0'),
                        receiptTimestampMs = receiptTimestampMs,
                        totalLabel = configuration.priceLabelForQuantity(printQuantity),
                        contentResolver = context.contentResolver,
                        layoutDesign = configuration.layoutDesign(selectedLayout),
                        guestPhotoBrightness = guestBrightness.roundToInt().coerceIn(0, 100),
                        borderTemplate = selectedReceiptDocumentTemplate,
                    )
                    val animatedGif = gifRenderer.renderAnimatedGifStrip(
                        sourcePhotos = sourcePhotos,
                        finalStripFile = printStrip,
                        brand = configuration.brandSnapshot(),
                        outputFile = File(outputDirectory, "animated-gif-strip.gif"),
                        layoutId = selectedLayout,
                        guestPhotoBrightness = guestBrightness.roundToInt().coerceIn(0, 100),
                    )
                    galleryMediaExporter.export(
                        sessionId = sessionId,
                        photoFile = printStrip,
                        gifFile = animatedGif,
                    )
                    LocalPhotoSession(
                        id = sessionId,
                        sourcePhotoPaths = sourcePhotos.map(File::getAbsolutePath),
                        printStripPath = printStrip.absolutePath,
                        layoutId = selectedLayout,
                        printQuantity = printQuantity,
                        stickerPlacements = stickers,
                        guestPrintBrightness = guestBrightness.roundToInt().coerceIn(0, 100),
                        configuration = configuration,
                        digitalStripPath = printStrip.absolutePath,
                        animatedGifPath = animatedGif.absolutePath,
                        borderTemplateId = selectedReceiptDocumentTemplate?.id,
                        borderTemplateVersion = selectedReceiptDocumentTemplate?.version,
                        accessCodeId = redeemedAccessCode?.accessCodeId,
                        accessCodePackageId = redeemedAccessCode?.packageId,
                        accessCodeBoothId = configuration.deliveryBoothId.ifBlank { "booth-local" },
                    )
                }
            }.onSuccess { session ->
                val deliveryLinks = withContext(Dispatchers.IO) {
                    deliveryClient.upload(configuration, session.id, File(session.digitalStripPath!!), File(session.animatedGifPath!!)).getOrNull()
                }
                sessionStore.saveSession(session)
                val accessCode = redeemedAccessCode
                if (accessCode != null && deliveryLinks != null) {
                    accessCodeClient.complete(accessCode.accessCodeId, session.id)
                        .onFailure { error -> println("Access code completion deferred: ${error.message}") }
                }
                val printJob = printQueue.enqueue(session.id, session.printStripPath, session.printQuantity)
                screen = BoothScreen.PrintingAnimation(session.copy(printStatus = PrintStatus.QUEUED), PrintStatus.QUEUED, if (deliveryLinks == null) "Printing your strip · cloud delivery unavailable" else "Preparing your strip", deliveryLinks)
                // Let the physical-print animation complete and remain visible
                // long enough for guests to understand what is happening.
                delay(4_500)
                val permittedPrinters = withContext(Dispatchers.IO) { printerDiscovery.discover().filter { it.hasPermission } }
                val printResult = when (permittedPrinters.size) {
                    0 -> Result.failure(IllegalStateException("No USB printer with permission is available. The strip is queued for manual retry."))
                    1 -> {
                        val printingJob = printQueue.markPrinting(printJob)
                        screen = BoothScreen.PrintingAnimation(session.copy(printStatus = PrintStatus.PRINTING), PrintStatus.PRINTING, "Printing your strip", deliveryLinks)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                repeat(session.printQuantity) {
                                    printerDiscovery.printXp80tsStrip(
                                        deviceName = permittedPrinters.first().deviceName,
                                        stripFile = File(session.printStripPath),
                                        brightness = configuration.thermalPrintBrightness,
                                        ditherThreshold = configuration.thermalDitherThreshold,
                                        bottomFeedLines = configuration.receiptBottomFeedLines,
                                    ).getOrThrow()
                                }
                            }
                        }.map { printingJob }
                    }
                    else -> Result.failure(IllegalStateException("More than one USB printer has permission. The strip is queued so the operator can choose the correct printer."))
                }
                printResult.onSuccess { printedJob ->
                    printQueue.markPrinted(printedJob)
                    scope.launch {
                        delay(600)
                        screen = BoothScreen.DigitalDelivery(session.copy(printStatus = PrintStatus.PRINTED), deliveryLinks)
                    }
                }.onFailure { error ->
                    val message = error.message ?: "Auto-print failed."
                    val latestJob = sessionStore.loadPrintJobs().firstOrNull { it.id == printJob.id } ?: printJob
                    printQueue.markFailed(latestJob, message)
                    screen = BoothScreen.Complete(session.copy(printStatus = PrintStatus.FAILED), PrintStatus.FAILED, message, deliveryLinks)
                }
            }.onFailure { error -> screen = BoothScreen.Failure(error.message ?: "The print strip could not be created.") }
        }
    }

    when (val current = screen) {
        BoothScreen.Welcome -> WelcomeScreen(configuration, hasCameraPermission, cameraController, { screen = BoothScreen.Title }, { screen = BoothScreen.OperatorGate })
        BoothScreen.Title -> TitleScreen(configuration, cameraController, {
            printQuantity = 1
            redeemedAccessCode = null
            selectedBorderTemplateId = activeBorderTemplates.firstOrNull { it.isDefault }?.id ?: activeBorderTemplates.firstOrNull()?.id
            screen = when (configuration.operationMode) {
                BoothOperationMode.RENTAL -> BoothScreen.LayoutSelection
                BoothOperationMode.CODE_ACCESS -> BoothScreen.CodeAccess
                BoothOperationMode.BUSINESS -> BoothScreen.Payment
            }
        }, { screen = BoothScreen.Welcome }, { screen = BoothScreen.OperatorGate })
        BoothScreen.Payment -> PaymentScreen(configuration, { quantity ->
            printQuantity = quantity
            screen = BoothScreen.BusinessPin(quantity)
        }, { screen = BoothScreen.Welcome })
        is BoothScreen.BusinessPin -> BusinessPinScreen(
            configuration = configuration,
            printQuantity = current.printQuantity,
            onSuccess = { screen = BoothScreen.LayoutSelection },
            onBack = { screen = BoothScreen.Payment },
        )
        BoothScreen.CodeAccess -> AccessCodeScreen(
            client = accessCodeClient,
            configuration = configuration,
            onSuccess = { redeemed ->
                redeemedAccessCode = redeemed
                val layout = runCatching { LayoutId.valueOf(redeemed.packageId) }.getOrNull()
                    ?: configuration.enabledLayouts.firstOrNull()
                if (layout == null) screen = BoothScreen.Failure("The access code has no available photostrip layout.")
                else {
                    selectedLayout = layout
                    selectedBorderTemplateId = borderTemplates.firstOrNull {
                        it.enabled && it.isRenderable() && it.layoutId == layout && it.isDefault
                    }?.id ?: borderTemplates.firstOrNull {
                        it.enabled && it.isRenderable() && it.layoutId == layout
                    }?.id
                    printQuantity = 1
                    stickers = emptyList()
                    guestBrightness = 50f
                    prepareCapture(layout)
                }
            },
            onBack = { screen = BoothScreen.Welcome },
        )
        BoothScreen.LayoutSelection -> LayoutSelectionScreen(configuration, { layout ->
            selectedLayout = layout
            // A template selection belongs to the layout. Never carry the
            // previous strip's document into a new photo-count geometry.
            selectedBorderTemplateId = borderTemplates.firstOrNull {
                it.enabled && it.isRenderable() && it.layoutId == layout && it.isDefault
            }?.id ?: borderTemplates.firstOrNull {
                it.enabled && it.isRenderable() && it.layoutId == layout
            }?.id
            stickers = emptyList()
            guestBrightness = 50f
            prepareCapture(layout)
        }, { screen = BoothScreen.Welcome })
        is BoothScreen.CaptureReady -> CameraReadyScreen(
            cameraController = cameraController,
            layoutId = current.layoutId,
            captureNumber = (current.retakeIndex ?: 0) + 1,
            photoCount = current.layoutId.photoCount,
            isRetake = current.retakeIndex != null,
            onStart = { beginCapture(current.layoutId, current.existingPhotos, current.retakeIndex) },
        )
        is BoothScreen.Countdown -> CameraCaptureScreen(cameraController, current.layoutId, current.seconds, current.captureNumber, current.photoCount)
        is BoothScreen.Review -> ReviewScreen(
            photos = current.photos,
            layoutId = selectedLayout,
            configuration = configuration,
            onRetakePhoto = { index -> prepareCapture(selectedLayout, current.photos, index) },
            onRetakeAll = { prepareCapture(selectedLayout) },
            onContinue = { screen = BoothScreen.BorderSelection(current.photos) },
        )
        is BoothScreen.BorderSelection -> ReceiptBorderSelectionScreen(
            photos = current.photos,
            layoutId = selectedLayout,
            configuration = configuration,
            templates = activeBorderTemplates,
            selectedTemplateId = selectedBorderTemplate?.id,
            onSelect = { selectedBorderTemplateId = it },
            onBack = { screen = BoothScreen.Review(current.photos) },
            onContinue = { screen = BoothScreen.Stickers(current.photos) },
        )
        is BoothScreen.Stickers -> StickerScreen(
            photos = current.photos,
            layoutId = selectedLayout,
            configuration = configuration,
            borderTemplate = selectedReceiptDocumentTemplate,
            stickers = stickers,
            printQuantity = printQuantity,
            allowPrintQuantityEditing = configuration.operationMode == BoothOperationMode.RENTAL,
            onPrintQuantityChange = { printQuantity = it },
            guestBrightness = guestBrightness,
            onBrightnessChange = { guestBrightness = it },
            onChange = { stickers = it },
            onBack = { screen = BoothScreen.BorderSelection(current.photos) },
            onConfirm = { createPrintSession(current.photos) },
        )
        is BoothScreen.Processing -> ProcessingScreen(
            photos = current.photos,
            layoutId = current.layoutId,
            configuration = configuration,
            stickers = stickers,
            guestBrightness = guestBrightness.roundToInt(),
            borderTemplate = selectedReceiptDocumentTemplate,
        )
        is BoothScreen.PrintingAnimation -> PrintingAnimationScreen(current.session, current.printStatus, current.printMessage)
        is BoothScreen.Complete -> CompleteScreen(current.session, current.printStatus, current.printMessage) { screen = BoothScreen.Welcome }
        is BoothScreen.DigitalDelivery -> DigitalDeliveryScreen(current.session, current.deliveryLinks) { screen = BoothScreen.Welcome }
        BoothScreen.OperatorGate -> OperatorGateScreen(pinStore, { screen = BoothScreen.OperatorSettings }, { screen = BoothScreen.Welcome })
        BoothScreen.OperatorSettings -> OperatorSettingsScreen(
            configuration, sessionStore.loadSessions().size,
            sessionStore.loadPrintJobs(),
            sessionStore.loadSessions().maxByOrNull { it.capturedAtEpochMs },
            sessionStore,
            borderTemplateStore,
            borderTemplates,
            { updatedTemplates ->
                borderTemplates = updatedTemplates
                selectedBorderTemplateId = updatedTemplates.firstOrNull {
                    it.enabled && it.layoutId == selectedLayout && it.isDefault
                }?.id ?: updatedTemplates.firstOrNull {
                    it.enabled && it.layoutId == selectedLayout
                }?.id
            },
            { updated -> configuration = updated; configurationStore.save(updated) },
            { screen = BoothScreen.ProvisioningSettings },
            { screen = BoothScreen.Welcome },
        )
        BoothScreen.ProvisioningSettings -> ProvisioningSettingsScreen(
            configuration = configuration,
            configurationStore = configurationStore,
            onSave = { updated -> configuration = updated; configurationStore.save(updated) },
            onBack = { screen = BoothScreen.OperatorSettings },
        )
        is BoothScreen.Failure -> FailureScreen(current.message) { screen = BoothScreen.Welcome }
    }
}

@Composable
private fun WelcomeScreen(configuration: BoothConfiguration, hasCameraPermission: Boolean, cameraController: CameraCaptureController, onStart: () -> Unit, onOperator: () -> Unit) {
    val recTransition = rememberInfiniteTransition(label = "record-indicator")
    val recDotAlpha by recTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
        label = "record-dot-alpha",
    )
    val recordDotAlpha = if (configuration.reducedMotion) 1f else recDotAlpha
    Box(Modifier.fillMaxSize().background(if (hasCameraPermission) BoothPalette.ink else BoothPalette.paper)) {
        if (hasCameraPermission) {
            CameraPreview(cameraController, Modifier.fillMaxSize())
            CrtVhsOverlay(
                modifier = Modifier.fillMaxSize(),
                intensity = configuration.crtIntensity,
                reducedMotion = configuration.reducedMotion,
                reducedGlitch = configuration.reducedGlitch,
                showLabels = isLandscape(),
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(configuration.venueName.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("MOD. 94 // CAMERA READY", color = Color.White.copy(alpha = 0.84f), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = BoothPalette.oxblood.copy(alpha = recordDotAlpha), fontSize = 36.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("REC", color = BoothPalette.oxblood, fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IconButton(onClick = onOperator, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Settings, "Operator settings", tint = Color.White) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood, contentColor = Color.White), modifier = Modifier.padding(bottom = 34.dp).height(68.dp).width(270.dp)) {
                    Icon(Icons.Filled.CameraAlt, null); Text("  START SESSION", fontSize = 19.sp)
                }
            }
            Text("TAP TO BEGIN  •  PHOTO RECEIPT  •  DIGITAL COPY INCLUDED", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
        }
    }
}

@Composable
private fun PhotoMomentPage(configuration: BoothConfiguration, hasCameraPermission: Boolean, cameraController: CameraCaptureController, onStart: () -> Unit, onOperator: () -> Unit) {
    val ownerMark = configuration.logoText.trim().ifBlank {
        configuration.venueName.split(" ").filter(String::isNotBlank).take(2).joinToString("") { it.take(1) }.ifBlank { "PB" }
    }.take(4).uppercase()
    val landscape = isLandscape()
    Box(Modifier.fillMaxSize().background(if (hasCameraPermission) Color.Black else Color(0xFFEDE6D8))) {
        if (hasCameraPermission) {
            CameraPreview(cameraController, Modifier.fillMaxSize())
            CrtVhsOverlay(
                modifier = Modifier.fillMaxSize(),
                intensity = configuration.crtIntensity,
                reducedMotion = configuration.reducedMotion,
                reducedGlitch = configuration.reducedGlitch,
            )
        }
        Column(Modifier.fillMaxSize().padding(if (landscape) 18.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().background(BoothPalette.paperLight.copy(alpha = 0.96f), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(BoothPalette.oxblood, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) { Text(ownerMark, color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                    Column { Text("FOTOAUTOMAT", color = BoothPalette.forest, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text("MOD. 94  •  ${configuration.venueName.uppercase()}", color = BoothPalette.inkMuted, fontSize = 8.sp, letterSpacing = 0.6.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SYS OK", color = BoothPalette.forest, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(Color(0xFFDCE8DB), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 5.dp))
                    Text("FILM: LOADED", color = BoothPalette.inkMuted, fontSize = 8.sp, modifier = Modifier.background(BoothPalette.paperPanel, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 5.dp))
                    IconButton(onClick = onOperator, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Settings, "Operator settings", tint = if (hasCameraPermission) Color.White else BoothPalette.forest) }
                }
            }
            Card(Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (hasCameraPermission) Color.Transparent else Color(0xFFF7F2E9)), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), shape = RoundedCornerShape(7.dp)) {
                if (landscape) {
                    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WelcomeMomentPanel(configuration, hasCameraPermission, onStart, Modifier.weight(7f))
                        WelcomeStripShowcase(configuration, Modifier.weight(5f))
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        WelcomeMomentPanel(configuration, hasCameraPermission, onStart, Modifier.fillMaxWidth())
                        WelcomeStripShowcase(configuration, Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(BoothPalette.paperLight.copy(alpha = 0.94f), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(4.dp)).padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("●  INSERT TOKENS OR TAP TO BEGIN  •  INSTANT THERMAL PAPER PRINT  •  DIGITAL COPY INCLUDED", color = if (hasCameraPermission) Color.White else BoothPalette.ink, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("VOICE PROMPT: OFF  ·  HI-CONTRAST", color = BoothPalette.inkMuted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun WelcomeMomentPanel(configuration: BoothConfiguration, hasCameraPermission: Boolean, onStart: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(BoothPalette.paperLight.copy(alpha = 0.92f), RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(6.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("●  REC / STANDBY", color = BoothPalette.oxblood, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("ANALOG PHOTO BOOTH EXPERIENCE", color = BoothPalette.forestLight, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 16.dp))
        Text(configuration.header.uppercase(), color = BoothPalette.ink, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 4.dp))
        Text("EST. 1994  •  ${configuration.venueName.uppercase()}", color = BoothPalette.forest, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp)
        Card(Modifier.padding(top = 18.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MAKE A MEMORY", color = BoothPalette.oxblood, style = MaterialTheme.typography.titleLarge)
                Text("Pay once. Pose with friends. Keep the thermal print forever.", color = BoothPalette.inkMuted, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WelcomeFeature("01", "STRIKE POSE", "Quick flash shots")
            WelcomeFeature("02", "TINT & STYLE", "Thermal preview")
            WelcomeFeature("03", "GRAB STRIP", "Print + digital copy")
        }
        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood, contentColor = Color.White), modifier = Modifier.padding(top = 18.dp).fillMaxWidth().height(64.dp)) {
            Icon(Icons.Filled.CameraAlt, null); Text("  START SESSION", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        if (!hasCameraPermission) Text("Camera permission is required after tapping Start.", color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun WelcomeStripShowcase(configuration: BoothConfiguration, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(Color(0xFFEDE5D6), RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFCCC1AC), RoundedCornerShape(6.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("THERMAL PRINTED", color = Color(0xFF6B4200), fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(Color(0xFFEAB308), RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
        Text("PAPER ROLL: 84%", color = BoothPalette.forest, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Card(Modifier.padding(top = 10.dp).widthIn(max = 250.dp), colors = CardDefaults.cardColors(Color(0xFFFFFDF9)), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(configuration.header.uppercase(), color = BoothPalette.ink, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                repeat(4) { index -> Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(64.dp).background(Color(0xFF161D18), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF333E35), RoundedCornerShape(2.dp)), contentAlignment = Alignment.BottomEnd) { Text("0${index + 1}", color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(4.dp)) } }
                Text(configuration.footer.uppercase(), color = BoothPalette.inkMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight)) { Text("PAYMENT DISPENSER  •  VIA QR CODE", color = BoothPalette.ink, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(10.dp)) }
    }
}

@Composable
private fun CrtVhsOverlay(
    modifier: Modifier = Modifier,
    intensity: Int = 82,
    reducedMotion: Boolean = false,
    reducedGlitch: Boolean = false,
    showLabels: Boolean = true,
) {
    val strength = (intensity.coerceIn(0, 100) / 100f)
    val transition = rememberInfiniteTransition(label = "crt-flicker")
    val animatedFlicker by transition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1_600), repeatMode = RepeatMode.Reverse),
        label = "crt-flicker-alpha",
    )
    val flickerAlpha = if (reducedMotion) 1f else animatedFlicker
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = flickerAlpha)) {
            val lineSpacing = 7.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(Color.Black.copy(alpha = 0.04f + (0.12f * strength)), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += lineSpacing
            }
            // Faded-film lift with a restrained pink/red shadow cast.
            drawRect(Color(0xFFE7D5BA).copy(alpha = 0.03f + (0.06f * strength)), size = size)
            drawRect(BoothPalette.magenta.copy(alpha = if (reducedGlitch) 0.012f else 0.05f * strength), size = size)

            // Static grain: intentionally sparse so the live preview stays readable on a tablet.
            val grainStep = (if (reducedGlitch) 24 else 14).dp.toPx()
            var row = 0
            var grainY = 0f
            while (grainY < size.height) {
                var column = 0
                var grainX = if (row % 2 == 0) 0f else grainStep / 2f
                while (grainX < size.width) {
                    if ((row * 37 + column * 17) % 11 >= 8) {
                        drawRect(
                            Color.White.copy(alpha = if (reducedGlitch) 0.012f else 0.04f * strength),
                            topLeft = androidx.compose.ui.geometry.Offset(grainX, grainY),
                            size = androidx.compose.ui.geometry.Size(1.2.dp.toPx(), 1.2.dp.toPx()),
                        )
                    }
                    grainX += grainStep
                    column++
                }
                grainY += grainStep
                row++
            }
        }
        val channelAlpha = if (reducedGlitch) 0.008f else 0.06f * strength
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(BoothPalette.magenta.copy(alpha = channelAlpha), Color.Transparent, BoothPalette.electricCyan.copy(alpha = channelAlpha)))))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f + (0.20f * strength))))))
        if (showLabels) {
            Text("● REC  VHS-01  •  AUTO-WIND", color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp, modifier = Modifier.align(Alignment.BottomStart).padding(22.dp))
            Text("TRACKING  ▮▮▮  SP 00:00:00", color = Color.White.copy(alpha = 0.7f), fontSize = 12.5.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp))
        }
    }
}

/**
 * Applies the landing-page grade to the live camera texture on API 31+.
 * The Compose overlay above supplies scanlines, grain, edge feathering, and channel tint.
 */
private fun applyLandingVhsGrade(previewView: PreviewView) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val saturation = 0.637f // muted vibrance, approximately -36.3
    val contrast = 1.525f // contrast +52.5
    val lift = -54f // faded-film lift balanced against reduced whites
    val red = 0.213f
    val green = 0.715f
    val blue = 0.072f
    val inverseSaturation = 1f - saturation
    val redRow = floatArrayOf(
        red * inverseSaturation + saturation,
        green * inverseSaturation,
        blue * inverseSaturation,
    )
    val greenRow = floatArrayOf(
        red * inverseSaturation,
        green * inverseSaturation + saturation,
        blue * inverseSaturation,
    )
    val blueRow = floatArrayOf(
        red * inverseSaturation,
        green * inverseSaturation,
        blue * inverseSaturation + saturation,
    )
    val matrix = ColorMatrix(floatArrayOf(
        contrast * redRow[0], contrast * redRow[1], contrast * redRow[2], 0f, lift + 10f,
        contrast * greenRow[0], contrast * greenRow[1] * 1.04f, contrast * greenRow[2], 0f, lift,
        contrast * blueRow[0], contrast * blueRow[1], contrast * blueRow[2] * 0.96f, 0f, lift + 5f,
        0f, 0f, 0f, 1f, 0f,
    ))
    previewView.setRenderEffect(
        android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix)),
    )
}

@Composable
private fun WelcomeHero(configuration: BoothConfiguration, hasCameraPermission: Boolean, onStart: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ANALOG PHOTO BOOTH EXPERIENCE", color = BoothPalette.forestLight, style = MaterialTheme.typography.labelLarge)
        Text(configuration.header.uppercase(), color = BoothPalette.forest, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 10.dp))
        Text("EST. 1994  •  ${configuration.venueName.uppercase()}", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        Card(Modifier.padding(top = 24.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MAKE A MEMORY", color = BoothPalette.oxblood, style = MaterialTheme.typography.headlineMedium)
                Text("Pay once. Pose with friends. Keep the real printed strip.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WelcomeFeature("01", "STRIKE POSE", "Quick flash shots")
            WelcomeFeature("02", "TINT & STYLE", "Thermal preview")
            WelcomeFeature("03", "GRAB STRIP", "Print + digital copy")
        }
        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood, contentColor = Color.White), modifier = Modifier.padding(top = 28.dp).height(72.dp).width(270.dp)) {
            Icon(Icons.Filled.CameraAlt, null); Text("  START SESSION", fontSize = 19.sp)
        }
        if (!hasCameraPermission) Text("Camera permission is required after tapping Start.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun WelcomeFeature(number: String, title: String, detail: String) {
    val accent = when (number) {
        "01" -> BoothPalette.electricCyan
        "02" -> BoothPalette.magenta
        else -> BoothPalette.warmAccent
    }
    Column(Modifier.width(110.dp).border(1.dp, Color(0xFFD5C9B4), RoundedCornerShape(6.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(34.dp).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
        Text(number, color = accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 5.dp))
        Text(title, color = BoothPalette.forest, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        Text(detail, color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun WelcomeStripRail(configuration: BoothConfiguration, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${configuration.priceLabel} / 4-POSE STRIP", color = BoothPalette.ink, style = MaterialTheme.typography.labelLarge)
            Text("CASHLESS & CARD READY", color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.padding(top = 16.dp).width(180.dp).height(300.dp).background(Color(0xFF1C1C17), RoundedCornerShape(4.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { index ->
                    Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFFFAF6EE), RoundedCornerShape(2.dp))) {
                        Text("0${index + 1}", color = Color(0xFFFAF6EE), fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
                    }
                }
            }
            Text("PHOTO-STAMP PAPER ROLL", color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun StitchTopBar(
    ownerMark: String,
    venueName: String,
    step: String,
    status: String = "SYSTEM READY",
    onBack: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BoothPalette.paperLight, RoundedCornerShape(6.dp))
            .border(1.dp, BoothPalette.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(BoothPalette.oxblood, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Text(ownerMark, color = Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(venueName.uppercase(), color = BoothPalette.ink, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(step, color = BoothPalette.inkMuted, fontSize = 8.sp, letterSpacing = 0.8.sp)
        }
        Text(status, color = BoothPalette.forest, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(Color(0xFFDCE8DB), RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 5.dp))
        onBack?.let {
            ElevatedButton(
                onClick = it,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(44.dp),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = BoothPalette.paperPanel),
            ) { Text("BACK", color = BoothPalette.ink, fontSize = 9.sp) }
        }
    }
}

@Composable
private fun StitchSectionLabel(step: String, title: String, right: String? = null) {
    Row(Modifier.fillMaxWidth().background(BoothPalette.paperLight, RoundedCornerShape(3.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(step, color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(BoothPalette.oxblood, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 4.dp))
        Text("  /  $title", color = BoothPalette.ink, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
        right?.let { Text(it, color = BoothPalette.forest, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
    }
}

@Composable
private fun TitleScreen(configuration: BoothConfiguration, cameraController: CameraCaptureController, onStart: () -> Unit, onBack: () -> Unit, onOperator: () -> Unit) {
    if (isLandscape()) {
        LandscapeTitleScreen(configuration, cameraController, onStart, onBack, onOperator)
        return
    }
    val ownerMark = configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase()
    val rentalMode = configuration.operationMode == BoothOperationMode.RENTAL
    val metrics = boothLayoutMetrics()
    Column(
        Modifier.fillMaxSize().background(BoothPalette.paper).padding(metrics.pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(BoothPalette.forest), contentAlignment = Alignment.Center) {
                    Text(ownerMark, color = BoothPalette.paperLight, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Column {
                    Text(configuration.venueName.uppercase(), color = BoothPalette.forest, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("TITLE PAGE  //  READY WHEN YOU ARE", color = BoothPalette.inkMuted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("● SYS OK", color = BoothPalette.forest, fontSize = 13.sp, modifier = Modifier.background(BoothPalette.paperLight, RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
                IconButton(onClick = onOperator, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Filled.Settings, "Operator settings", modifier = Modifier.size(30.dp), tint = BoothPalette.forest)
                }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 820.dp),
                colors = CardDefaults.cardColors(containerColor = BoothPalette.paperLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(Modifier.padding(horizontal = 44.dp * metrics.scale, vertical = 34.dp * metrics.scale), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ANALOG PHOTO BOOTH EXPERIENCE", color = BoothPalette.forestLight, style = MaterialTheme.typography.labelLarge)
                    Text(configuration.header.uppercase(), color = BoothPalette.forest, style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 10.dp))
                    Text("EST. 1994  •  ${configuration.venueName.uppercase()}", color = BoothPalette.inkMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    Card(
                        modifier = Modifier.padding(top = 28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 32.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MAKE A MEMORY", color = BoothPalette.oxblood, style = MaterialTheme.typography.headlineMedium)
                            Text(if (rentalMode) "Your session is ready. Pose with friends and keep the real thermal print." else "Scan to pay, then pose with friends. Keep the real thermal print.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TitleFeature("01", "STRIKE POSE", "Quick flash shots", Modifier.weight(1f))
                        TitleFeature("02", "TINT & STYLE", "Thermal preview", Modifier.weight(1f))
                        TitleFeature("03", "GET THE PRINT", "Digital copy included", Modifier.weight(1f))
                    }
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood, contentColor = Color.White),
                        modifier = Modifier.padding(top = 28.dp).height(72.dp).width(300.dp),
                    ) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Text("  START SESSION", fontSize = 19.sp)
                    }
                    Text(if (rentalMode) "RENTAL SESSION  •  DIGITAL PHOTO + GIF QR INCLUDED" else "PAYMENT REQUIRED BEFORE CAMERA CAPTURE", color = BoothPalette.inkMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FOTOAUTOMAT MOD. 94  •  FILM: LOADED  •  CAMERA READY", color = BoothPalette.inkMuted, fontSize = 11.sp)
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest, contentColor = BoothPalette.paperLight),
                modifier = Modifier.padding(top = 8.dp).height(52.dp).fillMaxWidth(0.62f),
            ) { Text("←  BACK TO HOME", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
    }
}

@Composable
private fun LandscapeTitleScreen(configuration: BoothConfiguration, cameraController: CameraCaptureController, onStart: () -> Unit, onBack: () -> Unit, onOperator: () -> Unit) {
    val rentalMode = configuration.operationMode == BoothOperationMode.RENTAL
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        // Use a fixed 50/50 cut instead of weights.  This prevents a live PreviewView
        // from influencing the split and guarantees that it cannot expand leftward.
        val splitWidth = maxWidth / 2
        val leftContentWidth = (splitWidth - 104.dp).coerceAtLeast(0.dp)
        val leftContentHeight = (maxHeight - 84.dp).coerceAtLeast(0.dp)

        Box(Modifier.align(Alignment.TopStart).width(splitWidth).fillMaxHeight().background(BoothPalette.paper)) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .width(leftContentWidth)
                    .height(leftContentHeight),
            ) {
                WelcomeBrandLogo(
                    logoPath = configuration.welcomeLogoPath,
                    fallbackMark = configuration.logoText,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Give the operator's brand a true hero treatment: half of
                        // the available left panel while preserving its own ratio.
                        .height(leftContentHeight * 0.50f),
                )
                // Header and venue name are owned by Operator Settings. Keep them
                // as live copy beneath the operator's marketing logo.
                Text(
                    configuration.header.uppercase(),
                    color = BoothPalette.forest,
                    fontSize = 46.sp,
                    lineHeight = 46.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    letterSpacing = (-1.8).sp,
                    modifier = Modifier.padding(top = 16.dp).widthIn(max = 560.dp),
                )
                Text(configuration.venueName.uppercase(), color = BoothPalette.inkMuted, fontSize = 14.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (rentalMode) "Step in, look at the camera\nand get ready for your photo."
                    else "Complete activation, then\nget ready for your photo.",
                    color = BoothPalette.ink,
                    style = MaterialTheme.typography.titleMedium,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 12.dp).widthIn(max = 430.dp),
                )
                // The weighted spacer preserves a clear CTA zone near the bottom of
                // the left panel without ever encroaching on the camera preview.
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest, contentColor = BoothPalette.paperLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(270.dp).height(62.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, null)
                    Text("  START SESSION", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 1.2.sp)
                }
                Text("FILM: LOADED  ·  CAMERA READY", color = BoothPalette.inkMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            }
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = splitWidth)
                .width(maxWidth - splitWidth)
                .fillMaxHeight()
                // PreviewView is backed by an Android view; explicitly clip it so
                // its center-cropped texture cannot bleed into the cream panel.
                .clipToBounds()
                .background(BoothPalette.ink),
        ) {
            // This is the real CameraX preview.  The guides and prompt stay above it,
            // so the guest sees their live position instead of a placeholder image.
            CameraPreview(cameraController, Modifier.fillMaxSize(), vhsEnabled = false)
            CaptureCameraCorners(Modifier.fillMaxSize().padding(32.dp))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).background(BoothPalette.mint, CircleShape))
                Text("LOOK HERE", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WelcomeBrandLogo(logoPath: String, fallbackMark: String, modifier: Modifier = Modifier) {
    val bitmap = remember(logoPath) {
        logoPath.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::exists)
            ?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
    }
    Box(
        modifier
            .background(BoothPalette.paperLight, RoundedCornerShape(6.dp))
            .border(2.dp, BoothPalette.forest.copy(alpha = 0.26f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Operator business logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(28.dp),
            )
        } else {
            Text(
                fallbackMark.trim().ifBlank { "PB" }.take(4).uppercase(),
                color = BoothPalette.forest,
                fontSize = 74.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = (-2).sp,
            )
        }
    }
}

@Composable
private fun TitleFeature(number: String, title: String, detail: String, modifier: Modifier = Modifier) {
    val accent = when (number) {
        "01" -> BoothPalette.electricCyan
        "02" -> BoothPalette.magenta
        else -> BoothPalette.warmAccent
    }
    Column(modifier.border(1.dp, Color(0xFFD5C9B4), RoundedCornerShape(6.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(38.dp).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
        Text(number, color = accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 5.dp))
        Text(title, color = BoothPalette.forest, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Text(detail, color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun CameraOutputCard(number: String, title: String, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(BoothPalette.paperLight, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).background(BoothPalette.forest, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text(number, color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CameraAlt, null, tint = BoothPalette.forest, modifier = Modifier.size(18.dp))
                Text(title, color = BoothPalette.ink, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(description, color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun PaymentScreen(configuration: BoothConfiguration, onProceed: (Int) -> Unit, onBack: () -> Unit) {
    val ownerMark = configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase()
    // A quantity is intentionally unset until the guest chooses one. This
    // prevents a hidden default tier from being charged or carried into the
    // session when the payment screen first appears.
    var selectedPrintQuantity by remember { mutableStateOf<Int?>(null) }
    val metrics = boothLayoutMetrics()
    if (isLandscape()) {
        LandscapePaymentScreen(
            configuration = configuration,
            printQuantity = selectedPrintQuantity,
            onQuantityChange = { selectedPrintQuantity = it },
            onProceed = { selectedPrintQuantity?.let(onProceed) },
            onBack = onBack,
        )
        return
    }
    val printQuantity = selectedPrintQuantity ?: 1
    val paymentContent: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "STEP 1 OF 3: ACTIVATION",
                color = BoothPalette.forest,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier
                    .background(BoothPalette.paperPanel, RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFFD6CDBC), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
            Text("STATION ID: TX-BER-94", color = BoothPalette.inkMuted, fontSize = 10.sp)
        }
        Text("READY WHEN YOU ARE", color = BoothPalette.forest, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 16.dp))
        Text("PAY VIA QRPH  ·  SCAN TO START SESSION", color = BoothPalette.oxblood, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text("Scan the QR code with your mobile banking or payment app to start session. Ask the attendant for payment confirmation.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 10.dp))
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TRANSACTION #94-882", color = BoothPalette.inkMuted, fontSize = 10.sp)
            Text("READY / 24V COIL OK", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).background(BoothPalette.paperPanel, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TOTAL PAYABLE", color = BoothPalette.inkMuted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(selectedPrintQuantity?.let(configuration::priceLabelForQuantity) ?: "SELECT QUANTITY", color = BoothPalette.ink, style = MaterialTheme.typography.headlineSmall)
        }
        PrintQuantitySelector(
            quantity = printQuantity,
            configuration = configuration,
            onQuantityChange = { selectedPrintQuantity = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(selectedPrintQuantity?.let { "STATIC QR  •  SELECTED TIER: $it PRINT${if (it == 1) "" else "S"}  •  ${configuration.priceLabelForQuantity(it)}" } ?: "CHOOSE A PRINT QUANTITY TO CONTINUE", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("APPLE PAY  ·  GOOGLE PAY  ·  BANK APP  ·  CARD", color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.padding(top = 18.dp).background(BoothPalette.paperLight, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(BoothPalette.oxblood))
            Text("WAITING FOR PAYMENT", color = BoothPalette.forest, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Text("TEST MODE · QRPH PROVIDER NOT CONNECTED", color = BoothPalette.inkMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        Text("This demo QR is visual only. Real QRPH unlock will require a verified provider and secure backend.", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    if (isLandscape()) {
        Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(horizontal = 34.dp, vertical = 22.dp)) {
            PaymentHeader(ownerMark, configuration.venueName, onBack)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.widthIn(max = 470.dp).padding(end = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, content = paymentContent)
                Card(Modifier.size(292.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text("CORRESPONDING QR TO PAY", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    DemoQrTile(Modifier.size(252.dp), "DEMO QRPH")
                }
                }
            }
            PaymentActions({ selectedPrintQuantity?.let(onProceed) }, onBack, enabled = selectedPrintQuantity != null)
        }
    } else {
        val qrSize = 250.dp * metrics.scale
        Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(metrics.pagePadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            PaymentHeader(ownerMark, configuration.venueName, onBack)
            Column(Modifier.fillMaxWidth().padding(top = 20.dp * metrics.scale), horizontalAlignment = Alignment.CenterHorizontally, content = paymentContent)
            Card(Modifier.padding(top = 18.dp * metrics.scale).size(qrSize), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text("CORRESPONDING QR TO PAY", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    DemoQrTile(Modifier.size((qrSize.value - 30f).coerceAtLeast(150f).dp), "DEMO QRPH")
                }
            }
            PaymentActions({ selectedPrintQuantity?.let(onProceed) }, onBack, Modifier.padding(top = 18.dp * metrics.scale), enabled = selectedPrintQuantity != null)
        }
    }
}

@Composable
private fun LandscapePaymentScreen(configuration: BoothConfiguration, printQuantity: Int?, onQuantityChange: (Int) -> Unit, onProceed: () -> Unit, onBack: () -> Unit) {
    val paymentSlots = paymentQrSlots(configuration)
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        val compact = maxWidth < 1100.dp
        val pagePadding = if (compact) 24.dp else 42.dp
        val titleSize = if (compact) 48.sp else 62.sp
        Column(Modifier.fillMaxSize().padding(horizontal = pagePadding, vertical = 24.dp)) {
            Text("CHOOSE PAYMENT", color = BoothPalette.forest, fontSize = titleSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = (-2).sp, maxLines = 1)
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 34.dp)) {
                Column(Modifier.weight(0.48f).fillMaxHeight()) {
                    PaymentQrGrid(paymentSlots, Modifier.fillMaxWidth().weight(1f))
                }
                Box(Modifier.fillMaxHeight().width(1.dp).background(BoothPalette.forest.copy(alpha = 0.5f)))
                Column(Modifier.weight(0.52f).fillMaxHeight()) {
                    PaymentSectionHeader("PRINT QUANTITY", null)
                    Text("Choose up to ${configuration.maxPrintQuantity} prints.", color = BoothPalette.ink, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    PaymentQuantityGrid(printQuantity, configuration.maxPrintQuantity, onQuantityChange, Modifier.fillMaxWidth().padding(top = 18.dp))
                    PaymentTotalCard(configuration, printQuantity, Modifier.fillMaxWidth().padding(top = 18.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, BoothPalette.forest),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BoothPalette.forest),
                    modifier = Modifier.weight(0.48f).height(66.dp),
                ) { Text("←  BACK", fontSize = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp) }
                Button(
                    onClick = onProceed,
                    enabled = printQuantity != null,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest, disabledContainerColor = BoothPalette.paperPanel, disabledContentColor = BoothPalette.inkMuted),
                    modifier = Modifier.weight(0.52f).height(66.dp),
                ) { Text("CONTINUE TO PAY  →", fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp) }
            }
        }
    }
}

private data class PaymentQrSlot(val index: Int, val title: String, val uri: String) {
    val isAvailable: Boolean get() = uri.isNotBlank()
}

private fun paymentQrSlots(configuration: BoothConfiguration): List<PaymentQrSlot> = (0 until 4).map { index ->
    PaymentQrSlot(
        index = index,
        title = configuration.paymentQrTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "QR ${index + 1}",
        uri = configuration.paymentQrUris.getOrNull(index).orEmpty(),
    )
}

@Composable
private fun PaymentSectionHeader(title: String, detail: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = BoothPalette.ink, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp)
        Box(Modifier.weight(1f).height(1.dp).background(BoothPalette.forest.copy(alpha = 0.65f)))
        detail?.let { Text(it, color = BoothPalette.inkMuted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.8.sp) }
    }
}

@Composable
private fun PaymentQrGrid(slots: List<PaymentQrSlot>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        slots.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { slot -> PaymentQrCard(slot, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun PaymentQrCard(slot: PaymentQrSlot, modifier: Modifier = Modifier) {
    val outline = if (slot.isAvailable) BoothPalette.forest else BoothPalette.border
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(if (slot.isAvailable) Color(0xFFE0ECD9) else BoothPalette.paperLight),
        border = androidx.compose.foundation.BorderStroke(if (slot.isAvailable) 2.dp else 1.dp, outline),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            if (slot.isAvailable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.title.uppercase(), color = BoothPalette.ink, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Icon(Icons.Filled.CheckCircle, contentDescription = "${slot.title} is available", tint = BoothPalette.forest, modifier = Modifier.size(25.dp))
                }
                // The QR is the action in this card. Giving it the former
                // helper-label space keeps all four codes comfortably scannable
                // without cropping any operator-provided image.
                PaymentQrTile(slot.uri, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 3.dp))
            } else {
                Spacer(Modifier.height(2.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("⊘", color = BoothPalette.inkMuted.copy(alpha = 0.55f), fontSize = 46.sp)
                    Text("NOT AVAILABLE", color = BoothPalette.inkMuted, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("Payment method unavailable", color = BoothPalette.inkMuted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun PaymentQuantityGrid(selectedQuantity: Int?, maxQuantity: Int, onQuantityChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (1..maxQuantity).toList().chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { quantity ->
                    val selected = quantity == selectedQuantity
                    Button(
                        onClick = { onQuantityChange(quantity) },
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BoothPalette.forest),
                        colors = ButtonDefaults.buttonColors(containerColor = if (selected) BoothPalette.forest else BoothPalette.paperLight, contentColor = if (selected) BoothPalette.paperLight else BoothPalette.forest),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).height(62.dp),
                    ) { Text(quantity.toString(), fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun PaymentTotalCard(configuration: BoothConfiguration, printQuantity: Int?, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight), border = androidx.compose.foundation.BorderStroke(1.dp, BoothPalette.forest.copy(alpha = 0.65f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("YOUR TOTAL", color = BoothPalette.inkMuted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp)
                Text(printQuantity?.let { "$it PRINT${if (it == 1) "" else "S"}" } ?: "SELECT QUANTITY", color = BoothPalette.forest, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
                Text(printQuantity?.let(configuration::priceLabelForQuantity) ?: "—", color = BoothPalette.forest, fontSize = 38.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                Text("Price set by your venue", color = BoothPalette.inkMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PaymentHeader(ownerMark: String, venueName: String, onBack: () -> Unit) {
    StitchTopBar(ownerMark, venueName, "STEP 1 / ACTIVATION", "PAYMENT READY", onBack)
}

@Composable
private fun PaymentActions(onTestPaymentConfirmed: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 420.dp
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onTestPaymentConfirmed,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null)
                    Text("  CONFIRM TEST PAYMENT", fontSize = 13.sp)
                }
                ElevatedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = BoothPalette.paperLight),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("CANCEL", color = BoothPalette.ink) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onTestPaymentConfirmed, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest), modifier = Modifier.height(54.dp).width(250.dp)) {
                    Icon(Icons.Filled.CheckCircle, null)
                    Text("  CONFIRM TEST PAYMENT", fontSize = 13.sp)
                }
                ElevatedButton(onClick = onBack, colors = ButtonDefaults.elevatedButtonColors(containerColor = BoothPalette.paperLight), modifier = Modifier.padding(start = 10.dp).height(54.dp)) {
                    Text("CANCEL", color = BoothPalette.ink)
                }
            }
        }
    }
}

@Composable
private fun DemoQrTile(modifier: Modifier = Modifier.size(196.dp), label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(Color.White)) {
        Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repeat(3) { Box(Modifier.size(38.dp).border(5.dp, Color.Black).padding(7.dp).background(Color.Black)) } }
            repeat(3) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(5) { index -> Box(Modifier.size(if (index % 2 == 0) 13.dp else 8.dp).background(Color.Black)) } } }
            Text(label, color = Color.Black, fontSize = 12.sp)
        }
    }
}

private fun landscapeQrUri(configuration: BoothConfiguration, index: Int): String = configuration.paymentQrUris.getOrNull(index).orEmpty()

private fun paymentQrTitle(configuration: BoothConfiguration, index: Int): String =
    configuration.paymentQrTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "${index + 1}. QR PAY"

@Composable
private fun PaymentQrTile(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        uri.takeIf { it.isNotBlank() }?.let { value -> runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(value))?.use(BitmapFactory::decodeStream) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "Operator payment QR", modifier = modifier.background(Color.White).padding(4.dp), contentScale = ContentScale.Fit)
    } else {
        DemoQrTile(modifier, "")
    }
}

@Composable
private fun LayoutSelectionScreen(configuration: BoothConfiguration, onSelect: (LayoutId) -> Unit, onBack: () -> Unit) {
    val landscape = isLandscape()
    if (landscape) {
        LandscapeLayoutSelectionScreen(configuration, onSelect, onBack)
        return
    }
    val ownerMark = configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase()
    val metrics = boothLayoutMetrics()
    Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(metrics.pagePadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        StitchTopBar(ownerMark, configuration.venueName, "STEP 2 / LAYOUT SELECTION", "PAYMENT VERIFIED", onBack)
        Text("PAYMENT RECEIVED", color = BoothPalette.forestLight, style = MaterialTheme.typography.titleLarge)
        Text("Choose your layout", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        LayoutId.entries.filter { it in configuration.enabledLayouts }.chunked(if (landscape) 4 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                row.forEach { layout -> LayoutCard(layout, configuration, Modifier.weight(1f), onSelect, landscape) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        ElevatedButton(onClick = onBack, colors = ButtonDefaults.elevatedButtonColors(containerColor = BoothPalette.paperLight), modifier = Modifier.padding(top = 8.dp)) { Text("Cancel session", color = BoothPalette.ink) }
    }
}

@Composable
private fun LandscapeLayoutSelectionScreen(configuration: BoothConfiguration, onSelect: (LayoutId) -> Unit, onBack: () -> Unit) {
    val layouts = listOf(LayoutId.ONE_PHOTO, LayoutId.THREE_STRIP, LayoutId.FOUR_GRID, LayoutId.SIX_GRID).filter { it in configuration.enabledLayouts }
    var selected by remember(layouts) { mutableStateOf(layouts.firstOrNull()) }
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        // Preserve the generated 16:9 design as the visual source of truth.
        // Fit avoids cropping the artwork on the 16:10 booth tablet.
        val artworkAspectRatio = 1680f / 945f
        val artworkWidth = minOf(maxWidth, maxHeight * artworkAspectRatio)
        val artworkHeight = artworkWidth / artworkAspectRatio
        val artworkLeft = (maxWidth - artworkWidth) / 2
        val artworkTop = (maxHeight - artworkHeight) / 2
        val cardTop = artworkTop + (artworkHeight * 0.196f)
        val cardWidth = artworkWidth * 0.23f
        val cardHeight = artworkHeight * 0.60f
        val cardOffsets = listOf(0.022f, 0.264f, 0.505f, 0.746f)

        Image(
            painter = painterResource(com.eventphotobooth.app.R.drawable.layout_selection_background),
            contentDescription = "Choose your layout",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .width(artworkWidth)
                .height(artworkHeight),
        )

        layouts.forEachIndexed { index, layout ->
            val isSelected = selected == layout
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = artworkLeft + (artworkWidth * cardOffsets[index]),
                        y = cardTop,
                    )
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(14.dp))
                    // The generated artwork contains the original one-photo border.  A neutral
                    // border masks that baked state; the runtime state then owns the selection.
                    .border(
                        width = 3.dp,
                        color = if (isSelected) BoothPalette.paperLight else BoothPalette.paper,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .background(if (isSelected) BoothPalette.forest.copy(alpha = 0.82f) else Color.Transparent)
                    .clickable(onClickLabel = "Select ${layout.displayName}") { selected = layout },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = BoothPalette.paperLight,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = layout.displayName.uppercase(),
                            color = BoothPalette.paperLight,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = "SELECTED",
                            color = BoothPalette.paperLight.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = artworkLeft + (artworkWidth * 0.022f), y = artworkTop + (artworkHeight * 0.842f))
                .width(artworkWidth * 0.178f)
                .height(artworkHeight * 0.105f)
                .clip(RoundedCornerShape(36.dp))
                .clickable(onClickLabel = "Back") { onBack() },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = artworkLeft + (artworkWidth * 0.212f), y = artworkTop + (artworkHeight * 0.842f))
                .width(artworkWidth * 0.764f)
                .height(artworkHeight * 0.105f)
                .clip(RoundedCornerShape(36.dp))
                .clickable(onClickLabel = "Continue with selected layout") { selected?.let(onSelect) },
        )
    }
}

@Composable
private fun LayoutCard(layout: LayoutId, configuration: BoothConfiguration, modifier: Modifier, onSelect: (LayoutId) -> Unit, landscape: Boolean = false) {
    val design = configuration.layoutDesign(layout)
    val metrics = boothLayoutMetrics()
    Card(modifier = modifier.height(if (landscape) 170.dp else 190.dp * metrics.scale).clickable { onSelect(layout) }, colors = CardDefaults.cardColors(BoothPalette.paperLight)) {
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LayoutDiagram(layout, Color(design.accentColor))
            Text(design.displayName, color = BoothPalette.ink, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            Text("${layout.photoCount} ${if (layout.photoCount == 1) "photo" else "photos"}", color = BoothPalette.inkMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LayoutDiagram(layout: LayoutId, color: Color, contentScale: ContentScale = ContentScale.Fit, padding: Dp = 4.dp) {
    val stripResource = when (layout) {
        LayoutId.FOUR_GRID -> com.eventphotobooth.app.R.drawable.strip_classic
        LayoutId.ONE_PHOTO -> com.eventphotobooth.app.R.drawable.strip_solo
        LayoutId.THREE_STRIP -> com.eventphotobooth.app.R.drawable.strip_triple
        LayoutId.SIX_GRID -> com.eventphotobooth.app.R.drawable.strip_retro
    }
    Image(
        painter = painterResource(stripResource),
        contentDescription = "${layout.displayName} strip preview",
        modifier = Modifier.fillMaxSize().padding(horizontal = padding, vertical = padding),
        contentScale = contentScale,
    )
}

@Composable
private fun CameraReadyScreen(
    cameraController: CameraCaptureController,
    layoutId: LayoutId,
    captureNumber: Int,
    photoCount: Int,
    isRetake: Boolean,
    onStart: () -> Unit,
) {
    val layoutSpec = com.eventphotobooth.app.data.PhotoLayoutSpecs.forLayout(layoutId)
    if (isLandscape()) {
        LandscapeCameraReadyScreen(cameraController, layoutSpec, captureNumber, photoCount, isRetake, onStart)
    } else {
        PortraitCameraReadyScreen(cameraController, layoutSpec, captureNumber, photoCount, isRetake, onStart)
    }
}

@Composable
private fun CameraCaptureScreen(
    cameraController: CameraCaptureController,
    layoutId: LayoutId,
    seconds: Int,
    captureNumber: Int,
    photoCount: Int,
) {
    val layoutSpec = com.eventphotobooth.app.data.PhotoLayoutSpecs.forLayout(layoutId)
    if (isLandscape()) {
        LandscapeCameraCountdownScreen(cameraController, layoutSpec, seconds, captureNumber, photoCount)
    } else {
        PortraitCameraCountdownScreen(cameraController, layoutSpec, seconds, captureNumber, photoCount)
    }
}

@Composable
private fun LandscapeCameraReadyScreen(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    captureNumber: Int,
    photoCount: Int,
    isRetake: Boolean,
    onStart: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        val previewPaneWidth = maxWidth * 0.56f
        val controlsPaneWidth = maxWidth - previewPaneWidth

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(previewPaneWidth)
                .fillMaxHeight()
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            CameraReadyPreviewWindow(
                cameraController = cameraController,
                layoutSpec = layoutSpec,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = previewPaneWidth)
                .width(controlsPaneWidth)
                .fillMaxHeight()
                .padding(horizontal = 42.dp, vertical = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "CAMERA\nREADY",
                    color = BoothPalette.forest,
                    fontSize = 52.sp,
                    lineHeight = 46.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    letterSpacing = (-1.2).sp,
                )
                CameraReadyProgressDots(captureNumber, photoCount, Modifier.padding(top = 18.dp))
                Text(
                    if (isRetake) "RETAKE PHOTO $captureNumber" else "PHOTO $captureNumber OF $photoCount",
                    color = BoothPalette.forest,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .width(42.dp)
                        .height(2.dp)
                        .background(BoothPalette.forest.copy(alpha = 0.55f)),
                )
                Text(
                    if (isRetake) "Step into the frame. This replaces only this photo."
                    else "Step into the frame. Tap start when you are ready.",
                    color = BoothPalette.ink,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(top = 16.dp).widthIn(max = 360.dp),
                )
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest, contentColor = BoothPalette.paperLight),
                    shape = RoundedCornerShape(40.dp),
                    modifier = Modifier.padding(top = 30.dp).width(300.dp).height(76.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(34.dp))
                    Text("  START PHOTO", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 0.8.sp)
                }
                Text(
                    "${photoCount} ${if (photoCount == 1) "photo" else "photos"}  ·  one keepsake",
                    color = BoothPalette.inkMuted,
                    fontSize = 14.sp,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraReadyPreviewWindow(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .aspectRatio(layoutSpec.photoAspectRatio)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black)
            .border(2.dp, BoothPalette.forest.copy(alpha = 0.56f), RoundedCornerShape(3.dp)),
    ) {
        // Keep the true final output ratio while showing the real CameraX feed.
        CameraPreview(cameraController, Modifier.fillMaxSize(), vhsEnabled = false)
        CaptureCameraCorners(Modifier.fillMaxSize().padding(24.dp))
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BoothPalette.forest.copy(alpha = 0.88f))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(BoothPalette.mint, CircleShape))
            Text("LOOK HERE", color = BoothPalette.paperLight, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun CameraReadyProgressDots(captureNumber: Int, photoCount: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(photoCount) { index ->
            val number = index + 1
            val isCurrent = number == captureNumber
            val isComplete = number < captureNumber
            val dotColor = when {
                isCurrent -> BoothPalette.forest
                isComplete -> BoothPalette.forestLight
                else -> BoothPalette.paperPanel
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(1.dp, BoothPalette.forest.copy(alpha = if (isCurrent) 0f else 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isComplete) "✓" else number.toString(),
                    color = if (isCurrent) BoothPalette.paperLight else BoothPalette.forest,
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PortraitCameraReadyScreen(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    captureNumber: Int,
    photoCount: Int,
    isRetake: Boolean,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (isRetake) "RETAKE PHOTO $captureNumber" else "PHOTO $captureNumber OF $photoCount", color = BoothPalette.ink, style = MaterialTheme.typography.titleLarge)
        CaptureProgressIndicator(captureNumber, photoCount, isReady = true, modifier = Modifier.padding(top = 10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            CaptureCameraWindow(cameraController, layoutSpec, Modifier.fillMaxWidth(0.94f))
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(BoothPalette.paperLight), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TAP WHEN READY", color = BoothPalette.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                CaptureStartButton(onStart, Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun LandscapeCameraCountdownScreen(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    seconds: Int,
    captureNumber: Int,
    photoCount: Int,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        val previewPaneWidth = maxWidth * 0.62f
        val controlsPaneWidth = maxWidth - previewPaneWidth

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(previewPaneWidth)
                .fillMaxHeight()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CountdownCameraPreviewWindow(
                cameraController = cameraController,
                layoutSpec = layoutSpec,
                captureNumber = captureNumber,
                photoCount = photoCount,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = previewPaneWidth)
                .width(controlsPaneWidth)
                .fillMaxHeight()
                .padding(horizontal = 30.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CountdownCopy(
                seconds = seconds,
                captureNumber = captureNumber,
                photoCount = photoCount,
                compact = false,
            )
        }
    }
}

@Composable
private fun PortraitCameraCountdownScreen(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    seconds: Int,
    captureNumber: Int,
    photoCount: Int,
) {
    Column(
        Modifier.fillMaxSize().background(BoothPalette.paper).padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CountdownCameraPreviewWindow(
                cameraController = cameraController,
                layoutSpec = layoutSpec,
                captureNumber = captureNumber,
                photoCount = photoCount,
            )
        }
        CountdownCopy(
            seconds = seconds,
            captureNumber = captureNumber,
            photoCount = photoCount,
            compact = true,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/**
 * Countdown-only live preview.  The visual treatment is intentionally separate from the
 * ready screen so the countdown can use the film badge without affecting framing before a
 * guest starts their session.
 */
@Composable
private fun CountdownCameraPreviewWindow(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    captureNumber: Int,
    photoCount: Int,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val previewModifier = if (maxWidth <= maxHeight * layoutSpec.photoAspectRatio) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxHeight()
        }
        BoxWithConstraints(
            previewModifier
                .aspectRatio(layoutSpec.photoAspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(1.dp, BoothPalette.forest.copy(alpha = 0.34f), RoundedCornerShape(18.dp)),
        ) {
            // The corner guide owns 22dp plus 14% of the narrow edge. Calculate the
            // badge inset from that exact geometry, leaving a visible gap at any crop.
            val filmBadgeStart = (
                minOf(maxWidth, maxHeight) * 0.14f + 34.dp
            ).coerceAtMost(maxWidth * 0.34f)
            CameraPreview(cameraController, Modifier.fillMaxSize(), vhsEnabled = false)
            CaptureCameraCorners(Modifier.fillMaxSize().padding(22.dp))
            CaptureFilmBadge(
                captureNumber = captureNumber,
                photoCount = photoCount,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = filmBadgeStart, top = 18.dp),
            )
            CaptureLookHereBadge(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun CaptureFilmBadge(
    captureNumber: Int,
    photoCount: Int,
    modifier: Modifier = Modifier,
) {
    val compactChips = photoCount >= 6
    val chipSize = if (compactChips) 14.dp else 18.dp
    val chipGap = if (compactChips) 6.dp else 8.dp
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BoothPalette.forest.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            "FILM $captureNumber / $photoCount",
            color = BoothPalette.paperLight,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            letterSpacing = 0.9.sp,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(chipGap),
        ) {
            repeat(photoCount) { index ->
                val number = index + 1
                val complete = number < captureNumber
                val current = number == captureNumber
                Box(
                    Modifier
                        .size(chipSize)
                        .clip(CircleShape)
                        .background(
                            when {
                                complete -> BoothPalette.paperLight
                                current -> BoothPalette.mint
                                else -> Color.Transparent
                            },
                        )
                        .border(1.5.dp, BoothPalette.mint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (complete) {
                        Text("✓", color = BoothPalette.forest, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureLookHereBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(BoothPalette.mint)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(BoothPalette.forest, CircleShape))
        Text(
            "LOOK HERE",
            color = BoothPalette.forest,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            letterSpacing = 0.9.sp,
        )
    }
}

@Composable
private fun CountdownCopy(
    seconds: Int,
    captureNumber: Int,
    photoCount: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val nextCopy = if (captureNumber == photoCount) "LAST PHOTO" else "NEXT: PHOTO ${captureNumber + 1}"
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "GET\nREADY",
            color = BoothPalette.forest,
            fontSize = if (compact) 38.sp else 54.sp,
            lineHeight = if (compact) 35.sp else 48.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            letterSpacing = (-1.1).sp,
        )
        Text(
            seconds.toString(),
            color = BoothPalette.forest,
            fontSize = if (compact) 100.sp else 146.sp,
            lineHeight = if (compact) 96.sp else 134.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            modifier = Modifier.padding(top = if (compact) 2.dp else 8.dp),
        )
        Text(
            "HOLD YOUR POSE.",
            color = BoothPalette.forest,
            fontSize = if (compact) 17.sp else 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            letterSpacing = if (compact) 0.8.sp else 1.1.sp,
            modifier = Modifier.padding(top = if (compact) 2.dp else 10.dp),
        )
        Box(
            Modifier
                .padding(top = if (compact) 12.dp else 22.dp)
                .width(if (compact) 160.dp else 240.dp)
                .height(1.dp)
                .background(BoothPalette.forest.copy(alpha = 0.58f)),
        )
        Box(
            Modifier
                .padding(top = if (compact) 12.dp else 20.dp)
                .width(if (compact) 190.dp else 250.dp)
                .height(if (compact) 42.dp else 52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(BoothPalette.forest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                nextCopy,
                color = BoothPalette.paperLight,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun CaptureCameraWindow(
    cameraController: CameraCaptureController,
    layoutSpec: com.eventphotobooth.app.data.PhotoLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.aspectRatio(layoutSpec.photoAspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(2.dp, BoothPalette.ink, RoundedCornerShape(8.dp)),
    ) {
        // PreviewView.FILL_CENTER center-crops the same 4:3 camera source as
        // the receipt renderer, so this physical window is the final crop.
        CameraPreview(cameraController, Modifier.fillMaxSize())
        CaptureCameraCorners(Modifier.fillMaxSize())
    }
}

@Composable
private fun CaptureCameraCorners(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val frameColor = BoothPalette.mint
        val cornerLength = minOf(size.width, size.height) * 0.14f
        val frameStroke = 6.dp.toPx()
        fun corner(horizontalStart: Float, horizontalEnd: Float, verticalStart: Float, verticalEnd: Float) {
            drawLine(frameColor, androidx.compose.ui.geometry.Offset(horizontalStart, verticalStart), androidx.compose.ui.geometry.Offset(horizontalEnd, verticalStart), strokeWidth = frameStroke)
            drawLine(frameColor, androidx.compose.ui.geometry.Offset(horizontalStart, verticalStart), androidx.compose.ui.geometry.Offset(horizontalStart, verticalEnd), strokeWidth = frameStroke)
        }
        corner(0f, cornerLength, 0f, cornerLength)
        corner(size.width, size.width - cornerLength, 0f, cornerLength)
        corner(0f, cornerLength, size.height, size.height - cornerLength)
        corner(size.width, size.width - cornerLength, size.height, size.height - cornerLength)
    }
}

@Composable
private fun CaptureStartButton(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onStart,
        modifier = modifier.size(112.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood, contentColor = BoothPalette.paperLight),
    ) {
        Box(Modifier.fillMaxSize().border(5.dp, BoothPalette.paperLight, CircleShape), contentAlignment = Alignment.Center) {
            Text("START", color = BoothPalette.paperLight, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
private fun CaptureProgressIndicator(captureNumber: Int, photoCount: Int, isReady: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(photoCount) { index ->
        val number = index + 1
        val completed = number < captureNumber
        val current = number == captureNumber
        val color = when {
            completed -> BoothPalette.mint
            current && isReady -> BoothPalette.electricCyan
            current -> BoothPalette.warmAccent
            else -> BoothPalette.paperPanel
        }
        val label = when {
            completed -> "DONE"
            current && isReady -> "READY"
            current -> "NOW"
            else -> "NEXT"
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color).border(2.dp, BoothPalette.paperLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (completed) "✓" else number.toString(), color = BoothPalette.ink, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(label, color = color, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    } }
}

@Composable
private fun ReviewScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    onRetakePhoto: (Int) -> Unit,
    onRetakeAll: () -> Unit,
    onContinue: () -> Unit,
) {
    if (isLandscape()) {
        LandscapeReviewScreen(photos, layoutId, configuration, onRetakePhoto, onRetakeAll, onContinue)
        return
    }
    var selectedPhotoIndex by remember(photos) { mutableStateOf(0) }
    val ownerMark = configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase()
    val metrics = boothLayoutMetrics()
    Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(metrics.pagePadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
        StitchTopBar(ownerMark, configuration.venueName, "STEP 4 / REVIEW", "CAPTURE COMPLETE")
        Text("YOUR MOMENTS", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("80 mm thermal receipt · scaled to fit this screen", color = BoothPalette.inkMuted)
        PhotoLayoutPreview(
            photos = photos,
            layoutId = layoutId,
            selectedPhotoIndex = selectedPhotoIndex,
            onPhotoTap = { selectedPhotoIndex = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (layoutId == LayoutId.THREE_STRIP) 430.dp else 360.dp)
                .padding(horizontal = 18.dp),
        )
        Text("Photo ${selectedPhotoIndex + 1} of ${photos.size} · tap a frame to inspect its final crop", color = BoothPalette.inkMuted, style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(BoothPalette.forest, BoothPalette.paperLight),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("CONTINUE TO TEMPLATES", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedButton(onClick = { onRetakePhoto(selectedPhotoIndex) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("RETAKE PHOTO ${selectedPhotoIndex + 1}", fontSize = 11.sp) }
                ElevatedButton(onClick = onRetakeAll, modifier = Modifier.weight(1f).height(48.dp)) { Text("RETAKE ALL", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun LandscapeReviewScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    onRetakePhoto: (Int) -> Unit,
    onRetakeAll: () -> Unit,
    onContinue: () -> Unit,
) {
    var selectedPhotoIndex by remember(photos) { mutableStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BoothPalette.paper)
            .padding(horizontal = 48.dp, vertical = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // The offset panel gives the photo receipt a physical, print-ready presence
            // without changing the actual photo layout or crop geometry.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight(0.91f)
                    .offset(x = 14.dp, y = 14.dp)
                    .background(BoothPalette.forest.copy(alpha = 0.62f), RoundedCornerShape(20.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight(0.91f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BoothPalette.paperLight, RoundedCornerShape(2.dp))
                    .border(1.dp, BoothPalette.forest.copy(alpha = 0.76f), RoundedCornerShape(20.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                PhotoLayoutPreview(
                    photos = photos,
                    layoutId = layoutId,
                    selectedPhotoIndex = selectedPhotoIndex,
                    onPhotoTap = { selectedPhotoIndex = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "YOUR\nMOMENTS",
                color = BoothPalette.forest,
                fontSize = 64.sp,
                lineHeight = 55.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = (-1.1).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "PHOTO ${selectedPhotoIndex + 1} OF ${photos.size}",
                color = BoothPalette.ink,
                fontSize = 21.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "Tap a photo to inspect the final crop.",
                color = BoothPalette.inkMuted,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp).fillMaxWidth(),
            )
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoothPalette.forest,
                    contentColor = BoothPalette.paperLight,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .height(82.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("CONTINUE TO ARTWORK", fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 1.sp)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = { onRetakePhoto(selectedPhotoIndex) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BoothPalette.forest),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BoothPalette.forest),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(70.dp),
                ) {
                    Text("RETAKE PHOTO ${selectedPhotoIndex + 1}", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 0.5.sp)
                }
                OutlinedButton(
                    onClick = onRetakeAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BoothPalette.forest),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BoothPalette.forest),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(70.dp),
                ) {
                    Text("RETAKE ALL", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@Composable
private fun StickerScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    borderTemplate: ResponsiveBorderTemplate?,
    stickers: List<StickerPlacement>,
    printQuantity: Int,
    allowPrintQuantityEditing: Boolean,
    onPrintQuantityChange: (Int) -> Unit,
    guestBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onChange: (List<StickerPlacement>) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (isLandscape()) {
        LandscapeStickerScreen(photos, layoutId, configuration, borderTemplate, stickers, printQuantity, allowPrintQuantityEditing, onPrintQuantityChange, guestBrightness, onBrightnessChange, onChange, onBack, onConfirm)
        return
    }
    var selectedSticker by remember { mutableStateOf(StickerId.HEART) }
    var selectedStickerIndex by remember { mutableStateOf<Int?>(null) }
    val stickerSafeArea = PhotoLayoutSpecs.forLayout(layoutId).stickerSafeArea

    fun addSticker(sticker: StickerId, x: Float = stickerSafeArea.centerX, y: Float = stickerSafeArea.centerY, scale: Float = 1f) {
        onChange(stickers + stickerSafeArea.clamp(StickerPlacement(sticker, x, y, scale)))
        selectedSticker = sticker
        selectedStickerIndex = stickers.size
    }

    fun updateSticker(index: Int, transform: (StickerPlacement) -> StickerPlacement) {
        onChange(stickers.mapIndexed { currentIndex, placement ->
            if (currentIndex == index) transform(placement) else placement
        })
    }

    val metrics = boothLayoutMetrics()
    Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(metrics.pagePadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        StitchTopBar(configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase(), configuration.venueName, "STEP 6 / CUSTOMIZE", "EDITING STRIP")
        Text("MAKE IT YOURS", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("The whole receipt is printable · Drag to move · Pinch to resize · Double-tap to copy", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 8.dp))
        StripOutputPreview(
            photos = photos,
            layoutId = layoutId,
            configuration = configuration,
            borderTemplate = borderTemplate,
            stickers = stickers,
            guestBrightness = guestBrightness.roundToInt(),
            thermalPreview = true,
            selectedStickerIndex = selectedStickerIndex,
            onSelectSticker = { selectedStickerIndex = it },
            onDuplicateSticker = { index, x, y ->
                stickers.getOrNull(index)?.let { placement ->
                    addSticker(placement.sticker, x, y, placement.scale)
                }
            },
            onTransformSticker = { index, xChange, yChange, scaleChange ->
                updateSticker(index) { placement ->
                    stickerSafeArea.clamp(placement.copy(
                        x = placement.x + xChange,
                        y = placement.y + yChange,
                        scale = placement.scale * scaleChange,
                    ))
                }
            },
        )
        Text("Sticker tray · swipe to see more", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { items(StickerId.entries) { sticker ->
            val selected = sticker == selectedSticker && selectedStickerIndex != null
            Card(Modifier.clickable { addSticker(sticker) }.border(if (selected) 2.dp else 1.dp, if (selected) BoothPalette.forest else Color(0xFFD5C9B4), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(BoothPalette.paperLight)) {
                Text(sticker.glyph + "  " + sticker.label, color = BoothPalette.ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), maxLines = 1)
            }
        } }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight)) {
            Column(Modifier.padding(16.dp)) {
                Text("Photo brightness", color = BoothPalette.ink, style = MaterialTheme.typography.titleMedium)
                Text("Preview the approximate black-and-white thermal result before printing.", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Slider(
                    value = guestBrightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Darker", color = BoothPalette.inkMuted, fontSize = 11.sp)
                    Text("Brighter", color = BoothPalette.inkMuted, fontSize = 11.sp)
                }
                Text("${guestBrightness.roundToInt()} / 100", color = BoothPalette.forest, fontSize = 12.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            }
        }
        if (allowPrintQuantityEditing) {
            PrintQuantitySelector(
                quantity = printQuantity,
                configuration = configuration,
                onQuantityChange = onPrintQuantityChange,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        } else {
            Text("PRINT QUANTITY: $printQuantity  •  TOTAL ${configuration.priceLabelForQuantity(printQuantity)}", color = BoothPalette.forest, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedButton(onClick = onBack) { Text("Back") }
            ElevatedButton(
                onClick = {
                    selectedStickerIndex?.let { index ->
                        onChange(stickers.filterIndexed { currentIndex, _ -> currentIndex != index })
                        selectedStickerIndex = null
                    }
                },
                enabled = selectedStickerIndex != null,
            ) { Text("Delete") }
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(Color(0xFFB4F5D1), Color(0xFF101719))) { Text("Create strip") }
        }
    }
}

@Composable
private fun PrintQuantitySelector(
    quantity: Int,
    configuration: BoothConfiguration,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier, colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("PRINT QUANTITY", color = BoothPalette.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("${configuration.priceLabelForQuantity(quantity)} total", color = BoothPalette.oxblood, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedButton(onClick = { onQuantityChange((quantity - 1).coerceAtLeast(1)) }, enabled = quantity > 1, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−", fontSize = 22.sp) }
                Text("$quantity", color = BoothPalette.forest, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                ElevatedButton(onClick = { onQuantityChange((quantity + 1).coerceAtMost(configuration.maxPrintQuantity)) }, enabled = quantity < configuration.maxPrintQuantity, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+", fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun LandscapeStickerScreen(
    photos: List<File>, layoutId: LayoutId, configuration: BoothConfiguration, borderTemplate: ResponsiveBorderTemplate?, stickers: List<StickerPlacement>,
    printQuantity: Int, allowPrintQuantityEditing: Boolean, onPrintQuantityChange: (Int) -> Unit, guestBrightness: Float,
    onBrightnessChange: (Float) -> Unit, onChange: (List<StickerPlacement>) -> Unit, onBack: () -> Unit, onConfirm: () -> Unit,
) {
    var selectedArtwork by remember { mutableStateOf(StickerArtworkId.CLOVER) }
    var selectedSheet by remember { mutableStateOf(StickerSheet.ROMANCE) }
    var selectedStickerIndex by remember { mutableStateOf<Int?>(null) }
    val stickerSafeArea = PhotoLayoutSpecs.forLayout(layoutId).stickerSafeArea
    fun addArtwork(artwork: StickerArtworkId, x: Float = stickerSafeArea.centerX, y: Float = stickerSafeArea.centerY, scale: Float = 1f) {
        onChange(stickers + stickerSafeArea.clamp(StickerPlacement(StickerId.HEART, x, y, scale, artwork)))
        selectedArtwork = artwork
        selectedStickerIndex = stickers.size
    }
    fun duplicateSticker(index: Int, x: Float, y: Float) {
        stickers.getOrNull(index)?.let { placement ->
            onChange(stickers + stickerSafeArea.clamp(placement.copy(x = x, y = y)))
            selectedStickerIndex = stickers.size
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF5F0E6))) {
        val titleSize = if (maxWidth >= 1200.dp) 58.sp else 42.sp
        val previewMaxHeight = maxHeight - 72.dp
        Row(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp), horizontalArrangement = Arrangement.spacedBy(38.dp)) {
            Column(Modifier.weight(3.1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f).border(2.dp, BoothPalette.forest, RoundedCornerShape(14.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                    StripOutputPreview(
                        photos = photos, layoutId = layoutId, configuration = configuration, stickers = stickers,
                        guestBrightness = guestBrightness.roundToInt(), thermalPreview = true,
                        selectedStickerIndex = selectedStickerIndex, onSelectSticker = { selectedStickerIndex = it },
                        onDuplicateSticker = ::duplicateSticker,
                        onTransformSticker = { index, dx, dy, zoom -> onChange(stickers.mapIndexed { current, placement ->
                            if (current == index) stickerSafeArea.clamp(placement.copy(x = placement.x + dx, y = placement.y + dy, scale = placement.scale * zoom)) else placement
                        }) },
                        maxPreviewWidth = 360.dp, maxPreviewHeight = previewMaxHeight, verticalPadding = 0.dp, borderTemplate = borderTemplate,
                    )
                }
                Text("Tap a sticker to select • drag or pinch to adjust", color = BoothPalette.inkMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.weight(6.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("MAKE IT YOURS", color = BoothPalette.forest, fontSize = titleSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = (-2).sp, maxLines = 1)
                    Text("Add the finishing touch.", color = BoothPalette.forest, fontSize = 25.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("STICKERS", color = BoothPalette.forest, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 2.sp)
                        if (selectedStickerIndex != null) OutlinedButton(onClick = {
                            onChange(stickers.filterIndexed { index, _ -> index != selectedStickerIndex })
                            selectedStickerIndex = null
                        }, modifier = Modifier.height(34.dp)) { Text("REMOVE SELECTED", fontSize = 9.sp) }
                    }
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StickerSheet.entries.forEach { sheet ->
                            val selected = sheet == selectedSheet
                            OutlinedButton(onClick = { selectedSheet = sheet }, modifier = Modifier.height(32.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) BoothPalette.forest else Color.Transparent, contentColor = if (selected) BoothPalette.paperLight else BoothPalette.forest)) {
                                Text(sheet.name.lowercase().replaceFirstChar(Char::uppercase), fontSize = 9.sp)
                            }
                        }
                    }
                    LazyRow(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(StickerArtworkId.entries.filter { it.sheet == selectedSheet }) { artwork ->
                            val selected = artwork == selectedArtwork
                            Card(Modifier.size(72.dp).clickable { addArtwork(artwork) }.border(if (selected) 3.dp else 1.dp, if (selected) BoothPalette.forest else Color(0xFF31573F), RoundedCornerShape(10.dp)), colors = CardDefaults.cardColors(if (selected) Color(0xFFE1EFD9) else BoothPalette.paperLight)) {
                                StickerArtworkThumbnail(artwork, Modifier.fillMaxSize().padding(6.dp))
                            }
                        }
                    }
                }
                Column(Modifier.padding(top = 4.dp)) {
                    Text("BRIGHTNESS", color = BoothPalette.forest, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 2.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("☼", color = BoothPalette.forest, fontSize = 25.sp)
                        Slider(value = guestBrightness, onValueChange = onBrightnessChange, valueRange = 0f..100f, steps = 9, modifier = Modifier.weight(1f))
                        Text("☀", color = BoothPalette.forest, fontSize = 25.sp)
                    }
                }
                if (allowPrintQuantityEditing) {
                    PrintQuantitySelector(quantity = printQuantity, configuration = configuration, onQuantityChange = onPrintQuantityChange, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("${printQuantity} PRINT${if (printQuantity == 1) "" else "S"} • ${configuration.priceLabelForQuantity(printQuantity)}", color = BoothPalette.inkMuted, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(66.dp)) { Text("‹  BACK", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 2.sp) }
                    Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest), modifier = Modifier.weight(1.35f).height(66.dp)) { Text("CONTINUE TO PRINT  ›", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp) }
                }
            }
        }
    }
}

@Composable
private fun StickerArtworkThumbnail(artwork: StickerArtworkId, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(artwork) { loadStickerArtwork(context.contentResolver, artwork) }
    if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = artwork.label, modifier = modifier, contentScale = ContentScale.Fit)
    else Text(artwork.label.take(1), color = BoothPalette.forest, modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally))
}

@Composable
internal fun StripOutputPreview(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    stickers: List<StickerPlacement>,
    guestBrightness: Int = 50,
    thermalPreview: Boolean = false,
    selectedStickerIndex: Int? = null,
    onSelectSticker: ((Int?) -> Unit)? = null,
    onDuplicateSticker: ((Int, Float, Float) -> Unit)? = null,
    onTransformSticker: ((Int, Float, Float, Float) -> Unit)? = null,
    maxPreviewWidth: Dp = 504.dp,
    verticalPadding: Dp = 16.dp,
    borderTemplate: ResponsiveBorderTemplate? = null,
    previewWidthOverride: Dp? = null,
    maxPreviewHeight: Dp? = null,
) {
    val currentStickers by rememberUpdatedState(stickers)
    val currentSelectedStickerIndex by rememberUpdatedState(selectedStickerIndex)
    val currentOnSelectSticker by rememberUpdatedState(onSelectSticker)
    val currentOnDuplicateSticker by rememberUpdatedState(onDuplicateSticker)
    val currentOnTransformSticker by rememberUpdatedState(onTransformSticker)
    val stickerSpec = remember(layoutId) { PhotoLayoutSpecs.forLayout(layoutId) }
    val context = LocalContext.current
    var receiptPreview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        photos.map { file -> file.absolutePath },
        layoutId,
        configuration.receiptTemplate,
        configuration.layoutDesign(layoutId),
        guestBrightness,
        borderTemplate?.id,
        borderTemplate?.version,
        borderTemplate?.normalizedAssetPath,
        borderTemplate?.guides,
        borderTemplate?.edgeMode,
    ) {
        // Keep the last completed bitmap visible while the next render is in
        // progress. Clearing it on every brightness tick made the receipt
        // flash off/on and made slider interaction feel delayed.
        val previewPhotos = photos.toList()
        withContext(Dispatchers.Default) {
            runCatching {
                val output = File(context.cacheDir, "receipt-preview-${UUID.randomUUID()}.png")
                try {
                    StripRenderer().renderPrintStrip(
                        sourcePhotos = previewPhotos,
                        brand = configuration.brandSnapshot(),
                        layoutId = layoutId,
                        // Sticker movement is drawn in the live overlay below.
                        // Keeping it out of this expensive PNG render prevents
                        // drag/pinch events from waiting on encode/decode work.
                        stickers = emptyList(),
                        outputFile = output,
                        receiptTemplate = configuration.receiptTemplate,
                        receiptNumber = "001256",
                        receiptTimestampMs = System.currentTimeMillis(),
                        totalLabel = configuration.priceLabelForQuantity(1),
                        contentResolver = context.contentResolver,
                        layoutDesign = configuration.layoutDesign(layoutId),
                        guestPhotoBrightness = guestBrightness,
                        borderTemplate = borderTemplate,
                    )
                    BitmapFactory.decodeFile(output.absolutePath)
                } finally {
                    output.delete()
                }
            }.getOrNull()
        }?.let { rendered -> receiptPreview = rendered }
    }
    val metrics = boothLayoutMetrics()
    val screenConfiguration = LocalConfiguration.current
    val previewAvailableWidth = if (screenConfiguration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        (screenConfiguration.screenWidthDp * 0.42f - 24f).toInt()
    } else {
        screenConfiguration.screenWidthDp - 36
    }
    val previewWidthLimit = minOf(maxPreviewWidth, previewWidthOverride ?: previewAvailableWidth.coerceAtLeast(220).dp)
    // Keep the on-screen receipt close to the real raster proportions so the
    // header, writing area, and barcode have the same breathing room as print.
    val previewAspectRatio = receiptPreview?.let { rendered -> rendered.width.toFloat() / rendered.height.toFloat() }
        ?: (576f / com.eventphotobooth.app.data.PhotoLayoutSpecs.forLayout(layoutId).defaultCanvasHeight)
    val previewWidthFromHeight = maxPreviewHeight
        ?.takeIf { it.value.isFinite() && it.value > 0f }
        ?.let { (it.value * previewAspectRatio).dp }
    val previewWidth = minOf(previewWidthLimit, previewWidthFromHeight ?: previewWidthLimit)
    val previewHeight = (previewWidth.value / previewAspectRatio).dp
    val previewModifier = Modifier
        .padding(top = verticalPadding)
        .size(previewWidth, previewHeight)
        // The surrounding editor is also light-colored. Give the receipt a
        // visible physical edge so its true template height is clear before
        // the guest sends it to the thermal cutter.
        .border(1.dp, BoothPalette.inkMuted.copy(alpha = 0.42f))
        .background(Color.White)
        .then(
            if (onSelectSticker != null) {
                Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { position ->
                                currentOnSelectSticker?.invoke(findStickerIndex(currentStickers, position.x, position.y, size.width.toFloat(), size.height.toFloat(), layoutId))
                            },
                            onDoubleTap = { position ->
                                val index = currentSelectedStickerIndex ?: findStickerIndex(currentStickers, position.x, position.y, size.width.toFloat(), size.height.toFloat(), layoutId)
                                if (index != null) currentOnDuplicateSticker?.invoke(
                                    index,
                                    position.x * 576f / size.width,
                                    position.y * stickerSpec.defaultCanvasHeight / size.height,
                                )
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val index = currentSelectedStickerIndex ?: findStickerIndex(currentStickers, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat(), layoutId)
                            if (index != null) {
                                currentOnTransformSticker?.invoke(
                                    index,
                                    pan.x * 576f / size.width,
                                    pan.y * stickerSpec.defaultCanvasHeight / size.height,
                                    zoom,
                                )
                            }
                        }
                    }
            } else Modifier,
        )
    Box(
        modifier = previewModifier,
        contentAlignment = Alignment.Center,
    ) {
        receiptPreview?.let { rendered ->
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = "80 mm thermal receipt preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: CircularProgressIndicator(color = BoothPalette.forest)
        LiveStickerOverlay(
            stickers = currentStickers,
            layoutId = layoutId,
            showSafeArea = onSelectSticker != null,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Draws stickers directly in the preview coordinate space so gesture updates
 * are visible on the next Compose frame instead of waiting for a full receipt
 * bitmap render. It delegates to the exact same receipt-coordinate drawing
 * function used to save and print the final strip.
 */
@Composable
private fun LiveStickerOverlay(
    stickers: List<StickerPlacement>,
    layoutId: LayoutId,
    showSafeArea: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentResolver = LocalContext.current.contentResolver
    Canvas(modifier) {
        val receiptCanvas = drawContext.canvas.nativeCanvas
        if (showSafeArea) {
            drawStickerSafeArea(receiptCanvas, layoutId, size.width.toInt(), size.height.toInt())
        }
        drawReceiptStickers(receiptCanvas, stickers, layoutId, size.width.toInt(), size.height.toInt(), contentResolver)
    }
}

@Composable
private fun ReceiptRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.7f)))
}

@Composable
private fun ReceiptWritingLine() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 4.dp).height(1.dp).background(Color.Black.copy(alpha = 0.8f)))
}

@Composable
private fun ReceiptLogoPreview(uri: String, fallback: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            uri.takeIf(String::isNotBlank)?.let { context.contentResolver.openInputStream(android.net.Uri.parse(it))?.use(BitmapFactory::decodeStream) }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), "Receipt logo", modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Text(fallback.ifBlank { "YOUR MOMENTS" }.uppercase(), color = Color(0xFF18201F), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ReceiptBarcodePreview(modifier: Modifier = Modifier) {
    val bars = remember { List(52) { index -> ((index * 17 + 7) % 5 + 1) } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { index, width ->
            Box(Modifier.width(width.dp.coerceAtMost(3.dp)).fillMaxHeight().background(if (index % 3 == 0) Color.Black else Color(0xFF222222)))
        }
    }
}

private fun findStickerIndex(
    stickers: List<StickerPlacement>,
    touchX: Float,
    touchY: Float,
    width: Float,
    height: Float,
    layoutId: LayoutId,
): Int? = stickers.indices.minByOrNull { index ->
    val sticker = stickers[index]
    val layout = PhotoLayoutSpecs.forLayout(layoutId)
    val stickerX = sticker.x * width / 576f
    val stickerY = sticker.y * height / layout.defaultCanvasHeight
    (touchX - stickerX) * (touchX - stickerX) + (touchY - stickerY) * (touchY - stickerY)
}?.takeIf { index ->
    val sticker = stickers[index]
    val layout = PhotoLayoutSpecs.forLayout(layoutId)
    val distanceX = touchX - sticker.x * width / 576f
    val distanceY = touchY - sticker.y * height / layout.defaultCanvasHeight
    val hitRadius = maxOf(56f, minOf(width, height) * 0.10f) * stickers[index].scale.coerceIn(0.75f, 2.5f)
    distanceX * distanceX + distanceY * distanceY <= hitRadius * hitRadius
}

@Composable
private fun PhotoLayoutPreview(
    photos: List<File>,
    layoutId: LayoutId,
    modifier: Modifier = Modifier,
    guestBrightness: Int = 50,
    thermalPreview: Boolean = false,
    frameColor: Color = Color(0xFFE1EAE6),
    selectedPhotoIndex: Int? = null,
    onPhotoTap: ((Int) -> Unit)? = null,
) {
    val spec = com.eventphotobooth.app.data.PhotoLayoutSpecs.forLayout(layoutId)
    val isGrid = spec.columns == 2
    if (layoutId == LayoutId.ONE_PHOTO) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            photos.firstOrNull()?.let { photo ->
                PhotoCell(
                    photo = photo,
                    modifier = Modifier.fillMaxWidth().aspectRatio(spec.photoAspectRatio).then(if (onPhotoTap != null) Modifier.clickable { onPhotoTap(0) } else Modifier),
                    photoAspectRatio = spec.photoAspectRatio,
                    guestBrightness = guestBrightness,
                    thermalPreview = thermalPreview,
                    frameColor = frameColor,
                    selected = selectedPhotoIndex == 0,
                )
            }
        }
        return
    }
    if (layoutId == LayoutId.THREE_STRIP) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            // Fit the complete three-frame composition inside the available
            // bounds. Previously each cell used the full width and the
            // weighted parent clipped the lower portions of the strip.
            val gap = 5.dp
            val availableCellHeight = ((maxHeight - gap * 2) / 3).coerceAtLeast(1.dp)
            val cellWidthFromHeight = availableCellHeight * spec.photoAspectRatio
            val cellWidth = minOf(maxWidth, cellWidthFromHeight)
            val cellHeight = (cellWidth / spec.photoAspectRatio).coerceAtLeast(1.dp)
            Column(
                modifier = Modifier.width(cellWidth),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                photos.forEachIndexed { index, photo ->
                    PhotoCell(
                        photo = photo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cellHeight)
                            .then(if (onPhotoTap != null) Modifier.clickable { onPhotoTap(index) } else Modifier),
                        photoAspectRatio = spec.photoAspectRatio,
                        guestBrightness = guestBrightness,
                        thermalPreview = thermalPreview,
                        frameColor = frameColor,
                        selected = selectedPhotoIndex == index,
                    )
                }
            }
        }
        return
    }
    val rows = if (isGrid) photos.chunked(spec.columns) else photos.map(::listOf)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEachIndexed { columnIndex, photo ->
                    val photoIndex = rowIndex * spec.columns + columnIndex
                    PhotoCell(
                        photo = photo,
                        modifier = Modifier.weight(1f).fillMaxSize().then(if (onPhotoTap != null) Modifier.clickable { onPhotoTap(photoIndex) } else Modifier),
                        photoAspectRatio = spec.photoAspectRatio,
                        guestBrightness = guestBrightness,
                        thermalPreview = thermalPreview,
                        frameColor = frameColor,
                        selected = selectedPhotoIndex == photoIndex,
                    )
                }
                if (row.size == 1 && isGrid) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: File,
    modifier: Modifier,
    photoAspectRatio: Float,
    guestBrightness: Int = 50,
    thermalPreview: Boolean = false,
    frameColor: Color = Color(0xFFE1EAE6),
    contentScale: ContentScale = ContentScale.Fit,
    selected: Boolean = false,
) {
    val bitmap = remember(photo.absolutePath, photoAspectRatio, guestBrightness, thermalPreview) {
        createPreviewBitmap(photo, photoAspectRatio, guestBrightness, thermalPreview)
    }
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            "Captured photo in final layout",
            modifier = modifier
                .background(Color(0xFFF7F3EB))
                .border(if (selected) 3.dp else 1.dp, if (selected) BoothPalette.forest else frameColor),
            contentScale = contentScale,
        )
    } else {
        Box(modifier.background(Color(0xFFDBE4DF)))
    }
}

private fun createPreviewBitmap(file: File, photoAspectRatio: Float, guestBrightness: Int, thermalPreview: Boolean): Bitmap? {
    val source = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val crop = com.eventphotobooth.app.render.centerCropSourceRect(source, photoAspectRatio)
    val cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.width(), crop.height())
    val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    val brightnessOffset = (guestBrightness.coerceIn(0, 100) - 50) * 2f
    val scale = if (thermalPreview) 1.18f else 1f
    val matrixValues = if (thermalPreview) {
        floatArrayOf(
            0.299f * scale, 0.587f * scale, 0.114f * scale, 0f, brightnessOffset,
            0.299f * scale, 0.587f * scale, 0.114f * scale, 0f, brightnessOffset,
            0.299f * scale, 0.587f * scale, 0.114f * scale, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f,
        )
    } else {
        floatArrayOf(
            1f, 0f, 0f, 0f, brightnessOffset,
            0f, 1f, 0f, 0f, brightnessOffset,
            0f, 0f, 1f, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f,
        )
    }
    Canvas(output).drawBitmap(
        cropped,
        null,
        Rect(0, 0, output.width, output.height),
        Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixValues)) },
    )
    if (cropped !== source) cropped.recycle()
    source.recycle()
    return output
}

@Composable
private fun PhotoThumbnail(photo: File) {
    val bitmap = remember(photo.absolutePath) { BitmapFactory.decodeFile(photo.absolutePath) }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Captured photo", contentScale = ContentScale.Crop, modifier = Modifier.size(156.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFB4F5D1), RoundedCornerShape(12.dp)))
}

@Composable
private fun ProcessingScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    stickers: List<StickerPlacement>,
    guestBrightness: Int,
    borderTemplate: ResponsiveBorderTemplate?,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BoothPalette.paper),
    ) {
        val landscape = maxWidth >= 700.dp
        val feedTransition = rememberInfiniteTransition(label = "photo-preparation")
        val feedProgress by feedTransition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.86f,
            animationSpec = infiniteRepeatable(tween(1_900), RepeatMode.Reverse),
            label = "preparation-progress",
        )
        val previewWidth = if (landscape) 230.dp else 190.dp
        val previewHeight = if (landscape) 470.dp else 330.dp
        val titleSize = if (landscape) 48.sp else 36.sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (landscape) 54.dp else 28.dp, vertical = if (landscape) 26.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "PREPARING YOUR PHOTOS",
                color = BoothPalette.forest,
                fontSize = titleSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = (-1.4).sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "Putting the finishing touches on your photo strip.",
                color = BoothPalette.inkMuted,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            FloatingStripStage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                stripOffset = ((0.5f - feedProgress) * 32f).dp,
            ) {
                StripOutputPreview(
                    photos = photos,
                    layoutId = layoutId,
                    configuration = configuration,
                    stickers = stickers,
                    guestBrightness = guestBrightness,
                    thermalPreview = true,
                    borderTemplate = borderTemplate,
                    maxPreviewWidth = previewWidth,
                    previewWidthOverride = previewWidth,
                    maxPreviewHeight = previewHeight,
                    verticalPadding = 0.dp,
                )
            }

            Column(
                modifier = Modifier.widthIn(max = if (landscape) 620.dp else 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "LAYOUT  •  ARTWORK  •  FINISHING",
                    color = BoothPalette.forest,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(14.dp)
                        .background(BoothPalette.paperPanel, RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(feedProgress)
                            .fillMaxHeight()
                            .background(BoothPalette.forest, RoundedCornerShape(14.dp)),
                    )
                }
                Text(
                    text = "Almost ready to print…",
                    color = BoothPalette.ink,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingStripStage(
    modifier: Modifier = Modifier,
    stripOffset: Dp,
    stripContent: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = stripOffset)
                .background(Color.White, RoundedCornerShape(2.dp))
                .border(1.dp, BoothPalette.ink.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            stripContent()
        }
    }
}

@Composable
private fun PrintingAnimationScreen(session: LocalPhotoSession, printStatus: PrintStatus, printMessage: String?) {
    val bitmap = remember(session.printStripPath) { BitmapFactory.decodeFile(session.printStripPath) }
    var animationProgress by remember(session.id) { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(2_200),
        label = "strip-feed",
    )
    LaunchedEffect(session.id) {
        delay(80)
        animationProgress = 1f
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BoothPalette.paper),
    ) {
        // The supplied 16:9 artwork remains intact. Fit prevents the title or
        // printer from being cropped on 16:10 tablet screens.
        val artworkAspectRatio = 1680f / 945f
        val artworkWidth = minOf(maxWidth, maxHeight * artworkAspectRatio)
        val artworkHeight = artworkWidth / artworkAspectRatio
        val artworkTop = (maxHeight - artworkHeight) / 2

        Image(
            painter = painterResource(com.eventphotobooth.app.R.drawable.printing_background),
            contentDescription = "Printing your photos",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .width(artworkWidth)
                .height(artworkHeight),
        )

        bitmap?.let {
            val receiptAspectRatio = it.width.toFloat() / it.height.toFloat()
            // Coordinates are relative to the supplied artwork: the black slot
            // begins near 22.5% of its height and is centered horizontally.
            val slotTop = artworkTop + (artworkHeight * 0.225f)
            val availableReceiptHeight = (maxHeight - slotTop - 16.dp).coerceAtLeast(80.dp)
            val receiptWidth = minOf(
                artworkWidth * 0.17f,
                260.dp,
                availableReceiptHeight * receiptAspectRatio,
            )
            val receiptHeight = receiptWidth / receiptAspectRatio
            val hiddenOffset = -(receiptHeight - 18.dp)
            val paperOffset = hiddenOffset * (1f - animatedProgress.coerceIn(0f, 1f))

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = slotTop)
                    .width(receiptWidth)
                    .height(receiptHeight)
                    .clip(RoundedCornerShape(0.dp)),
            ) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Your edited photo strip coming out of the printer",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(receiptWidth)
                        .aspectRatio(receiptAspectRatio)
                        .offset(y = paperOffset),
                )
            }
        }
    }
}

@Composable
private fun CompleteScreen(session: LocalPhotoSession, printStatus: PrintStatus, printMessage: String?, onDone: () -> Unit) {
    LaunchedEffect(session.id, printStatus) {
        if (printStatus != PrintStatus.PRINTING) {
            delay(if (printStatus == PrintStatus.PRINTED) 30_000 else 45_000)
            onDone()
        }
    }
    val headline = when (printStatus) {
        PrintStatus.PRINTED -> "Print sent"
        PrintStatus.FAILED -> "Ask staff if it does not print"
        PrintStatus.PRINTING, PrintStatus.QUEUED -> "Printing your strip"
        else -> "Preparing your strip"
    }
    val detail = when (printStatus) {
        PrintStatus.PRINTED -> "Your strip has been sent to the printer."
        PrintStatus.FAILED -> printMessage ?: "The print job is saved. Staff can retry it from operator settings."
        PrintStatus.PRINTING -> "Please wait while the printer receives your strip."
        PrintStatus.QUEUED -> "Your print job is saved and waiting for the printer."
        else -> "Your print strip is being prepared."
    }
    Row(Modifier.fillMaxSize().background(BoothPalette.paper).padding(horizontal = 54.dp, vertical = 42.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(54.dp)) {
        Column(Modifier.weight(0.48f)) {
            Text("THANK YOU", color = BoothPalette.ink, fontSize = 52.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(headline.uppercase(), color = BoothPalette.oxblood, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(detail, color = BoothPalette.inkMuted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.weight(0.52f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Card(colors = CardDefaults.cardColors(if (printStatus == PrintStatus.FAILED) Color(0xFFFFE4DF) else Color(0xFFE5F3E7)), border = androidx.compose.foundation.BorderStroke(1.dp, if (printStatus == PrintStatus.FAILED) BoothPalette.oxblood else BoothPalette.forest)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(if (printStatus == PrintStatus.FAILED) "STAFF ACTION MAY BE NEEDED" else "PRINT JOB SAVED", color = if (printStatus == PrintStatus.FAILED) BoothPalette.oxblood else BoothPalette.forest, style = MaterialTheme.typography.titleMedium)
                    Text("Your digital files remain safe even if the printer needs attention.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 8.dp))
                    if (printStatus == PrintStatus.PRINTING) CircularProgressIndicator(color = BoothPalette.forest, modifier = Modifier.padding(top = 18.dp))
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood)) { Text("DONE", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
    }
}

@Composable
private fun DigitalDeliveryScreen(session: LocalPhotoSession, deliveryLinks: DeliveryLinks?, onDone: () -> Unit) {
    LaunchedEffect(session.id) {
        delay(60_000)
        onDone()
    }
    val digitalStripReady = session.digitalStripPath?.let { File(it).exists() } == true
    val animatedGifReady = session.animatedGifPath?.let { File(it).exists() } == true
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        val landscape = maxWidth >= 700.dp
        val qrSize = if (landscape) 248.dp else 212.dp
        val cardWidth = qrSize + 46.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (landscape) 52.dp else 26.dp, vertical = if (landscape) 26.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "GET YOUR DIGITAL COPIES",
                color = BoothPalette.forest,
                fontSize = if (landscape) 46.sp else 34.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = (-1.2).sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
            Text(
                "SAVE THE MOMENT",
                color = BoothPalette.forestLight,
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                if (deliveryLinks == null) "Your copies are saved safely on this tablet." else "Scan a code to download your photo strip or animated GIF.",
                color = BoothPalette.inkMuted,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
            )

            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(34.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                DigitalQrCard(
                    title = "PHOTO STRIP",
                    label = "LOCAL STRIP",
                    ready = digitalStripReady,
                    fileName = session.digitalStripPath?.let { File(it).name },
                    downloadUrl = deliveryLinks?.photoUrl,
                    qrSize = qrSize,
                    modifier = Modifier.width(cardWidth),
                )
                DigitalQrCard(
                    title = "ANIMATED GIF",
                    label = "LOCAL GIF",
                    ready = animatedGifReady,
                    fileName = session.animatedGifPath?.let { File(it).name },
                    downloadUrl = deliveryLinks?.gifUrl,
                    qrSize = qrSize,
                    modifier = Modifier.width(cardWidth),
                )
            }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                ) {
                DigitalQrCard(
                    title = "PHOTO STRIP",
                    label = "LOCAL STRIP",
                    ready = digitalStripReady,
                    fileName = session.digitalStripPath?.let { File(it).name },
                    downloadUrl = deliveryLinks?.photoUrl,
                    qrSize = qrSize,
                    modifier = Modifier.width(cardWidth),
                )
                DigitalQrCard(
                    title = "ANIMATED GIF",
                    label = "LOCAL GIF",
                    ready = animatedGifReady,
                    fileName = session.animatedGifPath?.let { File(it).name },
                    downloadUrl = deliveryLinks?.gifUrl,
                    qrSize = qrSize,
                    modifier = Modifier.width(cardWidth),
                )
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.width(if (landscape) 330.dp else 260.dp).height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
            ) { Text("DONE  →", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp) }
            Text("This screen returns to the start automatically.", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun DigitalQrCard(
    title: String,
    label: String,
    ready: Boolean,
    fileName: String?,
    downloadUrl: String?,
    qrSize: Dp,
    modifier: Modifier = Modifier.width(230.dp),
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BoothPalette.paperLight),
        border = androidx.compose.foundation.BorderStroke(2.dp, BoothPalette.forest),
    ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = BoothPalette.forest, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 1.sp)
        if (downloadUrl != null) {
            DownloadQrTile(downloadUrl, Modifier.padding(top = 12.dp), qrSize)
        } else {
            DemoQrTile(Modifier.padding(top = 12.dp).size(qrSize), label)
        }
        Text(if (downloadUrl != null) "SCAN TO DOWNLOAD" else "AVAILABLE ON THIS TABLET", color = BoothPalette.forest, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 12.dp))
        Text(if (ready) "SAVED LOCALLY" else "PREPARING FILE", color = if (ready) BoothPalette.forestLight else BoothPalette.warmAccent, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        fileName?.let { Text(it, color = BoothPalette.inkMuted, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun DownloadQrTile(url: String, modifier: Modifier = Modifier, size: Dp = 230.dp) {
    val bitmap = remember(url) { createQrBitmap(url, 360) }
    Card(modifier = modifier.size(size), colors = CardDefaults.cardColors(Color.White)) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Scan to download",
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("QR unavailable", color = BoothPalette.oxblood, modifier = Modifier.padding(20.dp))
        }
    }
}

private fun createQrBitmap(value: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints)
    Bitmap.createBitmap(size, size, Config.ARGB_8888).also { bitmap ->
        for (y in 0 until size) for (x in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}.getOrNull()


@Composable
private fun OperatorGateScreen(pinStore: OperatorPinStore, onSuccess: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val setup = !pinStore.hasPin()
    var editingConfirmation by remember(setup) { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(BoothPalette.chrome).padding(horizontal = 38.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.48f), verticalArrangement = Arrangement.Center) {
            Text(if (setup) "CREATE OPERATOR PIN" else "OPERATOR ACCESS", color = Color.White, fontSize = 38.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Protected booth controls and service settings.", color = BoothPalette.chromeText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
            CodeCellRow(pin, if (setup) 4 else 4, dark = true, selected = !editingConfirmation, onClick = { editingConfirmation = false })
            if (setup) {
                Text("CONFIRM PIN", color = BoothPalette.chromeText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 20.dp))
                CodeCellRow(confirmation, 4, dark = true, selected = editingConfirmation, onClick = { editingConfirmation = true })
            }
            error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 16.dp)) }
        }
        Column(Modifier.weight(0.52f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            NumericKeypad(
                dark = true,
                onDigit = { digit ->
                    if (editingConfirmation) confirmation = (confirmation + digit).take(12) else pin = (pin + digit).take(12)
                    error = null
                },
                onBackspace = { if (editingConfirmation) confirmation = confirmation.dropLast(1) else pin = pin.dropLast(1) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ElevatedButton(onClick = onBack, modifier = Modifier.weight(1f).height(54.dp)) { Text("BACK") }
                Button(onClick = {
                    error = when {
                        pin.length < 4 -> "Use at least four digits."
                        setup && confirmation.length < 4 -> { editingConfirmation = true; "Confirm the PIN." }
                        setup && pin != confirmation -> "The PINs do not match."
                        setup -> { pinStore.setPin(pin); onSuccess(); null }
                        pinStore.matches(pin) -> { onSuccess(); null }
                        else -> "That PIN is not correct."
                    }
                }, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood)) { Text(if (setup) "SAVE PIN" else "UNLOCK") }
            }
        }
    }
}

@Composable
private fun BusinessPinScreen(configuration: BoothConfiguration, printQuantity: Int, onSuccess: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLength = configuration.businessPin.length.coerceAtLeast(4)
    fun confirmPin() {
        error = when {
            configuration.businessPin.length < 4 -> "Operator PIN is not configured."
            pin == configuration.businessPin -> { onSuccess(); null }
            else -> "That PIN is not correct. Try again."
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        val compact = maxWidth < 900.dp
        // Keep the keypad and the persistent Back action in separate horizontal
        // zones. This is intentionally narrower on smaller landscape tablets;
        // otherwise the weighted keypad can cover the Back action.
        val keypadWidth = when {
            compact -> maxWidth * 0.68f
            maxWidth < 1060.dp -> 480.dp
            else -> 530.dp
        }
        val backButtonWidth = if (maxWidth < 1060.dp) 150.dp else 208.dp
        // The heading needs a wider measure than the keypad. Keeping both in the
        // same narrow column caused "PIN" to be clipped on the tablet.
        val headingWidth = (maxWidth - 64.dp).coerceAtMost(720.dp)
        val titleSize = when {
            compact -> 36.sp
            maxWidth < 1060.dp -> 44.sp
            else -> 56.sp
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(headingWidth)
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ENTER BUSINESS PIN",
                color = BoothPalette.forest,
                fontSize = titleSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = (-1).sp,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(72.dp).height(1.dp).background(BoothPalette.forest.copy(alpha = 0.65f)))
                Text("${if (pinLength == 4) "FOUR" else pinLength} DIGITS", color = BoothPalette.forestLight, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 3.sp)
                Box(Modifier.width(72.dp).height(1.dp).background(BoothPalette.forest.copy(alpha = 0.65f)))
            }
            BusinessPinCells(pin = pin, count = pinLength, modifier = Modifier.padding(top = 24.dp))
            error?.let { Text(it, color = BoothPalette.oxblood, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            ThemedCodeKeypad(
                onDigit = { digit -> pin = (pin + digit).take(pinLength); error = null },
                onBackspace = { pin = pin.dropLast(1); error = null },
                onPrimary = ::confirmPin,
                primaryLabel = "ENTER  →",
                modifier = Modifier.width(keypadWidth).padding(top = 24.dp),
            )
        }
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, BoothPalette.forest),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BoothPalette.forest),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 30.dp).height(60.dp).width(backButtonWidth),
        ) { Text("←  BACK", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp) }
    }
}

@Composable
private fun BusinessPinCells(pin: String, count: Int, modifier: Modifier = Modifier) {
    val cellSize = when {
        count <= 4 -> 86.dp
        count <= 6 -> 70.dp
        else -> 46.dp
    }
    val gap = when {
        count <= 4 -> 14.dp
        count <= 6 -> 10.dp
        else -> 6.dp
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(cellSize)
                    .background(BoothPalette.paperLight, RoundedCornerShape(13.dp))
                    .border(2.dp, BoothPalette.forest, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (index < pin.length) Text("●", color = BoothPalette.forest, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun ThemedCodeKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onPrimary: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val keyColor = BoothPalette.forest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..9).map(Int::toString).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Button(
                        onClick = { onDigit(key) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = keyColor, contentColor = BoothPalette.paperLight),
                        modifier = Modifier.weight(1f).height(70.dp),
                    ) { Text(key, fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onBackspace,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = keyColor, contentColor = BoothPalette.paperLight),
                modifier = Modifier.weight(1f).height(70.dp),
            ) { Text("⌫", fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            Button(
                onClick = { onDigit("0") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = keyColor, contentColor = BoothPalette.paperLight),
                modifier = Modifier.weight(1f).height(70.dp),
            ) { Text("0", fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black) }
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoothPalette.forestLight,
                    contentColor = BoothPalette.paperLight,
                    disabledContainerColor = BoothPalette.paperPanel,
                    disabledContentColor = BoothPalette.inkMuted,
                ),
                modifier = Modifier.weight(1f).height(70.dp),
            ) { Text(primaryLabel, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.5.sp) }
        }
    }
}

@Composable
private fun AccessCodeScreen(
    client: AccessCodeClient,
    configuration: BoothConfiguration,
    onSuccess: (RedeemedAccessCode) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun redeemCode() {
        if (code.length != 5 || loading) return
        loading = true
        scope.launch {
            client.redeem(code).onSuccess(onSuccess).onFailure { error = it.message ?: "That code could not be verified." }
            loading = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(BoothPalette.paper)) {
        val compact = maxWidth < 900.dp
        val keypadWidth = when {
            compact -> maxWidth * 0.68f
            maxWidth < 1060.dp -> 480.dp
            else -> 530.dp
        }
        val backButtonWidth = if (maxWidth < 1060.dp) 150.dp else 208.dp
        val headingWidth = (maxWidth - 64.dp).coerceAtMost(720.dp)
        val titleSize = when {
            compact -> 36.sp
            maxWidth < 1060.dp -> 44.sp
            else -> 56.sp
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(headingWidth)
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ENTER ACCESS CODE",
                color = BoothPalette.forest,
                fontSize = titleSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                letterSpacing = (-1).sp,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(72.dp).height(1.dp).background(BoothPalette.forest.copy(alpha = 0.65f)))
                Text("FIVE DIGITS", color = BoothPalette.forestLight, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 3.sp)
                Box(Modifier.width(72.dp).height(1.dp).background(BoothPalette.forest.copy(alpha = 0.65f)))
            }
            BusinessPinCells(pin = code, count = 5, modifier = Modifier.padding(top = 24.dp))
            error?.let { Text(it, color = BoothPalette.oxblood, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            ThemedCodeKeypad(
                onDigit = { digit -> code = (code + digit).take(5); error = null },
                onBackspace = { code = code.dropLast(1); error = null },
                onPrimary = ::redeemCode,
                primaryLabel = if (loading) "CHECKING…" else "REDEEM  →",
                primaryEnabled = code.length == 5 && !loading,
                modifier = Modifier.width(keypadWidth).padding(top = 24.dp),
            )
            Text(
                "${configuration.venueName.uppercase()}  •  PACKAGE ACCESS",
                color = BoothPalette.forest,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        OutlinedButton(
            onClick = onBack,
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, BoothPalette.forest),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BoothPalette.forest),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 30.dp).height(60.dp).width(backButtonWidth),
        ) { Text("←  BACK", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp) }
    }
}

@Composable
private fun CodeCellRow(value: String, count: Int, dark: Boolean, selected: Boolean, onClick: () -> Unit = {}) {
    Row(Modifier.padding(top = 24.dp).clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { index ->
            Box(
                Modifier.size(58.dp)
                    .background(if (dark) BoothPalette.chromeRaised else BoothPalette.paperLight, RoundedCornerShape(8.dp))
                    .border(if (selected) 2.dp else 1.dp, if (selected) BoothPalette.focus else BoothPalette.border, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (index < value.length) "●" else "", color = if (dark) BoothPalette.mint else BoothPalette.ink, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun NumericKeypad(dark: Boolean = false, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(onClick = { onDigit(key) }, modifier = Modifier.weight(1f).height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = if (dark) BoothPalette.chromeRaised else BoothPalette.paperLight, contentColor = if (dark) Color.White else BoothPalette.ink), shape = RoundedCornerShape(8.dp)) { Text(key, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElevatedButton(onClick = onBackspace, modifier = Modifier.weight(1f).height(58.dp)) { Text("DELETE") }
            Button(onClick = { onDigit("0") }, modifier = Modifier.weight(2f).height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = if (dark) BoothPalette.chromeRaised else BoothPalette.paperLight, contentColor = if (dark) Color.White else BoothPalette.ink), shape = RoundedCornerShape(8.dp)) { Text("0", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
    }
}

@Composable
private fun LayoutDesignEditor(
    designs: Map<LayoutId, LayoutDesign>,
    onChange: (LayoutId, LayoutDesign) -> Unit,
) {
    val colorPresets = listOf(
        Triple(0xFFB4F5D1L, 0xFFFFFFFFL, 0xFFE1EAE6L),
        Triple(0xFFFFC7D9L, 0xFFFFF2F6L, 0xFFF2B7C8L),
        Triple(0xFFFFD98EL, 0xFFFFFBEBL, 0xFFF2D18AL),
        Triple(0xFFC9C2FFL, 0xFFF8F6FFL, 0xFFD4CDF6L),
        Triple(0xFFB9DFFFL, 0xFFF2FAFFL, 0xFFC3E3F4L),
    )
    Card(colors = CardDefaults.cardColors(BoothPalette.chrome)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Layout designs", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Keep the photo geometry reliable while customizing each layout's name and visual preset.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
            LayoutId.entries.forEach { layout ->
                val design = designs[layout] ?: LayoutDesign(layout.displayName, 0xFFB4F5D1L)
                Card(colors = CardDefaults.cardColors(Color(0xFF263035)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LayoutDiagram(layout, Color(design.accentColor))
                            Text("${layout.photoCount} photos", color = Color(0xFFD6E6DF), fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                        OutlinedTextField(
                            value = design.displayName,
                            onValueChange = { onChange(layout, design.copy(displayName = it.take(28))) },
                            label = { Text("Guest-facing layout name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Text("Accent preset", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            colorPresets.forEach { (color, background, frame) ->
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .border(if (design.accentColor == color) 3.dp else 1.dp, Color.White, CircleShape)
                                        .clickable { onChange(layout, design.copy(accentColor = color, backgroundColor = background, frameColor = frame)) },
                                )
                            }
                        }
                        Text("Choose a color for the layout preview, header accent, and photo frames.", color = Color(0xFF9AA8A2), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

private fun priceTextValues(pricesMinor: List<Int>, count: Int, fallbackBaseMinor: Int): List<String> = List(count) { index ->
    "%.2f".format((pricesMinor.getOrNull(index) ?: fallbackBaseMinor * (index + 1)) / 100.0)
}

@Composable
private fun PriceTierEditor(
    title: String,
    description: String,
    values: List<String>,
    colors: androidx.compose.material3.TextFieldColors,
    onValuesChange: (List<String>) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(BoothPalette.chrome)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(description, color = Color(0xFFD6E6DF), fontSize = 12.sp)
            values.chunked(2).forEachIndexed { rowIndex, rowValues ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowValues.forEachIndexed { columnIndex, value ->
                        val index = rowIndex * 2 + columnIndex
                        OutlinedTextField(
                            value = value,
                            onValueChange = { updatedValue ->
                                if (updatedValue.all { it.isDigit() || it == '.' }) {
                                    onValuesChange(values.toMutableList().also { it[index] = updatedValue })
                                }
                            },
                            label = { Text("${index + 1} print${if (index == 0) "" else "s"} · PHP") },
                            colors = colors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowValues.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private enum class OperatorSettingsTab(val label: String, val description: String) {
    GENERAL("General", "Venue identity, operator PIN, delivery status, and display accessibility."),
    MODES("Modes", "Choose the guest entry flow: Rental, Business, or Code access."),
    BUSINESS("Business", "Configure checkout prices and the payment QR choices shown to guests."),
    LAYOUTS("Layout designs", "Enable photo-strip layouts and tailor their names and visual presets."),
    RECEIPT_TEMPLATES("Receipt templates", "Manage bundled and tablet-only responsive receipt borders."),
    HARDWARE("Hardware", "Connect a printer, test it, and tune thermal output for the installed paper."),
    PRINT_QUEUE("Print queue", "Review local print jobs and retry anything waiting for the printer."),
}

@Composable
private fun OperatorSettingsTabs(
    selectedTab: OperatorSettingsTab,
    onSelect: (OperatorSettingsTab) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(BoothPalette.chrome)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OperatorSettingsTab.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tab ->
                        ElevatedButton(
                            onClick = { onSelect(tab) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (selectedTab == tab) BoothPalette.mint else BoothPalette.chromeRaised,
                                contentColor = if (selectedTab == tab) BoothPalette.ink else BoothPalette.chromeText,
                            ),
                        ) {
                            Text(if (selectedTab == tab) "✓ ${tab.label}" else tab.label, fontSize = 12.sp)
                        }
                    }
                }
            }
            Text(selectedTab.description, color = BoothPalette.chromeText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun OperatorSettingsScreen(
    configuration: BoothConfiguration,
    sessionCount: Int,
    printJobs: List<PrintJob>,
    latestSession: LocalPhotoSession?,
    sessionStore: LocalSessionStore,
    borderTemplateStore: LocalReceiptBorderTemplateStore,
    borderTemplates: List<ResponsiveBorderTemplate>,
    onBorderTemplatesChanged: (List<ResponsiveBorderTemplate>) -> Unit,
    onSaveConfiguration: (BoothConfiguration) -> Unit,
    onOpenProvisioning: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brandingAssetStore = remember { LocalBrandingAssetStore(context.applicationContext) }
    val discovery = remember { UsbPrinterDiscovery(context.applicationContext) }; var usbDevices by remember { mutableStateOf(discovery.discover()) }
    var welcomeLogoPath by remember(configuration) { mutableStateOf(configuration.welcomeLogoPath) }
    var welcomeLogoMessage by remember { mutableStateOf<String?>(null) }
    var qrUris by remember(configuration) { mutableStateOf(configuration.paymentQrUris.toMutableList()) }
    var qrTitles by remember(configuration) { mutableStateOf(configuration.paymentQrTitles.toMutableList()) }
    var qrSlot by remember { mutableStateOf(0) }
    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val updated = qrUris.toMutableList()
        while (updated.size <= qrSlot) updated.add("")
        updated[qrSlot] = uri.toString()
        qrUris = updated
    }
    val welcomeLogoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { brandingAssetStore.importWelcomeLogo(uri) }
            }.onSuccess { importedPath ->
                if (welcomeLogoPath.isNotBlank() && welcomeLogoPath != configuration.welcomeLogoPath) {
                    brandingAssetStore.remove(welcomeLogoPath)
                }
                welcomeLogoPath = importedPath
                welcomeLogoMessage = "Logo ready. Save kiosk configuration to publish it on the welcome screen."
            }.onFailure { error ->
                welcomeLogoMessage = error.message ?: "The selected logo could not be used."
            }
        }
    }
    val printQueue = remember { LocalPrintQueue(sessionStore) }
    var visiblePrintJobs by remember(printJobs) { mutableStateOf(printJobs.asReversed()) }
    var printerPermissionMessage by remember { mutableStateOf<String?>(null) }
    var venueName by remember(configuration) { mutableStateOf(configuration.venueName) }; var header by remember(configuration) { mutableStateOf(configuration.header) }; var logoText by remember(configuration) { mutableStateOf(configuration.logoText) }; var footer by remember(configuration) { mutableStateOf(configuration.footer) }; var enabledLayouts by remember(configuration) { mutableStateOf(configuration.enabledLayouts) }; var layoutDesigns by remember(configuration) { mutableStateOf(configuration.layoutDesigns) }
    var thermalPrintBrightness by remember(configuration) { mutableStateOf(configuration.thermalPrintBrightness.toFloat()) }
    var thermalDitherThreshold by remember(configuration) { mutableStateOf(configuration.thermalDitherThreshold.toFloat()) }
    var receiptBottomFeedLines by remember(configuration) { mutableStateOf(configuration.receiptBottomFeedLines.toFloat()) }
    var crtIntensity by remember(configuration) { mutableStateOf(configuration.crtIntensity.toFloat()) }
    var reducedMotion by remember(configuration) { mutableStateOf(configuration.reducedMotion) }
    var reducedGlitch by remember(configuration) { mutableStateOf(configuration.reducedGlitch) }
    var operationMode by remember(configuration) { mutableStateOf(configuration.operationMode) }
    var businessPin by remember(configuration) { mutableStateOf(configuration.businessPin) }
    var accessCodeQuantity by remember { mutableStateOf("10") }
    var accessCodePackage by remember { mutableStateOf(configuration.enabledLayouts.firstOrNull() ?: LayoutId.THREE_STRIP) }
    var accessCodeMessage by remember { mutableStateOf<String?>(null) }
    var accessCodeBusy by remember { mutableStateOf(false) }
    var lastAccessBatch by remember { mutableStateOf<GeneratedAccessBatch?>(null) }
    var accessCodeTotals by remember { mutableStateOf<String?>(null) }
    var printPriceTexts by remember(configuration) { mutableStateOf(priceTextValues(configuration.printPriceTiersMinor, 10, configuration.priceMinor)) }
    var selectedTab by remember { mutableStateOf(OperatorSettingsTab.GENERAL) }
    val pinGenerator = remember { SecureRandom() }
    val operatorFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = BoothPalette.mint,
        unfocusedLabelColor = BoothPalette.chromeText,
        focusedBorderColor = BoothPalette.focus,
        unfocusedBorderColor = BoothPalette.chromeMuted,
        cursorColor = BoothPalette.mint,
    )
    val metrics = boothLayoutMetrics()
    val accessCodeClient = remember(configuration.accessCodeSharedKey, configuration.deliveryBoothId) {
        AccessCodeClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, configuration.accessCodeSharedKey, configuration.deliveryBoothId.ifBlank { "booth-local" })
    }
    LaunchedEffect(configuration.accessCodeSharedKey, configuration.deliveryBoothId) {
        if (configuration.accessCodeSharedKey.isNotBlank()) {
            accessCodeClient.maintenance()
            accessCodeMessage = accessCodeClient.health().fold({ "Code service ready for $it" }, { it.message ?: "Code service unavailable" })
            accessCodeTotals = accessCodeClient.totals().fold(
                { totals -> "Remaining today: ${totals.issued} issued · ${totals.claimed} in progress · ${totals.completed} completed" },
                { "Remaining count unavailable" },
            )
        }
    }
    Box(Modifier.fillMaxSize().background(BoothPalette.chrome)) {
    Column(
        Modifier.fillMaxSize()
            .widthIn(max = 1_120.dp)
            .align(Alignment.TopCenter)
            .padding(metrics.pagePadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.gap),
    ) {
        Text("OPERATOR SETTINGS", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Local kiosk controls, arranged around how the booth is operated. A published backend configuration will replace these controls in Release 2.", color = Color(0xFFD6E6DF))
        OperatorSettingsTabs(selectedTab = selectedTab, onSelect = { selectedTab = it })
        if (selectedTab == OperatorSettingsTab.GENERAL) {
            Text("VENUE & STRIP CUSTOMIZATION", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("These details appear throughout the local booth experience and on the printed strip.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
            OutlinedTextField(venueName, { venueName = it.take(40) }, label = { Text("Venue name") }, colors = operatorFieldColors, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(header, { header = it.take(40) }, label = { Text("Strip header") }, colors = operatorFieldColors, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(logoText, { logoText = it.take(4) }, label = { Text("Owner logo mark (up to 4 characters)") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
            Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WelcomeBrandLogo(
                        logoPath = welcomeLogoPath,
                        fallbackMark = logoText,
                        modifier = Modifier.size(142.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Welcome screen business logo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show a large logo on the left side of Your Moment. PNG, JPG, and WebP are accepted; the logo is saved locally for offline kiosk use.",
                            color = Color(0xFFD6E6DF),
                            fontSize = 12.sp,
                        )
                        Button(
                            onClick = { welcomeLogoPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) { Text(if (welcomeLogoPath.isBlank()) "Upload business logo" else "Replace business logo") }
                        if (welcomeLogoPath.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    if (welcomeLogoPath != configuration.welcomeLogoPath) brandingAssetStore.remove(welcomeLogoPath)
                                    welcomeLogoPath = ""
                                    welcomeLogoMessage = "Logo will be removed when you save kiosk configuration."
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                            ) { Text("Remove business logo") }
                        }
                    }
                }
                welcomeLogoMessage?.let { message ->
                    Text(message, color = BoothPalette.mint, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp))
                }
            }
            OutlinedTextField(footer, { footer = it.take(60) }, label = { Text("Strip footer") }, colors = operatorFieldColors, modifier = Modifier.fillMaxWidth())
            Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Receipt design", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Open the Receipt Templates tab to create a complete 80 mm design. Text, logo, images, photo slots, blank space, barcode, layers, and print-safe positioning are edited there.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                }
            }
        }
        if (selectedTab == OperatorSettingsTab.BUSINESS) {
        PriceTierEditor("Print prices · 1–10 prints", "Set the total price for each quantity. These prices are shared by Business and Rental modes.", printPriceTexts, operatorFieldColors) { printPriceTexts = it }
        }
        if (selectedTab == OperatorSettingsTab.GENERAL) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Business payment PIN", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Staff enter this PIN after payment and before capture. Change it daily or whenever the event changes.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(businessPin, { businessPin = it.filter(Char::isDigit).take(12) }, label = { Text("Operator PIN") }, colors = operatorFieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.weight(1f))
                    ElevatedButton(onClick = { businessPin = (1000 + pinGenerator.nextInt(9000)).toString() }, modifier = Modifier.height(54.dp)) { Text("Generate PIN") }
                }
            }
        }
        }
        if (selectedTab == OperatorSettingsTab.MODES) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Code-based access", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Generate daily five-digit vouchers. They expire at 11:59 PM Asia/Taipei and open the linked layout at the booth.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Text("Code service: ${if (configuration.accessCodeSharedKey.isNotBlank()) "Configured" else "Needs provisioning"}", color = if (configuration.accessCodeSharedKey.isNotBlank()) BoothPalette.mint else Color(0xFFFFB4AB), fontSize = 12.sp)
                Text("Booth ID: ${configuration.deliveryBoothId.ifBlank { "Not configured" }}", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Text("The service key is locked after provisioning and is never shown to operators.", color = Color(0xFF9AA8A2), fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(accessCodeQuantity, { accessCodeQuantity = it.filter(Char::isDigit).take(4) }, label = { Text("Quantity") }, colors = operatorFieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    ElevatedButton(onClick = { accessCodePackage = configuration.enabledLayouts.firstOrNull() ?: LayoutId.THREE_STRIP }, modifier = Modifier.weight(2f).height(56.dp)) { Text("Package: ${accessCodePackage.displayName}") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(configuration.enabledLayouts.toList()) { layout ->
                        ElevatedButton(onClick = { accessCodePackage = layout }, colors = ButtonDefaults.elevatedButtonColors(containerColor = if (accessCodePackage == layout) BoothPalette.mint else Color(0xFF263035))) { Text(layout.displayName, fontSize = 11.sp) }
                    }
                }
                Button(
                    enabled = !accessCodeBusy && configuration.accessCodeSharedKey.isNotBlank() && accessCodeQuantity.toIntOrNull()?.let { it in 1..1000 } == true,
                    onClick = {
                        accessCodeBusy = true
                        accessCodeMessage = "Generating and printing codes…"
                        scope.launch {
                            accessCodeClient.createBatch(accessCodePackage.name, accessCodePackage.displayName, accessCodeQuantity.toInt()).fold(
                                onSuccess = { batch ->
                                    lastAccessBatch = batch
                                    val printer = withContext(Dispatchers.IO) { discovery.discover().firstOrNull { it.hasPermission } }
                                    accessCodeMessage = if (printer == null) "${batch.codes.size} codes generated, but no permitted printer is connected." else withContext(Dispatchers.IO) { discovery.printXp80tsAccessCodes(printer.deviceName, batch.packageLabel, batch.codes).getOrElse { it.message ?: "Voucher printing failed." } }
                                    accessCodeTotals = accessCodeClient.totals().getOrNull()?.let { totals -> "Remaining today: ${totals.issued} issued · ${totals.claimed} in progress · ${totals.completed} completed" }
                                },
                                onFailure = { accessCodeMessage = it.message ?: "Code generation failed." },
                            )
                            accessCodeBusy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (accessCodeBusy) "Working…" else "Generate and print codes") }
                accessCodeMessage?.let { Text(it, color = if (it.contains("failed", true) || it.contains("unavailable", true)) Color(0xFFFFB4AB) else Color(0xFFD6E6DF), fontSize = 12.sp) }
                accessCodeTotals?.let { Text(it, color = Color(0xFFD6E6DF), fontSize = 12.sp) }
                lastAccessBatch?.let { batch ->
                    Text("Last batch: ${batch.codes.size} ${batch.packageLabel} vouchers", color = Color.White, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElevatedButton(
                            enabled = !accessCodeBusy,
                            onClick = {
                                accessCodeBusy = true
                                scope.launch {
                                    val printer = withContext(Dispatchers.IO) { discovery.discover().firstOrNull { it.hasPermission } }
                                    accessCodeMessage = if (printer == null) "No permitted printer is connected." else withContext(Dispatchers.IO) { discovery.printXp80tsAccessCodes(printer.deviceName, batch.packageLabel, batch.codes).getOrElse { it.message ?: "Voucher reprint failed." } }
                                    accessCodeBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Reprint last batch", fontSize = 11.sp) }
                        ElevatedButton(
                            enabled = !accessCodeBusy && batch.codeIds.isNotEmpty(),
                            onClick = {
                                accessCodeBusy = true
                                scope.launch {
                                    val result = accessCodeClient.cancel(batch.codeIds.last())
                                    accessCodeMessage = result.fold({ "Cancelled the last code in the batch." }, { it.message ?: "Code cancellation failed." })
                                    accessCodeTotals = accessCodeClient.totals().getOrNull()?.let { totals -> "Remaining today: ${totals.issued} issued · ${totals.claimed} in progress · ${totals.completed} completed" }
                                    accessCodeBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel last code", fontSize = 11.sp) }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Session mode", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Rental skips the payment gate. Business uses the existing payment and PIN flow. Code access validates a printed daily voucher and opens the linked layout.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoothOperationMode.entries.forEach { mode ->
                        ElevatedButton(
                            onClick = { operationMode = mode },
                            modifier = Modifier.weight(1f).height(54.dp),
                            colors = ButtonDefaults.elevatedButtonColors(containerColor = if (operationMode == mode) BoothPalette.mint else Color(0xFF263035)),
                        ) { Text(if (operationMode == mode) "✓ ${mode.label}" else mode.label, fontSize = 12.sp) }
                    }
                }
            }
        }
        }
        if (selectedTab == OperatorSettingsTab.BUSINESS) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Payment QR codes", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Upload the operator QR images shown to guests. These are stored locally on this kiosk and displayed as a 2 × 2 grid.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { index ->
                        ElevatedButton(onClick = { qrSlot = index; qrPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.elevatedButtonColors(containerColor = if (qrUris.getOrNull(index).isNullOrBlank()) Color(0xFF263035) else BoothPalette.mint)) {
                            Text(if (qrUris.getOrNull(index).isNullOrBlank()) "QR ${index + 1}\nUPLOAD" else "QR ${index + 1}\nREADY", fontSize = 10.sp)
                        }
                    }
                }
                Text("Add a title so guests know which payment QR to scan.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                (0..3).forEach { index ->
                    val title = qrTitles.getOrNull(index).orEmpty()
                    OutlinedTextField(
                        value = title,
                        onValueChange = { value ->
                            val updated = qrTitles.toMutableList()
                            while (updated.size <= index) updated.add("")
                            updated[index] = value.take(32)
                            qrTitles = updated
                        },
                        label = { Text("QR ${index + 1} title") },
                        placeholder = { Text("e.g. GCash, Maya, Card") },
                        colors = operatorFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
        if (selectedTab == OperatorSettingsTab.GENERAL) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cloud digital delivery", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Cloudflare Worker/R2 delivery is managed in the owner-only provisioning screen. Operators only see safe connection status.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Text("Worker URL: ${if (configuration.deliveryWorkerUrl.isNotBlank()) "Configured" else "Not configured"}", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Text("Booth ID: ${configuration.deliveryBoothId.ifBlank { "Not configured" }}", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Text("Upload key: ${if (configuration.deliveryDeviceKey.isNotBlank()) "Configured" else "Not configured"}", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                ElevatedButton(onClick = onOpenProvisioning) { Text("Open booth provisioning") }
            }
        }
        }
        if (selectedTab == OperatorSettingsTab.LAYOUTS) {
        Text("Enabled layouts", color = Color.White, style = MaterialTheme.typography.titleMedium)
        LayoutId.entries.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { layout ->
            val enabled = layout in enabledLayouts
            val design = layoutDesigns[layout] ?: configuration.layoutDesign(layout)
            ElevatedButton(onClick = { enabledLayouts = if (enabled && enabledLayouts.size > 1) enabledLayouts - layout else enabledLayouts + layout }, modifier = Modifier.weight(1f), colors = ButtonDefaults.elevatedButtonColors(containerColor = if (enabled) Color(0xFF365248) else Color(0xFF263035))) { Text(if (enabled) "✓ ${design.displayName}" else design.displayName, fontSize = 12.sp) }
        } } }
        LayoutDesignEditor(
            designs = layoutDesigns,
            onChange = { layout, design -> layoutDesigns = layoutDesigns + (layout to design) },
        )
        }
        if (selectedTab == OperatorSettingsTab.RECEIPT_TEMPLATES) {
            ReceiptTemplateCatalogPanel(
                store = borderTemplateStore,
                templates = borderTemplates,
                onTemplatesChanged = onBorderTemplatesChanged,
            )
        }
        if (selectedTab == OperatorSettingsTab.HARDWARE) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp)) {
                Text("Thermal print brightness", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("${thermalPrintBrightness.roundToInt()} · lower is darker, higher is lighter", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp)
                Slider(
                    value = thermalPrintBrightness,
                    onValueChange = { thermalPrintBrightness = it.coerceIn(0f, 100f) },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Darker", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                    Text("Lighter", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                }
                Text("Applies to thermal image printing for proof labels and receipt strips. Tune this per printer and paper roll.", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
                Text("Dither threshold", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text("${thermalDitherThreshold.roundToInt()} · lower prints more black dots, higher prints fewer black dots", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp)
                Slider(
                    value = thermalDitherThreshold,
                    onValueChange = { thermalDitherThreshold = it.coerceIn(80f, 180f) },
                    valueRange = 80f..180f,
                    steps = 9,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("Bottom feed before cut", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text("${receiptBottomFeedLines.roundToInt()} lines · increase if the cutter leaves the last content too close to the edge", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp)
                Slider(
                    value = receiptBottomFeedLines,
                    onValueChange = { receiptBottomFeedLines = it.coerceIn(2f, 10f) },
                    valueRange = 2f..10f,
                    steps = 7,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        }
        if (selectedTab == OperatorSettingsTab.GENERAL) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp)) {
                Text("CRT & accessibility", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Landing effect intensity: ${crtIntensity.roundToInt()}%", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
                Slider(
                    value = crtIntensity,
                    onValueChange = { crtIntensity = it.coerceIn(0f, 100f) },
                    valueRange = 0f..100f,
                    steps = 9,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElevatedButton(
                        onClick = { reducedMotion = !reducedMotion },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = if (reducedMotion) BoothPalette.mint else Color(0xFF263035)),
                    ) { Text(if (reducedMotion) "Reduced motion: ON" else "Reduced motion: OFF", fontSize = 11.sp) }
                    ElevatedButton(
                        onClick = { reducedGlitch = !reducedGlitch },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = if (reducedGlitch) BoothPalette.mint else Color(0xFF263035)),
                    ) { Text(if (reducedGlitch) "Reduced glitch: ON" else "Reduced glitch: OFF", fontSize = 11.sp) }
                }
                Text("Reduced motion disables CRT flicker. Reduced glitch minimizes grain and RGB channel separation while preserving the cream-and-green theme.", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
            }
        }
        }
        if (selectedTab == OperatorSettingsTab.HARDWARE) PrinterReadinessCard(
            devices = usbDevices,
            queuedPrints = visiblePrintJobs.count { it.status == PrintStatus.QUEUED || it.status == PrintStatus.FAILED },
            permissionMessage = printerPermissionMessage,
            onRefresh = {
                printerPermissionMessage = null
                usbDevices = discovery.discover()
            },
            onRequestPermission = { device ->
                printerPermissionMessage = "Waiting for Android USB permission..."
                discovery.requestPermission(device.deviceName) { granted ->
                    printerPermissionMessage = if (granted) "USB permission granted. This confirms app-level access for discovery testing." else "USB permission was not granted."
                    usbDevices = discovery.discover()
                }
            },
            onDiagnosticPrint = { device ->
                printerPermissionMessage = "Sending XP-D4601B diagnostic label..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        discovery.printXpD4601bDiagnosticLabel(device.deviceName)
                    }
                    printerPermissionMessage = result.getOrElse { error -> error.message ?: "Diagnostic print failed." }
                    usbDevices = discovery.discover()
                }
            },
            onXp80tsReceiptTest = { device ->
                printerPermissionMessage = "Sending XP-80TS receipt test..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        discovery.printXp80tsDiagnosticReceipt(device.deviceName)
                    }
                    printerPermissionMessage = result.getOrElse { error -> error.message ?: "XP-80TS receipt test failed." }
                    usbDevices = discovery.discover()
                }
            },
            onXp80tsStripPrint = { device ->
                val stripFile = latestSession?.let { File(it.printStripPath) }
                if (stripFile == null) {
                    printerPermissionMessage = "Complete one photo session first, then print the latest strip."
                } else {
                    printerPermissionMessage = "Sending ${latestSession.printQuantity} copy${if (latestSession.printQuantity == 1) "" else "ies"} to XP-80TS..."
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                repeat(latestSession.printQuantity) {
                                    discovery.printXp80tsStrip(
                                        deviceName = device.deviceName,
                                        stripFile = stripFile,
                                        brightness = thermalPrintBrightness.roundToInt(),
                                        ditherThreshold = thermalDitherThreshold.roundToInt(),
                                        bottomFeedLines = receiptBottomFeedLines.roundToInt(),
                                    ).getOrThrow()
                                }
                            }
                        }
                        printerPermissionMessage = result.exceptionOrNull()?.message ?: "Print job sent."
                        usbDevices = discovery.discover()
                    }
                }
            },
            onStripProofPrint = { device ->
                val stripFile = latestSession?.let { File(it.printStripPath) }
                if (stripFile == null) {
                    printerPermissionMessage = "Complete one photo session first, then print a strip proof."
                } else {
                    printerPermissionMessage = "Sending latest strip proof to XP-D4601B..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            discovery.printXpD4601bStripProof(
                                deviceName = device.deviceName,
                                stripFile = stripFile,
                                brightness = thermalPrintBrightness.roundToInt(),
                                ditherThreshold = thermalDitherThreshold.roundToInt(),
                            )
                        }
                        printerPermissionMessage = result.getOrElse { error -> error.message ?: "Strip proof print failed." }
                        usbDevices = discovery.discover()
                    }
                }
            },
            hasLatestStrip = latestSession != null,
        )
        if (selectedTab == OperatorSettingsTab.PRINT_QUEUE) PrintQueueCard(
            jobs = visiblePrintJobs,
            onRetry = { job ->
                val permittedPrinters = discovery.discover().filter { it.hasPermission }
                if (permittedPrinters.size != 1) {
                    printerPermissionMessage = if (permittedPrinters.isEmpty()) "No USB printer with permission. Check power, paper, cable, and USB access." else "More than one USB printer has permission. Disconnect the extra printer before retrying."
                    usbDevices = discovery.discover()
                } else {
                    printerPermissionMessage = "Retrying print job..."
                    scope.launch {
                        val printingJob = printQueue.markPrinting(job)
                        visiblePrintJobs = sessionStore.loadPrintJobs().asReversed()
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                repeat(job.quantity) {
                                    discovery.printXp80tsStrip(
                                        deviceName = permittedPrinters.first().deviceName,
                                        stripFile = File(job.stripPath),
                                        brightness = thermalPrintBrightness.roundToInt(),
                                        ditherThreshold = thermalDitherThreshold.roundToInt(),
                                        bottomFeedLines = receiptBottomFeedLines.roundToInt(),
                                    ).getOrThrow()
                                }
                            }
                        }
                        result.onSuccess {
                            printQueue.markPrinted(printingJob)
                            printerPermissionMessage = "Print job sent."
                        }.onFailure { error ->
                            printQueue.markFailed(printingJob, error.message ?: "Retry failed.")
                            printerPermissionMessage = error.message ?: "Retry failed."
                        }
                        visiblePrintJobs = sessionStore.loadPrintJobs().asReversed()
                        usbDevices = discovery.discover()
                    }
                }
            },
        )
        if (selectedTab == OperatorSettingsTab.PRINT_QUEUE) Card(colors = CardDefaults.cardColors(Color(0xFF172225))) { Column(Modifier.padding(18.dp)) { Text("Local sessions: $sessionCount", color = Color.White); Text("Prints needing attention: ${visiblePrintJobs.count { it.status == PrintStatus.QUEUED || it.status == PrintStatus.FAILED }}", color = Color.White, modifier = Modifier.padding(top = 6.dp)); Text("Each completed session creates one durable print job with its requested copy count. If a print fails, check paper, cover, power, cable, USB permission, then retry from the queue.", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp)) } }
        Button(onClick = {
            fun parsePrices(values: List<String>, fallbackBaseMinor: Int): List<Int> = values.mapIndexed { index, value ->
                ((value.toDoubleOrNull() ?: fallbackBaseMinor * (index + 1) / 100.0) * 100).toInt().coerceAtLeast(0)
            }
            val printPrices = parsePrices(printPriceTexts, configuration.priceMinor)
            onSaveConfiguration(configuration.copy(venueName = venueName.ifBlank { "Your venue" }, header = header.ifBlank { "PHOTO MOMENT" }, logoText = logoText.ifBlank { "PB" }, welcomeLogoPath = welcomeLogoPath, footer = footer.ifBlank { "Thanks for visiting" }, priceMinor = printPrices.firstOrNull() ?: configuration.priceMinor, operationMode = operationMode, businessPin = businessPin.filter(Char::isDigit).take(12), printPriceTiersMinor = printPrices, enabledLayouts = enabledLayouts, layoutDesigns = layoutDesigns, paymentQrUris = qrUris, paymentQrTitles = qrTitles, crtIntensity = crtIntensity.roundToInt().coerceIn(0, 100), reducedMotion = reducedMotion, reducedGlitch = reducedGlitch, thermalPrintBrightness = thermalPrintBrightness.roundToInt(), thermalDitherThreshold = thermalDitherThreshold.roundToInt().coerceIn(80, 180), receiptBottomFeedLines = receiptBottomFeedLines.roundToInt().coerceIn(2, 10)))
            if (configuration.welcomeLogoPath != welcomeLogoPath) {
                brandingAssetStore.remove(configuration.welcomeLogoPath)
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save kiosk configuration") }
        ElevatedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Return to booth") }
    }
    }
}

@Composable
private fun ProvisioningSettingsScreen(
    configuration: BoothConfiguration,
    configurationStore: LocalBoothConfigurationStore,
    onSave: (BoothConfiguration) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var workerUrl by remember(configuration) { mutableStateOf(configuration.deliveryWorkerUrl) }
    var boothId by remember(configuration) { mutableStateOf(configuration.deliveryBoothId) }
    // Stored secrets are deliberately never loaded into editable UI state. A blank field
    // means "keep the currently provisioned secret"; entering a value rotates it.
    var uploadKey by remember(configuration) { mutableStateOf("") }
    var accessKey by remember(configuration) { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val operatorFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = BoothPalette.mint,
        unfocusedLabelColor = BoothPalette.chromeText,
        focusedBorderColor = BoothPalette.focus,
        unfocusedBorderColor = BoothPalette.chromeMuted,
        cursorColor = BoothPalette.mint,
    )
    val validWorkerUrl = workerUrl.trim().startsWith("https://")
    val validBoothId = boothId.matches(Regex("[A-Za-z0-9_-]{1,64}"))
    val hasUploadKey = configuration.deliveryDeviceKey.isNotBlank()
    val hasAccessKey = configuration.accessCodeSharedKey.isNotBlank()
    val validUploadKey = (uploadKey.isBlank() && hasUploadKey) || (uploadKey.length >= 16 && !uploadKey.any(Char::isWhitespace))
    val validAccessKey = (accessKey.isBlank() && hasAccessKey) || (accessKey.length >= 16 && !accessKey.any(Char::isWhitespace))
    val canSave = validWorkerUrl && validBoothId && validUploadKey && validAccessKey
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(configurationStore.exportSafe(configuration).toByteArray())
                    } ?: error("The backup file could not be opened.")
                }
            }
            result.onSuccess { message = "Safe configuration backup exported. Credentials were excluded." }
                .onFailure { message = it.message ?: "Backup export failed." }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("The backup file could not be opened.")
                    configurationStore.importSafe(json, configuration)
                }
            }
            result.onSuccess { imported ->
                onSave(imported)
                workerUrl = imported.deliveryWorkerUrl
                boothId = imported.deliveryBoothId
                uploadKey = ""
                accessKey = ""
                message = "Safe backup restored. Existing credentials were preserved; enter new values only to rotate them."
            }.onFailure { message = it.message ?: "Backup import failed." }
        }
    }
    Box(Modifier.fillMaxSize().background(BoothPalette.chrome)) {
    Column(
        Modifier.fillMaxSize()
            .widthIn(max = 760.dp)
            .align(Alignment.TopCenter)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("BOOTH PROVISIONING", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Owner-only setup for Cloudflare/R2 delivery and RetroRoll code access. These credentials are never shown in normal Operator Settings.", color = Color(0xFFD6E6DF))
        OutlinedTextField(workerUrl, { workerUrl = it.trim() }, label = { Text("Cloudflare Worker URL") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(boothId, { boothId = it.filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' }.take(64) }, label = { Text("Booth ID") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(uploadKey, { uploadKey = it.trim() }, label = { Text("Booth upload key — blank keeps current") }, colors = operatorFieldColors, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(accessKey, { accessKey = it.trim() }, label = { Text("RetroRoll code key — blank keeps current") }, colors = operatorFieldColors, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        if (!validWorkerUrl || !validBoothId || !validUploadKey || !validAccessKey) {
            Text("Enter an HTTPS Worker URL, a valid booth ID, and non-empty credentials before saving.", color = Color(0xFFFFB4AB), fontSize = 12.sp)
        }
        Button(
            enabled = canSave,
            onClick = {
                onSave(configuration.copy(deliveryWorkerUrl = workerUrl.trim(), deliveryBoothId = boothId, deliveryDeviceKey = uploadKey.trim().ifBlank { configuration.deliveryDeviceKey }, accessCodeSharedKey = accessKey.trim().ifBlank { configuration.accessCodeSharedKey }))
                uploadKey = ""
                accessKey = ""
                message = "Provisioning saved. Secrets remain hidden; blank fields keep the current values."
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
        ) { Text("Save provisioning") }
        ElevatedButton(
            onClick = {
                onSave(configuration.copy(deliveryWorkerUrl = "", deliveryBoothId = "", deliveryDeviceKey = "", accessCodeSharedKey = ""))
                workerUrl = ""
                boothId = ""
                uploadKey = ""
                accessKey = ""
                message = "Provisioning reset. All booth credentials were removed from this tablet."
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Reset provisioning") }
        Card(colors = CardDefaults.cardColors(BoothPalette.chromeRaised)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Safe configuration backup", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Backups include branding, pricing, layouts, and hardware preferences. Secrets are excluded and must be provisioned again.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElevatedButton(onClick = { exportLauncher.launch("photobooth-config-backup.json") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Export backup") }
                    ElevatedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Import backup") }
                }
            }
        }
        message?.let { Text(it, color = if (it.contains("failed", true) || it.contains("removed", true)) Color(0xFFFFB4AB) else Color(0xFFD6E6DF), fontSize = 12.sp) }
        ElevatedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to Operator Settings") }
    }
    }
}

@Composable
private fun PrintQueueCard(jobs: List<PrintJob>, onRetry: (PrintJob) -> Unit) {
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PRINT QUEUE", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Check paper, close the printer cover, confirm USB permission, then retry failed or queued jobs.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
            if (jobs.isEmpty()) {
                Text("No print jobs yet.", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 6.dp))
            }
            jobs.forEach { job ->
                Card(colors = CardDefaults.cardColors(Color(0xFF263035)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(job.status.label, color = job.status.statusColor(), style = MaterialTheme.typography.titleSmall)
                            Text("Attempts ${job.attempts}", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                        }
                        Text("Session ${job.sessionId.take(8)}  •  ${job.quantity} print${if (job.quantity == 1) "" else "s"}", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        job.lastError?.let { Text("Last error: $it", color = Color(0xFFFFD8A8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                        if (job.status == PrintStatus.FAILED || job.status == PrintStatus.QUEUED) {
                            ElevatedButton(onClick = { onRetry(job) }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry print") }
                        }
                    }
                }
            }
        }
    }
}

private val PrintStatus.label: String
    get() = when (this) {
        PrintStatus.NOT_QUEUED -> "Not queued"
        PrintStatus.QUEUED -> "Queued"
        PrintStatus.PRINTING -> "Printing"
        PrintStatus.PRINTED -> "Printed"
        PrintStatus.FAILED -> "Failed"
    }

private fun PrintStatus.statusColor(): Color = when (this) {
    PrintStatus.PRINTED -> Color(0xFFB4F5D1)
    PrintStatus.FAILED -> Color(0xFFFFB4AB)
    PrintStatus.PRINTING -> Color(0xFFFFD8A8)
    PrintStatus.QUEUED -> Color(0xFFD6E6DF)
    PrintStatus.NOT_QUEUED -> Color(0xFF9AA8A2)
}

@Composable
private fun PrinterReadinessCard(
    devices: List<DiscoveredUsbDevice>,
    queuedPrints: Int,
    permissionMessage: String?,
    onRefresh: () -> Unit,
    onRequestPermission: (DiscoveredUsbDevice) -> Unit,
    onDiagnosticPrint: (DiscoveredUsbDevice) -> Unit,
    onXp80tsReceiptTest: (DiscoveredUsbDevice) -> Unit,
    onXp80tsStripPrint: (DiscoveredUsbDevice) -> Unit,
    onStripProofPrint: (DiscoveredUsbDevice) -> Unit,
    hasLatestStrip: Boolean,
) {
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) { Column(Modifier.padding(18.dp)) {
        Text("PRINTER READINESS", color = Color.White, style = MaterialTheme.typography.titleMedium)
        val status = when {
            devices.isEmpty() -> "No USB device detected. Connect a powered OTG hub and printer."
            devices.any { it.hasPermission } -> "${devices.size} USB device(s) detected. Permission is granted for at least one device."
            else -> "${devices.size} USB device(s) detected. Android permission is needed before the app can open the printer."
        }
        Text(status, color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp))
        devices.forEach { device ->
            Card(colors = CardDefaults.cardColors(Color(0xFF263035)), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(device.deviceName, color = Color.White, fontSize = 13.sp)
                    Text("Vendor ${device.vendorId} · Product ${device.productId}", color = Color(0xFFD6E6DF), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    Text(if (device.hasPermission) "Permission granted" else "Permission needed", color = if (device.hasPermission) Color(0xFFB4F5D1) else Color(0xFFFFD8A8), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    if (!device.hasPermission) {
                        ElevatedButton(onClick = { onRequestPermission(device) }, modifier = Modifier.padding(top = 8.dp)) { Text("Request USB access") }
                    } else {
                        ElevatedButton(onClick = { onXp80tsReceiptTest(device) }, modifier = Modifier.padding(top = 8.dp)) { Text("Print XP-80TS receipt test") }
                        ElevatedButton(onClick = { onXp80tsStripPrint(device) }, enabled = hasLatestStrip, modifier = Modifier.padding(top = 8.dp)) { Text("Print latest strip on XP-80TS") }
                        Text("Use this for the 80 mm receipt printer. It sends a small ESC/POS test receipt and may trigger the cutter.", color = Color(0xFFD6E6DF), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        ElevatedButton(onClick = { onDiagnosticPrint(device) }, modifier = Modifier.padding(top = 8.dp)) { Text("Print XP-D4601B test label") }
                        ElevatedButton(onClick = { onStripProofPrint(device) }, enabled = hasLatestStrip, modifier = Modifier.padding(top = 8.dp)) { Text("Print latest strip proof") }
                        Text("Proof only: this scales the latest strip for the XP-D4601B label printer. Final 80 mm receipt output still waits for XP-80T validation.", color = Color(0xFFD6E6DF), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        permissionMessage?.let { Text(it, color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp) }
        Text("Queue: $queuedPrints job(s) need the printer path.", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp))
        ElevatedButton(onClick = onRefresh, modifier = Modifier.padding(top = 10.dp)) { Text("Refresh USB discovery") }
    } }
}

@Composable
private fun FailureScreen(message: String, onTryAgain: () -> Unit) = BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .background(BoothPalette.paper)
        .padding(horizontal = 40.dp, vertical = 32.dp),
) {
    val wideLayout = maxWidth >= 700.dp

    @Composable
    fun RecoveryCopy(modifier: Modifier = Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
            Text(
                text = "SESSION INTERRUPTED",
                color = BoothPalette.oxblood,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "LET'S TRY\nTHAT AGAIN",
                color = BoothPalette.ink,
                style = MaterialTheme.typography.displayMedium,
                lineHeight = 52.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Your session could not be completed. No settings were changed.",
                color = BoothPalette.inkMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }

    @Composable
    fun RecoveryAction(modifier: Modifier = Modifier) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(BoothPalette.paperPanel),
            border = androidx.compose.foundation.BorderStroke(1.dp, BoothPalette.ink.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text(
                    text = "WHAT HAPPENED",
                    color = BoothPalette.ink,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.5.sp,
                )
                Text(message, color = BoothPalette.ink, style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = onTryAgain,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BoothPalette.oxblood,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("RETURN TO START", letterSpacing = 1.sp)
                }
            }
        }
    }

    if (wideLayout) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecoveryCopy(Modifier.weight(1f))
            RecoveryAction(Modifier.weight(0.8f).widthIn(max = 520.dp))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            RecoveryCopy(Modifier.fillMaxWidth())
            RecoveryAction(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CameraPreview(controller: CameraCaptureController, modifier: Modifier = Modifier, vhsEnabled: Boolean = true) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope(); val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView keeps the Compose title, controls, and CRT layer above the camera feed.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    LaunchedEffect(vhsEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (vhsEnabled) applyLandingVhsGrade(previewView) else previewView.setRenderEffect(null)
        }
    }
    DisposableEffect(lifecycleOwner) { scope.launch { runCatching { controller.bindPreview(previewView, lifecycleOwner) } }; onDispose { controller.release() } }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private const val COUNTDOWN_SECONDS = 5

@Composable
internal fun isLandscape(): Boolean = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

private data class BoothLayoutMetrics(
    val pagePadding: Dp,
    val gap: Dp,
    val scale: Float,
)

@Composable
private fun boothLayoutMetrics(): BoothLayoutMetrics {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scale = if (landscape) {
        1f
    } else {
        (configuration.screenHeightDp / 1280f).coerceIn(0.72f, 1f)
    }
    return BoothLayoutMetrics(
        pagePadding = if (landscape) 10.dp else if (scale < 0.86f) 12.dp else 18.dp,
        gap = if (scale < 0.86f) 8.dp else 12.dp,
        scale = scale,
    )
}

@Preview(name = "Landing · tablet portrait", showSystemUi = true, device = "spec:width=800dp,height=1280dp,dpi=240")
@Preview(name = "Landing · tablet landscape", showSystemUi = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun LandingScreenPreview() {
    MaterialTheme(colorScheme = BoothLightColors, typography = BoothTypography) {
        WelcomeScreen(
            configuration = BoothConfiguration(
                venueName = "BERLIN-MITTE",
                header = "FOTOAUTOMAT",
                priceMinor = 500,
            ),
            hasCameraPermission = false,
            cameraController = CameraCaptureController(LocalContext.current),
            onStart = {},
            onOperator = {},
        )
    }
}
