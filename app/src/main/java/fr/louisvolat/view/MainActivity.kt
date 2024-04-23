package fr.louisvolat.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import fr.louisvolat.R
import fr.louisvolat.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding
    private lateinit var mbottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainerView,MainFragment()).commit()

        mbottomNavigationView=findViewById(R.id.bottom_navigation)

        setupNavigation()
    }

    private fun setupNavigation() {
        mbottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tracker -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, MainFragment()).commit()
                    true
                }
                R.id.upload -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, UploadFragment()).commit()
                    true
                }
                R.id.settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, SettingsFragment()).commit()
                    true
                }

                else -> {
                    false
                }
            }
        }
    }
}