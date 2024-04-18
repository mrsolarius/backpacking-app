package fr.louisvolat.api.services

import fr.louisvolat.api.dto.CoordinateDTO
import fr.louisvolat.api.dto.CoordinateListPostDTO
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CoordinateService {
    @GET("/api/coordinates")
    fun getCoordinates(): Call<List<CoordinateDTO>>

    @POST("/api/coordinates")
    fun postCoordinates(@Body coordinates: CoordinateListPostDTO): Call<List<CoordinateDTO>>

    @GET("/api/coordinates/{id}")
    fun getCoordinate(@Path("id") id: Int): Call<CoordinateDTO>

    @DELETE("/api/coordinates/{id}")
    fun deleteCoordinate(@Path("id") id: Int): Call<String>

}