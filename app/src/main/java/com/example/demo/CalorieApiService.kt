package com.example.demo

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CalorieApiService {
    @Multipart
    @POST("api/upload/top")
    suspend fun uploadTopImage(@Part image: MultipartBody.Part): Response<UploadResponse>

    @Multipart
    @POST("api/upload/side")
    suspend fun uploadSideImage(@Part image: MultipartBody.Part): Response<UploadResponse>

    @GET("api/numpy/top")
    suspend fun getTopNumpy(): Response<NumpyResponse>

    @GET("api/numpy/side")
    suspend fun getSideNumpy(): Response<NumpyResponse>

    @GET("api/result")
    suspend fun getFinalResult(): Response<CalorieResult>
}
