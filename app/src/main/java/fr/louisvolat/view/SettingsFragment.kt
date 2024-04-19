package fr.louisvolat.view

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import fr.louisvolat.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}