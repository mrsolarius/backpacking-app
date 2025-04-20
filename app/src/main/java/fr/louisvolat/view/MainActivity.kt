package fr.louisvolat.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import fr.louisvolat.R
import fr.louisvolat.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Définir le launcher pour détecter la création d'un voyage
    private val createTravelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Rafraîchir les données si nécessaire
            // Vous pourriez avoir besoin de communiquer avec le fragment actif
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val currentFragment = navHostFragment.childFragmentManager.fragments[0]
            if (currentFragment is TravelsFragment) {
                currentFragment.refreshData()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialiser NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setupNavigation()
        setupFab()
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            // Lancer la nouvelle Activity au lieu d'utiliser NavController
            launchCreateTravelActivity()
        }

        navController.addOnDestinationChangedListener { _, _, _ ->
            // Vous pouvez supprimer la référence à createTravelFragment
            // puisqu'il sera dans une Activity séparée maintenant
            binding.fab.show()
        }
    }

    // Nouvelle méthode pour lancer l'activity de création
    private fun launchCreateTravelActivity() {
        val intent = Intent(this, CreateTravelActivity::class.java)
        createTravelLauncher.launch(intent)
    }

    private fun setupNavigation() {
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)
    }
}