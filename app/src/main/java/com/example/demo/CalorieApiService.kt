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

    // Keeping these as requested by user, assuming partner will add them
    @GET("api/numpy/top")
    suspend fun getTopNumpy(): Response<NumpyResponse>

    @GET("api/numpy/side")
    suspend fun getSideNumpy(): Response<NumpyResponse>
}
