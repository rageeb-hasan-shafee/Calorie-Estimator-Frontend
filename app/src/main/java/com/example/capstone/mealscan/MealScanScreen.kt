package com.example.capstone.mealscan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.capstone.ui.theme.MealScan

@Composable
fun MealScanRoute(modifier: Modifier = Modifier, viewModel: MealScanViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    MealScanScreen(
        state = state,
        onApiBaseChange = viewModel::setApiBase,
        onFileChosen = viewModel::onFileChosen,
        onReplace = viewModel::onReplace,
        onClearLog = viewModel::clearLog,
        modifier = modifier,
    )
}

@Composable
fun MealScanScreen(
    state: MealScanUiState,
    onApiBaseChange: (String) -> Unit,
    onFileChosen: (ScanView, android.net.Uri) -> Unit,
    onReplace: (ScanView) -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MealScan.bg).navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScopeBar(apiBase = state.apiBase, onApiBaseChange = onApiBaseChange)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item { HeroSection() }
                item { PipelineRail(state.stages) }
                item {
                    ScanCard(
                        index = "01",
                        title = "Top view",
                        scanView = ScanView.TOP,
                        state = state.top,
                        onFileChosen = { uri -> onFileChosen(ScanView.TOP, uri) },
                        onReplace = { onReplace(ScanView.TOP) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                item {
                    ScanCard(
                        index = "02",
                        title = "Side view",
                        scanView = ScanView.SIDE,
                        state = state.side,
                        onFileChosen = { uri -> onFileChosen(ScanView.SIDE, uri) },
                        onReplace = { onReplace(ScanView.SIDE) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                if (state.report.visible) {
                    item {
                        ReportCard(state.report, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
                item {
                    LogCard(
                        log = state.log,
                        onClear = onClearLog,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (state.toast != null) {
            ToastBubble(
                message = state.toast,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun ScopeBar(apiBase: String, onApiBaseChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MealScan.bg.copy(alpha = 0.92f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("◉", color = MealScan.teal, fontFamily = MealScan.mono, fontSize = 13.sp)
                Text("MEAL SCAN", color = MealScan.text, fontFamily = MealScan.mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("API BASE", color = MealScan.textFaint, fontFamily = MealScan.mono, fontSize = 10.sp)
                OutlinedTextField(
                    value = apiBase,
                    onValueChange = onApiBaseChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = MealScan.mono, fontSize = 12.sp, color = MealScan.textDim),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MealScan.panel,
                        unfocusedContainerColor = MealScan.panel,
                        focusedBorderColor = MealScan.teal,
                        unfocusedBorderColor = MealScan.line,
                        cursorColor = MealScan.teal,
                    ),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MealScan.line))
    }
}

@Composable
private fun HeroSection() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text(
            "Two angles.\nOne plate, fully read.",
            color = MealScan.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 34.sp,
        )
        Text(
            "Upload a top view and a side view. The pipeline segments what's on the plate, classifies each piece, and estimates volume & nutrition — while you watch it happen.",
            color = MealScan.textDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private val PipelineLabels = mapOf(
    PipelineStage.UPLOAD to "Upload",
    PipelineStage.PROCESS to "Process queued",
    PipelineStage.SEGMENT to "Segmenting",
    PipelineStage.CLASSIFY to "Classifying",
    PipelineStage.REPORT to "Nutrition report",
)

@Composable
private fun PipelineRail(stages: Map<PipelineStage, StageVisual>) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState())
            .background(MealScan.panel, RoundedCornerShape(999.dp))
            .border(1.dp, MealScan.line, RoundedCornerShape(999.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PipelineStage.entries.forEach { stage ->
            val visual = stages[stage] ?: StageVisual.PENDING
            val textColor = when (visual) {
                StageVisual.ACTIVE -> MealScan.text
                StageVisual.DONE -> MealScan.textDim
                StageVisual.PENDING -> MealScan.textFaint
            }
            val bg = if (visual == StageVisual.ACTIVE) MealScan.panelRaised else Color.Transparent
            val dotColor = if (visual != StageVisual.PENDING) MealScan.teal else MealScan.textFaint

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(bg, RoundedCornerShape(999.dp))
                    .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            ) {
                Box(Modifier.size(6.dp).background(dotColor, CircleShape))
                Text(PipelineLabels.getValue(stage), color = textColor, fontFamily = MealScan.mono, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReportCard(report: ReportUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MealScan.panel, RoundedCornerShape(10.dp))
            .border(1.dp, MealScan.tealDim, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("03", color = MealScan.textFaint, fontFamily = MealScan.mono, fontSize = 12.sp)
                Text("Nutrition report", color = MealScan.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(
                "${report.totalCalories} kcal total",
                color = MealScan.teal,
                fontFamily = MealScan.mono,
                fontSize = 13.sp,
                modifier = Modifier.background(MealScan.tealDim, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        report.rows.forEachIndexed { i, row ->
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).background(row.color, CircleShape))
                        Text(
                            row.name.replaceFirstChar { it.uppercase() },
                            color = MealScan.text,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                    val volText = row.volumeCm3?.let { " · $it cm³" } ?: ""
                    Text(
                        "${row.calories} kcal$volText",
                        color = MealScan.textDim,
                        fontFamily = MealScan.mono,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                    )
                }
                if (i < report.rows.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MealScan.line))
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: List<LogEntry>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MealScan.panel, RoundedCornerShape(10.dp))
            .border(1.dp, MealScan.line, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activity log", color = MealScan.textDim, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "clear",
                color = MealScan.textFaint,
                fontFamily = MealScan.mono,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            if (log.isEmpty()) {
                Text(
                    "Ready. Upload a top view and a side view to begin.",
                    color = MealScan.textDim,
                    fontFamily = MealScan.mono,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
            log.take(60).forEach { entry ->
                val color = when (entry.level) {
                    "error" -> MealScan.red
                    "success" -> MealScan.teal
                    "warn" -> MealScan.amber
                    else -> MealScan.textDim
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.time, color = MealScan.textFaint, fontFamily = MealScan.mono, fontSize = 12.sp)
                    Text(entry.message, color = color, fontFamily = MealScan.mono, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ToastBubble(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        color = MealScan.text,
        fontFamily = MealScan.mono,
        fontSize = 13.sp,
        modifier = modifier
            .wrapContentWidth()
            .background(MealScan.panelRaised, RoundedCornerShape(999.dp))
            .border(1.dp, MealScan.redDim, RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
