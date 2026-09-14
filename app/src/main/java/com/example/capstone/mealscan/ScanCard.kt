package com.example.capstone.mealscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.example.capstone.ui.theme.MealScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ScanCard(
    index: String,
    title: String,
    scanView: ScanView,
    state: ViewUiState,
    onFileChosen: (Uri) -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onFileChosen(uri)
    }

    Column(
        modifier = modifier
            .background(MealScan.panel, RoundedCornerShape(10.dp))
            .border(1.dp, MealScan.line, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(index, color = MealScan.textFaint, fontFamily = MealScan.mono, fontSize = 12.sp)
                Text(title, color = MealScan.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            StatusBadge(state.status, state.statusTone)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(MealScan.stageBg),
        ) {
            if (state.imageUri == null) {
                DropZone(
                    label = "Drop or choose the ${title.lowercase()} photo",
                    onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            } else {
                ImageWithMasks(
                    uri = state.imageUri,
                    masks = state.masks.values.toList(),
                    sweeping = state.sweeping,
                    onReplace = onReplace,
                )
            }
        }

        val legendMasks = remember(state.masks) {
            val seen = LinkedHashMap<String, Pair<Color, String>>()
            state.masks.values.filter { it.visible }.forEach { m ->
                val key = m.label ?: m.filename
                if (!seen.containsKey(key)) {
                    val text = m.label?.replaceFirstChar { it.uppercase() } ?: m.filename.removeSuffix(".npy")
                    seen[key] = m.color to text
                }
            }
            seen.values.toList()
        }
        if (legendMasks.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(14.dp, 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                legendMasks.forEach { (color, text) -> LegendChip(color, text) }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, tone: StatusTone) {
    val color = when (tone) {
        StatusTone.ACTIVE, StatusTone.DONE -> MealScan.teal
        StatusTone.ERROR -> MealScan.red
        StatusTone.WARN -> MealScan.amber
        StatusTone.NONE -> MealScan.textDim
    }
    val borderColor = when (tone) {
        StatusTone.ACTIVE -> MealScan.tealDim
        StatusTone.ERROR -> MealScan.redDim
        StatusTone.WARN -> MealScan.amberDim
        else -> MealScan.line
    }
    Text(
        text = text,
        color = color,
        fontFamily = MealScan.mono,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 170.dp)
            .background(MealScan.panelRaised, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DropZone(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 220.dp)
            .padding(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, MealScan.line, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⤒", fontSize = 22.sp, color = MealScan.textFaint)
            Text(label, fontSize = 13.sp, color = MealScan.textDim)
        }
    }
}

@Composable
private fun LegendChip(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(MealScan.panelRaised, RoundedCornerShape(999.dp))
            .border(1.dp, MealScan.line, RoundedCornerShape(999.dp))
            .padding(start = 6.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(text, fontFamily = MealScan.mono, fontSize = 11.5.sp, color = MealScan.textDim)
    }
}

@Composable
private fun ImageWithMasks(uri: Uri, masks: List<MaskRegion>, sweeping: Boolean, onReplace: () -> Unit) {
    val context = LocalContext.current
    val bitmapState by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri, 1600) }
    }
    val bitmap = bitmapState
    val aspect = if (bitmap != null && bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 4f / 3f

    val maskBitmaps = remember(masks) {
        masks.filter { it.visible }
            .associate { m -> m.filename to maskToImageBitmap(m) }
    }
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val visible = masks.filter { it.visible }
            visible.forEach { m ->
                maskBitmaps[m.filename]?.let { img ->
                    drawImage(
                        image = img,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        colorFilter = ColorFilter.tint(m.color),
                    )
                }
            }
            visible.forEach { m ->
                val label = m.label ?: return@forEach
                val x = m.centroidFrac.x * size.width
                val y = m.centroidFrac.y * size.height
                drawLabel(this, label, x, y, m.color, density.density)
            }
        }

        if (sweeping) {
            SweepOverlay(Modifier.fillMaxSize())
        }

        Text(
            "↻ replace",
            fontFamily = MealScan.mono,
            fontSize = 11.sp,
            color = MealScan.textDim,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(Color(0xB80F1114), RoundedCornerShape(999.dp))
                .border(1.dp, MealScan.line, RoundedCornerShape(999.dp))
                .clickable(onClick = onReplace)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SweepOverlay(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "sweep")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepOffset",
    )
    Canvas(modifier) {
        val bandHeight = size.height * 0.4f
        val top = offset * size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, MealScan.teal.copy(alpha = 0.14f), Color.Transparent),
                startY = top,
                endY = top + bandHeight,
            ),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, bandHeight),
        )
    }
}

private fun drawLabel(scope: androidx.compose.ui.graphics.drawscope.DrawScope, text: String, x: Float, y: Float, color: Color, density: Float) {
    val nativeCanvas = scope.drawContext.canvas.nativeCanvas
    val textSizePx = 12.sp.value * density * 1.4f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = textSizePx
        textAlign = Paint.Align.CENTER
    }
    val padX = 9f * density
    val padY = 6f * density
    val textWidth = paint.measureText(text)
    val w = textWidth + padX * 2
    val h = textSizePx + padY * 2
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.argb(209, 239, 242, 246) }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    val rect = RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
    nativeCanvas.drawRoundRect(rect, h / 2, h / 2, bgPaint)
    nativeCanvas.drawRoundRect(rect, h / 2, h / 2, borderPaint)
    val textPaint = Paint(paint).apply { this.color = android.graphics.Color.rgb(4, 4, 4) }
    val fm = textPaint.fontMetrics
    val textY = y - (fm.ascent + fm.descent) / 2
    nativeCanvas.drawText(text, x, textY, textPaint)
}

private fun maskToImageBitmap(region: MaskRegion): ImageBitmap {
    val bitmap = Bitmap.createBitmap(region.maskWidth, region.maskHeight, Bitmap.Config.ALPHA_8)
    bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(region.mask))
    return bitmap.asImageBitmap()
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    val resolver = context.contentResolver
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // decodeStream always returns null in bounds-only mode — it reports size via
    // boundsOpts as a side effect instead, so failure must be read from there, not
    // from a null-check on the call's own result.
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

    var sampleSize = 1
    val (w, h) = boundsOpts.outWidth to boundsOpts.outHeight
    while (w / sampleSize > maxDimension || h / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
}
