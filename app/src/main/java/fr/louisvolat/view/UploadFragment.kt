package fr.louisvolat.view

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.louisvolat.databinding.FragmentUploadBinding
import fr.louisvolat.worker.UploadImageWorker

class UploadFragment : Fragment() {
    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    private lateinit var uploadButton:Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        uploadButton = binding.uploadImg


        uploadButton.setOnClickListener {
            this.uploadImage()
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun uploadImage() {
        launchNewPhotoPicker()
    }

    private fun launchNewPhotoPicker(){
//Launch the photo picker and let the user choose images .
        newPiker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val newPiker=registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris != null) {
            Log.d("PhotoPicker", "Selected URI: ${uris}")
            //launch the worker to upload the images
            runUploadImageWorker(uris)
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    private fun runUploadImageWorker(uris: List<Uri>) {
        val strUris = uris.map { it.toString() }.toTypedArray()
        val data = Data.Builder()
            .putStringArray("uris", strUris)
            .build()

        val uploadImageRequest = OneTimeWorkRequestBuilder<UploadImageWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(uploadImageRequest)
    }
}