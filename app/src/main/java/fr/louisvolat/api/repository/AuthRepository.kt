package fr.louisvolat.api.repository

import android.content.Context
import fr.louisvolat.api.ApiClient
import fr.louisvolat.api.dto.LoginRequest
import fr.louisvolat.api.dto.LoginResponse
import fr.louisvolat.api.dto.RegisterRequest
import fr.louisvolat.api.dto.RegisterResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Repository qui gère l'authentification et le stockage du token
 */
class AuthRepository(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    /**
     * Connexion utilisateur
     */
    fun login(email: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val loginRequest = LoginRequest(email, password)

        apiClient.authService.login(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        // Sauvegarde du token
                        apiClient.saveToken(it.token)
                        onSuccess(it.token)
                    } ?: onError("Réponse vide")
                } else {
                    when (response.code()) {
                        401 -> onError("Email ou mot de passe incorrect")
                        else -> onError("Erreur ${response.code()}: ${response.message()}")
                    }
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                onError("Échec de connexion: ${t.message}")
            }
        })
    }

    /**
     * Inscription utilisateur (disponible uniquement en localhost)
     */
    fun register(name: String, email: String, password: String,
                 onSuccess: (RegisterResponse) -> Unit,
                 onError: (String) -> Unit) {

        val registerRequest = RegisterRequest(name, email, password)

        apiClient.authService.register(registerRequest).enqueue(object : Callback<RegisterResponse> {
            override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        onSuccess(it)
                    } ?: onError("Réponse vide")
                } else {
                    when (response.code()) {
                        400 -> onError("Données invalides")
                        403 -> onError("Inscription disponible uniquement en localhost")
                        else -> onError("Erreur ${response.code()}: ${response.message()}")
                    }
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                onError("Échec d'inscription: ${t.message}")
            }
        })
    }

    /**
     * Déconnexion (suppression du token)
     */
    fun logout() {
        apiClient.clearToken()
    }

    /**
     * Vérifier si l'utilisateur est authentifié
     */
    fun isAuthenticated(): Boolean {
        return apiClient.isAuthenticated()
    }
}