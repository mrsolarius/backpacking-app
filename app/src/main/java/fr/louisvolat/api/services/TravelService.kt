package fr.louisvolat.api.services

import fr.louisvolat.api.dto.CreateTravelRequest
import fr.louisvolat.api.dto.TravelDTO
import retrofit2.Call
import retrofit2.http.*

interface TravelService {
    @GET("/api/travels")
    fun getAllTravels(): Call<List<TravelDTO>>

    @GET("/api/travels/user/{userId}")
    fun getTravelsByUser(@Path("userId") userId: Long): Call<List<TravelDTO>>

    @GET("/api/travels/mine")
    fun getMyTravels(@Query("includeDetails") includeDetails: Boolean = false): Call<List<TravelDTO>>

    @GET("/api/travels/{id}")
    fun getTravelById(
        @Path("id") id: Long,
        @Query("includeDetails") includeDetails: Boolean = false
    ): Call<TravelDTO>

    @POST("/api/travels")
    fun createTravel(@Body request: CreateTravelRequest): Call<TravelDTO>

    @PUT("/api/travels/{id}")
    fun <UpdateTravelRequest> updateTravel(
        @Path("id") id: Long,
        @Body request: UpdateTravelRequest
    ): Call<TravelDTO>

    @DELETE("/api/travels/{id}")
    fun deleteTravel(@Path("id") id: Long): Call<String>
}