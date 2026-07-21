package com.example.videoplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.R
import com.example.videoplayer.databinding.ActivityMainBinding

/**
 * Entry point – a URL-input screen that lets the user enter any M3U8 URL
 * and launch the player. Ships with a sample HLS stream URL pre-filled.
 *
 * Fields:
 *  - Stream URL      : The HLS .m3u8 URL to play initially.
 *  - Tracks JSON     : The 'tracks' JSON array from the movie details API response.
 *  - Video URL API   : The video-URL fetch API endpoint. Required for tracks
 *                      with existIndividualVideo=true. The player appends
 *                      &languageId=<id> to this URL when user selects such a track.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()
        setupClickListeners()
    }

    private fun setupAnimations() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
        binding.cardPlayer.startAnimation(slideUp)
    }

    private fun setupClickListeners() {
        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString()?.trim()
            val tracksJson = binding.etTracksJson.text?.toString()?.trim().orEmpty()
            val videoUrlApi = binding.etVideoUrlApi.text?.toString()?.trim().orEmpty()

            if (url.isNullOrEmpty()) {
                binding.tilStreamUrl.error = "Please enter a valid M3U8 URL"
                return@setOnClickListener
            }
            binding.tilStreamUrl.error = null

            launchPlayer(
                url = url,
                tracksJson = tracksJson.ifBlank { null },
                videoUrlApi = videoUrlApi.ifBlank { null }
            )
        }
    }

    private fun launchPlayer(
        url: String,
        tracksJson: String? = null,
        videoUrlApi: String? = null
    ) {
        android.util.Log.i(
            "MainActivity",
            "Launching player url=$url " +
                "tracksJsonPresent=${!tracksJson.isNullOrBlank()} " +
                "videoUrlApiPresent=${!videoUrlApi.isNullOrBlank()}"
        )
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
            if (!tracksJson.isNullOrBlank()) {
                putExtra(PlayerActivity.EXTRA_TRACKS_PAYLOAD, tracksJson)
            }
            if (!videoUrlApi.isNullOrBlank()) {
                putExtra(PlayerActivity.EXTRA_VIDEO_URL_API, videoUrlApi)
            }
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.slide_in_right, R.anim.slide_out_left
        )
        startActivity(intent, options.toBundle())
    }
}
