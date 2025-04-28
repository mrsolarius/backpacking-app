package fr.louisvolat.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.data.viewmodel.TravelViewModel
import fr.louisvolat.data.viewmodel.TravelViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.databinding.FragmentDetailBinding


class DetailFragment : Fragment() {

    // Binding pour le fragment
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    // NavController de ce fragment
    private lateinit var navController: NavController

    private lateinit var viewModel: TravelViewModel

    private fun setupBottomNavAnimations() {
        // Animer l'apparition du bottom nav
        binding.bottomNavigation.apply {
            alpha = 0f
            translationY = 100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = BackpakingLocalDataBase.getDatabase(requireContext())
        val pictureRepository = PictureRepository(
            database.pictureDao(),
            ApiClient.getInstance(requireContext()),
        )
        viewModel = ViewModelProvider(
            requireActivity(),
            TravelViewModelFactory(
                TravelRepository(
                    database.travelDao(),
                    database.pictureDao(),
                    ApiClient.getInstance(requireContext()),
                    pictureRepository
                )
            )
        )[TravelViewModel::class.java]

        // Activer les transitions partagées
        postponeEnterTransition()

        // Récupérer l'ID du voyage
        val travelId = arguments?.getLong("travelId") ?: -1L

        if (travelId != -1L) {
            // Stocker l'ID dans le ViewModel
            viewModel.setSelectedTravel(travelId)
            // Charger les détails du voyage
            viewModel.getTravelById(travelId).observe(viewLifecycleOwner) { travel ->
                // Mettre à jour le titre dans la toolbar
                binding.toolbarTravelName.text = travel.name

                // Démarrer la transition différée
                startPostponedEnterTransition()
            }

        } else {
            startPostponedEnterTransition()
        }

        // Configurer les animations de la bottom navigation
        setupBottomNavAnimations()
        // Récupère le NavController associé à ce NavHostFragment
        val navHostFragment = childFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val navGraph = navController.navInflater.inflate(R.navigation.bottom_nav_graph)
        navController.graph = navGraph

        setupNavigation()
    }

    private fun setupNavigation() {
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)
    }
}