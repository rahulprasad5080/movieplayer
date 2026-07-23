package com.example.videoplayer.ui;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.example.videoplayer.R;
import com.example.videoplayer.databinding.ActivityPlayerBinding;
import com.example.videoplayer.model.PlayerState;
import com.example.videoplayer.viewmodel.PlayerViewModel;

/**
 * Full-screen video player activity.
 *
 * Architecture:
 *  - PlayerViewModel owns PlayerManager (ExoPlayer + TrackSelector).
 *  - This Activity attaches the player to PlayerView and observes PlayerState via LiveData.
 *  - Audio track switching is delegated to MaterialAlertDialog.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_STREAM_URL = "extra_stream_url";
    public static final String EXTRA_TRACKS_PAYLOAD = "extra_tracks_payload";
    /**
     * The base URL of the video-URL API endpoint.
     * Required only when tracks with existIndividualVideo=true are present.
     *
     * Example value:
     *   "https://api.example.com/movie/episode/videoUrl?episodeId=123&resolution=2"
     *
     * The player will automatically append &languageId=<id> to this URL when
     * the user selects a track with existIndividualVideo=true.
     */
    public static final String EXTRA_VIDEO_URL_API = "extra_video_url_api";

    private ActivityPlayerBinding binding;
    private PlayerViewModel viewModel;

    public PlayerViewModel getViewModel() {
        return viewModel;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while video is playing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize ViewModel using ViewModelProvider
        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(PlayerViewModel.class);

        configureSystemUI();
        attachPlayerView();
        observePlayerState();
        observeFetchState();
        setupControls();

        // Load stream only on first creation, not on config change
        if (savedInstanceState == null) {
            String url = getIntent().getStringExtra(EXTRA_STREAM_URL);
            if (url == null) {
                Toast.makeText(this, "No stream URL provided", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String tracksPayload = getIntent().getStringExtra(EXTRA_TRACKS_PAYLOAD);
            String videoUrlApi = getIntent().getStringExtra(EXTRA_VIDEO_URL_API);

            android.util.Log.i("PlayerActivity",
                    "Launching playback url=" + url +
                    " tracksPayloadPresent=" + (tracksPayload != null && !tracksPayload.isEmpty()) +
                    " videoUrlApiPresent=" + (videoUrlApi != null && !videoUrlApi.isEmpty()));

            // Initialize the API repository if the caller provided the endpoint
            if (videoUrlApi != null && !videoUrlApi.trim().isEmpty()) {
                viewModel.initVideoUrlApi(videoUrlApi);
            }

            viewModel.loadAndPlay(url, tracksPayload);
        }
    }

    // ── Player View Attachment ────────────────────────────────────────────────

    @Override
    protected void onStart() {
        super.onStart();
        binding.playerView.setPlayer(viewModel.playerManager.getPlayer());
    }

    @Override
    protected void onStop() {
        super.onStop();
        binding.playerView.setPlayer(null);
        if (!isChangingConfigurations()) {
            viewModel.pause();
        }
    }

    // ── System UI ─────────────────────────────────────────────────────────────

    private void configureSystemUI() {
        // Full-screen immersive mode using deprecated flags for broad API compatibility
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    // ── Player View Setup ─────────────────────────────────────────────────────

    private void attachPlayerView() {
        binding.playerView.setControllerShowTimeoutMs(3000);
        binding.playerView.setControllerAutoShow(true);
        binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        binding.playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
    }

    // ── State Observation ─────────────────────────────────────────────────────

    private void observePlayerState() {
        viewModel.playerState.observe(this, state -> {
            if (state instanceof PlayerState.Idle) {
                showBuffering(false);
            } else if (state instanceof PlayerState.Buffering) {
                showBuffering(true);
            } else if (state instanceof PlayerState.Playing) {
                showBuffering(false);
                updateAudioTrackButton(((PlayerState.Playing) state).getAudioTracks().size());
            } else if (state instanceof PlayerState.Error) {
                showBuffering(false);
                showError(((PlayerState.Error) state).getMessage());
            }
        });
    }

    /**
     * Observes the video-URL API fetch state.
     *
     * - While the API call is in flight (isFetchingVideoUrl=true), the buffering
     *   spinner is shown and the audio track button is disabled to prevent
     *   a second tap while switching.
     * - If the API call fails, a Toast is shown with the error message.
     */
    private void observeFetchState() {
        viewModel.isFetchingVideoUrl.observe(this, isFetching -> {
            android.util.Log.d("PlayerActivity", "isFetchingVideoUrl=" + isFetching);
            if (isFetching != null && isFetching) {
                showBuffering(true);
                binding.btnAudioTrack.setEnabled(false);
            } else {
                showBuffering(false);
                binding.btnAudioTrack.setEnabled(true);
            }
        });

        viewModel.videoUrlFetchError.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Controls Setup ────────────────────────────────────────────────────────

    private void setupControls() {
        // Audio track button → open dialog with radio buttons
        binding.btnAudioTrack.setOnClickListener(v -> {
            java.util.List<com.example.videoplayer.model.AudioTrack> tracks = viewModel.audioTracks.getValue();
            if (tracks == null || tracks.isEmpty()) {
                Toast.makeText(this, "No audio tracks active", Toast.LENGTH_SHORT).show();
                return;
            }

            Integer selectedIdx = viewModel.playerManager.selectedAudioIndex.getValue();
            int selectedIndex = selectedIdx != null ? selectedIdx : -1;

            android.util.Log.i("PlayerActivity",
                    "Opening audio selector with " + tracks.size() +
                    " items; selected=" + selectedIndex);

            String[] trackLabels = new String[tracks.size()];
            for (int i = 0; i < tracks.size(); i++) {
                trackLabels[i] = tracks.get(i).getLabel();
            }

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle("Select Audio Language")
                    .setSingleChoiceItems(trackLabels, selectedIndex, (dialog, which) -> {
                        com.example.videoplayer.model.AudioTrack selectedTrack = tracks.get(which);
                        android.util.Log.i("PlayerActivity",
                                "User tapped track label=" + selectedTrack.getLabel() +
                                " individualVideo=" + selectedTrack.isExistIndividualVideo() +
                                " languageId=" + selectedTrack.getLanguageId());
                        dialog.dismiss();
                        viewModel.selectAudioTrack(selectedTrack);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        // Back button
        binding.btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void showBuffering(boolean show) {
        binding.progressBuffering.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Show/hide the audio track button based on whether multiple tracks exist.
     */
    private void updateAudioTrackButton(int trackCount) {
        binding.btnAudioTrack.setEnabled(trackCount > 0);
        binding.btnAudioTrack.setAlpha(trackCount > 0 ? 1f : 0.4f);
        binding.tvTrackCount.setText(trackCount > 0 ? String.valueOf(trackCount) : "");
    }

    private void showError(String message) {
        Toast.makeText(this, "Playback error: " + message, Toast.LENGTH_LONG).show();
    }
}
