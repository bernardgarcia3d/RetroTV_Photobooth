package com.eventphotobooth.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.eventphotobooth.app.BuildConfig
import com.eventphotobooth.app.data.BoothConfiguration
import com.eventphotobooth.app.data.PaymongoAuthorizedSession
import com.eventphotobooth.app.data.PaymongoCheckout
import com.eventphotobooth.app.data.PaymongoPaymentClient
import com.eventphotobooth.app.data.PaymongoPriceTier
import com.eventphotobooth.app.data.PendingPaymongoOrder
import com.eventphotobooth.app.data.PendingPaymongoOrderState
import com.eventphotobooth.app.data.decodePaymongoQrImage
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class PaymongoPrinterReadiness(
    val ready: Boolean,
    val message: String,
)

private enum class PaymongoCheckoutState {
    CHECKING_READINESS,
    READY_TO_START,
    PREPARING_QR,
    WAITING_FOR_PAYMENT,
    CONFIRMING_PAYMENT,
    QR_EXPIRED,
    RECOVERY_REQUIRED,
    ERROR,
}

@Composable
internal fun PaymongoCheckoutScreen(
    configuration: BoothConfiguration,
    paymentClient: PaymongoPaymentClient,
    pendingOrder: PendingPaymongoOrder?,
    hasCameraPermission: Boolean,
    requestCameraPermission: () -> Unit,
    checkPrinter: suspend () -> PaymongoPrinterReadiness,
    onPendingOrderChange: (PendingPaymongoOrder?) -> Unit,
    onPaymentAuthorized: (PaymongoAuthorizedSession) -> Unit,
    onRecoverClaimedSession: (PaymongoAuthorizedSession) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PaymongoCheckoutState.CHECKING_READINESS) }
    var selectedQuantity by remember { mutableStateOf(pendingOrder?.printQuantity ?: 1) }
    var workingOrder by remember(pendingOrder?.requestId) { mutableStateOf(pendingOrder) }
    var checkout by remember { mutableStateOf<PaymongoCheckout?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var readinessMessage by remember { mutableStateOf("Checking camera, printer, and payment service.") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastPaymentStatus by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var clockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPriceManager by remember { mutableStateOf(false) }

    if (showPriceManager) {
        PaymongoPriceManagementScreen(
            configuration = configuration,
            paymentClient = paymentClient,
            onBack = { showPriceManager = false },
        )
        return
    }

    fun savePending(order: PendingPaymongoOrder?) {
        workingOrder = order
        onPendingOrderChange(order)
    }

    fun showError(message: String) {
        statusMessage = message
        state = PaymongoCheckoutState.ERROR
    }

    fun refreshReadiness() {
        scope.launch {
            state = PaymongoCheckoutState.CHECKING_READINESS
            statusMessage = null
            val configurationIssue = paymentClient.configurationIssue()
            val printer = checkPrinter()
            val paymentService = if (configurationIssue == null) paymentClient.health().getOrDefault(false) else false
            val savedOrder = workingOrder
            readinessMessage = when {
                savedOrder != null && savedOrder.boothId != configuration.deliveryBoothId -> "A pending payment belongs to a different booth configuration. Do not continue it on this tablet."
                !hasCameraPermission -> "Camera permission is required before accepting payment."
                configurationIssue != null -> configurationIssue
                !printer.ready -> printer.message
                !paymentService -> "PayMongo payment service is not ready. Check the staging backend and tablet network."
                else -> "Camera ready. ${printer.message} Payment service ready."
            }
            state = when {
                savedOrder != null && savedOrder.boothId != configuration.deliveryBoothId -> PaymongoCheckoutState.ERROR
                savedOrder?.state == PendingPaymongoOrderState.SESSION_CLAIMED ||
                    savedOrder?.state == PendingPaymongoOrderState.FULFILLMENT_PENDING -> PaymongoCheckoutState.RECOVERY_REQUIRED
                !hasCameraPermission || configurationIssue != null || !printer.ready || !paymentService -> PaymongoCheckoutState.ERROR
                savedOrder?.qrImageData != null && (savedOrder.qrExpiresAtEpochMillis ?: 0) > System.currentTimeMillis() -> {
                    qrBitmap = decodePaymongoQrImage(requireNotNull(savedOrder.qrImageData))
                    PaymongoCheckoutState.WAITING_FOR_PAYMENT
                }
                else -> PaymongoCheckoutState.READY_TO_START
            }
        }
    }

    suspend fun checkPaymentStatus(ignoreBusy: Boolean = false, showConfirming: Boolean = false): Boolean {
        val order = workingOrder ?: return false
        if (busy && !ignoreBusy) return false
        val ownedBusyState = !busy
        if (ownedBusyState) busy = true
        if (showConfirming) state = PaymongoCheckoutState.CONFIRMING_PAYMENT
        return try {
            val paymentStatus = paymentClient.paymentStatus(order.orderId).getOrElse { error ->
                statusMessage = error.message ?: "Connection interrupted while checking payment."
                state = if ((order.qrExpiresAtEpochMillis ?: 0) > System.currentTimeMillis()) {
                    PaymongoCheckoutState.WAITING_FOR_PAYMENT
                } else {
                    PaymongoCheckoutState.QR_EXPIRED
                }
                return false
            }
            lastPaymentStatus = paymentStatus.paymentStatus.replace('_', ' ').uppercase(Locale.US)
            if (!paymentStatus.verifiedPaid) {
                statusMessage = "Payment is not confirmed yet."
                state = if ((order.qrExpiresAtEpochMillis ?: 0) > System.currentTimeMillis()) {
                    PaymongoCheckoutState.WAITING_FOR_PAYMENT
                } else {
                    PaymongoCheckoutState.QR_EXPIRED
                }
                false
            } else {
                val claimed = paymentClient.claimSession(order.orderId).getOrElse { error ->
                    showError(error.message ?: "Payment was received, but the booth session could not be activated.")
                    return false
                }
                val claimedOrder = order.copy(
                    state = PendingPaymongoOrderState.SESSION_CLAIMED,
                    sessionId = claimed.sessionId,
                    qrImageData = null,
                    qrExpiresAtEpochMillis = null,
                )
                savePending(claimedOrder)
                onPaymentAuthorized(
                    PaymongoAuthorizedSession(
                        orderId = order.orderId,
                        sessionId = claimed.sessionId,
                        printQuantity = order.printQuantity,
                        amountMinor = order.amountMinor,
                        currency = order.currency,
                    ),
                )
                true
            }
        } finally {
            if (ownedBusyState) busy = false
        }
    }

    suspend fun attachNewQr(activeCheckout: PaymongoCheckout) {
        busy = true
        state = PaymongoCheckoutState.PREPARING_QR
        statusMessage = null
        paymentClient.createQrCode(activeCheckout).onSuccess { qr ->
            val saved = (workingOrder ?: PendingPaymongoOrder(
                orderId = activeCheckout.orderId,
                requestId = activeCheckout.requestId,
                boothId = configuration.deliveryBoothId,
                printQuantity = activeCheckout.printQuantity,
                amountMinor = activeCheckout.amountMinor,
                currency = activeCheckout.currency,
            )).copy(
                qrImageData = qr.imageData,
                qrExpiresAtEpochMillis = qr.expiresAtEpochMillis,
            )
            qrBitmap = qr.decodeBitmap()
            savePending(saved)
            state = PaymongoCheckoutState.WAITING_FOR_PAYMENT
        }.onFailure { error ->
            showError(error.message ?: "A QR code could not be prepared.")
        }
        busy = false
    }

    fun createOrResumeOrder() {
        scope.launch {
            busy = true
            state = PaymongoCheckoutState.PREPARING_QR
            statusMessage = null
            val existing = workingOrder
            val requestId = existing?.requestId ?: UUID.randomUUID().toString()
            val result = paymentClient.createOrder(existing?.printQuantity ?: selectedQuantity, requestId).getOrElse { error ->
                showError(error.message ?: "The payment order could not be created.")
                busy = false
                return@launch
            }
            checkout = result
            val saved = existing?.copy(
                printQuantity = result.printQuantity,
                amountMinor = result.amountMinor,
                currency = result.currency,
            ) ?: PendingPaymongoOrder(
                orderId = result.orderId,
                requestId = requestId,
                boothId = configuration.deliveryBoothId,
                printQuantity = result.printQuantity,
                amountMinor = result.amountMinor,
                currency = result.currency,
            )
            savePending(saved)
            val confirmed = checkPaymentStatus(ignoreBusy = true)
            if (!confirmed && state != PaymongoCheckoutState.ERROR) attachNewQr(result)
            busy = false
        }
    }

    LaunchedEffect(Unit) { refreshReadiness() }
    LaunchedEffect(hasCameraPermission) {
        if (state == PaymongoCheckoutState.ERROR && !busy) refreshReadiness()
    }
    LaunchedEffect(workingOrder?.qrImageData) {
        val savedQr = workingOrder?.qrImageData
        if (savedQr != null) qrBitmap = decodePaymongoQrImage(savedQr)
    }
    LaunchedEffect(state, workingOrder?.qrExpiresAtEpochMillis) {
        val expiry = workingOrder?.qrExpiresAtEpochMillis ?: return@LaunchedEffect
        if (state != PaymongoCheckoutState.WAITING_FOR_PAYMENT) return@LaunchedEffect
        var pollDelayMillis = 2_000L
        var nextPollAtMillis = System.currentTimeMillis() + pollDelayMillis
        while (state == PaymongoCheckoutState.WAITING_FOR_PAYMENT && System.currentTimeMillis() < expiry) {
            clockMillis = System.currentTimeMillis()
            if (clockMillis >= nextPollAtMillis) {
                checkPaymentStatus()
                pollDelayMillis = (pollDelayMillis * 2).coerceAtMost(8_000L)
                nextPollAtMillis = System.currentTimeMillis() + pollDelayMillis
            }
            delay(1_000)
        }
        if (state == PaymongoCheckoutState.WAITING_FOR_PAYMENT && System.currentTimeMillis() >= expiry) {
            qrBitmap = null
            workingOrder?.let { savePending(it.copy(qrImageData = null)) }
            statusMessage = "This QR payment window expired without confirmation. The original order total is still frozen."
            state = PaymongoCheckoutState.QR_EXPIRED
        }
    }

    val amountLabel = workingOrder?.let { formatPaymongoAmount(it.amountMinor, it.currency) }
    val activeQrExpiresIn = ((workingOrder?.qrExpiresAtEpochMillis ?: 0L) - clockMillis).coerceAtLeast(0L) / 1_000L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoothPalette.paper)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PAYMONGO QR PH", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("STAGING CHECKOUT", color = BoothPalette.oxblood, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(
            "${BuildConfig.PAYMONGO_ENVIRONMENT}  Secure server verification required",
            color = BoothPalette.inkMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(top = 18.dp, bottom = 20.dp),
            colors = CardDefaults.cardColors(BoothPalette.paperLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    PaymongoCheckoutState.CHECKING_READINESS -> {
                        CircularProgressIndicator(color = BoothPalette.forest)
                        Text(readinessMessage, color = BoothPalette.ink, textAlign = TextAlign.Center)
                    }

                    PaymongoCheckoutState.READY_TO_START,
                    PaymongoCheckoutState.PREPARING_QR,
                    PaymongoCheckoutState.ERROR -> {
                        CheckoutQuantityPicker(
                            selectedQuantity = workingOrder?.printQuantity ?: selectedQuantity,
                            enabled = workingOrder == null && !busy && state != PaymongoCheckoutState.PREPARING_QR,
                            onSelect = { selectedQuantity = it },
                        )
                        if (workingOrder != null) {
                            Text("ORDER TOTAL", color = BoothPalette.inkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(amountLabel.orEmpty(), color = BoothPalette.forest, style = MaterialTheme.typography.headlineSmall)
                            Text("Quantity and amount are locked by the payment server.", color = BoothPalette.inkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        } else {
                            Text("The final PHP amount is confirmed by the secure payment server before the QR is shown.", color = BoothPalette.inkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        if (state == PaymongoCheckoutState.PREPARING_QR || busy) {
                            CircularProgressIndicator(color = BoothPalette.forest, modifier = Modifier.size(30.dp))
                            Text("Preparing a single-use QR.", color = BoothPalette.ink, textAlign = TextAlign.Center)
                        } else if (state == PaymongoCheckoutState.READY_TO_START) {
                            Button(
                                onClick = ::createOrResumeOrder,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                            ) { Text(if (workingOrder == null) "CONTINUE TO PAYMENT" else "CONTINUE THIS PAYMENT") }
                        }
                        if (state == PaymongoCheckoutState.ERROR) {
                            Text(statusMessage ?: readinessMessage, color = BoothPalette.error, fontSize = 12.sp, textAlign = TextAlign.Center)
                            if (!hasCameraPermission) {
                                ElevatedButton(onClick = requestCameraPermission, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("GRANT CAMERA PERMISSION") }
                            }
                            ElevatedButton(onClick = ::refreshReadiness, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("CHECK READINESS AGAIN") }
                        }
                        if (workingOrder == null && !busy) {
                            OutlinedButton(onClick = { showPriceManager = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("OPERATOR PRICE MANAGEMENT")
                            }
                        }
                    }

                    PaymongoCheckoutState.WAITING_FOR_PAYMENT,
                    PaymongoCheckoutState.CONFIRMING_PAYMENT -> {
                        Text("SCAN TO PAY", color = BoothPalette.forest, style = MaterialTheme.typography.titleLarge)
                        Text(amountLabel.orEmpty(), color = BoothPalette.ink, style = MaterialTheme.typography.headlineSmall)
                        Text("${workingOrder?.printQuantity ?: selectedQuantity} print${if ((workingOrder?.printQuantity ?: selectedQuantity) == 1) "" else "s"}", color = BoothPalette.inkMuted)
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "PayMongo QR Ph payment code",
                                modifier = Modifier
                                    .size(300.dp)
                                    .background(Color.White, RoundedCornerShape(14.dp))
                                    .border(1.dp, BoothPalette.border, RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                            )
                        } ?: CircularProgressIndicator(color = BoothPalette.forest)
                        Text("Open your bank or e-wallet app, then scan this QR Ph code.", color = BoothPalette.inkMuted, textAlign = TextAlign.Center)
                        Text("QR window: ${formatCountdown(activeQrExpiresIn)}", color = BoothPalette.oxblood, fontWeight = FontWeight.Bold)
                        Text("Order ${workingOrder?.orderId?.takeLast(8)?.uppercase().orEmpty()}", color = BoothPalette.inkMuted, fontSize = 11.sp)
                        if (state == PaymongoCheckoutState.CONFIRMING_PAYMENT || busy) {
                            CircularProgressIndicator(color = BoothPalette.forest, modifier = Modifier.size(30.dp))
                            Text("Confirming securely.", color = BoothPalette.ink)
                        } else {
                            Button(onClick = { scope.launch { checkPaymentStatus(showConfirming = true) } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Text("I HAVE PAID - CHECK STATUS")
                            }
                        }
                        lastPaymentStatus?.let { Text("Provider status: $it", color = BoothPalette.inkMuted, fontSize = 11.sp) }
                        statusMessage?.let { Text(it, color = BoothPalette.inkMuted, fontSize = 12.sp, textAlign = TextAlign.Center) }
                    }

                    PaymongoCheckoutState.QR_EXPIRED -> {
                        Text("QR WINDOW EXPIRED", color = BoothPalette.oxblood, style = MaterialTheme.typography.titleLarge)
                        Text(amountLabel.orEmpty(), color = BoothPalette.ink, style = MaterialTheme.typography.headlineSmall)
                        Text(statusMessage ?: "No payment was confirmed. You can create a new QR for the same server-priced order.", color = BoothPalette.inkMuted, textAlign = TextAlign.Center)
                        Button(onClick = ::createOrResumeOrder, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("GENERATE A NEW QR") }
                        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("RETURN TO START") }
                    }

                    PaymongoCheckoutState.RECOVERY_REQUIRED -> {
                        val fulfillmentPending = workingOrder?.state == PendingPaymongoOrderState.FULFILLMENT_PENDING
                        Text(if (fulfillmentPending) "FULFILLMENT RECOVERY REQUIRED" else "PAID SESSION RECOVERY REQUIRED", color = BoothPalette.oxblood, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            if (fulfillmentPending) {
                                "This paid order already entered media or printing. Do not restart capture or charge the guest again. An operator must review its existing print job."
                            } else {
                                "A paid session was already claimed on this tablet. It will not be reassigned automatically to another guest."
                            },
                            color = BoothPalette.inkMuted,
                            textAlign = TextAlign.Center,
                        )
                        if (!fulfillmentPending) {
                            Text("Only resume this if the original guest is still present. This does not create another payment or another session.", color = BoothPalette.inkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Button(
                                onClick = {
                                    val order = workingOrder
                                    val sessionId = order?.sessionId
                                    if (order == null || sessionId.isNullOrBlank()) {
                                        showError("The saved paid session is incomplete. Ask the operator to reconcile the original order.")
                                    } else {
                                        onRecoverClaimedSession(
                                            PaymongoAuthorizedSession(
                                                orderId = order.orderId,
                                                sessionId = sessionId,
                                                printQuantity = order.printQuantity,
                                                amountMinor = order.amountMinor,
                                                currency = order.currency,
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                            ) { Text("RESUME ORIGINAL PAID SESSION") }
                        }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("RETURN TO START") }
                    }
                }
            }
        }
        if (state == PaymongoCheckoutState.READY_TO_START || state == PaymongoCheckoutState.ERROR) {
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.widthIn(min = 240.dp)) { Text("RETURN TO START") }
        }
    }
}

@Composable
private fun CheckoutQuantityPicker(selectedQuantity: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SELECT PRINT QUANTITY", color = BoothPalette.ink, style = MaterialTheme.typography.titleMedium)
        Text("This selection is sent to the server. The server decides the final amount.", color = BoothPalette.inkMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            (1..5).forEach { quantity -> QuantityOption(quantity, quantity == selectedQuantity, enabled, onSelect) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            (6..10).forEach { quantity -> QuantityOption(quantity, quantity == selectedQuantity, enabled, onSelect) }
        }
    }
}

@Composable
private fun QuantityOption(quantity: Int, selected: Boolean, enabled: Boolean, onSelect: (Int) -> Unit) {
    val border = if (selected) BoothPalette.forest else BoothPalette.border
    val background = if (selected) BoothPalette.forest else BoothPalette.paper
    val text = if (selected) BoothPalette.paperLight else BoothPalette.ink
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onSelect(quantity) },
        contentAlignment = Alignment.Center,
    ) { Text(quantity.toString(), color = text, fontWeight = FontWeight.Bold) }
}

/**
 * The PIN is a local owner-control for the tablet UI. The server still scopes
 * the actual change to the dedicated credential installed for this booth.
 */
@Composable
private fun PaymongoPriceManagementScreen(
    configuration: BoothConfiguration,
    paymentClient: PaymongoPaymentClient,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val prices = remember { mutableStateListOf<String>().apply { repeat(10) { add("") } } }
    var ownerPin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadPrices() {
        loading = true
        message = null
        scope.launch {
            paymentClient.priceTiers().onSuccess { tiers ->
                prices.indices.forEach { index -> prices[index] = "" }
                tiers.forEach { tier ->
                    prices[tier.printQuantity - 1] = String.format(Locale.US, "%.2f", tier.amountMinor / 100.0)
                }
                message = if (tiers.isEmpty()) "No active server prices yet. Enter the quantities you want to sell." else "Loaded the active server prices."
            }.onFailure { error ->
                message = error.message ?: "Could not load prices. This tablet may need owner provisioning."
            }
            loading = false
        }
    }

    fun savePrices() {
        val tiers = prices.mapIndexedNotNull { index, raw ->
            val amount = raw.trim().replace(",", "").toDoubleOrNull() ?: return@mapIndexedNotNull null
            if (amount <= 0.0) return@mapIndexedNotNull null
            PaymongoPriceTier(index + 1, (amount * 100).toInt())
        }
        if (tiers.isEmpty() || prices.any { it.isNotBlank() && (it.trim().replace(",", "").toDoubleOrNull() ?: 0.0) <= 0.0 }) {
            message = "Enter at least one valid PHP price. Leave an unavailable quantity blank."
            return
        }
        loading = true
        message = null
        scope.launch {
            paymentClient.replacePriceTiers(tiers).onSuccess { saved ->
                prices.indices.forEach { index -> prices[index] = "" }
                saved.forEach { tier -> prices[tier.printQuantity - 1] = String.format(Locale.US, "%.2f", tier.amountMinor / 100.0) }
                message = "Prices saved securely. New checkouts now use this version; existing orders keep their original price."
            }.onFailure { error ->
                message = error.message ?: "Could not save prices."
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BoothPalette.paper).padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PAYMONGO PRICING", color = BoothPalette.forest, style = MaterialTheme.typography.headlineMedium)
        Text("${configuration.deliveryBoothId.ifBlank { "UNPROVISIONED BOOTH" }}  owner control", color = BoothPalette.inkMuted, fontSize = 12.sp)
        if (!unlocked) {
            Text("Enter the local Business payment PIN to edit server-owned prices.", color = BoothPalette.inkMuted, textAlign = TextAlign.Center)
            OutlinedTextField(
                value = ownerPin,
                onValueChange = { ownerPin = it.filter(Char::isDigit).take(12) },
                label = { Text("Business payment PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
            )
            Button(
                enabled = ownerPin.isNotBlank() && !loading,
                onClick = {
                    if (ownerPin == configuration.businessPin) {
                        unlocked = true
                        ownerPin = ""
                        loadPrices()
                    } else message = "Incorrect Business payment PIN."
                },
            ) { Text("UNLOCK PRICING") }
        } else {
            Text("Set a PHP total for each quantity. Leave a row blank to hide that quantity from checkout.", color = BoothPalette.inkMuted, textAlign = TextAlign.Center)
            (1..10).forEach { quantity ->
                OutlinedTextField(
                    value = prices[quantity - 1],
                    onValueChange = { input -> prices[quantity - 1] = input.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                    label = { Text("$quantity print${if (quantity == 1) "" else "s"} - PHP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                )
            }
            Button(onClick = ::savePrices, enabled = !loading, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).height(54.dp)) {
                Text(if (loading) "SAVING." else "SAVE SERVER PRICES")
            }
        }
        if (loading) CircularProgressIndicator(color = BoothPalette.forest, modifier = Modifier.size(28.dp))
        message?.let { Text(it, color = if (it.contains("saved", ignoreCase = true) || it.contains("Loaded")) BoothPalette.forest else BoothPalette.error, textAlign = TextAlign.Center, fontSize = 12.sp) }
        OutlinedButton(onClick = onBack, enabled = !loading, modifier = Modifier.widthIn(min = 240.dp)) { Text("RETURN TO CHECKOUT") }
    }
}

internal fun formatPaymongoAmount(amountMinor: Int, currency: String): String = when (currency) {
    "PHP" -> "?" + String.format(Locale.US, "%.2f", amountMinor / 100.0)
    else -> "$currency " + String.format(Locale.US, "%.2f", amountMinor / 100.0)
}

private fun formatCountdown(seconds: Long): String = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)

