package fr.louisvolat.api.services

import fr.louisvolat.api.dto.CoordinateDTO
import fr.louisvolat.api.dto.CreateCoordinateResponseConfirm
import fr.louisvolat.api.dto.CreateCoordinatesRequest
import retrofit2.Call
import retrofit2.http.*

interface CoordinateService {
    @GET("/api/travels/{travelId}/coordinates")
    fun getCoordinatesByTravel(@Path("travelId") travelId: Long): Call<List<CoordinateDTO>>

    @POST("/api/travels/{travelId}/coordinates")
    fun addCoordinatesToTravel(
        @Path("travelId") travelId: Long,
        @Body request: CreateCoordinatesRequest
    ): Call<CreateCoordinateResponseConfirm>

    @DELETE("/api/travels/{travelId}/coordinates/{id}")
    fun deleteCoordinate(
        @Path("travelId") travelId: Long,
        @Path("id") id: Long
    ): Call<String>

}