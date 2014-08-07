package com.google.android.exoplayer.demo.full.player;

import android.media.MediaCodec;
import android.os.Handler;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HLSSampleSource;

public class HLSRendererBuilder implements DemoPlayer.RendererBuilder {

  private static final String TAG = "HLSRendererBuilder";

  private final String userAgent;
  private final boolean audioOnly;

  private String url;

  public HLSRendererBuilder(String userAgent, String url) {
    this(userAgent, url, false);
  }

  public HLSRendererBuilder(String userAgent, String url, boolean audioOnly) {
    this.url = url;
    this.userAgent = userAgent;
    this.audioOnly = audioOnly;
  }

  public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {

    Handler mainHandler = player.getMainHandler();

    HLSSampleSource sampleSource = new HLSSampleSource(this.url);

    sampleSource.setAudioOnly(this.audioOnly);

    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = new MediaCodecVideoTrackRenderer(sampleSource,
            null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
            mainHandler, player, 1);
    renderers[DemoPlayer.TYPE_AUDIO] = new MediaCodecAudioTrackRenderer(sampleSource, null, true, mainHandler, player);;
    renderers[DemoPlayer.TYPE_TEXT] = null;
    renderers[DemoPlayer.TYPE_DEBUG] = null;
    callback.onRenderers(null, null, renderers);
  }
}
