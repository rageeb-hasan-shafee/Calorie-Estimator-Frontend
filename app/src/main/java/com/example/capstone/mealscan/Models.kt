package com.example.capstone.mealscan

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** Which of the two photos a piece of state belongs to. Mirrors `views = ['top', 'side']` in app.js. */
enum class ScanView { TOP, SIDE }

enum class StatusTone { NONE, ACTIVE, DONE, ERROR, WARN }

/** The five stages shown on the pipeline rail (`#pipelineRail li[data-stage]`). */
enum class PipelineStage { UPLOAD, PROCESS, SEGMENT, CLASSIFY, REPORT }

enum class StageVisual { PENDING, ACTIVE, DONE }

/** One segmented region of a plate photo. Mirrors the `masks` map entries built in app.js. */
data class MaskRegion(
    val filename: String,
    val mask: ByteArray, // compact alpha mask, row-major, 0 = outside and 255 = inside
    val maskWidth: Int,
    val maskHeight: Int,
    val color: Color,
    val visible: Boolean = true,
    val label: String? = null,
    val centroidFrac: Offset, // normalized 0..1 centroid, for label placement
)

data class ViewUiState(
    val uploaded: Boolean = false,
    val pipelineStarted: Boolean = false,
    val status: String = "Waiting for image",
    val statusTone: StatusTone = StatusTone.NONE,
    val imageUri: Uri? = null,
    val sweeping: Boolean = false,
    // insertion order matters for stable legend/canvas ordering, same as a JS object literal
    val masks: LinkedHashMap<String, MaskRegion> = LinkedHashMap(),
)

data class LogEntry(val time: String, val message: String, val level: String)

data class NutrientValue(val label: String, val value: Double, val unit: String)

data class FoodBreakdownRow(
    val name: String,
    val color: Color,
    val calories: Int,
    val volumeCm3: Double?,
    val nutrients: List<NutrientValue> = emptyList(),
)

data class ReportUiState(
    val visible: Boolean = false,
    val totalCalories: Int = 0,
    val totalVolumeCm3: Double? = null,
    val totalNutrients: List<NutrientValue> = emptyList(),
    val rows: List<FoodBreakdownRow> = emptyList(),
)

data class MealScanUiState(
    val apiBase: String = "https://precisely-amd-mercury-signature.trycloudflare.com",
    val top: ViewUiState = ViewUiState(),
    val side: ViewUiState = ViewUiState(),
    val stages: Map<PipelineStage, StageVisual> = PipelineStage.entries.associateWith { StageVisual.PENDING },
    val report: ReportUiState = ReportUiState(),
    val log: List<LogEntry> = emptyList(),
    val toast: String? = null,
)
