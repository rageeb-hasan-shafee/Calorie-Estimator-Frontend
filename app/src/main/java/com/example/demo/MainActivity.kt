package com.example.demo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.demo.ui.theme.DemoTheme

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

            ImagePickerCard(
                label = "Top View Image",
                imageUri = viewModel.topImageUri,
                onPickImage = { topImageLauncher.launch("image/*") }
            )

            ImagePickerCard(
                label = "Side View Image",
                imageUri = viewModel.sideImageUri,
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
                    Text("Processing...")
                } else {
                    Text("Calculate Calories")
                }
            }

            viewModel.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            // Show full nutrition report (including individual items)
            viewModel.resultData?.let { data ->
                NutritionReport(data)
            }
        }
    }
}

@Composable
fun NutritionReport(data: NutritionData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Meal Totals
        ResultCard(label = "Total Meal Nutrition", result = data.meal_totals)

        // 2. Individual Item Breakdown
        data.per_food_breakdown?.forEach { (foodName, breakdown) ->
            ResultCard(
                label = foodName.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
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
        vitD = breakdown.vitamins.vit_d_ug
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
                    imageVector = if (isIndividual) Icons.Default.Add else Icons.Default.Check,
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NutritionRow("Carbohydrates", "${"%.1f".format(result.carbs)}g")
            NutritionRow("Protein", "${"%.1f".format(result.protein)}g")
            NutritionRow("Fat", "${"%.1f".format(result.fat)}g")
            NutritionRow("Fiber", "${"%.1f".format(result.fiber)}g")

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

@Composable
fun ImagePickerCard(label: String, imageUri: Uri?, onPickImage: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        onClick = onPickImage
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
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
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(text = "Select $label")
                }
            }
        }
    }
}
