package fr.louisvolat.api.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gestionnaire sécurisé des tokens JWT utilisant Android Keystore
 */
class SecureTokenManager private constructor(context: Context) {
    companion object {
        private const val TAG = "SecureTokenManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "jwt_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = "]"

        private const val PREF_NAME = "secure_token_prefs"
        private const val ENCRYPTED_TOKEN_KEY = "encrypted_jwt_token"
        private const val TOKEN_EXPIRATION_KEY = "token_expiration"

        // Au lieu d'une instance statique avec contexte, utilisez une méthode factory
        fun getInstance(context: Context): SecureTokenManager {
            // Utilisez applicationContext pour éviter les fuites de mémoire
            return SecureTokenManager(context.applicationContext)
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        // S'assurer que la clé existe dans le keystore
        createKeyIfNotExists()
    }

    /**
     * Crée une clé de chiffrement AES dans le Android Keystore si elle n'existe pas déjà
     */
    private fun createKeyIfNotExists() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )

                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false) // Ne nécessite pas d'authentification utilisateur
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()

                Log.d(TAG, "Nouvelle clé générée dans le Android Keystore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création de la clé: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Récupère la clé de chiffrement depuis le Android Keystore
     */
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la récupération de la clé: ${e.message}")
            null
        }
    }

    /**
     * Chiffre le token JWT et le stocke dans les SharedPreferences
     */
    fun saveToken(token: String) {
        try {
            val secretKey = getSecretKey() ?: return

            // Initialiser le chiffrement
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Récupérer l'IV généré
            val iv = cipher.iv

            // Chiffrer le token
            val tokenBytes = token.toByteArray(StandardCharsets.UTF_8)
            val encryptedToken = cipher.doFinal(tokenBytes)

            // Combiner l'IV et le token chiffré pour le stockage
            val ivAndEncryptedToken = Base64.encodeToString(iv, Base64.NO_WRAP) +
                    IV_SEPARATOR +
                    Base64.encodeToString(encryptedToken, Base64.NO_WRAP)

            // Enregistrer dans les SharedPreferences
            prefs.edit().putString(ENCRYPTED_TOKEN_KEY, ivAndEncryptedToken).apply()

            // Extraire et enregistrer la date d'expiration
            extractExpirationFromToken(token)?.let { expirationDate ->
                saveTokenExpiration(expirationDate)
            }

            Log.d(TAG, "Token JWT chiffré et enregistré avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chiffrement du token: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Récupère et déchiffre le token JWT
     * @return Le token JWT déchiffré ou null en cas d'erreur
     */
    fun getToken(): String? {
        val encryptedData = prefs.getString(ENCRYPTED_TOKEN_KEY, null) ?: return null

        return try {
            val secretKey = getSecretKey() ?: return null

            // Séparer l'IV et le token chiffré
            val parts = encryptedData.split(IV_SEPARATOR)
            if (parts.size != 2) return null

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedToken = Base64.decode(parts[1], Base64.NO_WRAP)

            // Initialiser le déchiffrement
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            // Déchiffrer le token
            val decryptedToken = cipher.doFinal(encryptedToken)
            String(decryptedToken, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du déchiffrement du token: ${e.message}")
            null
        }
    }

    /**
     * Supprimer le token et sa date d'expiration
     */
    fun clearToken() {
        prefs.edit()
            .remove(ENCRYPTED_TOKEN_KEY)
            .remove(TOKEN_EXPIRATION_KEY)
            .apply()

        Log.d(TAG, "Token JWT et date d'expiration supprimés")
    }

    /**
     * Vérifier si l'utilisateur est authentifié avec un token valide
     */
    fun isAuthenticated(): Boolean {
        val token = getToken()
        return !token.isNullOrEmpty() && !isTokenExpired()
    }

    /**
     * Extraire la date d'expiration du token JWT
     */
    private fun extractExpirationFromToken(token: String): Date? {
        return try {
            // Extraire la partie payload (deuxième partie du token)
            val parts = token.split(".")
            if (parts.size != 3) return null

            // Décoder la partie payload (Base64)
            val payload = Base64.decode(parts[1], Base64.URL_SAFE)
            val payloadJson = String(payload)

            // Parser le JSON pour extraire exp
            val jsonObject = org.json.JSONObject(payloadJson)
            val expClaim = jsonObject.optLong("exp", 0)
            if (expClaim > 0) Date(expClaim * 1000) else null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'extraction de la date d'expiration: ${e.message}")
            null
        }
    }

    /**
     * Enregistrer la date d'expiration du token
     */
    private fun saveTokenExpiration(expirationDate: Date) {
        prefs.edit().putLong(TOKEN_EXPIRATION_KEY, expirationDate.time).apply()
    }

    /**
     * Vérifier si le token est expiré
     */
    fun isTokenExpired(): Boolean {
        val expirationTime = prefs.getLong(TOKEN_EXPIRATION_KEY, 0)

        // Si aucune date d'expiration n'est enregistrée, considérer comme expiré
        if (expirationTime == 0L) return true

        // Vérifier si la date d'expiration est passée
        return Date().time >= expirationTime
    }
}