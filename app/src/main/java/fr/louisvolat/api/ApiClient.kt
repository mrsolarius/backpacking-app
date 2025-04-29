package fr.louisvolat.api

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import fr.louisvolat.api.dto.LoginRequest
import fr.louisvolat.api.dto.LoginResponse
import fr.louisvolat.api.exception.ErrorInterceptor
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
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit


/**
 * Gestionnaire pour les services API avec gestion sécurisée du token JWT
 * et repository d'authentification intégré
 */
class ApiClient private constructor(context: Context) {
    companion object {
        private const val BASE_URL_PREF_NAME = "api_url"

        // Méthode factory au lieu d'une instance statique
        fun getInstance(context: Context): ApiClient {
            // Utiliser applicationContext pour éviter les fuites de mémoire
            return ApiClient(context.applicationContext)
        }
    }

    val context: Context = context.applicationContext
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").setLenient().create()

    // Gestionnaire sécurisé des tokens
    private val tokenManager = SecureTokenManager.getInstance(context)

    // Les services sont maintenant recréés à chaque accès
    val authService: AuthService
        get() = createPublicRetrofit().create(AuthService::class.java)

    // Services publics
    val travelService: TravelService
        get() = createAuthRetrofit().create(TravelService::class.java)

    val coordinateService: CoordinateService
        get() = createAuthRetrofit().create(CoordinateService::class.java)

    val pictureService: PictureService
        get() = createAuthRetrofit().create(PictureService::class.java)

    val auth: Auth = Auth()

    private fun getBaseUrl(): String {
        return prefs.getString(BASE_URL_PREF_NAME, null)
            ?: throw IllegalStateException("Base URL not set. Please set it before using the API client.")
    }

    private fun createBaseHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private fun createAuthenticatedHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = tokenManager.getToken()
            val newRequest = chain.request().newBuilder()
                .apply {
                    if (!token.isNullOrEmpty()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()
            chain.proceed(newRequest)
        }

        return createBaseHttpClient().newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(ErrorInterceptor(context))
            .build()
    }

    private fun createPublicRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(createBaseHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun createAuthRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(createAuthenticatedHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addConverterFactory(ScalarsConverterFactory.create())
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

    fun saveToken(token: String) = tokenManager.saveToken(token)
    fun clearToken() = tokenManager.clearToken()
    fun isAuthenticated() = tokenManager.isAuthenticated()
}