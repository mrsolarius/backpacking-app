package fr.louisvolat.view

import android.os.Build
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Explode
import android.view.MenuItem
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import fr.louisvolat.R
import fr.louisvolat.databinding.ActivityCreateTravelBinding

class CreateTravelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTravelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTravelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurer la barre d'action avec une flèche de retour
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.create_travel_title)

        // Ajouter le fragment au conteneur si c'est la première création
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, CreateTravelFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Gérer le clic sur la flèche de retour
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}