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
        binding.etStreamUrl.setText("https://img1.hsxco.com/hls_mps/af87fa81accabd7b9e646064481c9d25970071bb/720/index_218.m3u8?auth_key=ZkEpb49YHvDaV3wb57lw7Wj1FcE69%2FBVywkvqA%2B2Hdo%3D&expire=1777356690733")
    }

    private fun setupAnimations() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
        binding.cardPlayer.startAnimation(slideUp)
    }

    private fun setupClickListeners() {
        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString()?.trim()
            if (url.isNullOrEmpty()) {
                binding.tilStreamUrl.error = "Please enter a valid M3U8 URL"
                return@setOnClickListener
            }
            binding.tilStreamUrl.error = null
            launchPlayer(url)
        }
    }

    private fun launchPlayer(url: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
