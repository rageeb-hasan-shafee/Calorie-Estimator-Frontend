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
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CalorieViewModel : ViewModel() {

    // Configure OkHttpClient with longer timeouts for AI processing
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES) // Allow up to 2 minutes for processing
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Using 10.0.2.2 to access localhost from Android Emulator
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://calorie.loca.lt/")
        .client(okHttpClient) // Attach the custom client
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(CalorieApiService::class.java)

    var topImageUri by mutableStateOf<Uri?>(null)
    var sideImageUri by mutableStateOf<Uri?>(null)
    
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
            
            try {
                // 1. Upload Top Image
                val topFile = getFileFromUri(context, topUri, "top_image.jpg")
                val topPart = MultipartBody.Part.createFormData(
                    "file", topFile.name, topFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val topRes = apiService.uploadTopImage(topPart)
                if (!topRes.isSuccessful || topRes.body()?.ok != true) {
                    throw Exception("Top upload failed")
                }

                // 2. Upload Side Image
                val sideFile = getFileFromUri(context, sideUri, "side_image.jpg")
                val sidePart = MultipartBody.Part.createFormData(
                    "file", sideFile.name, sideFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val sideRes = apiService.uploadSideImage(sidePart)
                if (!sideRes.isSuccessful || sideRes.body()?.ok != true) {
                    throw Exception("Side upload failed")
                }

                // 3. Trigger Full Process Pipeline
                val processRes = apiService.processImages()
                if (processRes.isSuccessful && processRes.body()?.ok == true) {
                    resultData = processRes.body()?.data
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
