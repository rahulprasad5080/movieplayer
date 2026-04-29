package com.gxgx.daqiandy.widgets.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.Surface;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.extractor.DefaultExtractorsFactory;
import cn.jzvd.JZDataSource;
import cn.jzvd.JZMediaInterface;
import cn.jzvd.JZTextureView;
import cn.jzvd.Jzvd;
import com.google.common.collect.ImmutableList;
import com.gxgx.base.bean.ErrorPlayBean;
import com.gxgx.daqiandy.R;
import com.gxgx.daqiandy.app.DqApplication;
import com.gxgx.daqiandy.bean.DnsConfigBean;
import com.gxgx.daqiandy.bean.ExoplayerBufferConfigBean;
import com.gxgx.daqiandy.bean.LiveTvChannelDetailBean;
import com.gxgx.daqiandy.bean.MovieResult;
import com.gxgx.daqiandy.bean.PlayerBufferBean;
import com.gxgx.daqiandy.bean.PlayerLoadingBufferBean;
import com.gxgx.daqiandy.bean.SelectionBitRateBean;
import com.gxgx.daqiandy.widgets.player.JZMediaExo;
import com.jeremyliao.liveeventbus.LiveEventBus;
import com.vungle.ads.internal.model.AdPayload;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kotlin.collections.CollectionsKt;
import kotlin.io.FilesKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Ref;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import nc.so;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UnstableApi
@SourceDebugExtension({"SMAP\nJZMediaExo.kt\nKotlin\n*S Kotlin\n*F\n+ 1 JZMediaExo.kt\ncom/gxgx/daqiandy/widgets/player/JZMediaExo\n+ 2 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 5 Uri.kt\nandroidx/core/net/UriKt\n*L\n1#1,1304:1\n13402#2,2:1305\n1863#3,2:1307\n216#4,2:1309\n29#5:1311\n*S KotlinDebug\n*F\n+ 1 JZMediaExo.kt\ncom/gxgx/daqiandy/widgets/player/JZMediaExo\n*L\n943#1:1305,2\n1224#1:1307,2\n193#1:1309,2\n351#1:1311\n*E\n"})
public final class JZMediaExo extends JZMediaInterface implements Player.Listener {
    private final long BUFFERING_TIMEOUT_MS;

    @NotNull
    private final String TAG;

    @NotNull
    private AnalyticsListener analyticsListener;

    @Nullable
    private BandwidthMeter bandwidthMeter;

    @Nullable
    private final BasePlayer basePlayer;
    private int bufferingCount;
    private long bufferingStartTime;
    private long bufferingStartTime1;

    @NotNull
    private final Runnable bufferingTimeoutRunnable;

    @Nullable
    private Runnable callback;

    @NotNull
    private String errorTrackString;

    @Nullable
    private Float fps;
    private boolean hasFirstReady;
    private boolean hasReported;
    private boolean hasShownNetworkBadTip;
    private boolean isBuffering;
    private boolean isSeeking;
    private boolean isUserPaused;
    private long maxSingleBuffering;

    @NotNull
    private final MediaSourceEventListener mediaSourceEventListener;
    private long previousSeek;

    @NotNull
    private Runnable reportRunnable;

    @Nullable
    private ExoPlayer simpleExoPlayer;
    private float speed;
    private int tempPlayerErrorCode;
    private int tempPlayerErrorCodeGroup;
    private long totalBufferingTime;

    public final class onBufferingUpdate implements Runnable {
        public onBufferingUpdate() {
        }

        public static final void run$lambda$0(JZMediaExo jZMediaExo, int i10) {
            jZMediaExo.jzvd.setBufferProgress(i10);
        }

        @Override
        public void run() {
            if (JZMediaExo.this.simpleExoPlayer != null) {
                ExoPlayer exoPlayer = JZMediaExo.this.simpleExoPlayer;
                Intrinsics.checkNotNull(exoPlayer);
                final int bufferedPercentage = exoPlayer.getBufferedPercentage();
                final JZMediaExo jZMediaExo = JZMediaExo.this;
                jZMediaExo.handler.post(new Runnable() {
                    @Override
                    public final void run() {
                        JZMediaExo.onBufferingUpdate.run$lambda$0(jZMediaExo, bufferedPercentage);
                    }
                });
                if (bufferedPercentage < 100) {
                    JZMediaExo jZMediaExo2 = JZMediaExo.this;
                    Handler handler = jZMediaExo2.handler;
                    Runnable runnable = jZMediaExo2.callback;
                    Intrinsics.checkNotNull(runnable);
                    handler.postDelayed(runnable, 300L);
                    return;
                }
                JZMediaExo jZMediaExo3 = JZMediaExo.this;
                Handler handler2 = jZMediaExo3.handler;
                Runnable runnable2 = jZMediaExo3.callback;
                Intrinsics.checkNotNull(runnable2);
                handler2.removeCallbacks(runnable2);
            }
        }
    }

    private final RenderersFactory buildRenderersFactory(Context context, boolean z10) {
        DefaultRenderersFactory enableDecoderFallback = new DefaultRenderersFactory(context.getApplicationContext()).setExtensionRendererMode(z10 ? 2 : 1).setEnableDecoderFallback(true);
        Intrinsics.checkNotNullExpressionValue(enableDecoderFallback, "setEnableDecoderFallback(...)");
        return enableDecoderFallback;
    }

    public static final void onPlayerStateChanged$lambda$14(int i10, JZMediaExo jZMediaExo, boolean z10) {
        if (i10 == 2) {
            jZMediaExo.setBuffer(true);
            jZMediaExo.jzvd.onStatePreparingPlaying();
            Handler handler = jZMediaExo.handler;
            Runnable runnable = jZMediaExo.callback;
            Intrinsics.checkNotNull(runnable);
            handler.post(runnable);
            return;
        }
        if (i10 != 3) {
            if (i10 != 4) {
                return;
            }
            jZMediaExo.jzvd.onCompletion();
        } else {
            jZMediaExo.setBuffer(false);
            if (z10) {
                jZMediaExo.jzvd.onStatePlaying();
            }
        }
    }

    public static final DrmSessionManager prepare$lambda$11$lambda$10(DrmSessionManager drmSessionManager, MediaItem mediaItem) {
        return drmSessionManager;
    }

    @Nullable
    public final List<SelectionBitRateBean> getBitRates() {
        try {
            ExoPlayer exoPlayer = this.simpleExoPlayer;
            if (exoPlayer != null) {
                TrackSelector trackSelector = exoPlayer.getTrackSelector();
                DefaultTrackSelector defaultTrackSelector = trackSelector instanceof DefaultTrackSelector ? (DefaultTrackSelector) trackSelector : null;
                if (defaultTrackSelector != null) {
                    MappingTrackSelector.MappedTrackInfo currentMappedTrackInfo = defaultTrackSelector.getCurrentMappedTrackInfo();
                    TrackGroupArray trackGroups = currentMappedTrackInfo != null ? currentMappedTrackInfo.getTrackGroups(0) : null;
                    if (trackGroups == null) {
                        return null;
                    }
                    ArrayList arrayList = new ArrayList();
                    int i10 = trackGroups.length;
                    for (int i11 = 0; i11 < i10; i11++) {
                        TrackGroup trackGroup = trackGroups.get(i11);
                        Intrinsics.checkNotNullExpressionValue(trackGroup, "get(...)");
                        int i12 = trackGroup.length;
                        for (int i13 = 0; i13 < i12; i13++) {
                            Format format = trackGroup.getFormat(i13);
                            Intrinsics.checkNotNullExpressionValue(format, "getFormat(...)");
                            StringBuilder sb2 = new StringBuilder();
                            sb2.append(format.width);
                            sb2.append('P');
                            String string = sb2.toString();
                            arrayList.add(new SelectionBitRateBean(i11, i13, string));
                            StringBuilder sb3 = new StringBuilder();
                            sb3.append("trackName: ");
                            sb3.append(string);
                        }
                    }
                    return arrayList;
                }
            }
        } catch (Exception e10) {
            e10.printStackTrace();
        }
        return null;
    }

    public final int getDynamicBufferPercent(long j10, long j11, long j12) {
        long j13 = (j12 / 1000) / 60;
        return Math.min((int) (((j10 * 100) / j12) + (((float) (((j11 - j10) * 100.0d) / j12)) * (j13 < 5 ? 1.0f : j13 < 30 ? 2.0f : j13 < 60 ? 3.0f : j13 < 120 ? 4.0f : 5.0f))), 100);
    }

    @Override
    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
        androidx.media3.common.y.a(this, audioAttributes);
    }

    @Override
    public void onAudioSessionIdChanged(int i10) {
        androidx.media3.common.y.b(this, i10);
    }

    @Override
    public void onAvailableCommandsChanged(Player.Commands commands) {
        androidx.media3.common.y.c(this, commands);
    }

    @Override
    public void onCues(CueGroup cueGroup) {
        androidx.media3.common.y.d(this, cueGroup);
    }

    @Override
    public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
        androidx.media3.common.y.f(this, deviceInfo);
    }

    @Override
    public void onDeviceVolumeChanged(int i10, boolean z10) {
        androidx.media3.common.y.g(this, i10, z10);
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
        androidx.media3.common.y.h(this, player, events);
    }

    @Override
    public void onIsLoadingChanged(boolean z10) {
        androidx.media3.common.y.i(this, z10);
    }

    @Override
    public void onLoadingChanged(boolean z10) {
    }

    @Override
    public void onMaxSeekToPreviousPositionChanged(long j10) {
        androidx.media3.common.y.l(this, j10);
    }

    @Override
    public void onMediaItemTransition(MediaItem mediaItem, int i10) {
        androidx.media3.common.y.m(this, mediaItem, i10);
    }

    @Override
    public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
        androidx.media3.common.y.n(this, mediaMetadata);
    }

    @Override
    public void onMetadata(Metadata metadata) {
        androidx.media3.common.y.o(this, metadata);
    }

    @Override
    public void onPlayWhenReadyChanged(boolean z10, int i10) {
        androidx.media3.common.y.p(this, z10, i10);
    }

    @Override
    public void onPlaybackParametersChanged(@NotNull PlaybackParameters playbackParameters) {
        Intrinsics.checkNotNullParameter(playbackParameters, "playbackParameters");
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(int i10) {
        androidx.media3.common.y.s(this, i10);
    }

    @Override
    public void onPlayerErrorChanged(PlaybackException playbackException) {
        androidx.media3.common.y.u(this, playbackException);
    }

    @Override
    public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
        androidx.media3.common.y.w(this, mediaMetadata);
    }

    @Override
    public void onPositionDiscontinuity(int i10) {
    }

    @Override
    public void onRenderedFirstFrame() {
    }

    @Override
    public void onRepeatModeChanged(int i10) {
    }

    @Override
    public void onSeekBackIncrementChanged(long j10) {
        androidx.media3.common.y.B(this, j10);
    }

    @Override
    public void onSeekForwardIncrementChanged(long j10) {
        androidx.media3.common.y.C(this, j10);
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean z10) {
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean z10) {
        androidx.media3.common.y.E(this, z10);
    }

    @Override
    public void onSurfaceSizeChanged(int i10, int i11) {
        androidx.media3.common.y.F(this, i10, i11);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NotNull SurfaceTexture surface) {
        Intrinsics.checkNotNullParameter(surface, "surface");
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NotNull SurfaceTexture surface, int i10, int i11) {
        Intrinsics.checkNotNullParameter(surface, "surface");
    }

    @Override
    public void onSurfaceTextureUpdated(@NotNull SurfaceTexture surface) {
        Intrinsics.checkNotNullParameter(surface, "surface");
    }

    @Override
    public void onTimelineChanged(@NotNull Timeline timeline, int i10) {
        Intrinsics.checkNotNullParameter(timeline, "timeline");
    }

    @Override
    public void onTrackSelectionParametersChanged(TrackSelectionParameters trackSelectionParameters) {
        androidx.media3.common.y.H(this, trackSelectionParameters);
    }

    @Override
    public void onVolumeChanged(float f10) {
        androidx.media3.common.y.K(this, f10);
    }

    @Override
    public void release() {
        final HandlerThread handlerThread;
        final ExoPlayer exoPlayer;
        JZMediaInterface.SAVED_SURFACE = null;
        if (this.mMediaHandler != null && (handlerThread = this.mMediaHandlerThread) != null && (exoPlayer = this.simpleExoPlayer) != null) {
            Intrinsics.checkNotNull(exoPlayer, "null cannot be cast to non-null type androidx.media3.exoplayer.ExoPlayer");
            this.mMediaHandler.post(new Runnable() {
                @Override
                public final void run() {
                    JZMediaExo.release$lambda$13(exoPlayer, handlerThread);
                }
            });
            this.simpleExoPlayer = null;
            xb.v.j("EventLogger===111=" + this.simpleExoPlayer);
        }
        xb.v.j("EventLogger===SAVED_SURFACE=" + this.simpleExoPlayer);
        reset();
    }

    public final void reset() {
        this.hasFirstReady = false;
        this.isSeeking = false;
        this.isUserPaused = false;
        this.bufferingStartTime1 = -1L;
        this.totalBufferingTime = 0L;
        this.bufferingCount = 0;
        this.maxSingleBuffering = 0L;
        this.hasShownNetworkBadTip = false;
    }

    public static final void bufferingTimeoutRunnable$lambda$19(final JZMediaExo jZMediaExo) {
        if (jZMediaExo.bufferingStartTime1 > 0) {
            jZMediaExo.hasShownNetworkBadTip = true;
            jZMediaExo.handler.post(new Runnable() {
                @Override
                public final void run() {
                    JZMediaExo.bufferingTimeoutRunnable$lambda$19$lambda$18(this.f27856a);
                }
            });
            xb.v.j("buffering===NETWORK_BAD===bufferingTimeoutRunnable");
        }
    }

    public static final void bufferingTimeoutRunnable$lambda$19$lambda$18(JZMediaExo jZMediaExo) {
        BasePlayer basePlayer = jZMediaExo.basePlayer;
        if (basePlayer != null) {
            basePlayer.showNetworkBadTip(true);
        }
    }

    private final MediaItem.SubtitleConfiguration getTextSource(Context context, String str, String str2) {
        try {
            xb.v.b(this.TAG, "subTitle=== " + str + ' ');
            String strRemovePrefix = StringsKt.removePrefix(str, (CharSequence) AdPayload.FILE_SCHEME);
            xb.v.b(this.TAG, "subTitle.removePrefix=== " + strRemovePrefix + ' ');
            String extension = FilesKt.getExtension(new File(strRemovePrefix));
            Format formatBuild = new Format.Builder().setSampleMimeType(Intrinsics.areEqual(extension, "vtt") ? "text/vtt" : Intrinsics.areEqual(extension, "srt") ? "application/x-subrip" : MimeTypes.getMediaMimeType(extension)).setSelectionFlags(1).setLanguage(str2).build();
            Intrinsics.checkNotNullExpressionValue(formatBuild, "build(...)");
            MediaItem.SubtitleConfiguration subtitleConfigurationBuild = new MediaItem.SubtitleConfiguration.Builder(Uri.parse(str)).setMimeType((String) Assertions.checkNotNull(formatBuild.sampleMimeType)).setLanguage(formatBuild.language).setSelectionFlags(formatBuild.selectionFlags).build();
            Intrinsics.checkNotNullExpressionValue(subtitleConfigurationBuild, "build(...)");
            return subtitleConfigurationBuild;
        } catch (Error e10) {
            e10.printStackTrace();
            xb.r0.a(DqApplication.f14778j.e(), context.getString(R.string.unsupported_subtitles));
            return null;
        } catch (Exception e11) {
            e11.printStackTrace();
            xb.r0.a(DqApplication.f14778j.e(), context.getString(R.string.unsupported_subtitles));
            return null;
        }
    }

    public static final void onPlaybackStateChanged$lambda$21(JZMediaExo jZMediaExo, PlayerLoadingBufferBean playerLoadingBufferBean) {
        BasePlayer basePlayer = jZMediaExo.basePlayer;
        if (basePlayer != null) {
            basePlayer.reportLoadingBuffering(playerLoadingBufferBean);
        }
    }

    public static final void onPlaybackStateChanged$lambda$22(JZMediaExo jZMediaExo) {
        BasePlayer basePlayer = jZMediaExo.basePlayer;
        if (basePlayer != null) {
            basePlayer.showNetworkBadTip(false);
        }
    }

    public static final void onPlayerError$lambda$17(JZMediaExo jZMediaExo, String str, PlaybackException playbackException, long j10) {
        jZMediaExo.jzvd.onError(jZMediaExo.tempPlayerErrorCodeGroup, jZMediaExo.tempPlayerErrorCode);
        LiveEventBus.get(ic.g.f49757g0, ErrorPlayBean.class).post(new ErrorPlayBean(str, String.valueOf(playbackException.errorCode), jZMediaExo.errorTrackString, String.valueOf(j10)));
    }

    public static final void onPositionDiscontinuity$lambda$20(JZMediaExo jZMediaExo) {
        jZMediaExo.jzvd.onSeekComplete();
    }

    public static final void onVideoSizeChanged$lambda$12(JZMediaExo jZMediaExo, VideoSize videoSize) {
        jZMediaExo.jzvd.onVideoSizeChanged((int) (videoSize.width * videoSize.pixelWidthHeightRatio), videoSize.height);
    }

    public static final void prepare$lambda$11(Context context, JZMediaExo jZMediaExo) {
        String str;
        String str2;
        String url;
        String abbreviate;
        MediaItem.SubtitleConfiguration textSource;
        MediaSource mediaSourceCreateMediaSource;
        SurfaceTexture surfaceTexture;
        JZDataSource jZDataSource;
        Object[] objArr;
        MovieResult.Track track;
        String abbreviate2;
        Object obj;
        MovieResult.Track track2;
        String abbreviate3;
        MovieResult.Subtitle subtitle;
        String url2;
        Object currentUrl;
        JZDataSource jZDataSource2;
        HashMap<String, String> map;
        DefaultTrackSelector defaultTrackSelector = new DefaultTrackSelector(context);
        DefaultLoadControl.Builder allocator = new DefaultLoadControl.Builder().setAllocator(new DefaultAllocator(true, 65536));
        ExoplayerBufferConfigBean exoplayerBufferConfigBeanV = hc.d.f49049a.v();
        if (exoplayerBufferConfigBeanV == null) {
            xb.v.c("getExoplayerBufferConfig 0set Exoplayer 180000, 300000, 1000, 5000");
            allocator.setBufferDurationsMs(180000, 300000, 1000, 5000);
        } else {
            Integer minBufferMs = exoplayerBufferConfigBeanV.getMinBufferMs();
            int iIntValue = minBufferMs != null ? minBufferMs.intValue() : 180000;
            Integer maxBufferMs = exoplayerBufferConfigBeanV.getMaxBufferMs();
            int iIntValue2 = maxBufferMs != null ? maxBufferMs.intValue() : 300000;
            Integer bufferForPlaybackMs = exoplayerBufferConfigBeanV.getBufferForPlaybackMs();
            int iIntValue3 = bufferForPlaybackMs != null ? bufferForPlaybackMs.intValue() : 1000;
            Integer bufferForPlaybackAfterRebufferMs = exoplayerBufferConfigBeanV.getBufferForPlaybackAfterRebufferMs();
            int iIntValue4 = bufferForPlaybackAfterRebufferMs != null ? bufferForPlaybackAfterRebufferMs.intValue() : 5000;
            if (iIntValue <= 0 || iIntValue2 <= 0 || iIntValue2 <= iIntValue) {
                xb.v.c("getExoplayerBufferConfig 2set Exoplayer 180000, 300000, 1000, 5000");
                allocator.setBufferDurationsMs(180000, 300000, 1000, 5000);
            } else {
                xb.v.c("getExoplayerBufferConfig 1set Exoplayer " + iIntValue + ", " + iIntValue2 + ", " + iIntValue3 + ", " + iIntValue4);
                allocator.setBufferDurationsMs(iIntValue, iIntValue2, iIntValue3, iIntValue4);
            }
        }
        DefaultLoadControl defaultLoadControlBuild = allocator.setPrioritizeTimeOverSizeThresholds(false).setTargetBufferBytes(-1).build();
        Intrinsics.checkNotNullExpressionValue(defaultLoadControlBuild, "build(...)");
        jZMediaExo.bandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();
        DefaultRenderersFactory enableDecoderFallback = new DefaultRenderersFactory(context).setEnableAudioTrackPlaybackParams(true).setEnableDecoderFallback(true);
        Intrinsics.checkNotNullExpressionValue(enableDecoderFallback, "setEnableDecoderFallback(...)");
        enableDecoderFallback.forceEnableMediaCodecAsynchronousQueueing();
        if (jZMediaExo.simpleExoPlayer == null) {
            ExoPlayer.Builder builder = new ExoPlayer.Builder(context, enableDecoderFallback);
            Context applicationContext = context.getApplicationContext();
            Intrinsics.checkNotNullExpressionValue(applicationContext, "getApplicationContext(...)");
            ExoPlayer.Builder loadControl = builder.setRenderersFactory(jZMediaExo.buildRenderersFactory(applicationContext, true)).setTrackSelector(defaultTrackSelector).setLoadControl(defaultLoadControlBuild);
            BandwidthMeter bandwidthMeter = jZMediaExo.bandwidthMeter;
            Intrinsics.checkNotNull(bandwidthMeter);
            jZMediaExo.simpleExoPlayer = loadControl.setBandwidthMeter(bandwidthMeter).build();
        }
        xb.v.j("EventLogger===" + jZMediaExo.simpleExoPlayer);
        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
        final Ref.ObjectRef objectRef = new Ref.ObjectRef();
        str = "";
        objectRef.element = "";
        final Ref.ObjectRef objectRef2 = new Ref.ObjectRef();
        objectRef2.element = "";
        Jzvd jzvd = jZMediaExo.jzvd;
        if (jzvd == null || (jZDataSource2 = jzvd.jzDataSource) == null || (map = jZDataSource2.headerMap) == null || !(!map.isEmpty())) {
            str2 = "";
        } else {
            xb.v.j("flixygo key : " + jZDataSource2.headerMap);
            StringBuilder sb2 = new StringBuilder();
            sb2.append("headerMap===");
            sb2.append(map);
            sb2.append("  ==");
            sb2.append(map.containsKey(LiveTvChannelDetailBean.LICENSE_TYPE));
            HashMap map2 = new HashMap();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                map2.put(entry.getKey(), entry.getValue());
            }
            if (map2.containsKey("Referer")) {
                String str3 = (String) map2.get("Referer");
                if (str3 == null || str3.length() == 0) {
                    map2.remove("Referer");
                }
                StringBuilder sb3 = new StringBuilder();
                sb3.append("referer==111===");
                sb3.append(str3);
            }
            if (map2.containsKey(LiveTvChannelDetailBean.LICENSE_TYPE)) {
                String str4 = (String) map2.get(LiveTvChannelDetailBean.LICENSE_TYPE);
                if (str4 == null) {
                    str4 = "";
                }
                String str5 = (String) map2.get(LiveTvChannelDetailBean.LICENSE_KEY);
                str = str5 != null ? str5 : "";
                StringBuilder sb4 = new StringBuilder();
                sb4.append("keyType==111===");
                sb4.append((String) map2.get(LiveTvChannelDetailBean.LICENSE_TYPE));
                sb4.append(" keyStr==111==");
                sb4.append((String) map2.get(LiveTvChannelDetailBean.LICENSE_KEY));
                map2.remove(LiveTvChannelDetailBean.LICENSE_TYPE);
                map2.remove(LiveTvChannelDetailBean.LICENSE_KEY);
                String str6 = str4;
                str2 = str;
                str = str6;
            } else {
                str2 = "";
            }
            if (map2.containsKey("User-Agent")) {
                String str7 = (String) map2.get("User-Agent");
                if (str7 != null) {
                    factory.setUserAgent(str7);
                }
                map2.remove("User-Agent");
            }
            if (map2.containsKey(LiveTvChannelDetailBean.LICENSE_COOKIE) && ((String) map2.get(LiveTvChannelDetailBean.LICENSE_COOKIE)) != null) {
                factory.setAllowCrossProtocolRedirects(true);
            }
            if (map2.containsKey(DnsConfigBean.URL_TYPE_HOST)) {
                ?? r12 = (String) map2.get(DnsConfigBean.URL_TYPE_HOST);
                if (r12 != 0) {
                    objectRef.element = r12;
                }
                map2.remove(DnsConfigBean.URL_TYPE_HOST);
            }
            if (map2.containsKey(DnsConfigBean.URL_TYPE_HOST_TS)) {
                ?? r122 = (String) map2.get(DnsConfigBean.URL_TYPE_HOST_TS);
                if (r122 != 0) {
                    objectRef2.element = r122;
                }
                map2.remove(DnsConfigBean.URL_TYPE_HOST_TS);
            }
            StringBuilder sb5 = new StringBuilder();
            sb5.append("headerMap===111===");
            sb5.append(map2);
            if (!map2.isEmpty()) {
                factory.setDefaultRequestProperties((Map<String, String>) map2);
            }
        }
        JZDataSource jZDataSource3 = jZMediaExo.jzvd.jzDataSource;
        String string = (jZDataSource3 == null || (currentUrl = jZDataSource3.getCurrentUrl()) == null) ? null : currentUrl.toString();
        if (string == null) {
            return;
        }
        Jzvd jzvd2 = jZMediaExo.jzvd;
        if (jzvd2 == null || (jZDataSource = jzvd2.jzDataSource) == null || (objArr = jZDataSource.objects) == null) {
            url = null;
            abbreviate = null;
        } else if (!(objArr.length == 0)) {
            Object obj2 = objArr[0];
            xb.v.j("SubtitleView===1000====mSubtitle==" + obj2 + "====" + (obj2 instanceof MovieResult.Subtitle));
            if (obj2 == null || !(obj2 instanceof MovieResult.Subtitle) || (url2 = (subtitle = (MovieResult.Subtitle) obj2).getUrl()) == null || StringsKt.isBlank(url2)) {
                if (obj2 != null && (obj2 instanceof MovieResult.Track) && (abbreviate2 = (track = (MovieResult.Track) obj2).getAbbreviate()) != null && !StringsKt.isBlank(abbreviate2)) {
                    defaultTrackSelector.setParameters(defaultTrackSelector.getParameters().buildUpon().setPreferredTextLanguage("en").setPreferredAudioLanguage(track.getAbbreviate()).build());
                }
                url = null;
                abbreviate = null;
            } else {
                url = subtitle.getUrl();
                abbreviate = subtitle.getAbbreviate();
            }
            if (objArr.length > 1 && (obj = objArr[1]) != null && (obj instanceof MovieResult.Track) && (abbreviate3 = (track2 = (MovieResult.Track) obj).getAbbreviate()) != null && !StringsKt.isBlank(abbreviate3)) {
                defaultTrackSelector.setParameters(defaultTrackSelector.getParameters().buildUpon().setPreferredTextLanguage("en").setPreferredAudioLanguage(track2.getAbbreviate()).build());
            }
        }
        if (url == null || abbreviate == null) {
            textSource = null;
        } else {
            xb.v.j("SubtitleView===9999====mSubtitle==" + url);
            Intrinsics.checkNotNull(context);
            textSource = jZMediaExo.getTextSource(context, url, abbreviate);
        }
        if (StringsKt.contains$default((CharSequence) string, (CharSequence) ".m3u8", false, 2, (Object) null)) {
            if (StringsKt.contains$default((CharSequence) string, (CharSequence) cf.l.f4239f, false, 2, (Object) null) || StringsKt.contains$default((CharSequence) string, (CharSequence) cf.l.f4240g, false, 2, (Object) null)) {
                MediaItem mediaItemBuild = textSource != null ? new MediaItem.Builder().setUri(Uri.parse(string)).setSubtitleConfigurations(CollectionsKt.listOf(textSource)).build() : MediaItem.fromUri(Uri.parse(string));
                Intrinsics.checkNotNull(mediaItemBuild);
                OkHttpDataSource.Factory factory2 = factory;
                if (((CharSequence) objectRef.element).length() != 0) {
                    so.f55779a.Ci((String) objectRef.element, (String) objectRef2.element, string);
                    final String host = Uri.parse(string).getHost();
                    final String strReplace$default = host != null ? StringsKt.replace$default(host, "img1", "img", false, 4, (Object) null) : null;
                    Dns dns = new Dns() {
                        @Override
                        public List<InetAddress> lookup(String hostname) {
                            Intrinsics.checkNotNullParameter(hostname, "hostname");
                            return Intrinsics.areEqual(hostname, host) ? CollectionsKt.listOf(InetAddress.getByName(objectRef.element)) : Intrinsics.areEqual(hostname, strReplace$default) ? CollectionsKt.listOf(InetAddress.getByName(objectRef2.element)) : Dns.SYSTEM.lookup(hostname);
                        }
                    };
                    xb.v.j("host===" + host + k6.f0.f51635e + ((String) objectRef.element) + "==tsHost==" + strReplace$default + "==" + ((String) objectRef2.element));
                    factory2 = new OkHttpDataSource.Factory(new OkHttpClient.Builder().dns(dns).build());
                }
                mediaSourceCreateMediaSource = new DefaultMediaSourceFactory(factory2).createMediaSource(mediaItemBuild);
                Intrinsics.checkNotNullExpressionValue(mediaSourceCreateMediaSource, "createMediaSource(...)");
                mediaSourceCreateMediaSource.addEventListener(jZMediaExo.handler, jZMediaExo.mediaSourceEventListener);
            } else {
                Uri uriFromFile = Uri.fromFile(new File(string));
                FileDataSource.Factory factory3 = new FileDataSource.Factory();
                MediaItem mediaItemBuild2 = textSource != null ? new MediaItem.Builder().setUri(uriFromFile).setSubtitleConfigurations(CollectionsKt.listOf(textSource)).build() : MediaItem.fromUri(uriFromFile);
                Intrinsics.checkNotNull(mediaItemBuild2);
                mediaSourceCreateMediaSource = new DefaultMediaSourceFactory(factory3).createMediaSource(mediaItemBuild2);
                Intrinsics.checkNotNull(mediaSourceCreateMediaSource);
            }
        } else if (StringsKt.contains$default((CharSequence) string, (CharSequence) ".ts", false, 2, (Object) null) && !StringsKt.contains$default((CharSequence) string, (CharSequence) cf.l.f4239f, false, 2, (Object) null) && !StringsKt.contains$default((CharSequence) string, (CharSequence) cf.l.f4240g, false, 2, (Object) null)) {
            FileDataSource.Factory factory4 = new FileDataSource.Factory();
            DefaultExtractorsFactory adtsExtractorFlags = new DefaultExtractorsFactory().setTsExtractorTimestampSearchBytes(28200000).setTsExtractorMode(0).setAdtsExtractorFlags(1);
            Intrinsics.checkNotNullExpressionValue(adtsExtractorFlags, "setAdtsExtractorFlags(...)");
            mediaSourceCreateMediaSource = new ProgressiveMediaSource.Factory(factory4, adtsExtractorFlags).createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(string))));
            Intrinsics.checkNotNull(mediaSourceCreateMediaSource);
        } else if (StringsKt.contains$default((CharSequence) string, (CharSequence) ".mpd", false, 2, (Object) null) && StringsKt.contains$default((CharSequence) string, (CharSequence) cf.l.f4240g, false, 2, (Object) null)) {
            UUID uuid = (!StringsKt.contains$default((CharSequence) str, (CharSequence) "widevine", false, 2, (Object) null) && StringsKt.contains$default((CharSequence) str, (CharSequence) "clearkey", false, 2, (Object) null)) ? C.CLEARKEY_UUID : C.WIDEVINE_UUID;
            Intrinsics.checkNotNull(uuid);
            StringBuilder sb6 = new StringBuilder();
            sb6.append("mpd keyType==");
            sb6.append(str);
            byte[] bytes = str2.getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
            final DefaultDrmSessionManager defaultDrmSessionManagerBuild = new DefaultDrmSessionManager.Builder().setPlayClearSamplesWithoutKeys(true).setMultiSession(false).setKeyRequestParameters(new HashMap()).setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER).build(new LocalMediaDrmCallback(bytes));
            mediaSourceCreateMediaSource = new DashMediaSource.Factory(factory).setDrmSessionManagerProvider(new DrmSessionManagerProvider() {
                @Override
                public final DrmSessionManager get(MediaItem mediaItem) {
                    return JZMediaExo.prepare$lambda$11$lambda$10(defaultDrmSessionManagerBuild, mediaItem);
                }
            }).createMediaSource(MediaItem.fromUri(string));
            Intrinsics.checkNotNull(mediaSourceCreateMediaSource);
        } else {
            mediaSourceCreateMediaSource = new ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(Uri.parse(string)));
            Intrinsics.checkNotNull(mediaSourceCreateMediaSource);
        }
        StringBuilder sb7 = new StringBuilder();
        sb7.append("URL Link = ");
        sb7.append(string);
        ExoPlayer exoPlayer = jZMediaExo.simpleExoPlayer;
        Intrinsics.checkNotNull(exoPlayer);
        exoPlayer.addListener(jZMediaExo);
        ExoPlayer exoPlayer2 = jZMediaExo.simpleExoPlayer;
        if (exoPlayer2 != null) {
            exoPlayer2.addAnalyticsListener(jZMediaExo.analyticsListener);
        }
        if (jZMediaExo.jzvd.jzDataSource.looping) {
            ExoPlayer exoPlayer3 = jZMediaExo.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer3);
            exoPlayer3.setRepeatMode(1);
        } else {
            ExoPlayer exoPlayer4 = jZMediaExo.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer4);
            exoPlayer4.setRepeatMode(0);
        }
        ExoPlayer exoPlayer5 = jZMediaExo.simpleExoPlayer;
        Intrinsics.checkNotNull(exoPlayer5);
        exoPlayer5.setMediaSource(mediaSourceCreateMediaSource);
        ExoPlayer exoPlayer6 = jZMediaExo.simpleExoPlayer;
        Intrinsics.checkNotNull(exoPlayer6);
        exoPlayer6.prepare();
        ExoPlayer exoPlayer7 = jZMediaExo.simpleExoPlayer;
        Intrinsics.checkNotNull(exoPlayer7);
        exoPlayer7.pause();
        jZMediaExo.callback = jZMediaExo.new onBufferingUpdate();
        JZTextureView jZTextureView = jZMediaExo.jzvd.textureView;
        if (jZTextureView != null && (surfaceTexture = jZTextureView.getSurfaceTexture()) != null) {
            ExoPlayer exoPlayer8 = jZMediaExo.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer8);
            exoPlayer8.setVideoSurface(new Surface(surfaceTexture));
        }
        jZMediaExo.setSpeed(jZMediaExo.speed);
        jZMediaExo.jzvd.onPrepared();
    }

    public final void reportLoading(long j10) {
        int i10;
        long jCurrentTimeMillis;
        long jLongValue = 0;
        if (j10 > 0) {
            jCurrentTimeMillis = System.currentTimeMillis();
            i10 = 1;
        } else {
            i10 = 0;
            jCurrentTimeMillis = 0;
        }
        int i11 = i10;
        xb.v.j("setBuffer====555==" + i11 + "==fps==" + this.fps + "===isUserPaused==" + this.isUserPaused);
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        Format videoFormat = exoPlayer != null ? exoPlayer.getVideoFormat() : null;
        String str = videoFormat != null ? videoFormat.sampleMimeType : null;
        BandwidthMeter bandwidthMeter = this.bandwidthMeter;
        Long lValueOf = bandwidthMeter != null ? Long.valueOf(bandwidthMeter.getBitrateEstimate()) : null;
        ExoPlayer exoPlayer2 = this.simpleExoPlayer;
        Long lValueOf2 = exoPlayer2 != null ? Long.valueOf(exoPlayer2.getBufferedPosition()) : null;
        ExoPlayer exoPlayer3 = this.simpleExoPlayer;
        Long lValueOf3 = exoPlayer3 != null ? Long.valueOf(exoPlayer3.getCurrentPosition()) : null;
        if (lValueOf2 != null && lValueOf3 != null) {
            jLongValue = lValueOf2.longValue() - lValueOf3.longValue();
        }
        final PlayerBufferBean playerBufferBean = new PlayerBufferBean(i11, Long.valueOf(this.bufferingStartTime), Long.valueOf(jCurrentTimeMillis), null, null, null, null, null, null, null, null, null, null, null, null, null, null, this.fps, str, null, null, lValueOf != null ? Long.valueOf(lValueOf.longValue() / 1000) : null, Long.valueOf(jLongValue), null, null, null, null, null, 261750776, null);
        xb.v.j("setBuffer===111====" + playerBufferBean);
        this.handler.post(new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.reportLoading$lambda$15(this.f27820a, playerBufferBean);
            }
        });
    }

    public static final void reportLoading$lambda$15(JZMediaExo jZMediaExo, PlayerBufferBean playerBufferBean) {
        BasePlayer basePlayer = jZMediaExo.basePlayer;
        if (basePlayer != null) {
            basePlayer.reportPlayerLoadingEvent(playerBufferBean);
        }
    }

    public final void addListener(@NotNull Player.Listener listener) {
        Intrinsics.checkNotNullParameter(listener, "listener");
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer != null) {
            exoPlayer.addListener(listener);
        }
    }

    public final void changeTrack(@NotNull String language) {
        Intrinsics.checkNotNullParameter(language, "language");
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer != null) {
            Tracks currentTracks = exoPlayer.getCurrentTracks();
            Intrinsics.checkNotNullExpressionValue(currentTracks, "getCurrentTracks(...)");
            ImmutableList<Tracks.Group> groups = currentTracks.getGroups();
            Intrinsics.checkNotNullExpressionValue(groups, "getGroups(...)");
            for (Tracks.Group group : groups) {
                if (1 == group.getType()) {
                    Format trackFormat = group.getTrackFormat(0);
                    Intrinsics.checkNotNullExpressionValue(trackFormat, "getTrackFormat(...)");
                    StringBuilder sb2 = new StringBuilder();
                    sb2.append("audio format:");
                    sb2.append(trackFormat.language);
                } else if (3 == group.getType()) {
                    Format trackFormat2 = group.getTrackFormat(0);
                    Intrinsics.checkNotNullExpressionValue(trackFormat2, "getTrackFormat(...)");
                    StringBuilder sb3 = new StringBuilder();
                    sb3.append("text format:");
                    sb3.append(trackFormat2);
                } else {
                    Format trackFormat3 = group.getTrackFormat(0);
                    Intrinsics.checkNotNullExpressionValue(trackFormat3, "getTrackFormat(...)");
                    StringBuilder sb4 = new StringBuilder();
                    sb4.append("other format:");
                    sb4.append(trackFormat3);
                }
            }
            TrackSelector trackSelector = exoPlayer.getTrackSelector();
            DefaultTrackSelector defaultTrackSelector = trackSelector instanceof DefaultTrackSelector ? (DefaultTrackSelector) trackSelector : null;
            if (defaultTrackSelector != null) {
                defaultTrackSelector.setParameters(defaultTrackSelector.getParameters().buildUpon().setPreferredTextLanguage(language).setPreferredAudioLanguage(language).build());
            }
        }
    }

    @NotNull
    public final AnalyticsListener getAnalyticsListener() {
        return this.analyticsListener;
    }

    @Nullable
    public final BandwidthMeter getBandwidthMeter() {
        return this.bandwidthMeter;
    }

    public final int getBufferingCount() {
        return this.bufferingCount;
    }

    @Override
    public long getCurrentPosition() {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer == null) {
            return 0L;
        }
        Intrinsics.checkNotNull(exoPlayer);
        return exoPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer == null) {
            return 0L;
        }
        Intrinsics.checkNotNull(exoPlayer);
        return exoPlayer.getDuration();
    }

    @NotNull
    public final String getErrorTrackString() {
        return this.errorTrackString;
    }

    @Nullable
    public final Float getFps() {
        return this.fps;
    }

    public final long getMaxSingleBuffering() {
        return this.maxSingleBuffering;
    }

    @NotNull
    public final MediaSourceEventListener getMediaSourceEventListener() {
        return this.mediaSourceEventListener;
    }

    @NotNull
    public final Runnable getReportRunnable() {
        return this.reportRunnable;
    }

    public final int getTempPlayerErrorCode() {
        return this.tempPlayerErrorCode;
    }

    public final int getTempPlayerErrorCodeGroup() {
        return this.tempPlayerErrorCodeGroup;
    }

    public final long getTotalBufferingTime() {
        return this.totalBufferingTime;
    }

    @Override
    public boolean isPlaying() {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer == null) {
            return false;
        }
        Intrinsics.checkNotNull(exoPlayer);
        return exoPlayer.getPlayWhenReady();
    }

    @Override
    public void onCues(List list) {
        androidx.media3.common.y.e(this, list);
    }

    @Override
    public void onPlaybackStateChanged(int i10) {
        androidx.media3.common.y.r(this, i10);
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (i10 == 2) {
            if (!this.hasFirstReady || this.isSeeking || this.isUserPaused) {
                this.bufferingStartTime1 = -1L;
            } else {
                this.bufferingStartTime1 = jElapsedRealtime;
                this.hasShownNetworkBadTip = false;
                this.handler.removeCallbacks(this.bufferingTimeoutRunnable);
                this.handler.postDelayed(this.bufferingTimeoutRunnable, this.BUFFERING_TIMEOUT_MS);
            }
            xb.v.j("buffering===222==STATE_BUFFERING===isSeeking=" + this.isSeeking + "--hasFirstReady===" + this.hasFirstReady + "---isUserPaused===" + this.isUserPaused + "---bufferingStartTime1===" + this.bufferingStartTime1 + "---totalBufferingTime==" + this.totalBufferingTime + "---bufferingCount===" + this.bufferingCount + "===" + this.maxSingleBuffering);
            final PlayerLoadingBufferBean playerLoadingBufferBean = new PlayerLoadingBufferBean(Boolean.valueOf(this.hasFirstReady), Boolean.valueOf(this.isSeeking), Boolean.valueOf(this.isUserPaused), this.bufferingStartTime1, this.totalBufferingTime, this.bufferingCount, this.maxSingleBuffering);
            this.handler.post(new Runnable() {
                @Override
                public final void run() {
                    JZMediaExo.onPlaybackStateChanged$lambda$21(this.f27863a, playerLoadingBufferBean);
                }
            });
            return;
        }
        if (i10 != 3) {
            if (i10 != 4) {
                return;
            }
            this.handler.removeCallbacks(this.bufferingTimeoutRunnable);
            this.bufferingStartTime1 = -1L;
            xb.v.j("buffering===555==STATE_ENDED===bufferingStartTime1===" + this.bufferingStartTime1 + "--totalBufferingTime==" + this.totalBufferingTime + "---bufferingCount===" + this.bufferingCount + "===" + this.maxSingleBuffering);
            return;
        }
        long j10 = this.bufferingStartTime1;
        if (j10 > 0) {
            long j11 = jElapsedRealtime - j10;
            if (j11 > 80) {
                this.totalBufferingTime += j11;
                this.bufferingCount++;
                this.maxSingleBuffering = Math.max(this.maxSingleBuffering, j11);
            }
            xb.v.j("buffering===3333==STATE_READY===isSeeking=" + this.isSeeking + "--hasFirstReady===" + this.hasFirstReady + "---isUserPaused===" + this.isUserPaused + "---bufferingStartTime1===" + this.bufferingStartTime1 + "---duration==" + j11 + "--totalBufferingTime==" + this.totalBufferingTime + "---bufferingCount===" + this.bufferingCount + "===" + this.maxSingleBuffering);
            this.handler.removeCallbacks(this.bufferingTimeoutRunnable);
            this.handler.post(new Runnable() {
                @Override
                public final void run() {
                    JZMediaExo.onPlaybackStateChanged$lambda$22(this.f27870a);
                }
            });
            this.bufferingStartTime1 = -1L;
        }
        if (!this.hasFirstReady) {
            this.hasFirstReady = true;
        }
        this.isSeeking = false;
        xb.v.j("buffering===4444==STATE_READY===isSeeking=" + this.isSeeking + "--hasFirstReady==" + this.hasFirstReady + "---bufferingStartTime1===" + this.bufferingStartTime1 + "--totalBufferingTime==" + this.totalBufferingTime + "---bufferingCount===" + this.bufferingCount + "===" + this.maxSingleBuffering);
    }

    @Override
    public void onPlayerError(@NotNull final PlaybackException error) {
        String string;
        Object currentUrl;
        Intrinsics.checkNotNullParameter(error, "error");
        JZDataSource jZDataSource = this.jzvd.jzDataSource;
        if (jZDataSource == null || (currentUrl = jZDataSource.getCurrentUrl()) == null || (string = currentUrl.toString()) == null) {
            string = "";
        }
        final String str = string;
        int i10 = error.errorCode;
        this.tempPlayerErrorCode = i10;
        this.tempPlayerErrorCodeGroup = i10 / 1000;
        StringBuilder sb2 = new StringBuilder();
        sb2.append("errorMessage:" + error.getLocalizedMessage() + '\n');
        sb2.append("stack:");
        StackTraceElement[] stackTrace = error.getStackTrace();
        Intrinsics.checkNotNullExpressionValue(stackTrace, "getStackTrace(...)");
        for (StackTraceElement stackTraceElement : stackTrace) {
            sb2.append(stackTraceElement.toString());
        }
        this.errorTrackString = sb2.toString();
        final long currentPosition = getCurrentPosition() / 1000;
        this.handler.post(new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.onPlayerError$lambda$17(this.f27963a, str, error, currentPosition);
            }
        });
    }

    @Override
    public void onPlayerStateChanged(final boolean z10, final int i10) {
        StringBuilder sb2 = new StringBuilder();
        sb2.append("onPlayerStateChanged");
        sb2.append(i10);
        sb2.append("/ready=");
        sb2.append(z10);
        this.handler.post(new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.onPlayerStateChanged$lambda$14(i10, this, z10);
            }
        });
    }

    @Override
    public void onPositionDiscontinuity(@NotNull Player.PositionInfo oldPosition, @NotNull Player.PositionInfo newPosition, int i10) {
        Intrinsics.checkNotNullParameter(oldPosition, "oldPosition");
        Intrinsics.checkNotNullParameter(newPosition, "newPosition");
        androidx.media3.common.y.y(this, oldPosition, newPosition, i10);
        if (i10 == 1 || i10 == 2) {
            this.isSeeking = true;
        }
        this.handler.post(new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.onPositionDiscontinuity$lambda$20(this.f27834a);
            }
        });
        xb.v.j("buffering===111==isSeeking=" + this.isSeeking);
    }

    @Override
    public void onSurfaceTextureAvailable(@NotNull SurfaceTexture surface, int i10, int i11) {
        Intrinsics.checkNotNullParameter(surface, "surface");
        SurfaceTexture surfaceTexture = JZMediaInterface.SAVED_SURFACE;
        if (surfaceTexture != null) {
            this.jzvd.textureView.setSurfaceTexture(surfaceTexture);
            return;
        }
        JZMediaInterface.SAVED_SURFACE = surface;
        prepare();
        JZMediaInterface.SAVED_SURFACE = surface;
    }

    @Override
    public void onTracksChanged(@NotNull Tracks tracks) {
        Intrinsics.checkNotNullParameter(tracks, "tracks");
        androidx.media3.common.y.I(this, tracks);
    }

    @Override
    public void onVideoSizeChanged(@NotNull final VideoSize videoSize) {
        Intrinsics.checkNotNullParameter(videoSize, "videoSize");
        this.handler.post(new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.onVideoSizeChanged$lambda$12(this.f27876a, videoSize);
            }
        });
    }

    @Override
    public void pause() {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer != null) {
            Intrinsics.checkNotNull(exoPlayer);
            exoPlayer.pause();
        }
    }

    @Override
    public void prepare() {
        final Context context = this.jzvd.getContext();
        release();
        HandlerThread handlerThread = new HandlerThread("JZVD");
        this.mMediaHandlerThread = handlerThread;
        handlerThread.start();
        this.mMediaHandler = new Handler(context.getMainLooper());
        this.handler = new Handler(Looper.getMainLooper());
        this.mMediaHandler.post(new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.prepare$lambda$11(context, this);
            }
        });
    }

    @Override
    public void seekTo(long j10) {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer == null) {
            return;
        }
        Intrinsics.checkNotNull(exoPlayer);
        if (!exoPlayer.isPlaying()) {
            ExoPlayer exoPlayer2 = this.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer2);
            exoPlayer2.play();
        }
        if (j10 == 0 || j10 == getDuration() || j10 != this.previousSeek) {
            ExoPlayer exoPlayer3 = this.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer3);
            if (j10 >= exoPlayer3.getBufferedPosition()) {
                this.jzvd.onStatePreparingPlaying();
            }
            ExoPlayer exoPlayer4 = this.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer4);
            exoPlayer4.seekTo(j10);
            this.previousSeek = j10;
            this.jzvd.seekToInAdvance = j10;
        }
    }

    public final void setAnalyticsListener(@NotNull AnalyticsListener analyticsListener) {
        Intrinsics.checkNotNullParameter(analyticsListener, "<set-?>");
        this.analyticsListener = analyticsListener;
    }

    public final void setBandwidthMeter(@Nullable BandwidthMeter bandwidthMeter) {
        this.bandwidthMeter = bandwidthMeter;
    }

    public final void setBitRate(@Nullable SelectionBitRateBean selectionBitRateBean) {
        if (selectionBitRateBean == null) {
            return;
        }
        try {
            ExoPlayer exoPlayer = this.simpleExoPlayer;
            if (exoPlayer != null) {
                TrackSelector trackSelector = exoPlayer.getTrackSelector();
                DefaultTrackSelector defaultTrackSelector = trackSelector instanceof DefaultTrackSelector ? (DefaultTrackSelector) trackSelector : null;
                if (defaultTrackSelector != null) {
                    SparseArray sparseArray = new SparseArray();
                    sparseArray.put(selectionBitRateBean.getGroupIndex(), new DefaultTrackSelector.SelectionOverride(selectionBitRateBean.getGroupIndex(), selectionBitRateBean.getTrackIndex()));
                    MappingTrackSelector.MappedTrackInfo currentMappedTrackInfo = defaultTrackSelector.getCurrentMappedTrackInfo();
                    if (currentMappedTrackInfo == null) {
                        return;
                    }
                    DefaultTrackSelector.Parameters.Builder builderBuildUpon = defaultTrackSelector.getParameters().buildUpon();
                    Intrinsics.checkNotNullExpressionValue(builderBuildUpon, "buildUpon(...)");
                    builderBuildUpon.clearSelectionOverrides(0).setRendererDisabled(0, false);
                    builderBuildUpon.setSelectionOverride(0, currentMappedTrackInfo.getTrackGroups(0), (DefaultTrackSelector.SelectionOverride) sparseArray.get(0));
                    defaultTrackSelector.setParameters(builderBuildUpon);
                }
            }
        } catch (Exception e10) {
            e10.printStackTrace();
        }
    }

    public final void setErrorTrackString(@NotNull String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.errorTrackString = str;
    }

    public final void setFps(@Nullable Float f10) {
        this.fps = f10;
    }

    public final void setReportRunnable(@NotNull Runnable runnable) {
        Intrinsics.checkNotNullParameter(runnable, "<set-?>");
        this.reportRunnable = runnable;
    }

    @Override
    public void setSpeed(float f10) {
        this.speed = f10;
        if (this.simpleExoPlayer != null) {
            PlaybackParameters playbackParameters = new PlaybackParameters(f10, 1.0f);
            ExoPlayer exoPlayer = this.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer);
            exoPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    @Override
    public void setSurface(@NotNull Surface surface) {
        Intrinsics.checkNotNullParameter(surface, "surface");
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer != null) {
            Intrinsics.checkNotNull(exoPlayer);
            exoPlayer.setVideoSurface(surface);
        }
    }

    public final void setTempPlayerErrorCode(int i10) {
        this.tempPlayerErrorCode = i10;
    }

    public final void setTempPlayerErrorCodeGroup(int i10) {
        this.tempPlayerErrorCodeGroup = i10;
    }

    @Override
    public void setVolume(float f10, float f11) {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer != null) {
            Intrinsics.checkNotNull(exoPlayer);
            exoPlayer.setVolume(f10);
            ExoPlayer exoPlayer2 = this.simpleExoPlayer;
            Intrinsics.checkNotNull(exoPlayer2);
            exoPlayer2.setVolume(f11);
        }
    }

    @Override
    public void start() {
        ExoPlayer exoPlayer = this.simpleExoPlayer;
        if (exoPlayer == null) {
            prepare();
        } else {
            Intrinsics.checkNotNull(exoPlayer);
            exoPlayer.play();
        }
    }

    public JZMediaExo(@Nullable Jzvd jzvd) {
        BasePlayer basePlayer;
        super(jzvd);
        this.TAG = "JZMediaExo";
        this.speed = 1.0f;
        this.errorTrackString = "";
        if (jzvd instanceof BasePlayer) {
            basePlayer = (BasePlayer) jzvd;
        } else {
            basePlayer = null;
        }
        this.basePlayer = basePlayer;
        this.mediaSourceEventListener = new MediaSourceEventListener() {
            @Override
            public void onDownstreamFormatChanged(int i10, MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
                androidx.media3.exoplayer.source.r.a(this, i10, mediaPeriodId, mediaLoadData);
            }

            @Override
            public void onLoadCanceled(int i10, MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                androidx.media3.exoplayer.source.r.b(this, i10, mediaPeriodId, loadEventInfo, mediaLoadData);
            }

            @Override
            public void onLoadCompleted(int i10, MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                androidx.media3.exoplayer.source.r.c(this, i10, mediaPeriodId, loadEventInfo, mediaLoadData);
            }

            @Override
            public void onLoadStarted(int i10, MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, int i11) {
                androidx.media3.exoplayer.source.r.e(this, i10, mediaPeriodId, loadEventInfo, mediaLoadData, i11);
            }

            @Override
            public void onUpstreamDiscarded(int i10, MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
                androidx.media3.exoplayer.source.r.f(this, i10, mediaPeriodId, mediaLoadData);
            }

            @Override
            public void onLoadError(int i10, MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean z10) {
                Intrinsics.checkNotNullParameter(loadEventInfo, "loadEventInfo");
                Intrinsics.checkNotNullParameter(mediaLoadData, "mediaLoadData");
                Intrinsics.checkNotNullParameter(error, "error");
                androidx.media3.exoplayer.source.r.d(this, i10, mediaPeriodId, loadEventInfo, mediaLoadData, error, z10);
                so soVar = so.f55779a;
                String string = loadEventInfo.uri.toString();
                Intrinsics.checkNotNullExpressionValue(string, "toString(...)");
                soVar.Pm(string, error.getMessage());
            }
        };
        this.reportRunnable = new Runnable() {
            @Override
            public void run() {
                ExoPlayer exoPlayer;
                xb.v.j("setBuffer====111");
                if (!this.this$0.isBuffering || this.this$0.hasReported || (exoPlayer = this.this$0.simpleExoPlayer) == null || exoPlayer.getPlaybackState() != 2) {
                    return;
                }
                xb.v.j("setBuffer====222");
                this.this$0.reportLoading(0L);
                this.this$0.hasReported = true;
            }
        };
        this.bufferingStartTime1 = -1L;
        this.BUFFERING_TIMEOUT_MS = 10000L;
        this.bufferingTimeoutRunnable = new Runnable() {
            @Override
            public final void run() {
                JZMediaExo.bufferingTimeoutRunnable$lambda$19(this.f27882a);
            }
        };
    }

    public static final void release$lambda$13(ExoPlayer exoPlayer, HandlerThread handlerThread) {
        exoPlayer.release();
        handlerThread.quit();
    }

    private final void setBuffer(boolean z10) {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (z10) {
            if (this.isBuffering) {
                return;
            }
            this.isBuffering = true;
            this.hasReported = false;
            this.bufferingStartTime = jCurrentTimeMillis;
            this.handler.removeCallbacks(this.reportRunnable);
            this.handler.postDelayed(this.reportRunnable, 3000L);
            return;
        }
        if (!this.isBuffering) {
            return;
        }
        this.isBuffering = false;
        this.handler.removeCallbacks(this.reportRunnable);
        long j10 = this.bufferingStartTime;
        if (j10 > 0) {
            long j11 = jCurrentTimeMillis - j10;
            xb.v.j("setBuffer====333===" + j11);
            if (j11 >= 3000) {
                xb.v.j("setBuffer====444");
                reportLoading(j11);
                this.hasReported = true;
            }
            this.bufferingStartTime = 0L;
        }
    }

    @Override
    public void onIsPlayingChanged(boolean z10) {
        androidx.media3.common.y.j(this, z10);
        this.isUserPaused = !z10;
        xb.v.j("buffering===666==onIsPlayingChanged===isUserPaused===" + this.isUserPaused + "===bufferingStartTime1=" + this.bufferingStartTime1 + "--totalBufferingTime==" + this.totalBufferingTime + "---bufferingCount===" + this.bufferingCount + "===" + this.maxSingleBuffering);
    }
}
