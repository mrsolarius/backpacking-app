package fr.louisvolat.api

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.GsonBuilder
import fr.louisvolat.api.services.AuthService
import fr.louisvolat.api.services.CoordinateService
import fr.louisvolat.api.services.PictureService
import fr.louisvolat.api.services.TravelService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire pour les services API avec gestion du token JWT
 */
class ApiClient(context: Context) {
    companion object {
        private const val PREF_NAME = "api_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val BASE_URL_PREF_NAME = "api_url" // À remplacer avec votre URL
        private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080" // URL par défaut


        @Volatile
        private var INSTANCE: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create()

    // Services
    val authService: AuthService
    val travelService: TravelService
    val coordinateService: CoordinateService
    val pictureService: PictureService

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
            val token = getToken()
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

    // Méthodes de gestion du token
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun isAuthenticated(): Boolean {
        return !getToken().isNullOrEmpty()
    }
}