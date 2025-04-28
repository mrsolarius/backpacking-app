import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.louisvolat.upload.service.UploadService
import fr.louisvolat.upload.viewmodel.UploadImageViewModel

class UploadImageViewModelFactory(private val uploadService: UploadService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadImageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UploadImageViewModel(uploadService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}