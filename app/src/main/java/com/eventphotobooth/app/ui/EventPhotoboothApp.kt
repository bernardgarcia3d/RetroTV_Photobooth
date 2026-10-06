package com.eventphotobooth.app.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.eventphotobooth.app.capture.CameraCaptureController
import com.eventphotobooth.app.delivery.CloudDeliveryClient
import com.eventphotobooth.app.delivery.DeliveryLinks
import com.eventphotobooth.app.data.BoothConfiguration
import com.eventphotobooth.app.data.BoothOperationMode
import com.eventphotobooth.app.digital.LocalGifRenderer
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.LayoutDesign
import com.eventphotobooth.app.data.LocalBoothConfigurationStore
import com.eventphotobooth.app.data.LocalPhotoSession
import com.eventphotobooth.app.data.LocalSessionStore
import com.eventphotobooth.app.data.OperatorPinStore
import com.eventphotobooth.app.data.PrintJob
import com.eventphotobooth.app.data.PrintStatus
import com.eventphotobooth.app.data.StickerId
import com.eventphotobooth.app.data.StickerPlacement
import com.eventphotobooth.app.printer.DiscoveredUsbDevice
import com.eventphotobooth.app.printer.LocalPrintQueue
import com.eventphotobooth.app.printer.UsbPrinterDiscovery
import com.eventphotobooth.app.render.StripRenderer
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
    data object LayoutSelection : BoothScreen
    data class Countdown(val seconds: Int, val captureNumber: Int, val photoCount: Int) : BoothScreen
    data class Review(val photos: List<File>) : BoothScreen
    data class Stickers(val photos: List<File>) : BoothScreen
    data object Processing : BoothScreen
    data class PrintingAnimation(val session: LocalPhotoSession, val printStatus: PrintStatus, val printMessage: String?, val deliveryLinks: DeliveryLinks? = null) : BoothScreen
    data class Complete(val session: LocalPhotoSession, val printStatus: PrintStatus, val printMessage: String?, val deliveryLinks: DeliveryLinks? = null) : BoothScreen
    data class DigitalDelivery(val session: LocalPhotoSession, val deliveryLinks: DeliveryLinks? = null) : BoothScreen
    data object OperatorGate : BoothScreen
    data object OperatorSettings : BoothScreen
    data class Failure(val message: String) : BoothScreen
}

@Composable
fun EventPhotoboothApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStore = remember { LocalSessionStore(context.applicationContext) }
    val configurationStore = remember { LocalBoothConfigurationStore(context.applicationContext) }
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
    var selectedLayout by remember { mutableStateOf(LayoutId.THREE_STRIP) }
    var printQuantity by remember { mutableStateOf(1) }
    var stickers by remember { mutableStateOf<List<StickerPlacement>>(emptyList()) }
    var guestBrightness by remember { mutableStateOf(50f) }
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

    val beginCapture: () -> Unit = {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            scope.launch {
                runCatching {
                    val sessionDirectory = File(context.filesDir, "sessions/${UUID.randomUUID()}").apply { mkdirs() }
                    buildList {
                        repeat(selectedLayout.photoCount) { index ->
                            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                                screen = BoothScreen.Countdown(seconds, index + 1, selectedLayout.photoCount)
                                countdownTone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                delay(1_000)
                            }
                            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                            add(cameraController.capture(File(sessionDirectory, "source-${index + 1}.jpg")))
                        }
                    }
                }.onSuccess { photos -> screen = BoothScreen.Review(photos) }
                    .onFailure { error -> screen = BoothScreen.Failure(error.message ?: "The camera could not capture this session.") }
            }
        }
    }

    val createPrintSession: (List<File>) -> Unit = { sourcePhotos ->
        scope.launch {
            screen = BoothScreen.Processing
            runCatching {
                withContext(Dispatchers.Default) {
                    val sessionId = UUID.randomUUID().toString()
                    val outputDirectory = File(context.filesDir, "completed/$sessionId").apply { mkdirs() }
                    val printStrip = renderer.renderPrintStrip(
                        sourcePhotos = sourcePhotos,
                        brand = configuration.brandSnapshot(),
                        layoutId = selectedLayout,
                        stickers = stickers,
                        outputFile = File(outputDirectory, "print-strip.png"),
                        layoutDesign = configuration.layoutDesign(selectedLayout),
                        guestPhotoBrightness = guestBrightness.roundToInt().coerceIn(0, 100),
                    )
                    val animatedGif = gifRenderer.renderAnimatedGifStrip(
                        sourcePhotos = sourcePhotos,
                        finalStripFile = printStrip,
                        brand = configuration.brandSnapshot(),
                        outputFile = File(outputDirectory, "animated-gif-strip.gif"),
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
                    )
                }
            }.onSuccess { session ->
                val deliveryLinks = withContext(Dispatchers.IO) {
                    deliveryClient.upload(configuration, session.id, File(session.digitalStripPath!!), File(session.animatedGifPath!!)).getOrNull()
                }
                sessionStore.saveSession(session)
                val printJob = printQueue.enqueue(session.id, session.printStripPath, session.printQuantity)
                screen = BoothScreen.PrintingAnimation(session.copy(printStatus = PrintStatus.QUEUED), PrintStatus.QUEUED, if (deliveryLinks == null) "Printing your strip · cloud delivery unavailable" else "Preparing your strip", deliveryLinks)
                delay(1_800)
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
            screen = if (configuration.operationMode == BoothOperationMode.RENTAL) BoothScreen.LayoutSelection else BoothScreen.Payment
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
        BoothScreen.LayoutSelection -> LayoutSelectionScreen(configuration, { layout ->
            selectedLayout = layout
            stickers = emptyList()
            guestBrightness = 50f
            beginCapture()
        }, { screen = BoothScreen.Welcome })
        is BoothScreen.Countdown -> CameraCaptureScreen(cameraController, current.seconds, current.captureNumber, current.photoCount)
        is BoothScreen.Review -> ReviewScreen(
            photos = current.photos,
            layoutId = selectedLayout,
            configuration = configuration,
            onRetake = { beginCapture() },
            onContinue = { screen = BoothScreen.Stickers(current.photos) },
        )
        is BoothScreen.Stickers -> StickerScreen(
            photos = current.photos,
            layoutId = selectedLayout,
            configuration = configuration,
            stickers = stickers,
            printQuantity = printQuantity,
            allowPrintQuantityEditing = configuration.operationMode == BoothOperationMode.RENTAL,
            onPrintQuantityChange = { printQuantity = it },
            guestBrightness = guestBrightness,
            onBrightnessChange = { guestBrightness = it },
            onChange = { stickers = it },
            onBack = { screen = BoothScreen.Review(current.photos) },
            onConfirm = { createPrintSession(current.photos) },
        )
        BoothScreen.Processing -> ProcessingScreen()
        is BoothScreen.PrintingAnimation -> PrintingAnimationScreen(current.session, current.printStatus, current.printMessage)
        is BoothScreen.Complete -> CompleteScreen(current.session, current.printStatus, current.printMessage) { screen = BoothScreen.Welcome }
        is BoothScreen.DigitalDelivery -> DigitalDeliveryScreen(current.session, current.deliveryLinks) { screen = BoothScreen.Welcome }
        BoothScreen.OperatorGate -> OperatorGateScreen(pinStore, { screen = BoothScreen.OperatorSettings }, { screen = BoothScreen.Welcome })
        BoothScreen.OperatorSettings -> OperatorSettingsScreen(
            configuration, sessionStore.loadSessions().size,
            sessionStore.loadPrintJobs(),
            sessionStore.loadSessions().maxByOrNull { it.capturedAtEpochMs },
            sessionStore,
            { updated -> configuration = updated; configurationStore.save(updated) },
            { screen = BoothScreen.Welcome },
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
    Box(Modifier.fillMaxSize().background(if (hasCameraPermission) Color.Black else BoothPalette.paper)) {
        if (hasCameraPermission) {
            CameraPreview(cameraController, Modifier.fillMaxSize())
            CrtVhsOverlay(
                modifier = Modifier.fillMaxSize(),
                intensity = configuration.crtIntensity,
                reducedMotion = configuration.reducedMotion,
                reducedGlitch = configuration.reducedGlitch,
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
            .background(BoothPalette.paperLight, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFD2C9B8), RoundedCornerShape(4.dp))
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
        onBack?.let { ElevatedButton(onClick = it, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(32.dp), colors = ButtonDefaults.elevatedButtonColors(containerColor = BoothPalette.paperPanel)) { Text("BACK", color = BoothPalette.ink, fontSize = 9.sp) } }
    }
}

@Composable
private fun StitchBottomRail(text: String) {
    Text(text, color = BoothPalette.inkMuted, fontSize = 8.sp, letterSpacing = 0.5.sp, modifier = Modifier.fillMaxWidth().background(BoothPalette.paperLight, RoundedCornerShape(3.dp)).border(1.dp, Color(0xFFD2C9B8), RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun StitchNavigationBar(active: String) {
    Row(Modifier.fillMaxWidth().background(BoothPalette.paperLight, RoundedCornerShape(3.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("● FOTOAUTOMAT", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(BoothPalette.paperPanel, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 7.dp))
        Text("MOD. 94", color = BoothPalette.inkMuted, fontSize = 8.sp, modifier = Modifier.background(BoothPalette.paperPanel, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 7.dp))
        Text("SYS OK  •  FILM: LOADED", color = BoothPalette.forest, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
        listOf("CAPTURE STAGE", "STRIP PREVIEW", "PRINT LAB", "DIGITAL ARCHIVE").forEach { item ->
            Text(item, color = if (item == active) BoothPalette.paperLight else BoothPalette.inkMuted, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(if (item == active) BoothPalette.forest else BoothPalette.paperPanel, RoundedCornerShape(3.dp)).padding(horizontal = 9.dp, vertical = 9.dp))
        }
        Text("CRT: ON  •  NOISE: OFF", color = BoothPalette.forest, fontSize = 8.sp, modifier = Modifier.background(BoothPalette.paperPanel, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 8.dp))
        Text("◷ 14:38:09", color = BoothPalette.oxbloodDark, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(BoothPalette.paperLight, RoundedCornerShape(3.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 8.dp))
        Box(Modifier.size(25.dp).background(BoothPalette.forest, CircleShape), contentAlignment = Alignment.Center) { Text("●", color = BoothPalette.paperLight, fontSize = 9.sp) }
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
private fun StitchHardwareFooter() {
    Row(Modifier.fillMaxWidth().background(Color(0xFFEDE5D6), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFFCCC1AC), RoundedCornerShape(3.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("☎  ATTENDANT CALL", color = Color.White, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.background(BoothPalette.oxblood, RoundedCornerShape(2.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
        Text("▰  INSERT TOKEN / PM 2  •  CREDITS: 02", color = BoothPalette.inkMuted, fontSize = 8.sp)
        Text("◖  ───  ◌  ───  ▫", color = BoothPalette.inkMuted, fontSize = 9.sp)
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
    Column(Modifier.fillMaxSize().background(Color(0xFFF4EFE6)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(7f).fillMaxHeight(), colors = CardDefaults.cardColors(Color(0xFFF7F2E9)), elevation = CardDefaults.cardElevation(3.dp), shape = RoundedCornerShape(5.dp)) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("◉  REC  •  STANDBY", color = BoothPalette.oxblood, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp)
                    Text("▣  ANALOG PHOTO BOOTH EXPERIENCE", color = BoothPalette.forest, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 16.dp).background(BoothPalette.paperLight, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    Text(configuration.header.uppercase(), color = BoothPalette.ink, fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 5.dp))
                    Text("EST. 1994  •  ${configuration.venueName.uppercase()}", color = BoothPalette.forest, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp)
                    Card(Modifier.padding(top = 15.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(horizontal = 32.dp, vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("MAKE A MEMORY", color = BoothPalette.oxblood, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text(if (rentalMode) "Pose with friends. Keep the thermal print and digital copies." else "Pay once. Pose with friends. Keep the thermal print forever.", color = BoothPalette.inkMuted, fontSize = 11.sp) } }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TitleFeature("01", "STRIKE POSE", "Quick flash shots", Modifier.weight(1f))
                        TitleFeature("02", "TINT & STYLE", "Thermal preview", Modifier.weight(1f))
                        TitleFeature("03", "GRAB STRIP", "Print + digital copy", Modifier.weight(1f))
                    }
                    Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest, contentColor = Color.White), modifier = Modifier.padding(top = 16.dp).width(310.dp).height(72.dp)) { Icon(Icons.Filled.CameraAlt, null); Text("  START SESSION", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("● VIDEO SIGNAL: 525 LINES COMPOSITE", color = BoothPalette.forest, fontSize = 8.sp); Text("EXPOSURE: FIXED F/2.8  •  STROBE: SYNCHRONIZED", color = BoothPalette.inkMuted, fontSize = 8.sp) }
                }
            }
            Card(Modifier.weight(5f).fillMaxHeight(), colors = CardDefaults.cardColors(Color(0xFFEDE5D6)), elevation = CardDefaults.cardElevation(3.dp), shape = RoundedCornerShape(5.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).background(Color.Black)) {
                    CameraPreview(cameraController, Modifier.fillMaxSize(), vhsEnabled = true)
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .background(Color(0xCC111816), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(9.dp).background(BoothPalette.oxblood, CircleShape))
                        Text("REC", color = Color.White, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Text(
                        "LIVE CAMERA FEED",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .background(Color(0x99111816), RoundedCornerShape(3.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest, contentColor = BoothPalette.paperLight),
                modifier = Modifier.height(46.dp),
            ) { Text("←  BACK TO HOME", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp) }
            Box(Modifier.weight(1f)) { StitchHardwareFooter() }
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
    var printQuantity by remember { mutableStateOf(1) }
    val metrics = boothLayoutMetrics()
    if (isLandscape()) {
        LandscapePaymentScreen(configuration, printQuantity, { printQuantity = it }, { onProceed(printQuantity) }, onBack)
        return
    }
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
            Text(configuration.priceLabelForQuantity(printQuantity), color = BoothPalette.ink, style = MaterialTheme.typography.headlineSmall)
        }
        PrintQuantitySelector(
            quantity = printQuantity,
            configuration = configuration,
            onQuantityChange = { printQuantity = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text("STATIC QR  •  SELECTED TIER: $printQuantity PRINT${if (printQuantity == 1) "" else "S"}  •  ${configuration.priceLabelForQuantity(printQuantity)}", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
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
            PaymentActions({ onProceed(printQuantity) }, onBack)
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
            PaymentActions({ onProceed(printQuantity) }, onBack, Modifier.padding(top = 18.dp * metrics.scale))
        }
    }
}

@Composable
private fun LandscapePaymentScreen(configuration: BoothConfiguration, printQuantity: Int, onQuantityChange: (Int) -> Unit, onProceed: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4EFE6)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StitchSectionLabel("STEP 1 OF 3: ACTIVATION", "STATION ID: TX-BER-94", "● NETWORK: ONLINE 99.8%")
                Text("READY WHEN YOU ARE", color = BoothPalette.ink, fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Text("Scan the QR code with your mobile banking or payment app.\nAsk the attendant for payment confirmation.", color = BoothPalette.inkMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 7.dp))
                Text("READY  /  24V COIL OK", color = BoothPalette.forest, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(Color(0xFFFFFDF9)), elevation = CardDefaults.cardElevation(3.dp), shape = RoundedCornerShape(2.dp)) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Text("FOTOAUTOMAT MOD. 94\nTRANSACTION #94-882", color = BoothPalette.ink, fontSize = 9.sp, lineHeight = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("TERMINAL 04", color = BoothPalette.ink, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        Row(Modifier.fillMaxWidth().background(Color(0xFFF0ECE4), RoundedCornerShape(2.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("TOTAL PAYABLE", color = BoothPalette.ink, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(configuration.priceLabelForQuantity(printQuantity), color = Color.Black, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        PrintQuantitySelector(quantity = printQuantity, configuration = configuration, onQuantityChange = onQuantityChange, modifier = Modifier.fillMaxWidth())
                        Text("STATIC QR  •  $printQuantity PRINT${if (printQuantity == 1) "" else "S"}  •  ${configuration.priceLabelForQuantity(printQuantity)}", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
            Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(Color(0xFFFFFDF9)), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(2.dp)) {
                Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCAN CORRESPONDING QR TO PAY", color = BoothPalette.forest, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..1).forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (0..1).forEach { column ->
                                    val index = row * 2 + column
                                    Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(Color(0xFFF7F3EB)), shape = RoundedCornerShape(2.dp)) {
                                        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(paymentQrTitle(configuration, index), color = BoothPalette.ink, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            PaymentQrTile(landscapeQrUri(configuration, index), Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ElevatedButton(onClick = onBack, modifier = Modifier.weight(1f).height(50.dp)) {
                Text("CANCEL SESSION", color = BoothPalette.oxbloodDark, fontSize = 10.sp)
            }
            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text("PROCEED", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
    }
}

@Composable
private fun PaymentHeader(ownerMark: String, venueName: String, onBack: () -> Unit) {
    StitchTopBar(ownerMark, venueName, "STEP 1 / ACTIVATION", "PAYMENT READY", onBack)
}

@Composable
private fun PaymentActions(onTestPaymentConfirmed: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onTestPaymentConfirmed, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest), modifier = Modifier.height(54.dp).width(250.dp)) {
            Icon(Icons.Filled.CheckCircle, null)
            Text("  CONFIRM TEST PAYMENT", fontSize = 13.sp)
        }
        ElevatedButton(onClick = onBack, colors = ButtonDefaults.elevatedButtonColors(containerColor = BoothPalette.paperLight), modifier = Modifier.padding(start = 10.dp).height(54.dp)) {
            Text("CANCEL", color = BoothPalette.ink)
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
        StitchBottomRail("FOTOAUTOMAT MOD. 94  •  PAYMENT ACCEPTED  •  SELECT ONE PRINT FORMAT")
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
    Column(Modifier.fillMaxSize().background(Color(0xFFF4EFE6)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                StitchSectionLabel("STEP 2 OF 3: CHOOSE FORMAT", "SYS: MEMORY BANK LOADED", "PAPER STOCK  •  ILFORD PAN F 50 ISO")
                Text("PICK YOUR LOOK", color = BoothPalette.ink, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            }
            ElevatedButton(onClick = onBack, modifier = Modifier.height(42.dp)) { Text("BACK", fontSize = 9.sp) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LayoutId.FOUR_GRID, LayoutId.ONE_PHOTO, LayoutId.THREE_STRIP, LayoutId.SIX_GRID).filter { it in configuration.enabledLayouts }.forEachIndexed { index, layout ->
                val selected = index == 0
                val design = configuration.layoutDesign(layout)
                Card(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(layout) }.border(if (selected) 2.dp else 1.dp, if (selected) BoothPalette.forest else Color(0xFFD5CBB8), RoundedCornerShape(5.dp)), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(5.dp)) {
                    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("FORMAT 0${index + 1}  //  ${when (layout) { LayoutId.FOUR_GRID -> "ORIGIN"; LayoutId.ONE_PHOTO -> "STUDIO"; LayoutId.THREE_STRIP -> "EDITORIAL"; LayoutId.SIX_GRID -> "COLLAGE" }}", color = BoothPalette.inkMuted, fontSize = 7.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); if (selected) Text("◉ MOST POPULAR", color = BoothPalette.oxblood, fontSize = 7.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        Text(when (layout) { LayoutId.FOUR_GRID -> "CLASSIC STRIP"; LayoutId.ONE_PHOTO -> "SOLO PORTRAIT"; LayoutId.THREE_STRIP -> "TRIPLE COMBO"; LayoutId.SIX_GRID -> "RETRO 2X3 GRID" }, color = BoothPalette.ink, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF1D2521), RoundedCornerShape(2.dp)).padding(6.dp), contentAlignment = Alignment.Center) { LayoutDiagram(layout, Color(0xFFF6F0E6), contentScale = ContentScale.Fit, padding = 0.dp) }
                        Text("RATIO: ${when (layout) { LayoutId.ONE_PHOTO -> "3:4 GALLERY"; LayoutId.THREE_STRIP -> "16:8 HYBRID"; LayoutId.FOUR_GRID -> "2:2 QUAD"; LayoutId.SIX_GRID -> "2:3 MOSAIC" }}    SLOT 0${index + 1}", color = BoothPalette.inkMuted, fontSize = 7.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(BoothPalette.paperLight, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFD5CBB8), RoundedCornerShape(4.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SELECTED: CLASSIC 4-POSE STRIP  •  APPROX 3 MINS", color = BoothPalette.inkMuted, fontSize = 8.sp)
            Button(onClick = { LayoutId.FOUR_GRID.takeIf { it in configuration.enabledLayouts }?.let(onSelect) ?: configuration.enabledLayouts.firstOrNull()?.let(onSelect) }, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.oxblood), modifier = Modifier.height(46.dp)) { Text("CONFIRM & ENTER BOOTH →", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
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
private fun CameraCaptureScreen(cameraController: CameraCaptureController, seconds: Int, captureNumber: Int, photoCount: Int) {
    val landscape = isLandscape()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(cameraController, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopCenter).padding(top = if (landscape) 22.dp else 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CaptureProgressIndicator(captureNumber, photoCount)
            Text("PHOTO $captureNumber OF $photoCount", color = Color.White, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge)
            Text(seconds.toString(), color = Color.White, fontSize = if (landscape) 96.sp else 128.sp)
            Text(if (captureNumber == photoCount) "Last shot" else "Next: photo ${captureNumber + 1}", color = Color(0xFFD6E6DF))
        }
    }
}

@Composable
private fun CaptureProgressIndicator(captureNumber: Int, photoCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(photoCount) { index ->
        val number = index + 1
        val color = when { number < captureNumber -> Color(0xFFB4F5D1); number == captureNumber -> Color.White; else -> Color(0x66FFFFFF) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(18.dp).clip(CircleShape).background(color)); Text(if (number < captureNumber) "Taken" else if (number == captureNumber) "Now" else "Next", color = Color.White, fontSize = 10.sp) }
    } }
}

@Composable
private fun ReviewScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    onRetake: () -> Unit,
    onContinue: () -> Unit,
) {
    if (isLandscape()) {
        LandscapeReviewScreen(photos, layoutId, configuration, onRetake, onContinue)
        return
    }
    val ownerMark = configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase()
    val metrics = boothLayoutMetrics()
    Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(metrics.pagePadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
        StitchTopBar(ownerMark, configuration.venueName, "STEP 4 / REVIEW", "CAPTURE COMPLETE")
        Text("YOUR MOMENTS", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("This is your final printed layout.", color = BoothPalette.inkMuted)
        StripOutputPreview(photos, layoutId, configuration, emptyList())
        Text("Happy with your photo strip?", color = BoothPalette.inkMuted, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ElevatedButton(onClick = onRetake) { Text("Retake") }; Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(Color(0xFFB4F5D1), Color(0xFF101719))) { Text("Customize strip") } }
        StitchBottomRail("REVIEW OUTPUT  •  THERMAL PREVIEW  •  CHOOSE RETAKE OR CONTINUE")
    }
}

@Composable
private fun LandscapeReviewScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    onRetake: () -> Unit,
    onContinue: () -> Unit,
) {
    val reviewPreviewMaxWidth = if (layoutId == LayoutId.THREE_STRIP) 300.dp else 520.dp
    Row(Modifier.fillMaxSize().background(BoothPalette.paper).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(Modifier.weight(1.05f).fillMaxHeight(), colors = CardDefaults.cardColors(Color(0xFFE4DDD0)), elevation = CardDefaults.cardElevation(3.dp), shape = RoundedCornerShape(3.dp)) {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                StripOutputPreview(photos, layoutId, configuration, emptyList(), maxPreviewWidth = reviewPreviewMaxWidth, verticalPadding = 0.dp)
            }
        }
        Column(Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text("YOUR MOMENTS", color = BoothPalette.forest, fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("This is your final printed layout.", color = BoothPalette.inkMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Card(Modifier.fillMaxWidth().padding(top = 28.dp), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Happy with your photo strip?", color = BoothPalette.ink, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Choose retake if you want another capture, or continue to add stickers and brightness adjustments.", color = BoothPalette.inkMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ElevatedButton(onClick = onRetake, modifier = Modifier.weight(1f).height(54.dp)) {
                            Text("RETAKE", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(Color(0xFFB4F5D1), Color(0xFF101719)), modifier = Modifier.weight(1.35f).height(54.dp)) {
                            Text("CUSTOMIZE STRIP", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
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
        LandscapeStickerScreen(photos, layoutId, configuration, stickers, printQuantity, allowPrintQuantityEditing, onPrintQuantityChange, guestBrightness, onBrightnessChange, onChange, onBack, onConfirm)
        return
    }
    var selectedSticker by remember { mutableStateOf(StickerId.HEART) }
    var selectedStickerIndex by remember { mutableStateOf<Int?>(null) }

    fun addSticker(sticker: StickerId, x: Float = 0.5f, y: Float = 0.5f, scale: Float = 1f) {
        onChange(stickers + StickerPlacement(sticker, x, y, scale))
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
        StitchTopBar(configuration.logoText.trim().ifBlank { "PB" }.take(4).uppercase(), configuration.venueName, "STEP 5 / CUSTOMIZE", "EDITING STRIP")
        Text("MAKE IT YOURS", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("Tap a sticker to add it · Drag to move · Pinch to resize · Double-tap to copy", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 8.dp))
        StripOutputPreview(
            photos = photos,
            layoutId = layoutId,
            configuration = configuration,
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
                    placement.copy(
                        x = (placement.x + xChange).coerceIn(0.06f, 0.94f),
                        y = (placement.y + yChange).coerceIn(0.06f, 0.94f),
                        scale = (placement.scale * scaleChange).coerceIn(0.5f, 2.5f),
                    )
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
        StitchBottomRail("FINAL OUTPUT  •  STICKERS SAVED TO PRINT, GIF, AND DIGITAL STRIP")
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
    photos: List<File>, layoutId: LayoutId, configuration: BoothConfiguration, stickers: List<StickerPlacement>,
    printQuantity: Int, allowPrintQuantityEditing: Boolean, onPrintQuantityChange: (Int) -> Unit, guestBrightness: Float,
    onBrightnessChange: (Float) -> Unit, onChange: (List<StickerPlacement>) -> Unit, onBack: () -> Unit, onConfirm: () -> Unit,
) {
    var selectedSticker by remember { mutableStateOf(StickerId.HEART) }
    var selectedStickerIndex by remember { mutableStateOf<Int?>(null) }
    fun addSticker(sticker: StickerId) { onChange(stickers + StickerPlacement(sticker, 0.5f, 0.5f, 1f)); selectedSticker = sticker; selectedStickerIndex = stickers.size }
    Row(Modifier.fillMaxSize().background(Color(0xFFF4EFE6)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(5f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            StitchSectionLabel("STAGE 04/05", "PRINT COMPOSER & RETOUCH CONSOLE", "LIVE PREVIEW")
            Card(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), colors = CardDefaults.cardColors(Color(0xFFE4DDD0)), elevation = CardDefaults.cardElevation(3.dp)) {
                Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                    StripOutputPreview(photos, layoutId, configuration, stickers, guestBrightness.roundToInt(), true, selectedStickerIndex, { selectedStickerIndex = it }, { index, x, y -> stickers.getOrNull(index)?.let { addSticker(it.sticker) } }, { index, dx, dy, zoom -> onChange(stickers.mapIndexed { i, p -> if (i == index) p.copy(x = (p.x + dx).coerceIn(0.05f, 0.95f), y = (p.y + dy).coerceIn(0.05f, 0.95f), scale = (p.scale * zoom).coerceIn(0.5f, 2.5f)) else p }) })
                }
            }
        }
        Column(Modifier.weight(7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("MAKE IT YOURS", color = BoothPalette.ink, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Adjust brightness and decorate your thermal strip before printing.", color = BoothPalette.inkMuted, fontSize = 11.sp)
                }
            }
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("GESTURE GUIDE", color = BoothPalette.ink, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Drag to move  •  Pinch to resize  •  Double-tap to copy", color = BoothPalette.forest, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("HARDWARE EXPOSURE / INK DENSITY", color = BoothPalette.ink, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Slider(value = guestBrightness, onValueChange = onBrightnessChange, valueRange = 0f..100f, steps = 9)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("DARKER", color = BoothPalette.inkMuted, fontSize = 8.sp)
                        Text("BALANCED", color = BoothPalette.ink, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("BRIGHTER", color = BoothPalette.inkMuted, fontSize = 8.sp)
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(BoothPalette.paperLight), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RETRO STICKER TRAY", color = BoothPalette.ink, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Tap an icon to place", color = BoothPalette.inkMuted, fontSize = 8.sp)
                    }
                    LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StickerId.entries) { sticker ->
                            Card(Modifier.size(50.dp).clickable { addSticker(sticker) }.border(if (sticker == selectedSticker) 2.dp else 1.dp, if (sticker == selectedSticker) BoothPalette.forest else Color(0xFFD5C9B4), RoundedCornerShape(6.dp)), colors = CardDefaults.cardColors(if (sticker == selectedSticker) BoothPalette.mint else BoothPalette.paperPanel)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(sticker.glyph, fontSize = 23.sp) }
                            }
                        }
                    }
                }
            }
            if (allowPrintQuantityEditing) {
                PrintQuantitySelector(quantity = printQuantity, configuration = configuration, onQuantityChange = onPrintQuantityChange, modifier = Modifier.fillMaxWidth())
            } else {
                Text("PRINT QUANTITY: $printQuantity  •  TOTAL ${configuration.priceLabelForQuantity(printQuantity)}", color = BoothPalette.forest, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) { Text("RETAKE PHOTO 4", fontSize = 10.sp) }
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest), modifier = Modifier.weight(1.5f).height(52.dp)) { Text("PRINT THIS STRIP!", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun StripOutputPreview(
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
    maxPreviewWidth: Dp = 340.dp,
    verticalPadding: Dp = 16.dp,
) {
    val currentStickers by rememberUpdatedState(stickers)
    val currentSelectedStickerIndex by rememberUpdatedState(selectedStickerIndex)
    val currentOnSelectSticker by rememberUpdatedState(onSelectSticker)
    val currentOnDuplicateSticker by rememberUpdatedState(onDuplicateSticker)
    val currentOnTransformSticker by rememberUpdatedState(onTransformSticker)
    val metrics = boothLayoutMetrics()
    val previewHeight = when (layoutId) {
        LayoutId.ONE_PHOTO -> 430.dp
        LayoutId.THREE_STRIP -> 480.dp
        LayoutId.FOUR_GRID -> 380.dp
        LayoutId.SIX_GRID -> 470.dp
    }.let { it * metrics.scale }
    val screenConfiguration = LocalConfiguration.current
    val previewAvailableWidth = if (screenConfiguration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        (screenConfiguration.screenWidthDp * 0.42f - 24f).toInt()
    } else {
        screenConfiguration.screenWidthDp - 36
    }
    val previewWidth = minOf(maxPreviewWidth, previewAvailableWidth.coerceAtLeast(220).dp)
    val previewModifier = Modifier
        .padding(top = verticalPadding)
        .size(previewWidth, previewHeight)
        .clip(RoundedCornerShape(4.dp))
        .background(Color(configuration.layoutDesign(layoutId).backgroundColor))
        .then(
            if (onSelectSticker != null) {
                Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { position ->
                                currentOnSelectSticker?.invoke(findStickerIndex(currentStickers, position.x, position.y, size.width.toFloat(), size.height.toFloat()))
                            },
                            onDoubleTap = { position ->
                                val index = currentSelectedStickerIndex ?: findStickerIndex(currentStickers, position.x, position.y, size.width.toFloat(), size.height.toFloat())
                                if (index != null) currentOnDuplicateSticker?.invoke(index, position.x / size.width, position.y / size.height)
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val index = currentSelectedStickerIndex ?: findStickerIndex(currentStickers, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat())
                            if (index != null) {
                                currentOnTransformSticker?.invoke(index, pan.x / size.width, pan.y / size.height, zoom)
                            }
                        }
                    }
            } else Modifier,
        )
    Box(
        modifier = previewModifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(configuration.header.uppercase(), color = Color(0xFF18201F), style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(configuration.venueName, color = Color(0xFF46514E), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 8.dp))
            PhotoLayoutPreview(
                photos = photos,
                layoutId = layoutId,
                modifier = Modifier.weight(1f),
                guestBrightness = guestBrightness,
                thermalPreview = thermalPreview,
                frameColor = Color(configuration.layoutDesign(layoutId).frameColor),
            )
            Text(configuration.footer, color = Color(0xFF46514E), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            Text("KEEP THIS MOMENT", color = Color(0xFF46514E), fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
        }
        stickers.forEachIndexed { _, placement ->
            Text(
                placement.sticker.glyph,
                color = Color(0xFF1D2523),
                fontSize = 34.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = placement.scale
                        scaleY = placement.scale
                    }
                    .offset(
                        x = ((placement.x - 0.5f) * (previewWidth.value - 24f)).dp,
                        y = ((placement.y - 0.5f) * (previewHeight.value - 30)).dp,
                    ),
            )
        }
    }
}

private fun findStickerIndex(
    stickers: List<StickerPlacement>,
    touchX: Float,
    touchY: Float,
    width: Float,
    height: Float,
): Int? = stickers.indices.minByOrNull { index ->
    val sticker = stickers[index]
    val stickerX = sticker.x * width
    val stickerY = sticker.y * height
    (touchX - stickerX) * (touchX - stickerX) + (touchY - stickerY) * (touchY - stickerY)
}?.takeIf { index ->
    val sticker = stickers[index]
    val distanceX = touchX - sticker.x * width
    val distanceY = touchY - sticker.y * height
    distanceX * distanceX + distanceY * distanceY <= (48f * stickers[index].scale) * (48f * stickers[index].scale)
}

@Composable
private fun PhotoLayoutPreview(
    photos: List<File>,
    layoutId: LayoutId,
    modifier: Modifier = Modifier,
    guestBrightness: Int = 50,
    thermalPreview: Boolean = false,
    frameColor: Color = Color(0xFFE1EAE6),
) {
    val isGrid = layoutId == LayoutId.FOUR_GRID || layoutId == LayoutId.SIX_GRID
    if (layoutId == LayoutId.THREE_STRIP) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            photos.forEach { photo ->
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    PhotoCell(
                        photo = photo,
                        modifier = Modifier.fillMaxHeight().aspectRatio(4f / 3f),
                        guestBrightness = guestBrightness,
                        thermalPreview = thermalPreview,
                        frameColor = frameColor,
                    )
                }
            }
        }
        return
    }
    val rows = if (isGrid) photos.chunked(2) else photos.map(::listOf)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { photo ->
                    PhotoCell(
                        photo = photo,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        guestBrightness = guestBrightness,
                        thermalPreview = thermalPreview,
                        frameColor = frameColor,
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
    guestBrightness: Int = 50,
    thermalPreview: Boolean = false,
    frameColor: Color = Color(0xFFE1EAE6),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap = remember(photo.absolutePath, guestBrightness, thermalPreview) {
        createPreviewBitmap(photo, guestBrightness, thermalPreview)
    }
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            "Captured photo in final layout",
            modifier = modifier.background(Color(0xFFF7F3EB)).border(1.dp, frameColor),
            contentScale = contentScale,
        )
    } else {
        Box(modifier.background(Color(0xFFDBE4DF)))
    }
}

private fun createPreviewBitmap(file: File, guestBrightness: Int, thermalPreview: Boolean): Bitmap? {
    val source = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
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
        source,
        null,
        Rect(0, 0, output.width, output.height),
        Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixValues)) },
    )
    source.recycle()
    return output
}

@Composable
private fun PhotoThumbnail(photo: File) {
    val bitmap = remember(photo.absolutePath) { BitmapFactory.decodeFile(photo.absolutePath) }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Captured photo", contentScale = ContentScale.Crop, modifier = Modifier.size(156.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFB4F5D1), RoundedCornerShape(12.dp)))
}

@Composable
private fun ProcessingScreen() = Box(Modifier.fillMaxSize().background(BoothPalette.paper), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = BoothPalette.forest); Text("Creating your print strip", color = BoothPalette.ink, modifier = Modifier.padding(top = 18.dp)) } }

@Composable
private fun PrintingAnimationScreen(session: LocalPhotoSession, printStatus: PrintStatus, printMessage: String?) {
    val bitmap = remember(session.printStripPath) { BitmapFactory.decodeFile(session.printStripPath) }
    var animationProgress by remember(session.id) { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(animationProgress, animationSpec = tween(1_800), label = "strip-feed")
    LaunchedEffect(session.id) {
        delay(80)
        animationProgress = 1f
    }
    val status = when (printStatus) {
        PrintStatus.QUEUED -> "Preparing your strip"
        PrintStatus.PRINTING -> "Printing your strip"
        else -> "Sending your strip"
    }
    Column(
        Modifier.fillMaxSize().background(BoothPalette.paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("YOUR MOMENT IS ON THE WAY", color = BoothPalette.forest, style = MaterialTheme.typography.headlineSmall)
        Text(printMessage ?: status, color = BoothPalette.oxblood, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
        Box(
            Modifier
                .padding(top = 24.dp)
                .size(250.dp, 350.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BoothPalette.paperPanel),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(BoothPalette.forest))
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Your photo strip being sent to the printer",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .size(214.dp, 300.dp)
                        .offset(y = ((animatedProgress * 390f) - 330f).dp),
                )
            }
        }
        Text("Please wait while the printer receives your strip.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 18.dp))
        Text("The next screen confirms the printer queue status.", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
    Column(Modifier.fillMaxSize().background(BoothPalette.paper).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("THANK YOU", color = BoothPalette.forest, style = MaterialTheme.typography.displaySmall)
        Text(headline, color = BoothPalette.oxblood, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall)
        Text(detail, color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 12.dp))
        Text("Digital GIF and download QR will be enabled when secure delivery is connected.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 12.dp, bottom = 28.dp))
        if (printStatus == PrintStatus.PRINTING) CircularProgressIndicator(color = BoothPalette.forest, modifier = Modifier.padding(bottom = 18.dp))
        Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest)) { Text("Done") }
        Text(if (printStatus == PrintStatus.PRINTING) "This screen stays here while printing is in progress." else "This screen returns to the start automatically.", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
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
    Column(
        Modifier.fillMaxSize().background(BoothPalette.paper).padding(28.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("GET YOUR DIGITAL COPIES", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("Print sent. Your local digital files are ready on this tablet.", color = BoothPalette.oxblood, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        Text(if (deliveryLinks == null) "Cloud delivery is not configured. Files remain saved locally on this tablet." else "Scan either QR code within 1 hour to download your digital copy.", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 10.dp, bottom = 22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            DigitalQrCard(
                title = "Digital strip",
                label = "LOCAL STRIP",
                ready = digitalStripReady,
                fileName = session.digitalStripPath?.let { File(it).name },
                downloadUrl = deliveryLinks?.photoUrl,
            )
            DigitalQrCard(
                title = "Animated GIF",
                label = "LOCAL GIF",
                ready = animatedGifReady,
                fileName = session.animatedGifPath?.let { File(it).name },
                downloadUrl = deliveryLinks?.gifUrl,
            )
        }
        Button(onClick = onDone, modifier = Modifier.padding(top = 28.dp), colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest)) { Text("Done") }
        Text("This screen returns to the start automatically.", color = BoothPalette.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DigitalQrCard(title: String, label: String, ready: Boolean, fileName: String?, downloadUrl: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(230.dp)) {
        Text(title, color = BoothPalette.ink, style = MaterialTheme.typography.titleMedium)
        if (downloadUrl != null) {
            DownloadQrTile(downloadUrl, Modifier.padding(top = 10.dp))
        } else {
            DemoQrTile(Modifier.padding(top = 10.dp), label)
        }
        Text(if (ready) "Saved locally" else "Preparing", color = if (ready) BoothPalette.forest else BoothPalette.warmAccent, modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
        fileName?.let { Text(it, color = BoothPalette.inkMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
        Text(if (downloadUrl != null) "SCAN TO DOWNLOAD" else "QR READY AFTER CLOUD SETUP", color = BoothPalette.inkMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DownloadQrTile(url: String, modifier: Modifier = Modifier) {
    val bitmap = remember(url) { createQrBitmap(url, 360) }
    Card(modifier = modifier.size(230.dp), colors = CardDefaults.cardColors(Color.White)) {
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
    var pin by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; val setup = !pinStore.hasPin()
    Column(Modifier.fillMaxSize().background(Color(0xFF101719)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (setup) "Create operator PIN" else "Operator access", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.padding(top = 20.dp))
        if (setup) OutlinedTextField(confirmation, { confirmation = it.filter(Char::isDigit).take(12) }, label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.padding(top = 12.dp))
        error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 12.dp)) }
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { ElevatedButton(onClick = onBack) { Text("Back") }; Button(onClick = { error = when { pin.length < 4 -> "Use at least four digits."; setup && pin != confirmation -> "The PINs do not match."; setup -> { pinStore.setPin(pin); onSuccess(); null }; pinStore.matches(pin) -> { onSuccess(); null }; else -> "That PIN is not correct." } }) { Text(if (setup) "Save PIN" else "Unlock") } }
    }
}

@Composable
private fun BusinessPinScreen(configuration: BoothConfiguration, printQuantity: Int, onSuccess: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(BoothPalette.paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PAYMENT CONFIRMATION", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("Enter Operator PIN to Proceed", color = BoothPalette.oxblood, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text("${printQuantity} print${if (printQuantity == 1) "" else "s"}  •  ${configuration.priceLabelForQuantity(printQuantity)}", color = BoothPalette.inkMuted, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(12); error = null },
            label = { Text("Operator PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.padding(top = 22.dp),
        )
        error?.let { Text(it, color = BoothPalette.oxblood, modifier = Modifier.padding(top = 10.dp)) }
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedButton(onClick = onBack) { Text("Back to payment") }
            Button(
                onClick = {
                    error = when {
                        configuration.businessPin.length < 4 -> "Operator PIN is not configured. Ask the operator to set one."
                        pin == configuration.businessPin -> { onSuccess(); null }
                        else -> "That PIN is not correct. Try again."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
            ) { Text("Proceed") }
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
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
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
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
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

@Composable
private fun OperatorSettingsScreen(configuration: BoothConfiguration, sessionCount: Int, printJobs: List<PrintJob>, latestSession: LocalPhotoSession?, sessionStore: LocalSessionStore, onSaveConfiguration: (BoothConfiguration) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; val discovery = remember { UsbPrinterDiscovery(context.applicationContext) }; var usbDevices by remember { mutableStateOf(discovery.discover()) }
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
    val printQueue = remember { LocalPrintQueue(sessionStore) }
    var visiblePrintJobs by remember(printJobs) { mutableStateOf(printJobs.asReversed()) }
    var printerPermissionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var venueName by remember(configuration) { mutableStateOf(configuration.venueName) }; var header by remember(configuration) { mutableStateOf(configuration.header) }; var logoText by remember(configuration) { mutableStateOf(configuration.logoText) }; var footer by remember(configuration) { mutableStateOf(configuration.footer) }; var enabledLayouts by remember(configuration) { mutableStateOf(configuration.enabledLayouts) }; var layoutDesigns by remember(configuration) { mutableStateOf(configuration.layoutDesigns) }
    var thermalPrintBrightness by remember(configuration) { mutableStateOf(configuration.thermalPrintBrightness.toFloat()) }
    var thermalDitherThreshold by remember(configuration) { mutableStateOf(configuration.thermalDitherThreshold.toFloat()) }
    var receiptBottomFeedLines by remember(configuration) { mutableStateOf(configuration.receiptBottomFeedLines.toFloat()) }
    var crtIntensity by remember(configuration) { mutableStateOf(configuration.crtIntensity.toFloat()) }
    var reducedMotion by remember(configuration) { mutableStateOf(configuration.reducedMotion) }
    var reducedGlitch by remember(configuration) { mutableStateOf(configuration.reducedGlitch) }
    var deliveryWorkerUrl by remember(configuration) { mutableStateOf(configuration.deliveryWorkerUrl) }
    var deliveryBoothId by remember(configuration) { mutableStateOf(configuration.deliveryBoothId) }
    var deliveryDeviceKey by remember(configuration) { mutableStateOf(configuration.deliveryDeviceKey) }
    var operationMode by remember(configuration) { mutableStateOf(configuration.operationMode) }
    var businessPin by remember(configuration) { mutableStateOf(configuration.businessPin) }
    var printPriceTexts by remember(configuration) { mutableStateOf(priceTextValues(configuration.printPriceTiersMinor, 10, configuration.priceMinor)) }
    val pinGenerator = remember { SecureRandom() }
    val operatorFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color(0xFFD6E6DF),
        focusedBorderColor = Color(0xFFB4F5D1),
        unfocusedBorderColor = Color(0xFF9AA8A2),
        cursorColor = Color(0xFFB4F5D1),
    )
    val metrics = boothLayoutMetrics()
    Column(Modifier.fillMaxSize().background(Color(0xFF101719)).padding(metrics.pagePadding).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
        Text("OPERATOR SETTINGS", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Local MVP configuration. A published backend configuration will replace these controls in Release 2.", color = Color(0xFFD6E6DF))
        OutlinedTextField(venueName, { venueName = it.take(40) }, label = { Text("Venue name") }, colors = operatorFieldColors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(header, { header = it.take(40) }, label = { Text("Strip header") }, colors = operatorFieldColors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(logoText, { logoText = it.take(4) }, label = { Text("Owner logo mark (up to 4 characters)") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(footer, { footer = it.take(60) }, label = { Text("Strip footer") }, colors = operatorFieldColors, modifier = Modifier.fillMaxWidth())
        PriceTierEditor("Print prices · 1–10 prints", "Set the total price for each quantity. These prices are shared by Business and Rental modes.", printPriceTexts, operatorFieldColors) { printPriceTexts = it }
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
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Session mode", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Rental mode skips the payment gate but keeps local printing and both Digital Photo/GIF download QR codes. Business mode shows the configured payment QR page first.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
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
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cloud digital delivery", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Shared Cloudflare Worker/R2 delivery. The booth keeps local copies and cloud links expire after one hour.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                OutlinedTextField(deliveryWorkerUrl, { deliveryWorkerUrl = it.trim() }, label = { Text("Worker URL") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deliveryBoothId, { deliveryBoothId = it.filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' }.take(64) }, label = { Text("Booth ID") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deliveryDeviceKey, { deliveryDeviceKey = it.trim() }, label = { Text("Booth upload key") }, colors = operatorFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
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
        Button(onClick = {
            fun parsePrices(values: List<String>, fallbackBaseMinor: Int): List<Int> = values.mapIndexed { index, value ->
                ((value.toDoubleOrNull() ?: fallbackBaseMinor * (index + 1) / 100.0) * 100).toInt().coerceAtLeast(0)
            }
            val printPrices = parsePrices(printPriceTexts, configuration.priceMinor)
            onSaveConfiguration(configuration.copy(venueName = venueName.ifBlank { "Your venue" }, header = header.ifBlank { "PHOTO MOMENT" }, logoText = logoText.ifBlank { "PB" }, footer = footer.ifBlank { "Thanks for visiting" }, priceMinor = printPrices.firstOrNull() ?: configuration.priceMinor, operationMode = operationMode, businessPin = businessPin.filter(Char::isDigit).take(12), printPriceTiersMinor = printPrices, enabledLayouts = enabledLayouts, layoutDesigns = layoutDesigns, paymentQrUris = qrUris, paymentQrTitles = qrTitles, deliveryWorkerUrl = deliveryWorkerUrl, deliveryBoothId = deliveryBoothId, deliveryDeviceKey = deliveryDeviceKey, crtIntensity = crtIntensity.roundToInt().coerceIn(0, 100), reducedMotion = reducedMotion, reducedGlitch = reducedGlitch, thermalPrintBrightness = thermalPrintBrightness.roundToInt().coerceIn(0, 100), thermalDitherThreshold = thermalDitherThreshold.roundToInt().coerceIn(80, 180), receiptBottomFeedLines = receiptBottomFeedLines.roundToInt().coerceIn(2, 10)))
        }) { Text("Save kiosk configuration") }
        PrinterReadinessCard(
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
        PrintQueueCard(
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
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) { Column(Modifier.padding(18.dp)) { Text("Local sessions: $sessionCount", color = Color.White); Text("Prints needing attention: ${visiblePrintJobs.count { it.status == PrintStatus.QUEUED || it.status == PrintStatus.FAILED }}", color = Color.White, modifier = Modifier.padding(top = 6.dp)); Text("Each completed session creates one durable print job with its requested copy count. If a print fails, check paper, cover, power, cable, USB permission, then retry from the queue.", color = Color(0xFFD6E6DF), modifier = Modifier.padding(top = 8.dp)) } }
        ElevatedButton(onClick = onBack) { Text("Return to booth") }
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
private fun FailureScreen(message: String, onTryAgain: () -> Unit) = Box(Modifier.fillMaxSize().background(Color(0xFF101719)), contentAlignment = Alignment.Center) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("LET'S TRY THAT AGAIN", color = Color.White, style = MaterialTheme.typography.headlineSmall); Text(message, color = Color(0xFFD6E6DF), modifier = Modifier.padding(vertical = 16.dp)); Button(onClick = onTryAgain) { Text("Return to start") } } }

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
private fun isLandscape(): Boolean = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
