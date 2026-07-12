package com.example.videoplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.R
import com.example.videoplayer.databinding.ActivityMainBinding

/**
 * Entry point – a URL-input screen that lets the user enter any M3U8 URL
 * and launch the player. Ships with a sample HLS stream URL pre-filled.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()
        setupClickListeners()

        // Pre-fill the provided HLS URL for testing
        binding.etStreamUrl.setText("https://img1.hscow.com/hls_mps/0f3c62717d846ee8612d7601b65b02835fa89d27/720/index_287.m3u8?Expires=1783864853&KeyName=Signature&Signature=tFOAv2aUz7zAMbUvN-Y0VcED0WM8oD0wnHeQKVvFJsxxcc45eOa54zpv_yjS8TnlFrUSFh6g5IZkcG8tgv8GDg==")
    }

    private fun setupAnimations() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
        binding.cardPlayer.startAnimation(slideUp)
    }

    private fun setupClickListeners() {
        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString()?.trim()
            val tracksJson = binding.etTracksJson.text?.toString()?.trim().orEmpty()
            if (url.isNullOrEmpty()) {
                binding.tilStreamUrl.error = "Please enter a valid M3U8 URL"
                return@setOnClickListener
            }
            binding.tilStreamUrl.error = null
            launchPlayer(url, tracksJson.ifBlank { null })
        }
    }

    private fun launchPlayer(url: String, tracksJson: String? = null) {
        android.util.Log.i(
            "MainActivity",
            "Launching player url=$url tracksJsonPresent=${!tracksJson.isNullOrBlank()}"
        )
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
            if (!tracksJson.isNullOrBlank()) {
                putExtra(PlayerActivity.EXTRA_TRACKS_PAYLOAD, tracksJson)
            }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
