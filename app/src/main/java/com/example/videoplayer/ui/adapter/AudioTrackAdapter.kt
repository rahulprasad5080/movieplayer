package com.example.videoplayer.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videoplayer.R
import com.example.videoplayer.databinding.ItemAudioTrackBinding
import com.example.videoplayer.model.AudioTrack
import java.util.Locale

/**
 * RecyclerView adapter for the audio track selection bottom sheet.
 *
 * Uses [ListAdapter] + [DiffUtil] for efficient, animated list updates
 * whenever the audio track list changes (e.g. new tracks load mid-stream).
 *
 * @param onTrackSelected Callback fired when the user taps a track row.
 */
class AudioTrackAdapter(
    private val onTrackSelected: (AudioTrack) -> Unit
) : ListAdapter<AudioTrack, AudioTrackAdapter.AudioTrackViewHolder>(DIFF_CALLBACK) {

    /** Index of the currently active track so we can highlight it. */
    var selectedIndex: Int = -1
        set(value) {
            val old = field
            field = value
            if (old != value) {
                if (old != -1) notifyItemChanged(old)
                if (value != -1) notifyItemChanged(value)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioTrackViewHolder {
        val binding = ItemAudioTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AudioTrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AudioTrackViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedIndex)
    }

    inner class AudioTrackViewHolder(
        private val binding: ItemAudioTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: AudioTrack, isSelected: Boolean) {
            binding.tvTrackLabel.text = track.label
            binding.tvTrackLanguage.text = buildBadgeText(track)

            // Highlight selected row with accent tint and check icon
            val ctx = binding.root.context
            if (isSelected) {
                binding.ivTrackCheck.setImageResource(R.drawable.ic_check_circle)
                binding.ivTrackCheck.setColorFilter(
                    ContextCompat.getColor(ctx, R.color.accent_primary)
                )
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.track_selected_bg)
                )
            } else {
                binding.ivTrackCheck.setImageResource(R.drawable.ic_radio_unchecked)
                binding.ivTrackCheck.setColorFilter(
                    ContextCompat.getColor(ctx, R.color.text_secondary)
                )
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(ctx, android.R.color.transparent)
                )
            }

            binding.root.setOnClickListener { onTrackSelected(track) }
        }

        private fun buildBadgeText(track: AudioTrack): String {
            val firstWord = track.label
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                .orEmpty()

            return firstWord
                .take(2)
                .uppercase(Locale.ENGLISH)
                .ifBlank {
                    track.language
                        ?.take(2)
                        ?.uppercase(Locale.ENGLISH)
                        .orEmpty()
                }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AudioTrack>() {
            override fun areItemsTheSame(old: AudioTrack, new: AudioTrack) =
                old.groupIndex == new.groupIndex && old.index == new.index

            override fun areContentsTheSame(old: AudioTrack, new: AudioTrack) =
                old == new
        }
    }
}
