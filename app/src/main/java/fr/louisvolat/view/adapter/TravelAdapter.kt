package fr.louisvolat.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import fr.louisvolat.database.entity.Travel
import fr.louisvolat.database.entity.TravelWithCoverPicture
import fr.louisvolat.databinding.ItemTravelBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TravelAdapter(
    private val onItemClick: (Travel) -> Unit
) : ListAdapter<TravelWithCoverPicture, TravelAdapter.TravelViewHolder>(TravelDiffCallback()) {
    private lateinit var apiUrl: String
    private val BASE_URL_PREF_NAME = "api_url"
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TravelViewHolder {
        val binding = ItemTravelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val prefs = PreferenceManager.getDefaultSharedPreferences(parent.context)
        apiUrl = prefs.getString(BASE_URL_PREF_NAME, null).toString()
        return TravelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TravelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TravelViewHolder(
        private val binding: ItemTravelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(travelWithCover: TravelWithCoverPicture) {
            val travel = travelWithCover.travel
            val coverPicture = travelWithCover.coverPicture

            with(binding) {
                // Formatage des dates
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val startDate = dateFormat.format(Date(travel.startDate))
                val endDate = travel.endDate?.let { dateFormat.format(Date(it)) }

                // Binding des données textuelles
                travelName.text = travel.name
                travelDescription.text = travel.description
                travelDates.text = if (endDate != null) {
                    "$startDate - $endDate"
                } else {
                    "Depuis $startDate"
                }

                // Gestion de l'image de couverture
                if (coverPicture != null) {
                    travelCoverImage.isVisible = true

                    // Priorité à l'image locale si disponible
                    val localPath = coverPicture.localPath
                    if (localPath != null && File(localPath).exists()) {
                        // Utilisation de l'image locale
                        Glide.with(travelCoverImage)
                            .load(File(localPath))
                            .centerCrop()
                            .into(travelCoverImage)
                    } else {
                        // Utilisation de l'URL distante
                        Glide.with(travelCoverImage)
                            .load(apiUrl+coverPicture.rawVersion)
                            .centerCrop()
                            .into(travelCoverImage)
                    }
                } else {
                    // Pas d'image de couverture
                    travelCoverImage.isVisible = false
                }

                // Gestion du clic
                root.setOnClickListener { onItemClick(travel) }
            }
        }
    }

    private class TravelDiffCallback : DiffUtil.ItemCallback<TravelWithCoverPicture>() {
        override fun areItemsTheSame(oldItem: TravelWithCoverPicture, newItem: TravelWithCoverPicture): Boolean {
            return oldItem.travel.id == newItem.travel.id
        }

        override fun areContentsTheSame(oldItem: TravelWithCoverPicture, newItem: TravelWithCoverPicture): Boolean {
            return oldItem.travel == newItem.travel && oldItem.coverPicture == newItem.coverPicture
        }
    }
}