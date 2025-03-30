package fr.louisvolat.api.exception

import android.content.Context
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.UnknownServiceException

class ErrorInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: UnknownServiceException) {
            showToast("Connexion non sécurisée interdite ! Utilisez HTTPS.", context)
            // Empêcher le crash en retournant une réponse factice
            return createErrorResponse(chain, "Erreur de sécurité : connexion HTTP interdite")
        } catch (e: Exception) {
            showToast("Erreur réseau: ${e.localizedMessage}", context)
            return createErrorResponse(chain, "Erreur réseau : ${e.localizedMessage}")
        }
    }

    private fun showToast(message: String, context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun createErrorResponse(chain: Interceptor.Chain, message: String): okhttp3.Response {
        return okhttp3.Response.Builder()
            .request(chain.request())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(400) // Code HTTP pour erreur (peut être 403 ou 500 selon le contexte)
            .message(message)
            .body("".toResponseBody(null))
            .build()
    }
}
