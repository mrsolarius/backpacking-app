package fr.louisvolat.api.services

import fr.louisvolat.api.dto.PictureDTO
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*

interface PictureService {
    @GET("/api/travels/{travelId}/pictures")
    fun getPicturesByTravel(@Path("travelId") travelId: Long): Call<List<PictureDTO>>

    @GET("/api/travels/{travelId}/pictures/{id}")
    fun getPictureById(
        @Path("travelId") travelId: Long,
        @Path("id") id: Long
    ): Call<PictureDTO>

    @POST("/api/travels/{travelId}/pictures")
    @Multipart
    fun uploadPicture(
        @Path("travelId") travelId: Long,
        @Part picture: MultipartBody.Part
    ): Call<String>

    @POST("/api/travels/{travelId}/pictures/{id}/set-as-cover")
    fun setCoverPicture(
        @Path("travelId") travelId: Long,
        @Path("id") id: Long
    ): Call<String>

    @DELETE("/api/travels/{travelId}/pictures/{id}")
    fun deletePicture(
        @Path("travelId") travelId: Long,
        @Path("id") id: Long
    ): Call<String>
}