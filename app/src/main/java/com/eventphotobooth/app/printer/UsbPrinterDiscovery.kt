package com.eventphotobooth.app.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

data class DiscoveredUsbDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean,
)

/** Discovers connected USB devices only. Sending printer data waits for XP-80T validation. */
class UsbPrinterDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun discover(): List<DiscoveredUsbDevice> = usbManager.deviceList.values
        .sortedBy(UsbDevice::getDeviceName)
        .map { device ->
            DiscoveredUsbDevice(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                hasPermission = usbManager.hasPermission(device),
            )
        }

    fun requestPermission(deviceName: String, onResult: (Boolean) -> Unit) {
        val device = usbManager.deviceList.values.firstOrNull { it.deviceName == deviceName }
        if (device == null) {
            onResult(false)
            return
        }
        if (usbManager.hasPermission(device)) {
            onResult(true)
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            deviceName.hashCode(),
            Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                appContext.unregisterReceiver(this)
                onResult(granted)
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun printXpD4601bDiagnosticLabel(deviceName: String): Result<String> = runCatching {
        sendToPrinter(deviceName, buildXpD4601bDiagnosticTspl().toByteArray(StandardCharsets.US_ASCII))
        "Diagnostic label sent to XP-D4601B."
    }

    fun printXpD4601bStripProof(deviceName: String, stripFile: File, brightness: Int, ditherThreshold: Int): Result<String> = runCatching {
        check(stripFile.exists()) { "The latest strip image was not found." }
        val source = BitmapFactory.decodeFile(stripFile.absolutePath)
            ?: error("The latest strip image could not be opened.")
        val proof = source.toMonochromeProofBitmap(maxWidth = 384, maxHeight = 720)
        sendToPrinter(deviceName, buildXpD4601bProofTspl(proof, brightness.coerceIn(0, 100), ditherThreshold.coerceIn(80, 180)))
        "Latest strip proof sent to XP-D4601B."
    }

    fun printXp80tsDiagnosticReceipt(deviceName: String): Result<String> = runCatching {
        sendToPrinter(deviceName, buildXp80tsDiagnosticEscPos())
        "XP-80TS receipt test sent."
    }

    fun printXp80tsStrip(deviceName: String, stripFile: File, brightness: Int, ditherThreshold: Int, bottomFeedLines: Int): Result<String> = runCatching {
        check(stripFile.exists()) { "The latest strip image was not found." }
        val source = BitmapFactory.decodeFile(stripFile.absolutePath)
            ?: error("The latest strip image could not be opened.")
        val receipt = source.toReceiptBitmap(targetWidth = 576)
        sendToPrinter(
            deviceName,
            buildXp80tsRasterEscPos(
                bitmap = receipt,
                brightness = brightness.coerceIn(0, 100),
                ditherThreshold = ditherThreshold.coerceIn(80, 180),
                bottomFeedLines = bottomFeedLines.coerceIn(2, 10),
            ),
        )
        "Latest strip sent to XP-80TS."
    }

    fun printXp80tsAccessCodes(deviceName: String, packageLabel: String, codes: List<String>): Result<String> = runCatching {
        require(codes.isNotEmpty()) { "No access codes were generated." }
        // Send one complete ESC/POS receipt per code so the printer cuts each voucher separately.
        codes.forEach { code ->
            sendToPrinter(deviceName, buildAccessCodeReceipt(packageLabel, listOf(code)))
        }
        "${codes.size} access code receipts printed."
    }

    private fun sendToPrinter(deviceName: String, payload: ByteArray) {
        val device = usbManager.deviceList.values.firstOrNull { it.deviceName == deviceName }
            ?: error("USB printer is no longer connected.")
        check(usbManager.hasPermission(device)) { "USB permission is required before printing." }

        val target = findBulkOutInterface(device)
            ?: error("No USB bulk output endpoint was found for this device.")
        val connection = usbManager.openDevice(device)
            ?: error("Could not open the USB device.")

        try {
            check(connection.claimInterface(target.usbInterface, true)) { "Could not claim the printer USB interface." }
            var offset = 0
            while (offset < payload.size) {
                val chunkSize = minOf(16_384, payload.size - offset)
                val chunk = payload.copyOfRange(offset, offset + chunkSize)
                val sentBytes = connection.bulkTransfer(target.endpoint, chunk, chunk.size, 5_000)
                check(sentBytes > 0) { "The printer stopped accepting print data." }
                offset += sentBytes
            }
        } finally {
            runCatching { connection.releaseInterface(target.usbInterface) }
            connection.close()
        }
    }

    private fun findBulkOutInterface(device: UsbDevice): PrinterEndpoint? {
        repeat(device.interfaceCount) { interfaceIndex ->
            val usbInterface = device.getInterface(interfaceIndex)
            repeat(usbInterface.endpointCount) { endpointIndex ->
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    return PrinterEndpoint(usbInterface, endpoint)
                }
            }
        }
        return null
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.eventphotobooth.app.USB_PERMISSION"

        private fun buildXpD4601bDiagnosticTspl(): String = """
            SIZE 60 mm,30 mm
            GAP 2 mm,0 mm
            DENSITY 8
            SPEED 4
            DIRECTION 1
            CLS
            TEXT 40,35,"3",0,1,1,"EVENT PHOTOBOOTH"
            TEXT 40,80,"2",0,1,1,"XP-D4601B USB TEST"
            TEXT 40,120,"2",0,1,1,"DIAGNOSTIC ONLY"
            BAR 40,165,360,3
            PRINT 1,1
        """.trimIndent().replace("\n", "\r\n") + "\r\n"

        private fun buildXpD4601bProofTspl(bitmap: Bitmap, brightness: Int, ditherThreshold: Int): ByteArray {
            val widthBytes = (bitmap.width + 7) / 8
            val labelHeightMm = ((bitmap.height / 8f) + 18f).toInt().coerceIn(60, 110)
            return ByteArrayOutputStream().use { output ->
                output.writeAscii("SIZE 60 mm,$labelHeightMm mm\r\n")
                output.writeAscii("GAP 2 mm,0 mm\r\n")
                output.writeAscii("DENSITY 6\r\n")
                output.writeAscii("SPEED 3\r\n")
                output.writeAscii("DIRECTION 1\r\n")
                output.writeAscii("CLS\r\n")
                output.writeAscii("TEXT 24,18,\"2\",0,1,1,\"PHOTO STRIP PROOF\"\r\n")
                output.writeAscii("BITMAP 24,55,$widthBytes,${bitmap.height},0,")
                output.write(bitmap.toTsplBitmapBytes(widthBytes, brightness, ditherThreshold))
                output.writeAscii("\r\nPRINT 1,1\r\n")
                output.toByteArray()
            }
        }

        private fun buildXp80tsDiagnosticEscPos(): ByteArray = ByteArrayOutputStream().use { output ->
            output.write(byteArrayOf(0x1B, 0x40))
            output.write(byteArrayOf(0x1B, 0x61, 0x01))
            output.write(byteArrayOf(0x1D, 0x21, 0x11))
            output.writeAscii("EVENT PHOTOBOOTH\n")
            output.write(byteArrayOf(0x1D, 0x21, 0x00))
            output.writeAscii("XP-80TS RECEIPT TEST\n")
            output.writeAscii("------------------------\n")
            output.writeAscii("USB: connected\n")
            output.writeAscii("Command: ESC/POS\n")
            output.writeAscii("Paper: 80 mm class\n")
            output.writeAscii("Status: diagnostic only\n\n")
            output.writeAscii("If you can read this,\n")
            output.writeAscii("the receipt printer path works.\n\n\n")
            output.write(byteArrayOf(0x1B, 0x64, 0x03))
            output.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
            output.toByteArray()
        }

        private fun buildAccessCodeReceipt(packageLabel: String, codes: List<String>): ByteArray = ByteArrayOutputStream().use { output ->
            output.write(byteArrayOf(0x1B, 0x40))
            output.write(byteArrayOf(0x1B, 0x61, 0x01))
            output.write(byteArrayOf(0x1D, 0x21, 0x11))
            output.writeAscii("PHOTOBOOTH\n")
            output.write(byteArrayOf(0x1D, 0x21, 0x00))
            output.writeAscii("ACCESS CODES\n")
            output.writeAscii("$packageLabel\n")
            output.writeAscii("VALID TODAY UNTIL 11:59 PM\n")
            output.writeAscii("------------------------\n")
            codes.forEachIndexed { index, code ->
                output.writeAscii("${index + 1}. $code\n")
                output.writeAscii("------------------------\n")
            }
            output.writeAscii("Enter one code at the booth.\n\n\n")
            output.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
            output.toByteArray()
        }

        private fun buildXp80tsRasterEscPos(bitmap: Bitmap, brightness: Int, ditherThreshold: Int, bottomFeedLines: Int): ByteArray = ByteArrayOutputStream().use { output ->
            output.write(byteArrayOf(0x1B, 0x40))
            output.write(byteArrayOf(0x1B, 0x61, 0x01))
            output.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
            val widthBytes = (bitmap.width + 7) / 8
            output.write(widthBytes and 0xFF)
            output.write((widthBytes shr 8) and 0xFF)
            output.write(bitmap.height and 0xFF)
            output.write((bitmap.height shr 8) and 0xFF)
            output.write(bitmap.toEscPosRasterBytes(widthBytes, brightness, ditherThreshold))
            output.writeAscii("\n")
            output.write(byteArrayOf(0x1B, 0x64, bottomFeedLines.toByte()))
            output.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
            output.toByteArray()
        }
    }
}

private data class PrinterEndpoint(
    val usbInterface: UsbInterface,
    val endpoint: UsbEndpoint,
)

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.toByteArray(StandardCharsets.US_ASCII))
}

private fun Bitmap.toMonochromeProofBitmap(maxWidth: Int, maxHeight: Int): Bitmap {
    val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height, 1f)
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    val proof = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
    Canvas(proof).drawColor(Color.WHITE)
    Canvas(proof).drawBitmap(scaled, 0f, 0f, null)
    return proof
}

private fun Bitmap.toReceiptBitmap(targetWidth: Int): Bitmap {
    val scaledHeight = (height * (targetWidth.toFloat() / width)).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, scaledHeight, true)
    val receipt = Bitmap.createBitmap(targetWidth, scaledHeight, Bitmap.Config.ARGB_8888)
    Canvas(receipt).drawColor(Color.WHITE)
    Canvas(receipt).drawBitmap(scaled, 0f, 0f, null)
    return receipt
}

private fun Bitmap.toEscPosRasterBytes(widthBytes: Int, brightness: Int, ditherThreshold: Int): ByteArray {
    val output = ByteArray(widthBytes * height)
    val tones = FloatArray(width * height)
    val brightnessOffset = brightness.coerceIn(0, 100) * 1.2f - 26f

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = getPixel(x, y)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val luminance = red * 0.299f + green * 0.587f + blue * 0.114f
            tones[y * width + x] = ((luminance - 128f) * 1.08f + 128f + brightnessOffset).coerceIn(0f, 255f)
        }
    }

    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val oldTone = tones[index].coerceIn(0f, 255f)
            val newTone = if (oldTone < ditherThreshold.coerceIn(80, 180).toFloat()) 0f else 255f
            val error = oldTone - newTone

            if (newTone == 0f) {
                val byteIndex = y * widthBytes + x / 8
                output[byteIndex] = (output[byteIndex].toInt() or (0x80 shr (x % 8))).toByte()
            }
            tones.distributeError(x + 1, y, width, height, error * 7f / 16f)
            tones.distributeError(x - 1, y + 1, width, height, error * 3f / 16f)
            tones.distributeError(x, y + 1, width, height, error * 5f / 16f)
            tones.distributeError(x + 1, y + 1, width, height, error * 1f / 16f)
        }
    }
    return output
}

private fun Bitmap.toTsplBitmapBytes(widthBytes: Int, brightness: Int, ditherThreshold: Int): ByteArray {
    val output = ByteArray(widthBytes * height)
    val tones = FloatArray(width * height)
    val brightnessOffset = brightness.coerceIn(0, 100) * 1.2f - 26f
    val contrast = 1.15f

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = getPixel(x, y)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val luminance = red * 0.299f + green * 0.587f + blue * 0.114f
            tones[y * width + x] = ((luminance - 128f) * contrast + 128f + brightnessOffset).coerceIn(0f, 255f)
        }
    }

    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val oldTone = tones[index].coerceIn(0f, 255f)
            val newTone = if (oldTone < ditherThreshold.coerceIn(80, 180).toFloat()) 0f else 255f
            val error = oldTone - newTone

            if (newTone == 0f) {
                val index = y * widthBytes + x / 8
                output[index] = (output[index].toInt() or (0x80 shr (x % 8))).toByte()
            }
            tones.distributeError(x + 1, y, width, height, error * 7f / 16f)
            tones.distributeError(x - 1, y + 1, width, height, error * 3f / 16f)
            tones.distributeError(x, y + 1, width, height, error * 5f / 16f)
            tones.distributeError(x + 1, y + 1, width, height, error * 1f / 16f)
        }
    }
    return output
}

private fun FloatArray.distributeError(x: Int, y: Int, width: Int, height: Int, error: Float) {
    if (x !in 0 until width || y !in 0 until height) return
    val index = y * width + x
    this[index] = (this[index] + error).coerceIn(0f, 255f)
}
