package fr.louisvolat.view

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import fr.louisvolat.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        initDefaultPreferences()
    }

    private fun initDefaultPreferences() {
        // Force l'écriture de la valeur par défaut du XML si elle n'existe pas
        preferenceManager.sharedPreferences?.apply {
            if (!contains("api_url")) {
                edit().putString("api_url", "http://10.0.2.2:8080").apply()
            }
        }
    }
}