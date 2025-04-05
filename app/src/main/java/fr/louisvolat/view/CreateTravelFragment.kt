package fr.louisvolat.view

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import fr.louisvolat.R
import fr.louisvolat.databinding.FragmentCreateTravelBinding
import fr.louisvolat.utils.ImageUtils
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class CreateTravelFragment : Fragment() {

    private var _binding: FragmentCreateTravelBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var selectedDateTime: ZonedDateTime? = null
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")


    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri

                // Vérifier si l'image contient des coordonnées GPS
                lifecycleScope.launch {
                    if (ImageUtils.hasGpsCoordinates(requireContext(), uri)) {
                        binding.ivCoverPicture.setImageURI(uri)
                        binding.tvCoverPictureLabel.text = getString(R.string.cover_picture_selected)
                        binding.ivCoverPicture.visibility = View.VISIBLE
                    } else {
                        selectedImageUri = null
                        Snackbar.make(
                            binding.root,
                            getString(R.string.error_image_no_gps),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateTravelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        // Sélection d'image
        binding.btnSelectCoverPicture.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        // Sélection de date
        binding.btnSelectDate.setOnClickListener {
            showDateTimePicker()
        }

        // Bouton de validation
        binding.btnCreateTravel.setOnClickListener {
            if (validateForm()) {
                createTravel()
            }
        }

        // Bouton d'annulation
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()

        // Date Picker
        DatePickerDialog(
            requireContext(),
            { _, year, monthOfYear, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, monthOfYear)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Time Picker après sélection de la date
                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)

                        // Convertir Calendar en ZonedDateTime
                        val ldt = LocalDateTime.ofInstant(
                            calendar.toInstant(),
                            ZoneId.systemDefault()
                        )
                        selectedDateTime = ZonedDateTime.of(ldt, ZoneId.systemDefault())

                        // Afficher la date formatée
                        binding.tvSelectedDate.text = selectedDateTime?.format(formatter)
                        binding.tvSelectedDate.visibility = View.VISIBLE
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    0, // Toujours 0 minutes
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Validation du nom
        val name = binding.etTravelName.text.toString().trim()
        if (name.isEmpty()) {
            binding.tilTravelName.error = getString(R.string.error_name_required)
            isValid = false
        } else if (name.length < 3) {
            binding.tilTravelName.error = getString(R.string.error_name_too_short)
            isValid = false
        } else if (name.length > 255) {
            binding.tilTravelName.error = getString(R.string.error_name_too_long)
            isValid = false
        } else {
            binding.tilTravelName.error = null
        }

        // Validation de la description
        val description = binding.etTravelDescription.text.toString().trim()
        if (description.isNotEmpty()) {
            if (description.length < 25) {
                binding.tilTravelDescription.error = getString(R.string.error_description_too_short)
                isValid = false
            } else if (description.length > 8048) {
                binding.tilTravelDescription.error = getString(R.string.error_description_too_long)
                isValid = false
            } else {
                binding.tilTravelDescription.error = null
            }
        } else {
            binding.tilTravelDescription.error = null
        }

        // Validation de la date
        if (selectedDateTime == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_start_date_required),
                Toast.LENGTH_SHORT
            ).show()
            isValid = false
        }

        return isValid
    }

    private fun createTravel() {
        val name = binding.etTravelName.text.toString().trim()
        val description = binding.etTravelDescription.text.toString().trim()

        viewLifecycleOwner.lifecycleScope.launch {
            selectedDateTime?.let { date ->
                // Appel à la méthode de création de voyage
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}