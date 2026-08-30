package com.auracam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracam.camera.domain.CapturedMedia
import com.auracam.processing.ComputationalPipeline
import com.auracam.ui.theme.*
import com.auracam.ui.util.rememberMediaImage

@Composable
fun GalleryPreviewSheet(
    media: List<CapturedMedia>,
    initialIndex: Int,
    watermarkEnabled: Boolean,
    onShare: ((CapturedMedia) -> Unit)? = null,
    onDelete: (suspend (CapturedMedia) -> Unit)? = null,
    onLoadExif: (suspend (CapturedMedia) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (media.isEmpty()) {
        EmptyGallery(onClose = onClose, modifier = modifier)
        return
    }

    var chromeVisible by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CapturedMedia?>(null) }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, media.lastIndex),
        pageCount = { media.size }
    )
    val currentIndex = pagerState.currentPage.coerceIn(0, media.lastIndex)
    val current = media[currentIndex]

    val railState = rememberLazyListState()
    LaunchedEffect(currentIndex) { railState.animateScrollToItem(currentIndex) }

    LaunchedEffect(showDetails, current.id) {
        if (showDetails) onLoadExif?.invoke(current)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPitchBlack)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp
        ) { page ->
            ZoomableMedia(
                media = media[page],
                onToggleChrome = { chromeVisible = !chromeVisible },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopChrome(
                media = current,
                position = currentIndex + 1,
                total = media.size,
                detailsOpen = showDetails,
                onClose = onClose,
                onShare = onShare?.let { share -> { share(current) } },
                onDelete = onDelete?.let { { pendingDelete = current } },
                onToggleDetails = { showDetails = !showDetails }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && !showDetails,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomChrome(
                media = media,
                currentIndex = currentIndex,
                railState = railState,
                watermark = if (watermarkEnabled) watermarkTextFor(current) else null,
                onSelect = { pagerState.requestScrollToPage(it) }
            )
        }

        AnimatedVisibility(
            visible = showDetails,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            DetailsSheet(
                media = current,
                onDismiss = { showDetails = false }
            )
        }
    }

    val target = pendingDelete
    if (target != null && onDelete != null) {
        DeleteDialog(
            media = target,
            onConfirm = onDelete,
            onFinished = { pendingDelete = null }
        )
    }
}

@Composable
private fun TopChrome(
    media: CapturedMedia,
    position: Int,
    total: Int,
    detailsOpen: Boolean,
    onClose: () -> Unit,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onToggleDetails: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.Default.Close, "Close", onClick = onClose)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = media.fileName,
                    style = AuraCamTheme.typography.titleSmall,
                    color = PixelTextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$position / $total  •  ${media.exif.timestamp}",
                    style = AuraCamTheme.cameraTypography.hudMetric,
                    color = PixelTextSecondary,
                    maxLines = 1
                )
            }

            if (onShare != null) {
                GlassIconButton(Icons.Default.Share, "Share", onClick = onShare)
                Spacer(Modifier.width(6.dp))
            }
            if (onDelete != null) {
                GlassIconButton(Icons.Default.Delete, "Delete", onClick = onDelete)
                Spacer(Modifier.width(6.dp))
            }
            GlassIconButton(
                icon = Icons.Default.Info,
                contentDescription = "Photo details",
                active = detailsOpen,
                onClick = onToggleDetails
            )
        }
    }
}

@Composable
private fun BottomChrome(
    media: List<CapturedMedia>,
    currentIndex: Int,
    railState: androidx.compose.foundation.lazy.LazyListState,
    watermark: String?,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (watermark != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .pixelGlass(
                        shape = CircleShape,
                        backgroundColor = PixelGlassScrimHeavy,
                        borderColor = PixelGlassBorder
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = watermark,
                    style = AuraCamTheme.cameraTypography.hudMetric,
                    color = PixelTextPrimary
                )
            }
        }

        LazyRow(
            state = railState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(media, key = { _, item -> item.id }) { index, item ->
                RailThumbnail(
                    media = item,
                    selected = index == currentIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun RailThumbnail(
    media: CapturedMedia,
    selected: Boolean,
    onClick: () -> Unit
) {
    val thumbnail = rememberMediaImage(media.uri, maxDimension = 256)
    val size = if (selected) 62.dp else 50.dp

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(PixelSurfaceContainerHigh)
            .then(
                if (selected) {
                    Modifier.border(2.dp, PixelYellowAccent, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = media.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (selected) 1f else 0.55f }
            )
        }

        if (isVideo(media)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = PixelTextWhite,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ZoomableMedia(
    media: CapturedMedia,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val image = rememberMediaImage(media.uri)
    var scale by remember(media.id) { mutableStateOf(1f) }
    var offsetX by remember(media.id) { mutableStateOf(0f) }
    var offsetY by remember(media.id) { mutableStateOf(0f) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            CircularProgressIndicator(color = PixelYellowAccent)
            return@Box
        }

        Image(
            bitmap = image,
            contentDescription = media.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(media.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(media.id) {
                    detectTapGestures(
                        onTap = { onToggleChrome() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        )

        if (isVideo(media)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = PixelTextWhite,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailsSheet(media: CapturedMedia, onDismiss: () -> Unit) {
    val rows = detailRows(media)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(PixelSurfaceContainerLow)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PixelTextMuted)
            )
        }

        Text(
            text = "Details",
            style = AuraCamTheme.typography.titleMedium,
            color = PixelTextWhite,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            rows.forEach { (title, entries) ->
                if (entries.isEmpty()) return@forEach
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PixelSurfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = title,
                        style = AuraCamTheme.cameraTypography.pillLabel,
                        color = PixelYellowAccent,
                        fontWeight = FontWeight.Bold
                    )
                    entries.forEach { (label, value) -> DetailRow(label, value) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = AuraCamTheme.typography.bodySmall,
            color = PixelTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = AuraCamTheme.cameraTypography.hudMetric,
            color = PixelTextWhite,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun DeleteDialog(
    media: CapturedMedia,
    onConfirm: suspend (CapturedMedia) -> Unit,
    onFinished: () -> Unit
) {
    var deleting by remember(media.id) { mutableStateOf(false) }

    LaunchedEffect(media.id, deleting) {
        if (deleting) {
            onConfirm(media)
            onFinished()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!deleting) onFinished() },
        containerColor = PixelSurfaceContainerHigh,
        titleContentColor = PixelTextWhite,
        textContentColor = PixelTextSecondary,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Move to trash?") },
        text = { Text("${media.fileName} will be permanently deleted from this device.") },
        confirmButton = {
            TextButton(onClick = { deleting = true }, enabled = !deleting) {
                Text("Delete", color = PixelRecordRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onFinished, enabled = !deleting) {
                Text("Cancel", color = PixelTextSecondary)
            }
        }
    )
}

@Composable
private fun EmptyGallery(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPitchBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            GlassIconButton(Icons.Default.Close, "Close", onClick = onClose)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = PixelTextMuted,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No photos yet",
                style = AuraCamTheme.typography.titleMedium,
                color = PixelTextSecondary
            )
            Text(
                text = "Shots you take appear here.",
                style = AuraCamTheme.typography.bodySmall,
                color = PixelTextMuted
            )
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(
                if (active) {
                    Modifier.clip(CircleShape).background(PixelYellowAccent)
                } else {
                    Modifier.pixelGlass(
                        shape = CircleShape,
                        backgroundColor = PixelGlassScrimHeavy,
                        borderColor = PixelGlassBorder
                    )
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) PixelPitchBlack else PixelTextWhite,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun isVideo(media: CapturedMedia) =
    media.fileName.endsWith(".mp4", ignoreCase = true)

private fun watermarkTextFor(media: CapturedMedia): String? {
    val text = ComputationalPipeline.formatPixelWatermark(media.exif)
    return text.takeIf { media.exif.iso > 0 && media.exif.shutterSpeed.isNotBlank() }
}

private fun detailRows(media: CapturedMedia): List<Pair<String, List<Pair<String, String>>>> {
    val exif = media.exif

    val file = buildList {
        add("Name" to media.fileName)
        if (exif.timestamp.isNotBlank()) add("Captured" to exif.timestamp)
        if (exif.resolution.isNotBlank()) add("Resolution" to exif.resolution)
        if (exif.format.isNotBlank()) add("Format" to exif.format)
    }

    val camera = buildList {
        if (exif.deviceModel.isNotBlank()) add("Device" to exif.deviceModel)
        if (exif.lensFocalLength.isNotBlank()) add("Focal length" to exif.lensFocalLength)
        if (exif.aperture.isNotBlank()) add("Aperture" to exif.aperture)
        if (exif.shutterSpeed.isNotBlank()) add("Shutter" to exif.shutterSpeed)
        if (exif.iso > 0) add("ISO" to exif.iso.toString())
        if (exif.exposureBias.isNotBlank()) add("Exposure bias" to exif.exposureBias)
        if (exif.whiteBalance.isNotBlank()) add("White balance" to exif.whiteBalance)
    }

    val place = buildList {
        add("Location" to (exif.location ?: "Not recorded"))
    }

    return listOf(
        "File" to file,
        "Camera" to camera,
        "Place" to place
    )
}
