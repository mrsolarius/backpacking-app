package fr.louisvolat.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import fr.louisvolat.R
import fr.louisvolat.api.ApiClient
import fr.louisvolat.api.dto.PictureDTO
import fr.louisvolat.data.repository.PictureRepository
import fr.louisvolat.data.repository.TravelRepository
import fr.louisvolat.data.viewmodel.PictureViewModel
import fr.louisvolat.data.viewmodel.PictureViewModelFactory
import fr.louisvolat.data.viewmodel.TravelViewModel
import fr.louisvolat.data.viewmodel.TravelViewModelFactory
import fr.louisvolat.database.BackpakingLocalDataBase
import fr.louisvolat.databinding.FragmentTravelDetailBinding
import fr.louisvolat.view.adapter.PictureAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TravelDetailFragment : Fragment() {

    private var _binding: FragmentTravelDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var pictureViewModel: PictureViewModel
    private val travelViewModel: TravelViewModel by activityViewModels()

    private lateinit var pictureAdapter: PictureAdapter
    private var travelId: Long = -1L
    private lateinit var apiUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupérer l'URL de l'API depuis les préférences
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        apiUrl = prefs.getString("api_url", "") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTravelDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialiser les ViewModels
        initViewModels()

        // Configurer le RecyclerView
        setupRecyclerView()

        // Observer les données
        setupObservers()

        // Configurer le SwipeRefreshLayout
        binding.swipeRefresh.setOnRefreshListener {
            refreshPictures()
        }
    }

    private fun initViewModels() {
        // Initialiser le PictureViewModel
        val database = BackpakingLocalDataBase.getDatabase(requireContext())
        val pictureRepository = PictureRepository(
            database.pictureDao(),
            ApiClient.getInstance(requireContext())
        )

        pictureViewModel = ViewModelProvider(
            this,
            PictureViewModelFactory(pictureRepository)
        )[PictureViewModel::class.java]

        // Observer l'ID du voyage depuis le ViewModel partagé
        travelViewModel.selectedTravelId.observe(viewLifecycleOwner) { id ->
            if (id != -1L && id != travelId) {
                travelId = id
                loadPictures()
            }
        }
    }

    private fun setupRecyclerView() {
        pictureAdapter = PictureAdapter(
            apiUrl,
            onPictureLongClick = { picture, view -> handlePictureLongClick(picture, view) },
            onPictureClick = { picture -> handlePictureClick(picture) }
        )

        binding.recyclerViewPictures.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = pictureAdapter
        }
    }

    private fun setupObservers() {
        // Observer le chargement
        pictureViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading && pictureAdapter.itemCount == 0) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = isLoading && pictureAdapter.itemCount > 0
        }

        // Observer les erreurs
        pictureViewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                pictureViewModel.resetOperationState()
            }
        }

        // Observer le succès des opérations
        pictureViewModel.operationSuccess.observe(viewLifecycleOwner) { status ->
            when (status) {
                PictureViewModel.OperationStatus.SET_COVER_SUCCESS -> {
                    Toast.makeText(requireContext(), getString(R.string.set_cover_success), Toast.LENGTH_SHORT).show()
                    pictureViewModel.resetOperationState()
                }
                PictureViewModel.OperationStatus.DELETE_SUCCESS -> {
                    Toast.makeText(requireContext(), getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                    pictureViewModel.resetOperationState()
                }
                else -> {
                    // Ne rien faire pour NONE
                }
            }
        }

        // Observer les photos depuis l'API
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pictureViewModel.pictures.collectLatest { pictures ->
                    pictureAdapter.submitList(pictures)
                    updateEmptyState(pictures.isEmpty())
                }
            }
        }

        // Observer les photos locales
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pictureViewModel.localPictures.collectLatest { localPictures ->
                    pictureAdapter.updateLocalPictures(localPictures)
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvNoPictures.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun loadPictures() {
        if (travelId != -1L) {
            pictureViewModel.loadPicturesForTravel(travelId)
            // S'abonner aux mises à jour en temps réel
            pictureViewModel.observePicturesForTravelFromAPI(travelId)
        }
    }

    private fun refreshPictures() {
        if (travelId != -1L) {
            pictureViewModel.loadPicturesForTravel(travelId)
        } else {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun handlePictureClick(picture: PictureDTO) {
        // Pour une future fonctionnalité de visualisation plein écran
        Toast.makeText(requireContext(), getString(R.string.view_full_image), Toast.LENGTH_SHORT).show()
    }

    private fun handlePictureLongClick(picture: PictureDTO, view: View): Boolean {
        if (travelId == -1L) return false

        val popup = android.widget.PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.menu_picture_options, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_set_as_cover -> {
                    picture.id?.let { pictureId ->
                        pictureViewModel.setCoverPicture(travelId, pictureId)
                    }
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmationDialog(picture)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return true
    }

    private fun showDeleteConfirmationDialog(picture: PictureDTO) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_confirmation_title))
            .setMessage(getString(R.string.delete_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                picture.id?.let { pictureId ->
                    pictureViewModel.deletePicture(travelId, pictureId)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}