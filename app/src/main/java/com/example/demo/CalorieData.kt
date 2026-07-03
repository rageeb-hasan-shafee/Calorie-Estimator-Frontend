package com.example.demo

data class UploadResponse(
    val success: Boolean,
    val message: String
)

data class NumpyResponse(
    val data: List<List<Float>> // Placeholder for numpy array data
)

data class CalorieResult(
    val calories: Double,
    val foodName: String,
    val details: String
)
