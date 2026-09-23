package com.example.capstone.mealscan

import android.app.Application
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.capstone.ui.theme.MealScan
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Sequential upload -> process -> segment -> classify -> volume pipeline,
 * ported from app.js. One single poll loop against /result/state drives every
 * stage transition; see runPipeline() for the stage ordering.
 */
class MealScanViewModel(application: Application) : AndroidViewModel(application) {

    private object Config {
        const val INITIAL_DELAY_MS = 8000L
        const val POLL_INTERVAL_MS = 10000L
        const val MAX_POLL_ATTEMPTS = 70
        const val MAX_RENDERED_MASK_DIMENSION = 512
    }

    private val api = MealScanApi()
    private val cacheDir get() = getApplication<Application>().cacheDir
    private val contentResolver get() = getApplication<Application>().contentResolver

    private val _uiState = MutableStateFlow(MealScanUiState())
    val uiState: StateFlow<MealScanUiState> = _uiState

    private val categoryColors = LinkedHashMap<String, Color>()
    private var colorCursor = 0
    private fun categoryColor(category: String): Color = categoryColors.getOrPut(category) {
        MealScan.segmentColors[colorCursor % MealScan.segmentColors.size].also { colorCursor++ }
    }

    private var pipelineKicked = false
    private var toastJob: Job? = null

    private fun apiBase(): String = _uiState.value.apiBase.trimEnd('/')

    private fun view(view: ScanView) = if (view == ScanView.TOP) _uiState.value.top else _uiState.value.side

    private fun updateView(view: ScanView, block: (ViewUiState) -> ViewUiState) {
        _uiState.update { s -> if (view == ScanView.TOP) s.copy(top = block(s.top)) else s.copy(side = block(s.side)) }
    }

    private fun setStatus(view: ScanView, text: String, tone: StatusTone = StatusTone.NONE) {
        updateView(view) { it.copy(status = text, statusTone = tone) }
    }

    private fun setSweeping(view: ScanView, on: Boolean) {
        updateView(view) { it.copy(sweeping = on) }
    }

    private fun setStage(stage: PipelineStage, visual: StageVisual) {
        _uiState.update { s -> s.copy(stages = s.stages + (stage to visual)) }
    }

    fun setApiBase(text: String) {
        _uiState.update { it.copy(apiBase = text) }
    }

    fun log(message: String, level: String = "info") {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
        _uiState.update { it.copy(log = listOf(LogEntry(time, message, level)) + it.log) }
    }

    fun clearLog() {
        _uiState.update { it.copy(log = emptyList()) }
    }

    private fun showToast(message: String) {
        _uiState.update { it.copy(toast = message) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(4500)
            _uiState.update { it.copy(toast = null) }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(toast = null) }
    }

    /* =========================================================
       Upload handling
    ========================================================= */

    fun onFileChosen(scanView: ScanView, uri: Uri) {
        updateView(scanView) { it.copy(imageUri = uri) }
        setStatus(scanView, "Uploading…", StatusTone.ACTIVE)

        viewModelScope.launch {
            try {
                api.uploadImage(apiBase(), scanView, uri, contentResolver, cacheDir)
                updateView(scanView) { it.copy(uploaded = true) }
                setStatus(scanView, "Uploaded — waiting on the other angle", StatusTone.ACTIVE)
                log("[${scanView.tag()}] uploaded image", "success")
                maybeStartPipeline()
            } catch (e: Exception) {
                setStatus(scanView, "Upload failed: ${e.message}", StatusTone.ERROR)
                log("[${scanView.tag()}] upload failed: ${e.message}", "error")
                showToast("${scanView.tag()} image failed to upload: ${e.message}")
            }
        }
    }

    fun onReplace(scanView: ScanView) {
        if (view(scanView).pipelineStarted) {
            showToast("This image is already part of a running pipeline and can’t be swapped mid-run.")
            return
        }
        updateView(scanView) { ViewUiState() }
    }

    /* =========================================================
       Kick-off + sequential pipeline
    ========================================================= */

    private fun maybeStartPipeline() {
        if (pipelineKicked) return
        val s = _uiState.value
        if (!s.top.uploaded || !s.side.uploaded) return
        pipelineKicked = true
        updateView(ScanView.TOP) { it.copy(pipelineStarted = true) }
        updateView(ScanView.SIDE) { it.copy(pipelineStarted = true) }

        setStage(PipelineStage.UPLOAD, StageVisual.DONE)
        log("Both angles uploaded — starting pipeline.")

        viewModelScope.launch { runPipeline() }
    }

    private suspend fun runPipeline() {
        try {
            setStage(PipelineStage.PROCESS, StageVisual.ACTIVE)
            try {
                val message = api.startProcess(apiBase())
                setStage(PipelineStage.PROCESS, StageVisual.DONE)
                log(message, "success")
            } catch (e: Exception) {
                log("/process failed: ${e.message}", "error")
                showToast("Processing failed to start: ${e.message}")
                return
            }

            log("Waiting ${(Config.INITIAL_DELAY_MS / 1000)}s before checking pipeline state…")
            delay(Config.INITIAL_DELAY_MS)

            var seenSegmentationTop = false
            var seenSegmentationSide = false
            var seenClassificationTop = false
            var seenClassificationSide = false
            var seenVolume = false

            setSweeping(ScanView.TOP, true)
            setStage(PipelineStage.SEGMENT, StageVisual.ACTIVE)

            for (attempt in 1..Config.MAX_POLL_ATTEMPTS) {
                val state = try {
                    api.fetchState(apiBase())
                } catch (e: Exception) {
                    log("State check failed (attempt $attempt): ${e.message}", "warn")
                    delay(Config.POLL_INTERVAL_MS)
                    continue
                }

                var acted = false

                if (!seenSegmentationTop && state.stages["segmentation_top"] == true) {
                    seenSegmentationTop = true
                    acted = true
                    try {
                        handleSegmentationDone(ScanView.TOP)
                        setSweeping(ScanView.SIDE, true)
                    } catch (e: Exception) {
                        log("[top] segmentation handler crashed: ${e.message}", "error")
                        showToast("Top segmentation failed: ${e.message}")
                        setSweeping(ScanView.TOP, false)
                    }
                }

                if (seenSegmentationTop && !seenSegmentationSide && state.stages["segmentation_side"] == true) {
                    seenSegmentationSide = true
                    acted = true
                    try {
                        handleSegmentationDone(ScanView.SIDE)
                        setStage(PipelineStage.SEGMENT, StageVisual.DONE)
                        setStage(PipelineStage.CLASSIFY, StageVisual.ACTIVE)
                    } catch (e: Exception) {
                        log("[side] segmentation handler crashed: ${e.message}", "error")
                        showToast("Side segmentation failed: ${e.message}")
                        setSweeping(ScanView.SIDE, false)
                    }
                }

                if (seenSegmentationSide && !seenClassificationTop && state.stages["classification_top"] == true) {
                    seenClassificationTop = true
                    acted = true
                    try {
                        handleClassificationDone(ScanView.TOP)
                    } catch (e: Exception) {
                        log("[top] classification handler crashed: ${e.message}", "error")
                    }
                }

                if (seenClassificationTop && !seenClassificationSide && state.stages["classification_side"] == true) {
                    seenClassificationSide = true
                    acted = true
                    try {
                        handleClassificationDone(ScanView.SIDE)
                        setStage(PipelineStage.CLASSIFY, StageVisual.DONE)
                    } catch (e: Exception) {
                        log("[side] classification handler crashed: ${e.message}", "error")
                    }
                }

                if (seenClassificationSide && !seenVolume && state.stages["volume"] == true) {
                    seenVolume = true
                    acted = true
                    try {
                        handleVolumeDone()
                        return
                    } catch (e: Exception) {
                        log("Volume estimation handler crashed: ${e.message}", "error")
                    }
                }

                if (!acted) {
                    val doneList = state.completedStages.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none yet"
                    log("Waiting on pipeline — completed so far: $doneList (attempt $attempt)", "warn")
                }

                delay(Config.POLL_INTERVAL_MS)
            }

            log("Timed out waiting for the pipeline to finish.", "error")
            showToast("Timed out waiting for processing to finish.")
        } catch (e: Exception) {
            log("CRITICAL: Pipeline crashed: ${e.message}", "error")
            showToast("Pipeline failed: ${e.message}")
            setSweeping(ScanView.TOP, false)
            setSweeping(ScanView.SIDE, false)
        }
    }

    /* =========================================================
       Stage handlers
    ========================================================= */

    private suspend fun handleSegmentationDone(scanView: ScanView) {
        try {
            setStatus(scanView, "Segmenting…", StatusTone.ACTIVE)
            log("[${scanView.tag()}] segmentation complete — fetching masks…", "success")

            val allFilenames = api.fetchSegmentationList(apiBase(), scanView)
            val filenames = allFilenames
            log("[${scanView.tag()}] ${filenames.size} region(s) loaded on device.", "success")
            setStatus(scanView, "${filenames.size} region(s) found — loading masks…", StatusTone.ACTIVE)

            var loadedCount = 0
            for ((index, filename) in filenames.withIndex()) {
                try {
                    val mask = api.fetchMaskContent(apiBase(), scanView, filename)
                    val compactMask = compactMask(mask, Config.MAX_RENDERED_MASK_DIMENSION)
                    val region = MaskRegion(
                        filename = filename,
                        mask = compactMask.pixels,
                        maskWidth = compactMask.width,
                        maskHeight = compactMask.height,
                        color = MealScan.segmentColors[index % MealScan.segmentColors.size],
                        visible = true,
                        label = null,
                        centroidFrac = computeCentroidFrac(mask),
                    )
                    updateView(scanView) { v ->
                        val masks = LinkedHashMap(v.masks)
                        masks[filename] = region
                        v.copy(masks = masks)
                    }
                    loadedCount++
                    setStatus(scanView, "Masking region $loadedCount/${filenames.size}…", StatusTone.ACTIVE)
                } catch (e: Exception) {
                    log("[${scanView.tag()}] failed to fetch mask for $filename: ${e.message}", "error")
                }
            }

            setSweeping(scanView, false)
            setStatus(scanView, "${filenames.size} region(s) masked — waiting on classification…", StatusTone.ACTIVE)
        } catch (e: Exception) {
            setSweeping(scanView, false)
            setStatus(scanView, "Failed: ${e.message}", StatusTone.ERROR)
            log("[${scanView.tag()}] segmentation step failed: ${e.message}", "error")
            showToast("${scanView.tag()} segmentation failed: ${e.message}")
        }
    }

    private suspend fun handleClassificationDone(scanView: ScanView) {
        try {
            setStatus(scanView, "Classifying…", StatusTone.ACTIVE)
            log("[${scanView.tag()}] classification complete — applying labels…", "success")

            val categories = api.fetchClassification(apiBase(), scanView)

            fun basename(name: String) = name.substringAfterLast('/').substringAfterLast('\\')
            fun normalize(name: String) = basename(name).lowercase()
                .removeSuffix(".npy")
                .replace(Regex("[^a-z0-9]+"), "_")
            fun numericId(name: String): String? =
                Regex("(\\d+)(?!.*\\d)").find(basename(name))?.groupValues?.get(1)?.let { it.toInt().toString() }

            val byExact = HashMap<String, String>()
            val byBasename = HashMap<String, String>()
            val byNormalized = HashMap<String, String>()
            val byNumericId = HashMap<String, String>()
            val ambiguous = "__AMBIGUOUS__"

            categories.forEach { (category, files) ->
                files.forEach { filename ->
                    byExact[filename] = category
                    byBasename[basename(filename)] = category
                    byNormalized[normalize(filename)] = category
                    val id = numericId(filename)
                    if (id != null) {
                        val existing = byNumericId[id]
                        byNumericId[id] = if (existing != null && existing != category) ambiguous else category
                    }
                }
            }

            fun resolveCategory(filename: String): String? {
                byExact[filename]?.let { return it }
                byBasename[basename(filename)]?.let { return it }
                byNormalized[normalize(filename)]?.let { return it }
                val id = numericId(filename)
                val byId = id?.let { byNumericId[it] }
                if (byId != null && byId != ambiguous) return byId
                return null
            }

            val currentMasks = view(scanView).masks
            var labeledCount = 0
            var removedCount = 0
            val totalCount = currentMasks.size
            val updatedMasks = LinkedHashMap<String, MaskRegion>()
            currentMasks.forEach { (filename, m) ->
                val category = resolveCategory(filename)
                updatedMasks[filename] = if (category != null) {
                    labeledCount++
                    m.copy(label = category, color = categoryColor(category), visible = true)
                } else {
                    removedCount++
                    m.copy(label = null, visible = false)
                }
            }
            updateView(scanView) { it.copy(masks = updatedMasks) }

            val categoryFileCount = categories.values.sumOf { it.size }
            if (labeledCount == 0 && categoryFileCount > 0 && totalCount > 0) {
                val oursSample = currentMasks.keys.take(5).joinToString(", ")
                val theirsSample = categories.values.flatten().take(5).joinToString(", ")
                log(
                    "[${scanView.tag()}] 0 matches despite $categoryFileCount categorized file(s) — filename mismatch? " +
                        "segmentation gave: [$oursSample] vs classification gave: [$theirsSample]",
                    "error",
                )
            }

            log(
                "[${scanView.tag()}] classification applied — $labeledCount/$totalCount region(s) labeled, " +
                    "$removedCount unclassified region(s) removed.",
                "success",
            )
            setStatus(scanView, "$labeledCount food item(s) identified", StatusTone.DONE)
        } catch (e: Exception) {
            setStatus(scanView, "Failed: ${e.message}", StatusTone.ERROR)
            log("[${scanView.tag()}] classification step failed: ${e.message}", "error")
            showToast("${scanView.tag()} classification failed: ${e.message}")
        }
    }

    private suspend fun handleVolumeDone() {
        setStage(PipelineStage.REPORT, StageVisual.ACTIVE)
        try {
            val result = api.fetchVolumeEstimation(apiBase())
            val rows = result.perFood.map { (name, pair) ->
                FoodBreakdownRow(
                    name = name,
                    color = categoryColor(name),
                    calories = Math.round(pair.first).toInt(),
                    volumeCm3 = pair.second,
                    nutrients = result.nutrientsByFood[name].orEmpty(),
                )
            }
            _uiState.update {
                it.copy(
                    report = ReportUiState(
                        visible = true,
                        totalCalories = Math.round(result.totalCalories).toInt(),
                        totalVolumeCm3 = result.totalVolumeCm3,
                        totalNutrients = result.totalNutrients,
                        rows = rows,
                    ),
                )
            }
            setStage(PipelineStage.REPORT, StageVisual.DONE)
            log("Nutrition report ready.", "success")
        } catch (e: Exception) {
            log("Fetching the final report failed: ${e.message}", "error")
            showToast("Fetching the final report failed: ${e.message}")
        }
    }

    private fun computeCentroidFrac(mask: Array<BooleanArray>): Offset {
        val rows = mask.size
        val cols = if (rows > 0) mask[0].size else 0
        var sx = 0.0
        var sy = 0.0
        var count = 0
        for (y in 0 until rows) {
            val row = mask[y]
            for (x in 0 until cols) {
                if (row[x]) {
                    sx += x
                    sy += y
                    count++
                }
            }
        }
        if (count == 0) return Offset(0.5f, 0.5f)
        return Offset(((sx / count + 0.5) / cols).toFloat(), ((sy / count + 0.5) / rows).toFloat())
    }

    private data class CompactMask(val pixels: ByteArray, val width: Int, val height: Int)

    private fun compactMask(mask: Array<BooleanArray>, maxSide: Int): CompactMask {
        val sourceHeight = mask.size
        val sourceWidth = if (sourceHeight > 0) mask[0].size else 0
        if (sourceHeight == 0 || sourceWidth == 0) return CompactMask(byteArrayOf(0), 1, 1)

        val scale = minOf(1f, maxSide.toFloat() / maxOf(sourceWidth, sourceHeight).toFloat())
        val width = maxOf(1, (sourceWidth * scale).roundToInt())
        val height = maxOf(1, (sourceHeight * scale).roundToInt())
        val pixels = ByteArray(width * height)
        for (y in 0 until height) {
            val sourceY = (y * sourceHeight / height).coerceIn(0, sourceHeight - 1)
            val sourceRow = mask[sourceY]
            for (x in 0 until width) {
                val sourceX = (x * sourceWidth / width).coerceIn(0, sourceWidth - 1)
                if (sourceRow[sourceX]) pixels[y * width + x] = 255.toByte()
            }
        }
        return CompactMask(pixels, width, height)
    }

    private fun ScanView.tag() = if (this == ScanView.TOP) "top" else "side"
}
