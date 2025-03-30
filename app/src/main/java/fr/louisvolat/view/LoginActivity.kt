package fr.louisvolat.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import kotlinx.coroutines.*

class LoginActivity(private val dispatcherMain: CoroutineDispatcher = Dispatchers.Main) : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var settingsButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialiser ApiClient
        apiClient = ApiClient.getInstance(applicationContext)

        // Vérifier si déjà connecté avec un token valide
        if (apiClient.isAuthenticated()) {
            navigateToMainActivity()
            return
        }

        // Initialiser les vues
        usernameEditText = findViewById(R.id.editTextUsername)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
        settingsButton = findViewById(R.id.buttonSettings)
        progressBar = findViewById(R.id.progressBar)

        // Configurer les écouteurs
        loginButton.setOnClickListener { handleLogin() }
        settingsButton.setOnClickListener { openSettings() }
    }

    private fun handleLogin() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // Validation des champs
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fields_required, Toast.LENGTH_SHORT).show()
            return
        }

        // Afficher la barre de progression
        setLoading(true)

        // Appel à l'API pour le login
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Utiliser la méthode auth.login au lieu de authService.login directement
                var loginSuccess = false
                var loginError: String? = null

                // Créer un CompletableDeferred pour attendre la réponse asynchrone
                val deferred = CompletableDeferred<Unit>()

                apiClient.auth.login(username, password,
                    onSuccess = {
                        loginSuccess = true
                        deferred.complete(Unit)
                    },
                    onError = { errorMessage ->
                        loginError = errorMessage
                        deferred.complete(Unit)
                    }
                )

                // Attendre que l'opération asynchrone soit terminée
                deferred.await()

                withContext(dispatcherMain) {
                    handleLoginResponse(loginSuccess, loginError)
                }
            } catch (e: Exception) {
                withContext(dispatcherMain) {
                    Toast.makeText(this@LoginActivity, e.message ?: getString(R.string.login_error), Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun handleLoginResponse(isSuccess: Boolean, loginError: String?) {
        setLoading(false)
        if (isSuccess) {
            navigateToMainActivity()
        } else {
            Toast.makeText(this@LoginActivity, loginError ?: getString(R.string.login_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun openSettings() {
        // Créer un DialogFragment pour les paramètres
        val settingsDialog = SettingsDialogFragment()
        settingsDialog.show(supportFragmentManager, "SettingsDialog")
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
        settingsButton.isEnabled = !isLoading
        usernameEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading
    }
}