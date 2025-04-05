package fr.louisvolat.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import fr.louisvolat.R
import fr.louisvolat.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

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
            // Utiliser NavController pour naviguer vers CreateTravelFragment
            navController.navigate(R.id.action_travels_to_createTravel)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.createTravelFragment -> binding.fab.hide()
                else -> binding.fab.show()
            }
        }
    }

    private fun setupNavigation() {
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)
    }
}