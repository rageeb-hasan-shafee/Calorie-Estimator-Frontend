package com.example.demo

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

class CalorieViewModel : ViewModel() {

    // Using 10.0.2.2 to access localhost from Android Emulator
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/") 
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(CalorieApiService::class.java)

    var topImageUri by mutableStateOf<Uri?>(null)
    var sideImageUri by mutableStateOf<Uri?>(null)
    
    var topNumpyData by mutableStateOf<List<List<Float>>?>(null)
    var sideNumpyData by mutableStateOf<List<List<Float>>?>(null)
    
    var result by mutableStateOf<CalorieResult?>(null)
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
            topNumpyData = null
            sideNumpyData = null
            result = null
            
            try {
                // 1. Upload Top Image (matching 'file' part name from partner doc)
                val topFile = getFileFromUri(context, topUri, "top_image.jpg")
                val topPart = MultipartBody.Part.createFormData(
                    "file", topFile.name, topFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val topRes = apiService.uploadTopImage(topPart)
                if (!topRes.isSuccessful || topRes.body()?.ok != true) {
                    throw Exception("Top upload failed: ${topRes.message()}")
                }

                // 2. Upload Side Image
                val sideFile = getFileFromUri(context, sideUri, "side_image.jpg")
                val sidePart = MultipartBody.Part.createFormData(
                    "file", sideFile.name, sideFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val sideRes = apiService.uploadSideImage(sidePart)
                if (!sideRes.isSuccessful || sideRes.body()?.ok != true) {
                    throw Exception("Side upload failed: ${sideRes.message()}")
                }

                // 3. Trigger Full Process Pipeline
                val processRes = apiService.processImages()
                if (processRes.isSuccessful && processRes.body()?.ok == true) {
                    result = processRes.body()?.data?.meal_totals
                    
                    // Fetch Numpy Data (if endpoints are ready)
                    try {
                        topNumpyData = apiService.getTopNumpy().body()?.data
                        sideNumpyData = apiService.getSideNumpy().body()?.data
                    } catch (e: Exception) {
                        // Optional: Ignore if numpy endpoints aren't implemented yet
                    }
                } else {
                    errorMessage = "Processing failed: ${processRes.body()?.message ?: processRes.message()}"
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
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
