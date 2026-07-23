package com.example.videoplayer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

import com.example.videoplayer.R;
import com.example.videoplayer.databinding.ActivityMainBinding;

/**
 * Entry point – a URL-input screen that lets the user enter any M3U8 URL
 * and launch the player. Ships with a sample HLS stream URL pre-filled.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupAnimations();
        setupClickListeners();
    }

    private void setupAnimations() {
        try {
            android.view.animation.Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
            binding.cardPlayer.startAnimation(slideUp);
        } catch (Exception ignored) {}
    }

    private void setupClickListeners() {
        binding.btnPlay.setOnClickListener(v -> {
            String url = binding.etStreamUrl.getText() != null
                    ? binding.etStreamUrl.getText().toString().trim()
                    : null;
            String tracksJson = binding.etTracksJson.getText() != null
                    ? binding.etTracksJson.getText().toString().trim()
                    : "";
            String videoUrlApi = binding.etVideoUrlApi.getText() != null
                    ? binding.etVideoUrlApi.getText().toString().trim()
                    : "";

            if (url == null || url.isEmpty()) {
                binding.tilStreamUrl.setError("Please enter a valid M3U8 URL");
                return;
            }
            binding.tilStreamUrl.setError(null);

            launchPlayer(
                    url,
                    tracksJson.isEmpty() ? null : tracksJson,
                    videoUrlApi.isEmpty() ? null : videoUrlApi
            );
        });
    }

    private void launchPlayer(String url, String tracksJson, String videoUrlApi) {
        android.util.Log.i("MainActivity",
                "Launching player url=" + url +
                " tracksJsonPresent=" + (tracksJson != null && !tracksJson.isEmpty()) +
                " videoUrlApiPresent=" + (videoUrlApi != null && !videoUrlApi.isEmpty()));

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_STREAM_URL, url);
        if (tracksJson != null && !tracksJson.isEmpty()) {
            intent.putExtra(PlayerActivity.EXTRA_TRACKS_PAYLOAD, tracksJson);
        }
        if (videoUrlApi != null && !videoUrlApi.isEmpty()) {
            intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL_API, videoUrlApi);
        }

        Bundle options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
        ).toBundle();
        startActivity(intent, options);
    }
}
