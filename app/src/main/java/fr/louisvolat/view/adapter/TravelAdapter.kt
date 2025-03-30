// TravelAdapter.kt
package fr.louisvolat.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.louisvolat.database.entity.Travel
import fr.louisvolat.databinding.ItemTravelBinding
import java.text.SimpleDateFormat
import java.util.*

class TravelAdapter(
    private val onItemClick: (Travel) -> Unit
) : ListAdapter<Travel, TravelAdapter.TravelViewHolder>(TravelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TravelViewHolder {
        val binding = ItemTravelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TravelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TravelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TravelViewHolder(
        private val binding: ItemTravelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(travel: Travel) {
            with(binding) {
                // Formatage des dates
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val startDate = dateFormat.format(Date(travel.startDate))
                val endDate = travel.endDate?.let { dateFormat.format(Date(it)) }

                // Binding des données
                travelName.text = travel.name
                travelDescription.text = travel.description
                travelDates.text = if (endDate != null) {
                    "$startDate - $endDate"
                } else {
                    "Depuis $startDate"
                }

                // Gestion du clic
                root.setOnClickListener { onItemClick(travel) }
            }
        }
    }

    private class TravelDiffCallback : DiffUtil.ItemCallback<Travel>() {
        override fun areItemsTheSame(oldItem: Travel, newItem: Travel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Travel, newItem: Travel): Boolean {
            return oldItem == newItem
        }
    }
}