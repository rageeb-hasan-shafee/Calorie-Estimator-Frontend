package com.example.demo

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CalorieApiService {
    @Multipart
    @POST("upload/top")
    suspend fun uploadTopImage(@Part file: MultipartBody.Part): Response<GenericResponse>

    @Multipart
    @POST("upload/side")
    suspend fun uploadSideImage(@Part file: MultipartBody.Part): Response<GenericResponse>

    @POST("process")
    suspend fun processImages(): Response<ProcessResponse>

    @GET("result/classification/top")
    suspend fun getTopClassification(): Response<ClassificationListResponse>

    @GET("result/classification/side")
    suspend fun getSideClassification(): Response<ClassificationListResponse>
    
    @GET("result/segmentation/top")
    suspend fun getTopSegmentation(): Response<SegmentationListResponse>

    @GET("result/segmentation/side")
    suspend fun getSideSegmentation(): Response<SegmentationListResponse>
}
