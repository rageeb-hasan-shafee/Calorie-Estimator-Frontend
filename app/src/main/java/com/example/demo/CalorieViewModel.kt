package com.example.demo

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CalorieViewModel : ViewModel() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES) // Max timeout for heavy AI
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://calorie.loca.lt/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(CalorieApiService::class.java)

    var topImageUri by mutableStateOf<Uri?>(null)
    var sideImageUri by mutableStateOf<Uri?>(null)
    
    var topCategories by mutableStateOf<Map<String, List<String>>?>(null)
    var sideCategories by mutableStateOf<Map<String, List<String>>?>(null)
    
    var topSegmentationFiles by mutableStateOf<List<String>?>(null)
    var sideSegmentationFiles by mutableStateOf<List<String>?>(null)

    val topMaskData = mutableStateMapOf<String, List<List<Int>>>()
    val sideMaskData = mutableStateMapOf<String, List<List<Int>>>()

    var resultData by mutableStateOf<NutritionData?>(null)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun processImages(context: Context) {
        val topUri = topImageUri
        val sideUri = sideImageUri

        if (topUri == null || sideUri == null) {
            errorMessage = "Please select both top and side images"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            resultData = null
            // We DON'T clear masks here so the user can still see them if a retry happens
            
            try {
                // 1. Upload
                val topFile = getFileFromUri(context, topUri, "top_image.jpg")
                val topPart = MultipartBody.Part.createFormData("file", topFile.name, topFile.asRequestBody("image/*".toMediaTypeOrNull()))
                apiService.uploadTopImage(topPart)

                val sideFile = getFileFromUri(context, sideUri, "side_image.jpg")
                val sidePart = MultipartBody.Part.createFormData("file", sideFile.name, sideFile.asRequestBody("image/*".toMediaTypeOrNull()))
                apiService.uploadSideImage(sidePart)

                // 2. Start polling
                startPolling()

                // 3. Hit process
                val response = apiService.processImages()
                
                if (response.isSuccessful && response.body()?.ok == true) {
                    resultData = response.body()?.data
                } else {
                    val code = response.code()
                    errorMessage = when(code) {
                        502, 503 -> "Network dropped (502/503). The AI process is very heavy for the tunnel. Check detected items above."
                        404 -> "Processing script not found on server (404)."
                        else -> "Processing error: $code"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Connection lost: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isLoading) {
                fetchIntermediateResults()
                delay(2000)
            }
            // One final poll after main process ends to catch everything
            delay(1000)
            fetchIntermediateResults()
        }
    }

    private suspend fun fetchIntermediateResults() = coroutineScope {
        try {
            val topSeg = apiService.getTopSegmentation()
            if (topSeg.isSuccessful) {
                val files = topSeg.body()?.files ?: emptyList()
                topSegmentationFiles = files
                files.forEach { file ->
                    if (!topMaskData.containsKey(file)) {
                        launch {
                            val res = apiService.getTopSegmentationContent(file)
                            if (res.isSuccessful) res.body()?.let { topMaskData[file] = it.mask }
                        }
                    }
                }
            }
            
            val sideSeg = apiService.getSideSegmentation()
            if (sideSeg.isSuccessful) {
                val files = sideSeg.body()?.files ?: emptyList()
                sideSegmentationFiles = files
                files.forEach { file ->
                    if (!sideMaskData.containsKey(file)) {
                        launch {
                            val res = apiService.getSideSegmentationContent(file)
                            if (res.isSuccessful) res.body()?.let { sideMaskData[file] = it.mask }
                        }
                    }
                }
            }

            val topClass = apiService.getTopClassification()
            if (topClass.isSuccessful) topCategories = topClass.body()?.categories
            
            val sideClass = apiService.getSideClassification()
            if (sideClass.isSuccessful) sideCategories = sideClass.body()?.categories
        } catch (e: Exception) {}
    }

    private fun getFileFromUri(context: Context, uri: Uri, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}
