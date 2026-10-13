package com.eventphotobooth.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventphotobooth.app.data.BorderEdgeMode
import com.eventphotobooth.app.data.BorderSliceGuides
import com.eventphotobooth.app.data.BuiltInBorderStyle
import com.eventphotobooth.app.data.BoothConfiguration
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.LocalReceiptBorderTemplateStore
import com.eventphotobooth.app.data.ReceiptBorderSource
import com.eventphotobooth.app.data.ResponsiveBorderTemplate
import com.eventphotobooth.app.data.StickerPlacement
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ReceiptBorderSelectionScreen(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    templates: List<ResponsiveBorderTemplate>,
    selectedTemplateId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val selected = templates.firstOrNull { it.id == selectedTemplateId }
        ?: templates.firstOrNull { it.isDefault }
        ?: templates.firstOrNull()
    if (isLandscape()) {
        LandscapeArtworkCarousel(
            photos = photos,
            layoutId = layoutId,
            configuration = configuration,
            templates = templates,
            selectedTemplateId = selected?.id,
            onSelect = onSelect,
            onBack = onBack,
            onContinue = onContinue,
        )
    } else {
        PortraitArtworkCarousel(
            photos = photos,
            layoutId = layoutId,
            configuration = configuration,
            templates = templates,
            selectedTemplateId = selected?.id,
            onSelect = onSelect,
            onBack = onBack,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun LandscapeArtworkCarousel(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    templates: List<ResponsiveBorderTemplate>,
    selectedTemplateId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val carouselState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BoothPalette.paper)
            .padding(horizontal = 58.dp, vertical = 32.dp),
    ) {
        val previewColumnWidth = (maxWidth * 0.27f).coerceIn(280.dp, 360.dp)
        val cardWidth = (maxWidth * 0.14f).coerceIn(126.dp, 176.dp)
        val cardHeight = (maxHeight * 0.65f).coerceAtLeast(250.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = previewColumnWidth + 28.dp),
        ) {
            Text(
                "CHOOSE YOUR ARTWORK",
                color = BoothPalette.forest,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                "Pick a look for your print.",
                color = BoothPalette.ink,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 22.dp),
            )
            if (templates.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(cardHeight), contentAlignment = Alignment.Center) {
                    Text("No artwork is enabled for this layout.", color = BoothPalette.oxblood, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(cardHeight)) {
                    LazyRow(
                        state = carouselState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = cardWidth * 0.42f),
                    ) {
                        items(templates, key = { it.id }) { template ->
                            ArtworkCarouselCard(
                                template = template,
                                selected = template.id == selectedTemplateId,
                                onClick = { onSelect(template.id) },
                                modifier = Modifier.width(cardWidth).fillMaxHeight(),
                            )
                        }
                    }
                    if (templates.size > 1) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    carouselState.animateScrollToItem(
                                        (carouselState.firstVisibleItemIndex + 1).coerceAtMost(templates.lastIndex),
                                    )
                                }
                            },
                            enabled = carouselState.canScrollForward,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(64.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(BoothPalette.forest),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Show next artwork",
                                tint = BoothPalette.paperLight,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.width(214.dp).height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("BACK", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                    modifier = Modifier.width(250.dp).height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("CONTINUE", color = BoothPalette.paperLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BoothPalette.paperLight)
                }
            }
        }
        ArtworkLivePreview(
            photos = photos,
            layoutId = layoutId,
            configuration = configuration,
            selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(previewColumnWidth)
                .fillMaxHeight(),
            maxPreviewWidth = previewColumnWidth,
            maxPreviewHeight = maxHeight,
        )
    }
}

@Composable
private fun ArtworkLivePreview(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    selectedTemplate: ResponsiveBorderTemplate?,
    modifier: Modifier,
    maxPreviewWidth: androidx.compose.ui.unit.Dp,
    maxPreviewHeight: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (selectedTemplate == null) {
                Text("Select an artwork to preview it.", color = BoothPalette.inkMuted, fontSize = 12.sp)
            } else {
                StripOutputPreview(
                    photos = photos,
                    layoutId = layoutId,
                    configuration = configuration,
                    stickers = emptyList(),
                    borderTemplate = selectedTemplate,
                    maxPreviewWidth = maxPreviewWidth,
                    previewWidthOverride = maxPreviewWidth,
                    maxPreviewHeight = maxPreviewHeight,
                    verticalPadding = 0.dp,
                )
            }
        }
    }
}

@Composable
private fun PortraitArtworkCarousel(
    photos: List<File>,
    layoutId: LayoutId,
    configuration: BoothConfiguration,
    templates: List<ResponsiveBorderTemplate>,
    selectedTemplateId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val carouselState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoothPalette.paper)
            .padding(20.dp),
    ) {
        Text("CHOOSE YOUR ARTWORK", color = BoothPalette.forest, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("Pick a look for your print.", color = BoothPalette.ink, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        ArtworkLivePreview(
            photos = photos,
            layoutId = layoutId,
            configuration = configuration,
            selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId },
            modifier = Modifier.fillMaxWidth().height(344.dp),
            maxPreviewWidth = 226.dp,
            maxPreviewHeight = 328.dp,
        )
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp)) {
            if (templates.isEmpty()) {
                Text("No artwork is enabled for this layout.", color = BoothPalette.oxblood, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyRow(
                    state = carouselState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 68.dp),
                ) {
                    items(templates, key = { it.id }) { template ->
                        ArtworkCarouselCard(
                            template = template,
                            selected = template.id == selectedTemplateId,
                            onClick = { onSelect(template.id) },
                            modifier = Modifier.width(204.dp).fillMaxHeight(),
                        )
                    }
                }
                if (templates.size > 1) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                carouselState.animateScrollToItem(
                                    (carouselState.firstVisibleItemIndex + 1).coerceAtMost(templates.lastIndex),
                                )
                            }
                        },
                        enabled = carouselState.canScrollForward,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(BoothPalette.forest),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Show next artwork",
                            tint = BoothPalette.paperLight,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkCarouselCard(
    template: ResponsiveBorderTemplate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(onClickLabel = "Select ${template.name}", onClick = onClick)
            .border(if (selected) 3.dp else 2.dp, if (selected) BoothPalette.forest else BoothPalette.border, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(if (selected) BoothPalette.forest else BoothPalette.paperLight),
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BorderThumbnail(template, Modifier.fillMaxSize())
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected artwork",
                    tint = BoothPalette.mint,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp),
                )
            }
        }
    }
}

@Composable
internal fun ReceiptTemplateSettingsPanel(
    store: LocalReceiptBorderTemplateStore,
    templates: List<ResponsiveBorderTemplate>,
    onTemplatesChanged: (List<ResponsiveBorderTemplate>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newTemplateName by remember { mutableStateOf("") }
    var uploadLayoutId by remember { mutableStateOf<LayoutId?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.importBorder(uri, newTemplateName, uploadLayoutId) }
            }
            result.onSuccess { imported ->
                onTemplatesChanged(store.load())
                newTemplateName = ""
                message = "${imported.name} is saved locally on this tablet."
            }.onFailure { error ->
                message = error.message ?: "The border could not be imported."
            }
        }
    }
    fun persist(updated: ResponsiveBorderTemplate) {
        onTemplatesChanged(store.update(updated))
    }
    fun move(template: ResponsiveBorderTemplate, direction: Int) {
        val ordered = templates.sortedBy { it.sortOrder }.toMutableList()
        val from = ordered.indexOfFirst { it.id == template.id }
        val to = (from + direction).coerceIn(0, ordered.lastIndex)
        if (from >= 0 && from != to) {
            val item = ordered.removeAt(from)
            ordered.add(to, item)
            store.save(ordered)
            onTemplatesChanged(store.load())
        }
    }
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Receipt templates", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Bundled borders ship with the APK. Operator borders are saved only in this tablet's private app storage and work offline.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
            OutlinedTextField(
                value = newTemplateName,
                onValueChange = { newTemplateName = it.take(48) },
                label = { Text("Custom border name") },
                placeholder = { Text("e.g. Mia and Jay wedding") },
                colors = templateFieldColors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { uploadLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Upload transparent border") }
            Text("Use for layout", color = Color.White, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ElevatedButton(
                    onClick = { uploadLayoutId = null },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = if (uploadLayoutId == null) BoothPalette.mint else Color(0xFF263035)),
                ) { Text("All", fontSize = 10.sp) }
                LayoutId.entries.forEach { layout ->
                    ElevatedButton(
                        onClick = { uploadLayoutId = layout },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = if (uploadLayoutId == layout) BoothPalette.mint else Color(0xFF263035)),
                    ) { Text(layout.photoCount.toString(), fontSize = 10.sp) }
                }
            }
            Text("Use transparent PNG or WebP artwork. The center must remain clear for photos and customized receipt text.", color = Color(0xFF9AA8A2), fontSize = 11.sp)
            message?.let { status ->
                Text(status, color = if (status.contains("could not", true) || status.contains("Use a", true)) Color(0xFFFFB4AB) else BoothPalette.mint, fontSize = 12.sp)
            }
        }
    }
    Text("Available borders", color = Color.White, style = MaterialTheme.typography.titleMedium)
    templates.sortedBy { it.sortOrder }.forEachIndexed { index, template ->
        ReceiptTemplateManagementCard(
            template = template,
            position = index,
            size = templates.size,
            onToggle = {
                if (template.enabled && templates.count { it.enabled } == 1) {
                    message = "Keep at least one border enabled for Make It Yours."
                } else {
                    val updated = template.copy(enabled = !template.enabled)
                    val saved = store.update(updated)
                    if (!updated.enabled && template.isDefault) {
                        val fallback = saved.firstOrNull { it.enabled }
                        onTemplatesChanged(if (fallback == null) saved else store.setDefault(fallback.id))
                    } else {
                        onTemplatesChanged(saved)
                    }
                }
            },
            onDefault = { onTemplatesChanged(store.setDefault(template.id)) },
            onMoveEarlier = { move(template, -1) },
            onMoveLater = { move(template, 1) },
            onSave = { persist(it) },
            onRemove = {
                onTemplatesChanged(store.removeCustom(template.id))
                message = "${template.name} was removed from this tablet."
            },
        )
    }
}

@Composable
private fun ReceiptTemplateManagementCard(
    template: ResponsiveBorderTemplate,
    position: Int,
    size: Int,
    onToggle: () -> Unit,
    onDefault: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onSave: (ResponsiveBorderTemplate) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BorderThumbnail(template, Modifier.size(width = 64.dp, height = 94.dp))
                Column(Modifier.weight(1f)) {
                    Text(template.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(template.layoutId?.let { "For ${it.photoCount}-photo layout" } ?: "Reusable for all layouts", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                    Text(if (template.source == ReceiptBorderSource.BUNDLED) "Bundled with APK" else "Saved locally on this tablet", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                    Text("${if (template.enabled) "Enabled" else "Disabled"}${if (template.isDefault) " · Default" else ""}", color = if (template.enabled) BoothPalette.mint else Color(0xFF9AA8A2), fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedButton(onClick = onToggle, modifier = Modifier.weight(1f)) { Text(if (template.enabled) "Disable" else "Enable", fontSize = 11.sp) }
                ElevatedButton(onClick = onDefault, enabled = template.enabled && !template.isDefault, modifier = Modifier.weight(1f)) { Text(if (template.isDefault) "Default" else "Set default", fontSize = 11.sp) }
                ElevatedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) { Text(if (expanded) "Close" else "Edit", fontSize = 11.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedButton(onClick = onMoveEarlier, enabled = position > 0, modifier = Modifier.weight(1f)) { Text("Move up", fontSize = 11.sp) }
                ElevatedButton(onClick = onMoveLater, enabled = position < size - 1, modifier = Modifier.weight(1f)) { Text("Move down", fontSize = 11.sp) }
                if (template.source == ReceiptBorderSource.OPERATOR_CUSTOM) {
                    ElevatedButton(onClick = onRemove, modifier = Modifier.weight(1f), colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF5C2925))) { Text("Remove", fontSize = 11.sp) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (expanded) BorderGuideEditor(template = template, onSave = onSave)
        }
    }
}

@Composable
private fun BorderGuideEditor(template: ResponsiveBorderTemplate, onSave: (ResponsiveBorderTemplate) -> Unit) {
    var name by remember(template.id, template.updatedAtEpochMs) { mutableStateOf(template.name) }
    var left by remember(template.id, template.updatedAtEpochMs) { mutableStateOf(template.guides.left) }
    var right by remember(template.id, template.updatedAtEpochMs) { mutableStateOf(template.guides.right) }
    var top by remember(template.id, template.updatedAtEpochMs) { mutableStateOf(template.guides.top) }
    var bottom by remember(template.id, template.updatedAtEpochMs) { mutableStateOf(template.guides.bottom) }
    var edgeMode by remember(template.id, template.updatedAtEpochMs) { mutableStateOf(template.edgeMode) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Responsive border guides", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Text("The four guides define the flexible center. Customized text and photos automatically stay inside this safe area.", color = Color(0xFFD6E6DF), fontSize = 11.sp)
        OutlinedTextField(name, { name = it.take(48) }, label = { Text("Border name") }, colors = templateFieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
        GuideSlider("Left safe edge", left, 0.02f..0.35f) { left = it.coerceAtMost(right - 0.12f) }
        GuideSlider("Right safe edge", right, 0.65f..0.98f) { right = it.coerceAtLeast(left + 0.12f) }
        GuideSlider("Top safe edge", top, 0.02f..0.35f) { top = it.coerceAtMost(bottom - 0.12f) }
        GuideSlider("Bottom safe edge", bottom, 0.65f..0.98f) { bottom = it.coerceAtLeast(top + 0.12f) }
        Text("Edge behavior", color = Color.White, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BorderEdgeMode.entries.forEach { mode ->
                ElevatedButton(
                    onClick = { edgeMode = mode },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = if (edgeMode == mode) BoothPalette.mint else Color(0xFF263035)),
                ) { Text(if (mode == BorderEdgeMode.STRETCH) "Stretch edges" else "Repeat edges", fontSize = 11.sp) }
            }
        }
        Button(
            onClick = {
                onSave(
                    template.copy(
                        name = name.trim().ifBlank { template.name },
                        guides = BorderSliceGuides(left, right, top, bottom).sanitized(),
                        edgeMode = edgeMode,
                        version = template.version + 1,
                    ),
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save responsive layout") }
    }
}

@Composable
private fun GuideSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label · ${(value * 100).toInt()}%", color = Color(0xFFD6E6DF), fontSize = 11.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BorderThumbnail(template: ResponsiveBorderTemplate, modifier: Modifier) {
    // Older duplicated templates did not persist thumbnailPath, but their
    // document still contains the full-page artwork element. Use that asset
    // as a safe fallback so existing copies do not appear blank.
    val previewPath = template.thumbnailPath ?: template.document?.elements
        ?.firstOrNull { it.type == com.eventphotobooth.app.data.ReceiptElementType.IMAGE && it.assetUri.isNotBlank() }
        ?.assetUri
    val customBitmap: Bitmap? = remember(previewPath) {
        previewPath?.let(::File)?.takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (customBitmap != null) {
            Image(customBitmap.asImageBitmap(), contentDescription = "${template.name} border preview", modifier = Modifier.fillMaxSize())
        } else {
            val color = when (template.builtInStyle) {
                BuiltInBorderStyle.NONE -> BoothPalette.paperLight
                BuiltInBorderStyle.HEARTS -> BoothPalette.oxblood
                BuiltInBorderStyle.FILM -> Color(0xFF202020)
                BuiltInBorderStyle.MIDNIGHT -> BoothPalette.purple
                else -> BoothPalette.forest
            }
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(color = color, style = Stroke(width = 7f))
                if (template.builtInStyle == BuiltInBorderStyle.FILM) {
                    drawRect(color = color, size = androidx.compose.ui.geometry.Size(size.width, 14f))
                    drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 14f), size = androidx.compose.ui.geometry.Size(size.width, 14f))
                }
            }
        }
    }
}

private val templateFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color(0xFFD6E6DF),
        focusedBorderColor = Color(0xFFB4F5D1),
        unfocusedBorderColor = Color(0xFF9AA8A2),
        cursorColor = Color(0xFFB4F5D1),
    )
