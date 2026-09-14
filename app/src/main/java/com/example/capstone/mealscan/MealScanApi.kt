package com.example.capstone.mealscan

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Thrown for both network failures and non-2xx responses, message is user-facing. Mirrors fetchJSON() in app.js. */
class ApiException(message: String) : Exception(message)

data class PipelineState(val stages: Map<String, Boolean>, val completedStages: List<String>)

/**
 * Thin OkHttp wrapper over the Meal Scan FastAPI backend. Endpoint shapes and
 * error-surfacing behavior are ported 1:1 from app.js's fetchJSON().
 */
class MealScanApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // Increased for large mask downloads
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private suspend fun execute(request: Request): JSONObject? = withContext(Dispatchers.IO) {
        val response: Response
        try {
            response = client.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException("Network error reaching ${request.url} (${e.message})")
        }
        response.use { res ->
            val bodyText = res.body?.string()
            val json = try {
                if (bodyText.isNullOrBlank()) null else JSONObject(bodyText)
            } catch (_: Exception) {
                null
            }
            if (!res.isSuccessful) {
                val detail = json?.optString("detail")?.takeIf { it.isNotBlank() } ?: "HTTP ${res.code}"
                throw ApiException(detail)
            }
            json
        }
    }

    suspend fun uploadImage(base: String, view: ScanView, uri: Uri, contentResolver: ContentResolver, cacheDir: File) {
        val viewPath = view.name.lowercase()
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val tmp = File.createTempFile("upload_$viewPath", ".tmp", cacheDir)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: throw ApiException("Could not read the selected image")

            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val filePart = tmp.asRequestBody(mime.toMediaTypeOrNull())
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.$extension", filePart)
                .build()
            val request = Request.Builder()
                .url("$base/upload/$viewPath")
                .post(body)
                .build()
            execute(request)
        } finally {
            tmp.delete()
        }
    }

    suspend fun startProcess(base: String): String {
        val request = Request.Builder()
            .url("$base/process")
            .post("".toRequestBody(null))
            .build()
        val json = execute(request)
        return json?.optString("message")?.takeIf { it.isNotBlank() } ?: "Processing started on the server."
    }

    suspend fun fetchState(base: String): PipelineState {
        val json = execute(Request.Builder().url("$base/result/state").get().build()) ?: JSONObject()
        val stagesJson = json.optJSONObject("stages") ?: JSONObject()
        val stages = stagesJson.keys().asSequence().associateWith { key -> stagesJson.optBoolean(key) }
        val completedJson = json.optJSONArray("completed_stages") ?: JSONArray()
        val completed = (0 until completedJson.length()).map { completedJson.optString(it) }
        return PipelineState(stages, completed)
    }

    suspend fun fetchSegmentationList(base: String, view: ScanView): List<String> {
        val viewPath = view.name.lowercase()
        val json = execute(Request.Builder().url("$base/result/segmentation/$viewPath").get().build()) ?: JSONObject()
        val files = json.optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).map { files.optString(it) }
    }

    suspend fun fetchMaskContent(base: String, view: ScanView, filename: String): Array<BooleanArray> {
        val viewPath = view.name.lowercase()
        val encoded = Uri.encode(filename)
        val json = execute(
            Request.Builder().url("$base/result/segmentation/$viewPath/content/$encoded").get().build()
        ) ?: JSONObject()
        val rows = json.optJSONArray("mask") ?: JSONArray()
        return Array(rows.length()) { r ->
            val row = rows.optJSONArray(r) ?: JSONArray()
            BooleanArray(row.length()) { c -> isTruthy(row.opt(c)) }
        }
    }

    private fun isTruthy(value: Any?): Boolean = when {
        value == null || value == JSONObject.NULL -> false
        value is Boolean -> value
        value is Number -> value.toDouble() != 0.0
        else -> value.toString() != "0" && value.toString().isNotBlank()
    }

    /** category name -> list of mask filenames it contains. */
    suspend fun fetchClassification(base: String, view: ScanView): LinkedHashMap<String, List<String>> {
        val viewPath = view.name.lowercase()
        val json = execute(Request.Builder().url("$base/result/classification/$viewPath").get().build()) ?: JSONObject()
        val categories = json.optJSONObject("categories") ?: JSONObject()
        val result = LinkedHashMap<String, List<String>>()
        categories.keys().forEach { category ->
            val files = categories.optJSONArray(category) ?: JSONArray()
            result[category] = (0 until files.length()).map { files.optString(it) }
        }
        return result
    }

    data class VolumeResult(val totalCalories: Double, val perFood: LinkedHashMap<String, Pair<Double, Double?>>)

    suspend fun fetchVolumeEstimation(base: String): VolumeResult {
        val json = execute(Request.Builder().url("$base/volume-estimation").get().build()) ?: JSONObject()
        val data = json.optJSONObject("data") ?: JSONObject()
        val totals = data.optJSONObject("meal_totals") ?: JSONObject()
        val totalCalories = totals.optDouble("calories_kcal", 0.0).let { if (it.isNaN()) 0.0 else it }

        val breakdown = data.optJSONObject("per_food_breakdown") ?: JSONObject()
        val perFood = LinkedHashMap<String, Pair<Double, Double?>>()
        breakdown.keys().forEach { name ->
            val info = breakdown.optJSONObject(name) ?: JSONObject()
            val cal = info.optDouble("calories_kcal", 0.0).let { if (it.isNaN()) 0.0 else it }
            val vol = if (info.has("volume_cm3") && !info.isNull("volume_cm3")) info.optDouble("volume_cm3") else null
            perFood[name] = cal to vol
        }
        return VolumeResult(totalCalories, perFood)
    }
}
