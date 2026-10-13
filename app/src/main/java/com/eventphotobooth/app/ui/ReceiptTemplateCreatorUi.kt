package com.eventphotobooth.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventphotobooth.app.data.LocalReceiptBorderTemplateStore
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.ReceiptElement
import com.eventphotobooth.app.data.ReceiptElementType
import com.eventphotobooth.app.data.ReceiptTemplate
import com.eventphotobooth.app.data.ReceiptTemplateDocument
import com.eventphotobooth.app.data.ReceiptTextAlignment
import com.eventphotobooth.app.data.ResponsiveBorderTemplate
import com.eventphotobooth.app.data.photoRegions
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only catalog for the four artwork files shipped with the kiosk. */
@Composable
internal fun ReceiptTemplateCatalogPanel(
    store: LocalReceiptBorderTemplateStore,
    templates: List<ResponsiveBorderTemplate>,
    onTemplatesChanged: (List<ResponsiveBorderTemplate>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var uploadLayout by remember { mutableStateOf<LayoutId?>(null) }
    var uploadName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val layout = uploadLayout ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    store.importBorder(
                        uri = uri,
                        requestedName = uploadName.trim().ifBlank { "Custom ${layout.photoCount}-photo template" },
                        layoutId = layout,
                    )
                }
            }.onSuccess { imported ->
                onTemplatesChanged(store.load())
                uploadName = ""
                message = "${imported.name} was added to the ${layout.photoCount}-photo templates."
            }.onFailure { error ->
                message = error.message ?: "The selected template could not be opened."
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(BoothPalette.chrome)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("PRINT TEMPLATES", color = Color.White, fontSize = 18.sp)
                Text(
                    "Manage artwork by photo layout. Upload designs made from the matching guide JPEG, choose the active design, or remove an uploaded design.",
                    color = Color(0xFFD6E6DF),
                    fontSize = 12.sp,
                )
                Text(
                    "The canvas editor is intentionally removed. The app only inserts photos into the blank photo area and preserves the supplied print geometry.",
                    color = BoothPalette.mint,
                    fontSize = 12.sp,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = uploadName,
                    onValueChange = { uploadName = it.take(48) },
                    label = { Text("Uploaded template name (optional)") },
                    placeholder = { Text("e.g. Summer 3-photo design") },
                    singleLine = true,
                    colors = templateEditorFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                ElevatedButton(
                    onClick = {
                        onTemplatesChanged(store.restoreBundledTemplates())
                        message = "Bundled templates restored for every layout."
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Restore bundled templates") }
                message?.let { Text(it, color = BoothPalette.mint, fontSize = 12.sp) }
            }
        }
        LayoutId.entries.forEach { layout ->
            val layoutTemplates = templates.filter { it.layoutId == layout }
            Card(colors = CardDefaults.cardColors(BoothPalette.chrome)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${layout.photoCount} PHOTO${if (layout.photoCount == 1) "" else "S"}", color = Color.White, fontSize = 16.sp)
                            Text("${layoutTemplates.count { it.enabled }} active · ${layoutTemplates.size} available", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                uploadLayout = layout
                                uploadLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                            modifier = Modifier.height(48.dp),
                        ) { Text("Upload template") }
                    }
                    if (layoutTemplates.isEmpty()) {
                        Text("No design uploaded for this layout.", color = Color(0xFFFFB4AB), fontSize = 12.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(layoutTemplates, key = { it.id }) { template ->
                                TemplateManagementCard(
                                    template = template,
                                    canDelete = layoutTemplates.count { it.enabled } > 1 && !template.isDefault,
                                    onSelect = {
                                        onTemplatesChanged(store.setDefaultForLayout(template.id, layout))
                                        message = "${template.name} is now active for the ${layout.photoCount}-photo layout."
                                    },
                                    onDelete = {
                                        val updated = if (template.source == com.eventphotobooth.app.data.ReceiptBorderSource.OPERATOR_CUSTOM) {
                                            store.removeCustom(template.id)
                                        } else {
                                            store.update(template.copy(enabled = false, isDefault = false))
                                        }
                                        onTemplatesChanged(updated)
                                        message = if (template.source == com.eventphotobooth.app.data.ReceiptBorderSource.OPERATOR_CUSTOM) {
                                            "${template.name} was deleted from this tablet."
                                        } else {
                                            "${template.name} was disabled. Use Restore bundled templates to bring it back."
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateManagementCard(
    template: ResponsiveBorderTemplate,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.width(244.dp),
        colors = CardDefaults.cardColors(if (template.isDefault && template.enabled) Color(0xFF213D34) else BoothPalette.chromeRaised),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FixedTemplateThumbnail(template, Modifier.fillMaxWidth().height(172.dp))
            Text(template.name, color = Color.White, fontSize = 13.sp, maxLines = 2)
            Text(
                when {
                    template.isDefault && template.enabled -> "SELECTED · ${if (template.source == com.eventphotobooth.app.data.ReceiptBorderSource.BUNDLED) "BUILT-IN" else "UPLOADED"}"
                    template.enabled -> if (template.source == com.eventphotobooth.app.data.ReceiptBorderSource.BUNDLED) "BUILT-IN" else "UPLOADED"
                    else -> "DISABLED"
                },
                color = if (template.enabled) BoothPalette.mint else Color(0xFFFFB4AB),
                fontSize = 10.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onSelect,
                    enabled = template.enabled && !template.isDefault,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest),
                ) { Text(if (template.isDefault) "Selected" else "Select", fontSize = 10.sp) }
                ElevatedButton(
                    onClick = onDelete,
                    enabled = canDelete && template.enabled,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text(if (template.source == com.eventphotobooth.app.data.ReceiptBorderSource.OPERATOR_CUSTOM) "Delete" else "Disable", fontSize = 10.sp) }
            }
            if (template.isDefault && template.enabled) {
                Text("Select another design before removing this one.", color = Color(0xFFD6E6DF), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun FixedTemplateCard(template: ResponsiveBorderTemplate) {
    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FixedTemplateThumbnail(template, Modifier.width(104.dp).height(150.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(template.name, color = Color.White, fontSize = 16.sp)
                Text("${template.layoutId?.photoCount ?: 0}-photo layout", color = BoothPalette.mint, fontSize = 12.sp)
                Text("FIXED ARTWORK · PRINT SAFE", color = Color(0xFFD6E6DF), fontSize = 10.sp)
                Text(
                    "The app inserts captured photos into the blank photo area and prints this artwork at the layout's correct length.",
                    color = Color(0xFFD6E6DF),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FixedTemplateThumbnail(template: ResponsiveBorderTemplate, modifier: Modifier) {
    val path = template.thumbnailPath
    val bitmap = remember(path) {
        path?.let(::File)?.takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "${template.name} print template",
            modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color.White),
        )
    } else {
        Card(modifier = modifier, colors = CardDefaults.cardColors(Color.White)) {
            Text("Preview unavailable", color = Color.DarkGray, modifier = Modifier.padding(8.dp), fontSize = 10.sp)
        }
    }
}

@Composable
internal fun LegacyReceiptTemplateCreatorPanel(
    store: LocalReceiptBorderTemplateStore,
    templates: List<ResponsiveBorderTemplate>,
    configurationReceipt: ReceiptTemplate,
    onTemplatesChanged: (List<ResponsiveBorderTemplate>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var borderName by remember { mutableStateOf("") }
    var targetLayout by remember { mutableStateOf<LayoutId?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val selected = templates.firstOrNull { it.id == selectedId }
    val borderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val imported = store.importBorder(uri, borderName, targetLayout)
                    store.duplicateAsDocument(imported, imported.name, configurationReceipt)
                }
            }.onSuccess { editable ->
                    onTemplatesChanged(store.load())
                    selectedId = editable.id
                    borderName = ""
                    message = "${editable.name} uploaded and opened in the editor."
                }
                .onFailure { error -> message = error.message ?: "The border could not be uploaded." }
        }
    }

    LaunchedEffect(templates) {
        if (selectedId == null || templates.none { it.id == selectedId }) {
            selectedId = templates.firstOrNull { it.document != null }?.id ?: templates.firstOrNull()?.id
        }
    }

    fun createNew() {
        val created = store.createDocumentTemplate(name, configurationReceipt, targetLayout)
        onTemplatesChanged(store.load())
        selectedId = created.id
        name = ""
        message = "Draft created. Add your text, logo, images, and spacing, then save it."
    }

    fun duplicate(template: ResponsiveBorderTemplate) {
        val copy = store.duplicateAsDocument(template, name.ifBlank { "${template.name} copy" }, configurationReceipt)
        onTemplatesChanged(store.load())
        selectedId = copy.id
        name = ""
        message = "A new editable copy is ready."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RECEIPT TEMPLATE BUILDER", color = Color.White, fontSize = 18.sp)
                Text(
                    "Create the complete 80 mm receipt. Every object on the canvas is part of the saved template—there is no hidden Classic layer underneath.",
                    color = Color(0xFFD6E6DF),
                    fontSize = 12.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(48) },
                        label = { Text("New template name") },
                        placeholder = { Text("e.g. Your Moments") },
                        singleLine = true,
                        colors = templateEditorFieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::createNew, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest), modifier = Modifier.height(54.dp)) {
                        Text("Create template")
                    }
                }
                Text("UPLOAD BORDER ARTWORK", color = BoothPalette.mint, fontSize = 12.sp)
                Text("PNG, WebP, and JPG are supported. Transparent PNG/WebP works best for overlays; duplicate JPG artwork before editing the full receipt.", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                Text("Template layout", color = Color.White, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ElevatedButton(
                        onClick = { targetLayout = null },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = if (targetLayout == null) BoothPalette.mint else Color(0xFF263035)),
                    ) { Text("All", fontSize = 10.sp) }
                    LayoutId.entries.forEach { layout ->
                        ElevatedButton(
                            onClick = { targetLayout = layout },
                            colors = ButtonDefaults.elevatedButtonColors(containerColor = if (targetLayout == layout) BoothPalette.mint else Color(0xFF263035)),
                        ) { Text(layout.photoCount.toString(), fontSize = 10.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = borderName,
                        onValueChange = { borderName = it.take(48) },
                        label = { Text("Border name (optional)") },
                        placeholder = { Text("e.g. Summer frame") },
                        singleLine = true,
                        colors = templateEditorFieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { borderPicker.launch(arrayOf("image/*")) }, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest), modifier = Modifier.height(54.dp)) {
                        Text("Upload border")
                    }
                }
                message?.let { Text(it, color = BoothPalette.mint, fontSize = 12.sp) }
            }
        }

        Text("YOUR TEMPLATES", color = Color.White, fontSize = 16.sp)
        if (templates.isEmpty()) {
            Text("No templates yet. Create one to start designing.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates, key = { it.id }) { template ->
                    TemplateLibraryCard(
                        template = template,
                        selected = template.id == selectedId,
                        onClick = { selectedId = template.id },
                    )
                }
            }
        }

        selected?.let { template ->
            if (template.document == null) {
                Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${template.name} is ${if (template.source.name == "OPERATOR_CUSTOM") "an uploaded border" else "a built-in border"}", color = Color.White, fontSize = 16.sp)
                        Text("Open it as an artwork-first template with a reserved photo area. Add text or logos only when you want them; the original stays protected.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { duplicate(template) }, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest)) { Text("Duplicate and edit") }
                            ElevatedButton(onClick = { onTemplatesChanged(store.setDefault(template.id)) }) { Text(if (template.isDefault) "Default template" else "Use as default") }
                        }
                    }
                }
            } else {
                ReceiptDocumentEditor(
                    template = template,
                    onSave = { updated ->
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { store.update(updated) }
                            onTemplatesChanged(saved)
                            selectedId = updated.id
                            message = "${updated.name} saved. New sessions will use the new version."
                        }
                    },
                    onDuplicate = { duplicate(template) },
                    onSetDefault = { onTemplatesChanged(store.setDefault(template.id)) },
                    onDelete = {
                        onTemplatesChanged(store.removeCustom(template.id))
                        selectedId = null
                        message = "${template.name} removed from this tablet."
                    },
                )
            }
        }
    }
}

@Composable
private fun TemplateLibraryCard(template: ResponsiveBorderTemplate, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(170.dp).clickable(onClick = onClick).border(if (selected) 3.dp else 1.dp, if (selected) BoothPalette.mint else Color(0xFF42504C), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(Color(0xFF172225)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TemplateMiniPreview(template, Modifier.fillMaxWidth().height(150.dp))
            Text(template.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
            Text(template.layoutId?.let { "${it.photoCount}-photo only" } ?: "All layouts", color = Color(0xFFD6E6DF), fontSize = 10.sp)
            Text(
                when {
                    template.document != null -> "Editable template"
                    template.source.name == "OPERATOR_CUSTOM" -> "Uploaded border"
                    else -> "Built-in border"
                },
                color = Color(0xFFD6E6DF),
                fontSize = 10.sp,
            )
            Text(if (template.isDefault) "DEFAULT" else if (template.enabled) "AVAILABLE" else "DISABLED", color = if (template.enabled) BoothPalette.mint else Color(0xFFFFB4AB), fontSize = 9.sp)
        }
    }
}

@Composable
private fun TemplateMiniPreview(template: ResponsiveBorderTemplate, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        val path = template.thumbnailPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "${template.name} preview", modifier = Modifier.fillMaxWidth()) }
        } else {
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                drawRect(color = Color(0xFFF6F0E6))
                drawRect(color = BoothPalette.forest, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                drawLine(BoothPalette.inkMuted, androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .45f), androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .45f), 2f)
                drawLine(BoothPalette.inkMuted, androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .6f), androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .6f), 2f)
            }
        }
    }
}

@Composable
private fun ReceiptDocumentEditor(
    template: ResponsiveBorderTemplate,
    onSave: (ResponsiveBorderTemplate) -> Unit,
    onDuplicate: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    var document by remember(template.id, template.document?.version) { mutableStateOf(template.document!!) }
    var selectedElementId by remember(template.id, template.document?.version) { mutableStateOf(document.elements.firstOrNull()?.id) }
    var pendingAssetType by remember { mutableStateOf(ReceiptElementType.IMAGE) }
    var previewLayout by remember { mutableStateOf(LayoutId.ONE_PHOTO) }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val element = ReceiptElement(
            type = pendingAssetType,
            x = 170f,
            y = 120f,
            width = 236f,
            height = 150f,
            assetUri = uri.toString(),
            text = if (pendingAssetType == ReceiptElementType.LOGO) "Logo" else "Image",
        )
        document = document.withElement(element)
        selectedElementId = element.id
    }
    val selected = document.elements.firstOrNull { it.id == selectedElementId }

    Card(colors = CardDefaults.cardColors(Color(0xFF172225))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EDIT · ${document.name}", color = Color.White, fontSize = 17.sp)
                    Text("Canvas: 576 dots wide · print-safe 80 mm ratio", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                }
                ElevatedButton(onClick = onDuplicate) { Text("Duplicate") }
                ElevatedButton(onClick = onSetDefault, enabled = !template.isDefault) { Text(if (template.isDefault) "Default" else "Set default") }
                Button(onClick = { onSave(template.copy(name = document.name, document = document.copy(version = document.version + 1, name = document.name))) }, colors = ButtonDefaults.buttonColors(containerColor = BoothPalette.forest)) { Text("Save") }
            }
            OutlinedTextField(
                value = document.name,
                onValueChange = { value -> document = document.copy(name = value.take(48)) },
                label = { Text("Template name") },
                singleLine = true,
                colors = templateEditorFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(colors = CardDefaults.cardColors(Color(0xFF21322F))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("PHOTO STRIP PREVIEW", color = BoothPalette.mint, fontSize = 12.sp)
                    Text("Photo slots follow the selected guest layout. This preview does not add four editable boxes to your template.", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LayoutId.entries.forEach { layout ->
                            ElevatedButton(
                                onClick = { previewLayout = layout },
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.elevatedButtonColors(containerColor = if (previewLayout == layout) BoothPalette.mint else Color(0xFF263035)),
                            ) { Text("${layout.photoCount} photo${if (layout.photoCount == 1) "" else "s"}", fontSize = 9.sp) }
                        }
                    }
                    Text("Side border space · ${document.photoSideInset.toInt()} dots", color = Color(0xFFD6E6DF), fontSize = 11.sp)
                    Slider(
                        value = document.photoSideInset,
                        onValueChange = { document = document.copy(photoSideInset = it) },
                        valueRange = 0f..96f,
                        steps = 7,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                BoxWithConstraints(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    val canvasWidth = minOf(maxWidth, 340.dp)
                    ReceiptDocumentCanvas(
                        document = document,
                        layoutId = previewLayout,
                        selectedElementId = selectedElementId,
                        onSelect = { selectedElementId = it },
                        onTransform = { id, dx, dy, zoom ->
                            document.elements.firstOrNull { it.id == id }?.let { current ->
                                if (!current.locked) {
                                    val widthChange = current.width * (zoom - 1f)
                                    val heightChange = current.height * (zoom - 1f)
                                    val newWidth = (current.width + widthChange).coerceIn(12f, document.designWidth)
                                    val newHeight = (current.height + heightChange).coerceIn(8f, document.designHeight)
                                    val updated = current.copy(
                                        x = (current.x + dx - (newWidth - current.width) / 2f).coerceIn(0f, (document.designWidth - newWidth).coerceAtLeast(0f)),
                                        y = (current.y + dy - (newHeight - current.height) / 2f).coerceIn(0f, (document.designHeight - newHeight).coerceAtLeast(0f)),
                                        width = newWidth,
                                        height = newHeight,
                                    )
                                    document = document.replaceElement(updated)
                                }
                            }
                        },
                        modifier = Modifier.width(canvasWidth).height(canvasWidth * (document.designHeight / document.designWidth)),
                    )
                }

                // OperatorSettingsScreen already owns the page-level vertical scroll.
                // Keeping a second verticalScroll here gives this column an unbounded
                // height from Compose and crashes the tablet when the editor opens.
                // Let the outer screen scroll the complete editor as one document so
                // the canvas and inspector keep a stable, shared coordinate system.
                Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("ADD TO CANVAS", color = BoothPalette.mint, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorAction("Text") { addElement(document, ReceiptElementType.TEXT, selectedElementId) { document = it; selectedElementId = it.elements.lastOrNull()?.id } }
                        EditorAction("Line") { addElement(document, ReceiptElementType.LINE, selectedElementId) { document = it; selectedElementId = it.elements.lastOrNull()?.id } }
                        EditorAction("Spacer") { addElement(document, ReceiptElementType.SPACER, selectedElementId) { document = it; selectedElementId = it.elements.lastOrNull()?.id } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorAction("Barcode") { addElement(document, ReceiptElementType.BARCODE, selectedElementId) { document = it; selectedElementId = it.elements.lastOrNull()?.id } }
                        EditorAction("Upload logo") { pendingAssetType = ReceiptElementType.LOGO; assetPicker.launch(arrayOf("image/*")) }
                        EditorAction("Image") { pendingAssetType = ReceiptElementType.IMAGE; assetPicker.launch(arrayOf("image/*")) }
                    }
                    selected?.let { element ->
                        ElementInspector(
                            element = element,
                            document = document,
                            onChange = { document = document.replaceElement(it) },
                            onDelete = { document = document.removeElement(element.id); selectedElementId = document.elements.firstOrNull()?.id },
                        )
                    } ?: Text("Tap an object on the receipt to edit it.", color = Color(0xFFD6E6DF), fontSize = 12.sp)
                    Text("LAYERS", color = BoothPalette.mint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    document.elements.sortedByDescending { it.zIndex }.forEach { element ->
                        val layerModifier = Modifier.fillMaxWidth()
                            .then(if (element.type == ReceiptElementType.PHOTO_SLOT) Modifier else Modifier.clickable { selectedElementId = element.id })
                            .padding(vertical = 3.dp)
                        Row(layerModifier, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (element.id == selectedElementId) "●" else "○", color = if (element.id == selectedElementId) BoothPalette.mint else Color(0xFF9AA8A2), modifier = Modifier.width(22.dp))
                            Text(elementLabel(element), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(if (element.locked) "LOCKED" else "EDIT", color = if (element.locked) Color(0xFF9AA8A2) else Color(0xFFD6E6DF), fontSize = 9.sp)
                        }
                    }
                    ElevatedButton(onClick = onDelete, enabled = template.source.name == "OPERATOR_CUSTOM", colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF5C2925)), modifier = Modifier.fillMaxWidth()) { Text("Delete template") }
                }
            }
        }
    }
}

private fun addElement(document: ReceiptTemplateDocument, type: ReceiptElementType, selectedId: String?, update: (ReceiptTemplateDocument) -> Unit) {
    val selected = document.elements.firstOrNull { it.id == selectedId }
    val element = ReceiptElement(
        type = type,
        x = selected?.x ?: 90f,
        y = ((selected?.y ?: 220f) + (selected?.height ?: 42f) + 12f).coerceAtMost(document.designHeight - 44f),
        width = selected?.width ?: 396f,
        height = when (type) {
            ReceiptElementType.LINE -> 2f
            ReceiptElementType.SPACER -> 90f
            ReceiptElementType.PHOTO_SLOT -> 220f
            ReceiptElementType.BARCODE -> 58f
            else -> 42f
        },
        text = when (type) {
            ReceiptElementType.TEXT -> "New text"
            ReceiptElementType.PHOTO_SLOT -> "Photo ${document.elements.count { it.type == ReceiptElementType.PHOTO_SLOT } + 1}"
            ReceiptElementType.BARCODE -> "Receipt barcode"
            ReceiptElementType.SPACER -> "Writing space"
            else -> ""
        },
        fontSize = if (type == ReceiptElementType.TEXT) 28f else 26f,
        photoIndex = document.elements.count { it.type == ReceiptElementType.PHOTO_SLOT },
    )
    update(document.withElement(element))
}

@Composable
private fun EditorAction(label: String, onClick: () -> Unit) {
    ElevatedButton(onClick = onClick, modifier = Modifier.height(42.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp)) { Text(label, fontSize = 10.sp) }
}

@Composable
private fun ElementInspector(element: ReceiptElement, document: ReceiptTemplateDocument, onChange: (ReceiptElement) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SELECTED · ${elementLabel(element)}", color = Color.White, fontSize = 13.sp)
        if (element.type == ReceiptElementType.TEXT) {
            OutlinedTextField(element.text, { onChange(element.copy(text = it.take(120))) }, label = { Text("Text or token") }, colors = templateEditorFieldColors, modifier = Modifier.fillMaxWidth())
            Text("Tokens: {venue}  {date}  {time}  {total}  {receipt_number}", color = Color(0xFF9AA8A2), fontSize = 10.sp)
            Text("Font size · ${element.fontSize.toInt()} px", color = Color(0xFFD6E6DF), fontSize = 11.sp)
            Slider(value = element.fontSize, onValueChange = { onChange(element.copy(fontSize = it)) }, valueRange = 8f..120f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReceiptTextAlignment.entries.forEach { alignment ->
                    ElevatedButton(onClick = { onChange(element.copy(alignment = alignment)) }, colors = ButtonDefaults.elevatedButtonColors(containerColor = if (element.alignment == alignment) BoothPalette.mint else Color(0xFF263035)), modifier = Modifier.weight(1f)) { Text(alignment.name, fontSize = 9.sp) }
                }
            }
        }
        Text("Width · ${element.width.toInt()} px", color = Color(0xFFD6E6DF), fontSize = 11.sp)
        Slider(value = element.width, onValueChange = { onChange(element.copy(width = it.coerceIn(12f, document.designWidth - element.x))) }, valueRange = 12f..document.designWidth)
        Text("Height · ${element.height.toInt()} px", color = Color(0xFFD6E6DF), fontSize = 11.sp)
        Slider(value = element.height, onValueChange = { onChange(element.copy(height = it.coerceIn(8f, document.designHeight - element.y))) }, valueRange = 8f..document.designHeight)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElevatedButton(onClick = { onChange(element.copy(locked = !element.locked)) }, modifier = Modifier.weight(1f)) { Text(if (element.locked) "Unlock" else "Lock", fontSize = 11.sp) }
            ElevatedButton(onClick = onDelete, enabled = !element.locked, modifier = Modifier.weight(1f), colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF5C2925))) { Text("Delete", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ReceiptDocumentCanvas(
    document: ReceiptTemplateDocument,
    layoutId: LayoutId,
    selectedElementId: String?,
    onSelect: (String?) -> Unit,
    onTransform: (String, Float, Float, Float) -> Unit,
    modifier: Modifier,
) {
    val assetBitmaps = remember(document.elements.map { it.id to it.assetUri }) {
        document.elements
            .filter { it.type == ReceiptElementType.IMAGE || it.type == ReceiptElementType.LOGO }
            .mapNotNull { element ->
                element.assetUri.takeIf { it.isNotBlank() && !it.startsWith("content:") }
                    ?.let { path -> BitmapFactory.decodeFile(path)?.let { bitmap -> element.id to bitmap } }
            }
            .toMap()
    }
    Canvas(
        modifier = modifier
            .background(Color.White)
            .border(2.dp, BoothPalette.mint)
            .pointerInput(document, selectedElementId) {
                var draggingId: String? = null
                detectDragGestures(
                    onDragStart = { position ->
                        draggingId = findElement(document, position.x, position.y, size.width.toFloat())
                        onSelect(draggingId)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        draggingId?.let { id ->
                            onTransform(id, amount.x / (size.width / document.designWidth), amount.y / (size.width / document.designWidth), 1f)
                        }
                    },
                    onDragEnd = { draggingId = null },
                    onDragCancel = { draggingId = null },
                )
            }
            .pointerInput(document, selectedElementId) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val id = selectedElementId ?: findElement(document, centroid.x, centroid.y, size.width.toFloat())
                    id?.let { onTransform(it, pan.x / (size.width / document.designWidth), pan.y / (size.width / document.designWidth), zoom) }
                }
            },
    ) {
        val scale = size.width / document.designWidth
        drawRect(Color(document.backgroundColor), size = size)
        val photoRegions = document.photoRegions(layoutId)
        document.elements.sortedBy { it.zIndex }.forEach { element ->
            if (!element.visible) return@forEach
            if (element.type == ReceiptElementType.PHOTO_SLOT) return@forEach
            val left = element.x * scale
            val top = element.y * scale
            val width = element.width * scale
            val height = element.height * scale
            when (element.type) {
                ReceiptElementType.LINE -> drawLine(Color(element.color), androidx.compose.ui.geometry.Offset(left, top + height / 2f), androidx.compose.ui.geometry.Offset(left + width, top + height / 2f), max(1f, height))
                ReceiptElementType.SPACER -> drawRect(Color(0x22365C52), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height))
                ReceiptElementType.PHOTO_SLOT -> {
                    drawRect(Color(0xFFE7EEE9), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height))
                    drawRect(Color(0xFF485A49), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
                ReceiptElementType.BARCODE -> drawRect(Color.Black, androidx.compose.ui.geometry.Offset(left, top + height * .2f), androidx.compose.ui.geometry.Size(width, height * .6f))
                ReceiptElementType.LOGO,
                ReceiptElementType.IMAGE -> {
                    assetBitmaps[element.id]?.let { bitmap ->
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            bitmap,
                            null,
                            android.graphics.RectF(left, top, left + width, top + height),
                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                        )
                    } ?: drawRect(Color(0x22365C52), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height))
                }
                ReceiptElementType.TEXT -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = element.color.toInt()
                        textSize = element.fontSize * scale
                        textAlign = when (element.alignment) {
                            ReceiptTextAlignment.LEFT -> Paint.Align.LEFT
                            ReceiptTextAlignment.CENTER -> Paint.Align.CENTER
                            ReceiptTextAlignment.RIGHT -> Paint.Align.RIGHT
                        }
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, if (element.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    }
                    val x = when (element.alignment) {
                        ReceiptTextAlignment.LEFT -> left
                        ReceiptTextAlignment.CENTER -> left + width / 2f
                        ReceiptTextAlignment.RIGHT -> left + width
                    }
                    drawContext.canvas.nativeCanvas.drawText(element.text.take(28), x, top + height / 2f + paint.textSize / 3f, paint)
                }
            }
            if (element.id == selectedElementId) {
                drawRect(Color(0xFF0F766E), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }
        }
        photoRegions.forEachIndexed { index, region ->
            val left = region.x * scale
            val top = region.y * scale
            val width = region.width * scale
            val height = region.height * scale
            drawRect(Color(0xFFE7EEE9), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height))
            drawRect(Color(0xFF485A49), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF365C52.toInt()
                textSize = (if (photoRegions.size == 1) 22f else 16f) * scale
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText(
                if (photoRegions.size == 1) "PHOTO STRIP AREA" else "PHOTO ${index + 1}",
                left + width / 2f,
                top + height / 2f - (paint.ascent() + paint.descent()) / 2f,
                paint,
            )
        }
    }
}

private fun findElement(document: ReceiptTemplateDocument, x: Float, y: Float, canvasWidth: Float): String? {
    val scale = canvasWidth / document.designWidth
    val designX = x / scale
    val designY = y / scale
    return document.elements.asReversed().firstOrNull { element ->
        element.type != ReceiptElementType.PHOTO_SLOT &&
        element.visible && designX in element.x - 8f..(element.x + element.width + 8f) && designY in element.y - 8f..(element.y + element.height + 8f)
    }?.id
}

private fun elementLabel(element: ReceiptElement): String = when (element.type) {
    ReceiptElementType.TEXT -> element.text.ifBlank { "Text" }.take(24)
    ReceiptElementType.LOGO -> "Logo"
    ReceiptElementType.IMAGE -> "Image"
    ReceiptElementType.LINE -> "Divider line"
    ReceiptElementType.SPACER -> "Blank writing space"
    ReceiptElementType.PHOTO_SLOT -> "Photo area · auto layout"
    ReceiptElementType.BARCODE -> "Barcode"
}

private val templateEditorFieldColors
    @Composable get() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color(0xFFD6E6DF),
        focusedBorderColor = Color(0xFFB4F5D1),
        unfocusedBorderColor = Color(0xFF9AA8A2),
        cursorColor = Color(0xFFB4F5D1),
    )
