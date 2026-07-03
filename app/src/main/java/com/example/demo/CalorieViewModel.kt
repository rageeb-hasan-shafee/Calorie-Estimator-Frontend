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

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://your-backend-api.com/") // Replace with actual base URL
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
                // 1. Upload Top Image
                val topFile = getFileFromUri(context, topUri, "top_image.jpg")
                val topPart = MultipartBody.Part.createFormData(
                    "image", topFile.name, topFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                apiService.uploadTopImage(topPart)

                // 2. Upload Side Image
                val sideFile = getFileFromUri(context, sideUri, "side_image.jpg")
                val sidePart = MultipartBody.Part.createFormData(
                    "image", sideFile.name, sideFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                apiService.uploadSideImage(sidePart)

                // 3. Get Top Numpy
                val topNumpyResponse = apiService.getTopNumpy()
                if (topNumpyResponse.isSuccessful) {
                    topNumpyData = topNumpyResponse.body()?.data
                }

                // 4. Get Side Numpy
                val sideNumpyResponse = apiService.getSideNumpy()
                if (sideNumpyResponse.isSuccessful) {
                    sideNumpyData = sideNumpyResponse.body()?.data
                }

                // 5. Final Result
                val response = apiService.getFinalResult()
                if (response.isSuccessful) {
                    result = response.body()
                } else {
                    errorMessage = "Failed to get result: ${response.message()}"
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
