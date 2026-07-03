package com.example.demo

import com.google.gson.annotations.SerializedName

data class GenericResponse(
    val ok: Boolean,
    val message: String? = null,
    val saved_as: String? = null
)

data class NumpyResponse(
    val data: List<List<Float>>
)

data class ProcessResponse(
    val ok: Boolean,
    val message: String,
    val data: NutritionData
)

data class NutritionData(
    val meal_totals: CalorieResult,
    val per_food_breakdown: Map<String, FoodBreakdown>? = null
)

data class FoodBreakdown(
    val volume_cm3: Double,
    val calories_kcal: Double,
    val macros: Macros
)

data class Macros(
    val carbohydrates_g: Double,
    val protein_g: Double,
    val fat_g: Double,
    val fiber_g: Double
)

data class CalorieResult(
    @SerializedName("calories_kcal") val calories: Double,
    @SerializedName("carbohydrates_g") val carbs: Double,
    @SerializedName("protein_g") val protein: Double,
    @SerializedName("fat_g") val fat: Double,
    @SerializedName("fiber_g") val fiber: Double,
    @SerializedName("sodium_mg") val sodium: Double,
    @SerializedName("calcium_mg") val calcium: Double,
    @SerializedName("iron_mg") val iron: Double
)
