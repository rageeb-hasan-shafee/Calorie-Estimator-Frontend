package com.example.demo

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface CalorieApiService {
    @GET("test")
    suspend fun testApi(): Response<GenericResponse>

    @GET("/")
    suspend fun getRoot(): Response<Map<String, String>>

    @Multipart
    @POST("upload/top")
    suspend fun uploadTopImage(@Part file: MultipartBody.Part): Response<GenericResponse>

    @Multipart
    @POST("upload/side")
    suspend fun uploadSideImage(@Part file: MultipartBody.Part): Response<GenericResponse>

    @POST("process")
    suspend fun processImages(): Response<ProcessResponse>

    @GET("result/segmentation/top")
    suspend fun getTopSegmentation(): Response<SegmentationListResponse>

    @GET("result/segmentation/side")
    suspend fun getSideSegmentation(): Response<SegmentationListResponse>

    @GET("result/segmentation/top/content/{filename}")
    suspend fun getTopSegmentationContent(@Path("filename") filename: String): Response<NpyContentResponse>

    @GET("result/segmentation/side/content/{filename}")
    suspend fun getSideSegmentationContent(@Path("filename") filename: String): Response<NpyContentResponse>

    @GET("result/classification/top")
    suspend fun getTopClassification(): Response<ClassificationListResponse>

    @GET("result/classification/side")
    suspend fun getSideClassification(): Response<ClassificationListResponse>

    @GET("result/classification/top/content/{category}/{filename}")
    suspend fun getTopClassificationContent(
        @Path("category") category: String,
        @Path("filename") filename: String
    ): Response<NpyContentResponse>

    @GET("result/classification/side/content/{category}/{filename}")
    suspend fun getSideClassificationContent(
        @Path("category") category: String,
        @Path("filename") filename: String
    ): Response<NpyContentResponse>
}
