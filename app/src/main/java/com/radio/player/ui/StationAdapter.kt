package com.radio.player.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.radio.player.data.RadioStation
import com.radio.player.databinding.ItemStationBinding

class StationAdapter(
    private val onStationClick: (RadioStation) -> Unit,
    private val onFavoriteClick: (RadioStation) -> Unit,
    private val onLongClick: (RadioStation) -> Unit,
    private val onShareClick: (RadioStation) -> Unit
) : ListAdapter<RadioStation, StationAdapter.StationViewHolder>(StationDiffCallback()) {

    private var currentlyPlayingId: Long = -1

    fun setCurrentlyPlaying(id: Long) {
        val oldId = currentlyPlayingId
        currentlyPlayingId = id
        if (oldId != id) {
            notifyItemChanged(findPosition(oldId))
            notifyItemChanged(findPosition(id))
        }
    }

    private fun findPosition(id: Long): Int {
        return currentList.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StationViewHolder(
        private val binding: ItemStationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: RadioStation) {
            binding.stationName.text = station.name
            binding.stationGenre.text = station.genre.ifBlank { station.country }
            binding.stationUrl.text = station.streamUrl

            val isPlaying = station.id == currentlyPlayingId
            binding.playingIndicator.visibility = if (isPlaying) android.view.View.VISIBLE else android.view.View.GONE

            binding.favoriteButton.setImageResource(
                if (station.isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )

            binding.favoriteButton.setOnClickListener {
                onFavoriteClick(station)
            }

            binding.root.setOnClickListener {
                onStationClick(station)
            }

            binding.root.setOnLongClickListener {
                onLongClick(station)
                true
            }

            binding.shareButton.setOnClickListener {
                onShareClick(station)
            }
        }
    }

    class StationDiffCallback : DiffUtil.ItemCallback<RadioStation>() {
        override fun areItemsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean {
            return oldItem == newItem
        }
    }
}