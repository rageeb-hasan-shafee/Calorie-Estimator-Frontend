package com.example.demo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.demo.ui.theme.DemoTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                CalorieEstimationApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorieEstimationApp(viewModel: CalorieViewModel = viewModel()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val topImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.topImageUri = uri
    }

    val sideImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.sideImageUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calorie Estimator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Upload photos to estimate calories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            ImageWithOverlays(
                label = "Top View",
                imageUri = viewModel.topImageUri,
                masks = viewModel.topMaskData,
                categories = viewModel.topCategories,
                onPickImage = { topImageLauncher.launch("image/*") }
            )

            ImageWithOverlays(
                label = "Side View",
                imageUri = viewModel.sideImageUri,
                masks = viewModel.sideMaskData,
                categories = viewModel.sideCategories,
                onPickImage = { sideImageLauncher.launch("image/*") }
            )

            Button(
                onClick = { viewModel.processImages(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading && viewModel.topImageUri != null && viewModel.sideImageUri != null,
                shape = MaterialTheme.shapes.medium
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Pipeline Running...")
                } else {
                    Text("Calculate Calories")
                }
            }

            viewModel.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            // Intermediate Results (Segmentation & Classification)
            if (viewModel.isLoading || viewModel.topSegmentationFiles != null || viewModel.topCategories != null) {
                IntermediateResultsSection(viewModel)
            }

            // Final Nutrition Report
            viewModel.resultData?.let { data ->
                NutritionReport(data)
            }
        }
    }
}

@Composable
fun ImageWithOverlays(
    label: String,
    imageUri: Uri?,
    masks: Map<String, List<List<Int>>>,
    categories: Map<String, List<String>>?,
    onPickImage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        onClick = onPickImage
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Draw Masks and Labels
                Canvas(modifier = Modifier.fillMaxSize()) {
                    masks.forEach { (maskName, maskPixels) ->
                        val maskIndex = maskName.filter { it.isDigit() }
                        
                        val indexToCategory = mutableMapOf<String, String>()
                        categories?.forEach { (category, fileList) ->
                            fileList.forEach { fileName ->
                                val index = fileName.filter { it.isDigit() }
                                if (index.isNotEmpty()) indexToCategory[index] = category
                            }
                        }
                        
                        val categoryFound = if (maskIndex.isNotEmpty()) indexToCategory[maskIndex] else null
                        val isFood = categoryFound != null
                        val overlayColor = if (isFood) Color.Green.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.4f)
                        val labelText = categoryFound?.replace("_", " ")?.replaceFirstChar { it.titlecase(Locale.ROOT) } ?: if (categories != null) "Non-food" else "..."

                        // Draw mask pixels
                        val rows = maskPixels.size
                        val cols = if (rows > 0) maskPixels[0].size else 0
                        if (rows > 0 && cols > 0) {
                            val pixelWidth = size.width / cols
                            val pixelHeight = size.height / rows
                            
                            var centerX = 0f
                            var centerY = 0f
                            var count = 0
                            
                            for (r in 0 until rows) {
                                for (c in 0 until cols) {
                                    if (maskPixels[r][c] > 0) {
                                        drawRect(
                                            color = overlayColor,
                                            topLeft = Offset(c * pixelWidth, r * pixelHeight),
                                            size = androidx.compose.ui.geometry.Size(pixelWidth, pixelHeight)
                                        )
                                        centerX += c * pixelWidth
                                        centerY += r * pixelHeight
                                        count++
                                    }
                                }
                            }
                            
                            // Draw label at the center of the mask
                            if (count > 0) {
                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        color = if (isFood) android.graphics.Color.GREEN else android.graphics.Color.WHITE
                                        textSize = 40f
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
                                    }
                                    canvas.nativeCanvas.drawText(
                                        labelText,
                                        centerX / count,
                                        centerY / count,
                                        paint
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(text = "Select $label")
                }
            }
        }
    }
}

@Composable
fun IntermediateResultsSection(viewModel: CalorieViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Autonomous Pipeline Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        IntermediateCard(
            title = "Object Detection & Labeling",
            icon = Icons.Default.Troubleshoot,
            content = {
                if (viewModel.topSegmentationFiles == null && viewModel.sideSegmentationFiles == null && viewModel.isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        Text("Waiting for segmentation output...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        viewModel.topSegmentationFiles?.let { masks ->
                            MaskLabelingSection("Top View", masks, viewModel.topCategories)
                        }
                        viewModel.sideSegmentationFiles?.let { masks ->
                            MaskLabelingSection("Side View", masks, viewModel.sideCategories)
                        }
                        if (viewModel.isLoading && (viewModel.topSegmentationFiles == null || viewModel.sideSegmentationFiles == null)) {
                            Text("Analyzing remaining views...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun MaskLabelingSection(viewName: String, masks: List<String>, categories: Map<String, List<String>>?) {
    Column {
        Text(text = viewName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        
        val indexToCategory = mutableMapOf<String, String>()
        categories?.forEach { (category, fileList) ->
            fileList.forEach { fileName ->
                val index = fileName.filter { it.isDigit() }
                if (index.isNotEmpty()) indexToCategory[index] = category
            }
        }

        masks.forEach { maskName ->
            val maskIndex = maskName.filter { it.isDigit() }
            val categoryFound = if (maskIndex.isNotEmpty()) indexToCategory[maskIndex] else null
            val label = categoryFound?.replace("_", " ")?.replaceFirstChar { it.titlecase(Locale.ROOT) } 
                ?: if (categories != null) "Non-food" else "Identifying..."
            val isFood = categoryFound != null
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFood) Icons.Default.Restaurant else if (label == "Non-food") Icons.Default.Close else Icons.Default.Pending,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isFood) Color(0xFF4CAF50) else if (label == "Non-food") Color.Gray.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$maskName  ➜  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isFood) FontWeight.Bold else FontWeight.Normal,
                    color = if (isFood) MaterialTheme.colorScheme.primary else if (label == "Non-food") Color.Gray else Color.Unspecified
                )
            }
        }
    }
}

@Composable
fun IntermediateCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}

@Composable
fun NutritionReport(data: NutritionData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "Final Nutrition Report",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        ResultCard(label = "Total Meal Nutrition", result = data.meal_totals)

        data.per_food_breakdown?.forEach { (foodName, breakdown) ->
            ResultCard(
                label = foodName.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                result = breakdownToCalorieResult(breakdown),
                isIndividual = true
            )
        }
    }
}

fun breakdownToCalorieResult(breakdown: FoodBreakdown): CalorieResult {
    return CalorieResult(
        calories = breakdown.calories_kcal,
        carbs = breakdown.macros.carbohydrates_g,
        protein = breakdown.macros.protein_g,
        fat = breakdown.macros.fat_g,
        fiber = breakdown.macros.fiber_g,
        sodium = breakdown.minerals.sodium_mg,
        calcium = breakdown.minerals.calcium_mg,
        iron = breakdown.minerals.iron_mg,
        vitA = breakdown.vitamins.vit_a_ug,
        vitC = breakdown.vitamins.vit_c_mg,
        vitD = breakdown.vitamins.vit_d_ug,
        volume = breakdown.volume_cm3,
        macroSplit = breakdown.macroSplit
    )
}

@Composable
fun ResultCard(label: String, result: CalorieResult, isIndividual: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isIndividual) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIndividual) Icons.Default.Fastfood else Icons.Default.Analytics,
                    contentDescription = null,
                    tint = if (isIndividual) MaterialTheme.colorScheme.primary else Color.Green
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = if (isIndividual) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${"%.1f".format(result.calories)} kcal",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )

            result.volume?.let {
                Text(text = "Estimated Volume: ${"%.1f".format(it)} cm³", style = MaterialTheme.typography.bodyLarge)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NutritionRow("Carbohydrates", "${"%.1f".format(result.carbs)}g")
            NutritionRow("Protein", "${"%.1f".format(result.protein)}g")
            NutritionRow("Fat", "${"%.1f".format(result.fat)}g")
            NutritionRow("Fiber", "${"%.1f".format(result.fiber)}g")

            result.macroSplit?.let { split ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Macro Energy Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = "Carbs: ${split.carbs}% | Protein: ${split.protein}% | Fat: ${split.fat}%", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Micronutrients", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            NutritionRow("Sodium", "${"%.1f".format(result.sodium)}mg")
            NutritionRow("Calcium", "${"%.1f".format(result.calcium)}mg")
            NutritionRow("Iron", "${"%.1f".format(result.iron)}mg")

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Vitamins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            NutritionRow("Vitamin A", "${"%.1f".format(result.vitA)}µg")
            NutritionRow("Vitamin C", "${"%.1f".format(result.vitC)}mg")
            NutritionRow("Vitamin D", "${"%.1f".format(result.vitD)}µg")
        }
    }
}

@Composable
fun NutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
