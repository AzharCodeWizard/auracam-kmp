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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.camera.domain.CameraMode
import com.auracam.camera.domain.CapturedMedia
import com.auracam.processing.ComputationalPipeline
import com.auracam.ui.theme.*
import com.auracam.ui.util.rememberMediaImage
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, media.lastIndex),
        pageCount = { media.size }
    )
    val currentIndex = pagerState.currentPage.coerceIn(0, media.lastIndex)
    val current = media[currentIndex]

    val railState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        railState.animateScrollToItem(currentIndex)
    }

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
            pageSpacing = 12.dp,
            userScrollEnabled = true
        ) { page ->
            ZoomableMedia(
                media = media[page],
                onToggleChrome = { chromeVisible = !chromeVisible },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top App Bar Chrome
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
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

        // Bottom Thumbnail Strip Chrome
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
                onSelect = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                }
            )
        }

        // EXIF Details Bottom Sheet
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
private fun ZoomableMedia(
    media: CapturedMedia,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val imageBitmap = rememberMediaImage(media.uri)
    val isVideo = media.mode == CameraMode.VIDEO || media.fileName.endsWith(".mp4")

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(media.id) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scale = if (scale > 1.2f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                    onTap = { onToggleChrome() }
                )
            }
            .pointerInput(media.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f

                    scale = newScale
                    if (newScale > 1.05f) {
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = media.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else {
            // Fallback stylized preview placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF181B20)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.PlayArrow else Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = PixelYellowAccent,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = media.fileName,
                        color = PixelTextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Video Play Icon Overlay
        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = PixelYellowAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
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
                    listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.Default.Close, "Close", onClick = onClose)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = media.fileName,
                    style = AuraCamTheme.typography.titleSmall,
                    color = PixelTextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$position of $total  •  ${media.exif.timestamp}",
                    fontSize = 11.sp,
                    color = PixelTextSecondary,
                    maxLines = 1
                )
            }

            if (onShare != null) {
                GlassIconButton(Icons.Default.Share, "Share", onClick = onShare)
                Spacer(Modifier.width(8.dp))
            }
            if (onDelete != null) {
                GlassIconButton(Icons.Default.Delete, "Delete", onClick = onDelete)
                Spacer(Modifier.width(8.dp))
            }
            GlassIconButton(
                icon = Icons.Default.Info,
                contentDescription = "Details",
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
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (watermark != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = watermark,
                    color = PixelYellowAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Horizontal Thumbnail Filmstrip
        LazyRow(
            state = railState,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(media) { index, item ->
                val isSelected = index == currentIndex
                val thumb = rememberMediaImage(item.uri)

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF22262E))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) PixelYellowAccent else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = item.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.mode == CameraMode.VIDEO) Icons.Default.PlayArrow else Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = if (isSelected) PixelYellowAccent else PixelTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsSheet(
    media: CapturedMedia,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xF0181B22))
            .border(1.dp, PixelGlassBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Photo Info & EXIF",
                    style = AuraCamTheme.typography.titleMedium,
                    color = PixelTextWhite
                )
                GlassIconButton(Icons.Default.Close, "Dismiss", onClick = onDismiss)
            }

            // EXIF Parameter Grid
            val exif = media.exif
            InfoRow("File Name", media.fileName)
            InfoRow("Capture Time", exif.timestamp)
            InfoRow("Device", exif.deviceModel.ifBlank { "Google Pixel (AuraCam)" })
            InfoRow("Resolution", "${media.width} × ${media.height}")
            InfoRow("Format", media.format.label)

            if (exif.lensFocalLength.isNotBlank()) InfoRow("Focal Length", exif.lensFocalLength)
            if (exif.aperture.isNotBlank()) InfoRow("Aperture", exif.aperture)
            if (exif.shutterSpeed.isNotBlank()) InfoRow("Shutter Speed", exif.shutterSpeed)
            if (exif.iso > 0) InfoRow("ISO", "${exif.iso}")
            if (exif.exposureBias.isNotBlank()) InfoRow("Exposure Bias", exif.exposureBias)
            if (exif.whiteBalance.isNotBlank()) InfoRow("White Balance", exif.whiteBalance)
            val gpsLocation = exif.location
            if (gpsLocation != null) InfoRow("GPS Location", gpsLocation)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = PixelTextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = PixelTextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) PixelYellowAccent else Color(0x33FFFFFF))
            .clickable(onClick = onClick),
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

@Composable
private fun DeleteDialog(
    media: CapturedMedia,
    onConfirm: suspend (CapturedMedia) -> Unit,
    onFinished: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!deleting) onFinished() },
        title = { Text("Delete Photo?", color = PixelTextWhite) },
        text = {
            Text(
                "Are you sure you want to permanently delete \"${media.fileName}\"?",
                color = PixelTextSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    deleting = true
                    coroutineScope.launch {
                        onConfirm(media)
                        deleting = false
                        onFinished()
                    }
                },
                enabled = !deleting
            ) {
                if (deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PixelYellowAccent)
                } else {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onFinished, enabled = !deleting) {
                Text("Cancel", color = PixelTextWhite)
            }
        },
        containerColor = Color(0xFF1E222A)
    )
}

@Composable
private fun EmptyGallery(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPitchBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = PixelTextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "No Photos Yet",
                style = AuraCamTheme.typography.titleMedium,
                color = PixelTextWhite
            )
            Text(
                text = "Captured photos and videos will appear here",
                style = AuraCamTheme.typography.bodySmall,
                color = PixelTextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GlassIconButton(Icons.Default.Close, "Close", onClick = onClose)
        }
    }
}

private fun watermarkTextFor(media: CapturedMedia): String =
    "Shot on AuraCam • ${media.format.label}"
