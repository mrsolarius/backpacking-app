package fr.louisvolat.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.data.viewmodel.TravelViewModel
import fr.louisvolat.data.viewmodel.TravelViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.database.entity.Travel
import fr.louisvolat.databinding.FragmentTravelsListBinding
import fr.louisvolat.view.adapter.TravelAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TravelsListFragment : Fragment() {

    private var _binding: FragmentTravelsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val viewModel: TravelViewModel by viewModels {
        val database = BackpakingLocalDataBase.getDatabase(requireContext())
        val pictureRepository = PictureRepository(
            database.pictureDao(),
            ApiClient.getInstance(requireContext()),
        )
        TravelViewModelFactory(
            TravelRepository(
                database.travelDao(),
                database.pictureDao(),
                ApiClient.getInstance(requireContext()),
                pictureRepository
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTravelsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = binding.swipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener { refreshData() }

        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = TravelAdapter { travel, sharedView ->
                // Navigation avec transition partagée
                navigateToTravelDetail(travel, sharedView)
            }
        }
    }

    private fun navigateToTravelDetail(travel: Travel, sharedView: View) {
        // Créer le bundle avec l'ID du voyage
        val bundle = Bundle().apply {
            putLong("travelId", travel.id)
        }

        // Configurer les transitions
        val extras = FragmentNavigatorExtras(
            sharedView to "travel_name_transition"
        )

        // Démarrer la transition
        findNavController().navigate(
            R.id.action_list_to_details,
            bundle,
            null,
            extras
        )
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.travelsWithCoverPictures.collectLatest { travelsWithPictures ->
                    (binding.recyclerView.adapter as? TravelAdapter)?.submitList(travelsWithPictures)
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    fun refreshData() {
        swipeRefreshLayout.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.refreshTravels(
                    onComplete = {
                        // Rafraîchissement terminé
                        Log.d("TravelsFragment", "Travels refreshed")
                    }
                )
            } catch (e: Exception) {
                // Gérer les erreurs ici
                Log.e("TravelsFragment", "Error refreshing travels", e)
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}