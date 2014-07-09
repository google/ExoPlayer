package com.google.android.exoplayer.demo.full.player;

import android.media.MediaCodec;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HLSChunkSource;
import com.google.android.exoplayer.hls.MainPlaylist;
import com.google.android.exoplayer.hls.MainPlaylistFetcher;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;

public class HLSRendererBuilder implements DemoPlayer.RendererBuilder, ManifestFetcher.ManifestCallback<MainPlaylist> {
    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int VIDEO_BUFFER_SEGMENTS = 200;
    private static final int AUDIO_BUFFER_SEGMENTS = 60;

    private static final String TAG = "HLSRendererBuilder";

    private final String userAgent;
    private final TextView debugTextView;
    private DemoPlayer player;
    private RendererBuilderCallback callback;

    private String url;

    public HLSRendererBuilder(String userAgent, String url,
                                          TextView debugTextView) {
        this.url = url;
        this.userAgent = userAgent;
        this.debugTextView = debugTextView;
    }

    public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
        this.player = player;
        this.callback = callback;

        MainPlaylistFetcher fetcher = new MainPlaylistFetcher(url, this);
        fetcher.execute(url);

    }

    @Override
    public void onManifest(String contentId, MainPlaylist manifest) {
        // Invoke the callback.
        String[][] trackNames = new String[DemoPlayer.RENDERER_COUNT][];
        // XXX: is this important ?
        // trackNames[DemoPlayer.TYPE_AUDIO] = audioTrackNames;
        // trackNames[DemoPlayer.TYPE_TEXT] = textTrackNames;
        Handler mainHandler = player.getMainHandler();
        LoadControl loadControl = new DefaultLoadControl(new BufferPool(BUFFER_SEGMENT_SIZE));
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);

        DataSource dataSource = new HttpDataSource(userAgent, HttpDataSource.REJECT_PAYWALL_TYPES,
                bandwidthMeter);
        ChunkSource chunkSource = new HLSChunkSource(url, manifest, dataSource,
                new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter));

        // Build the video renderer.
        ChunkSampleSource videoSampleSource = new ChunkSampleSource(chunkSource, loadControl,
                VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player,
                DemoPlayer.TYPE_VIDEO);
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource,
                null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
                mainHandler, player, 50);

        // Build the debug renderer.
        TrackRenderer debugRenderer = debugTextView != null
                ? new DebugTrackRenderer(debugTextView, videoRenderer, videoSampleSource)
                : null;

        TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
        renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
        renderers[DemoPlayer.TYPE_AUDIO] = null;
        renderers[DemoPlayer.TYPE_TEXT] = null;
        renderers[DemoPlayer.TYPE_DEBUG] = debugRenderer;
        callback.onRenderers(null, null, renderers);
    }

    @Override
    public void onManifestError(String contentId, Exception e) {
        Log.d(TAG, "ManifestError");
        e.printStackTrace();
    }
}
