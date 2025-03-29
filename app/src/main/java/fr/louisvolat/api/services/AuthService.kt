package fr.louisvolat.api.services

import fr.louisvolat.api.dto.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("/api/auth/login")
    fun login(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @POST("/api/auth/register")
    fun register(@Body registerRequest: RegisterRequest): Call<RegisterResponse>
}