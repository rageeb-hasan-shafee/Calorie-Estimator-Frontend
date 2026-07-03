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

            // Show Top Numpy Data if available
            viewModel.topNumpyData?.let { data ->
                NumpyDataCard(label = "Top View Processed Data", data = data)
            }

            // Show Side Numpy Data if available
            viewModel.sideNumpyData?.let { data ->
                NumpyDataCard(label = "Side View Processed Data", data = data)
            }

            viewModel.result?.let { result ->
                ResultCard(result)
            }
        }
    }
}

@Composable
fun NumpyDataCard(label: String, data: List<List<Float>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            // Displaying a snippet of the numpy array data
            val previewText = data.take(3).joinToString("\n") { row ->
                row.take(5).joinToString(", ") + if (row.size > 5) "..." else ""
            } + if (data.size > 3) "\n..." else ""
            
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Shape: [${data.size}, ${data.firstOrNull()?.size ?: 0}]",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
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

@Composable
fun ResultCard(result: CalorieResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Green
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nutrition Report",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "${result.calories.toInt()} kcal",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            NutritionRow("Carbohydrates", "${result.carbs}g")
            NutritionRow("Protein", "${result.protein}g")
            NutritionRow("Fat", "${result.fat}g")
            NutritionRow("Fiber", "${result.fiber}g")
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Micronutrients", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            NutritionRow("Sodium", "${result.sodium}mg")
            NutritionRow("Calcium", "${result.calcium}mg")
            NutritionRow("Iron", "${result.iron}mg")
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
