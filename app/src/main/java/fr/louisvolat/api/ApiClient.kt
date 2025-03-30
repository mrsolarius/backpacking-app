package fr.louisvolat.api

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.GsonBuilder
import fr.louisvolat.api.dto.LoginRequest
import fr.louisvolat.api.dto.LoginResponse
import fr.louisvolat.api.security.SecureTokenManager
import fr.louisvolat.api.services.AuthService
import fr.louisvolat.api.services.CoordinateService
import fr.louisvolat.api.services.PictureService
import fr.louisvolat.api.services.TravelService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire pour les services API avec gestion sécurisée du token JWT
 * et repository d'authentification intégré
 */
class ApiClient private constructor(context: Context) {
    companion object {
        private const val PREF_NAME = "api_prefs"
        private const val BASE_URL_PREF_NAME = "api_url"
        private const val DEFAULT_BASE_URL = "http://192.168.25.28:8080" // URL par défaut

        // Méthode factory au lieu d'une instance statique
        fun getInstance(context: Context): ApiClient {
            // Utiliser applicationContext pour éviter les fuites de mémoire
            return ApiClient(context.applicationContext)
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create()

    // Gestionnaire sécurisé des tokens
    private val tokenManager = SecureTokenManager.getInstance(context)

    // Service d'authentification (private pour qu'il ne soit accessible que via auth)
    private val authService: AuthService

    // Services publics
    val travelService: TravelService
    val coordinateService: CoordinateService
    val pictureService: PictureService

    // Repository d'authentification intégré
    val auth: Auth

    init {
        // Client pour les services qui ne nécessitent pas d'authentification
        val publicHttpClient = createBaseHttpClient()
        val publicRetrofit = createRetrofit(publicHttpClient)

        // Client avec intercepteur d'authentification
        val authHttpClient = createAuthenticatedHttpClient()
        val authRetrofit = createRetrofit(authHttpClient)

        // Initialisation des services
        authService = publicRetrofit.create(AuthService::class.java)
        travelService = authRetrofit.create(TravelService::class.java)
        coordinateService = authRetrofit.create(CoordinateService::class.java)
        pictureService = authRetrofit.create(PictureService::class.java)

        // Initialiser le repository d'authentification
        auth = Auth()
    }

    private fun getBaseUrl(): String {
        return prefs.getString(BASE_URL_PREF_NAME, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    private fun createBaseHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun createAuthenticatedHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = tokenManager.getToken()
            val newRequest = if (!token.isNullOrEmpty()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(newRequest)
        }

        return createBaseHttpClient().newBuilder()
            .addInterceptor(authInterceptor)
            .build()
    }

    private fun createRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // Classe interne pour encapsuler les fonctionnalités d'authentification
    inner class Auth {
        /**
         * Connexion utilisateur
         */
        fun login(email: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
            val loginRequest = LoginRequest(email, password)

            authService.login(loginRequest).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.let {
                            saveToken(it.token)
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
         * Déconnexion (suppression du token)
         */
        fun logout() {
            clearToken()
        }
    }

    // Méthodes déléguées au SecureTokenManager
    fun saveToken(token: String) {
        tokenManager.saveToken(token)
    }

    fun clearToken() {
        tokenManager.clearToken()
    }

    fun isAuthenticated(): Boolean {
        return tokenManager.isAuthenticated()
    }
}