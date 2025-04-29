package fr.louisvolat.view.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import fr.louisvolat.api.dto.PictureDTO
import fr.louisvolat.database.entity.Picture
import fr.louisvolat.databinding.ItemPictureBinding
import java.io.File

class PictureAdapter(
    private val apiBaseUrl: String,
    private val onPictureLongClick: (PictureDTO, View) -> Boolean,
    private val onPictureClick: (PictureDTO) -> Unit
) : ListAdapter<PictureDTO, PictureAdapter.PictureViewHolder>(PictureDiffCallback()) {

    private val localPictures = mutableMapOf<Long, Picture>()

    fun updateLocalPictures(pictures: List<Picture>) {
        localPictures.clear()
        pictures.forEach { picture ->
            picture.id.let { id ->
                localPictures[id] = picture
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictureViewHolder {
        val binding = ItemPictureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PictureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PictureViewHolder, position: Int) {
        val picture = getItem(position)
        holder.bind(picture)
    }

    inner class PictureViewHolder(private val binding: ItemPictureBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(picture: PictureDTO) {
            // Vérifier si l'image est disponible en local
            val localPicture = picture.id?.let { localPictures[it] }
            val localPath = localPicture?.localPath

            // Utiliser l'image locale si disponible, sinon charger depuis l'API
            val imageSource = if (localPath != null && isPathValid(localPath)) {
                File(localPath)
            } else {
                "$apiBaseUrl${picture.path}"
            }

            // Charger l'image avec Glide
            Glide.with(binding.ivPicture.context)
                .load(imageSource)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivPicture)

            // Configurer les interactions
            binding.root.setOnClickListener { onPictureClick(picture) }
            binding.root.setOnLongClickListener { onPictureLongClick(picture, it) }
        }

        private fun isPathValid(path: String): Boolean {
            return try {
                // Pour une URI stockée comme chaîne
                if (path.startsWith("content://") || path.startsWith("file://")) {
                    true
                } else {
                    File(path).exists()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private class PictureDiffCallback : DiffUtil.ItemCallback<PictureDTO>() {
        override fun areItemsTheSame(oldItem: PictureDTO, newItem: PictureDTO): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PictureDTO, newItem: PictureDTO): Boolean {
            return oldItem.path == newItem.path &&
                    oldItem.createdAt == newItem.createdAt &&
                    oldItem.updatedAt == newItem.updatedAt
        }
    }
}