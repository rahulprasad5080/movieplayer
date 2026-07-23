package com.example.videoplayer.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.videoplayer.R;
import com.example.videoplayer.databinding.ItemAudioTrackBinding;
import com.example.videoplayer.model.AudioTrack;

import java.util.Locale;

/**
 * RecyclerView adapter for the audio track selection bottom sheet.
 *
 * Uses ListAdapter + DiffUtil for efficient, animated list updates
 * whenever the audio track list changes (e.g. new tracks load mid-stream).
 */
public class AudioTrackAdapter extends ListAdapter<AudioTrack, AudioTrackAdapter.AudioTrackViewHolder> {

    private final OnTrackSelectedListener onTrackSelected;

    private int selectedIndex = -1;

    public interface OnTrackSelectedListener {
        void onTrackSelected(AudioTrack track);
    }

    public AudioTrackAdapter(OnTrackSelectedListener onTrackSelected) {
        super(DIFF_CALLBACK);
        this.onTrackSelected = onTrackSelected;
    }

    public void setSelectedIndex(int value) {
        int old = selectedIndex;
        selectedIndex = value;
        if (old != value) {
            if (old != -1) notifyItemChanged(old);
            if (value != -1) notifyItemChanged(value);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @NonNull
    @Override
    public AudioTrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAudioTrackBinding binding = ItemAudioTrackBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new AudioTrackViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioTrackViewHolder holder, int position) {
        holder.bind(getItem(position), position == selectedIndex);
    }

    class AudioTrackViewHolder extends RecyclerView.ViewHolder {

        private final ItemAudioTrackBinding binding;

        AudioTrackViewHolder(ItemAudioTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AudioTrack track, boolean isSelected) {
            binding.tvTrackLabel.setText(track.getLabel());
            binding.tvTrackLanguage.setText(buildBadgeText(track));

            if (isSelected) {
                binding.ivTrackCheck.setImageResource(R.drawable.ic_check_circle);
                binding.ivTrackCheck.setColorFilter(
                        ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_primary)
                );
                binding.getRoot().setBackgroundColor(
                        ContextCompat.getColor(binding.getRoot().getContext(), R.color.track_selected_bg)
                );
            } else {
                binding.ivTrackCheck.setImageResource(R.drawable.ic_radio_unchecked);
                binding.ivTrackCheck.setColorFilter(
                        ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary)
                );
                binding.getRoot().setBackgroundColor(
                        ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.transparent)
                );
            }

            binding.getRoot().setOnClickListener(v -> {
                if (onTrackSelected != null) {
                    onTrackSelected.onTrackSelected(track);
                }
            });
        }

        private String buildBadgeText(AudioTrack track) {
            String firstWord = track.getLabel()
                    .trim()
                    .split("\\s+")[0];
            if (firstWord.isEmpty()) {
                String lang = track.getLanguage();
                if (lang != null && lang.length() >= 2) {
                    return lang.substring(0, 2).toUpperCase(Locale.ENGLISH);
                }
                return "";
            }
            String badge = firstWord.length() >= 2
                    ? firstWord.substring(0, 2)
                    : firstWord;
            return badge.toUpperCase(Locale.ENGLISH);
        }
    }

    private static final DiffUtil.ItemCallback<AudioTrack> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AudioTrack>() {
                @Override
                public boolean areItemsTheSame(@NonNull AudioTrack old, @NonNull AudioTrack new_) {
                    return old.getGroupIndex() == new_.getGroupIndex() && old.getIndex() == new_.getIndex();
                }

                @Override
                public boolean areContentsTheSame(@NonNull AudioTrack old, @NonNull AudioTrack new_) {
                    return old.equals(new_);
                }
            };
}
