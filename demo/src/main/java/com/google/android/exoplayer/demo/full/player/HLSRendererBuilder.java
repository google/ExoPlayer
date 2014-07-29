package com.google.android.exoplayer.demo.full.player;

import android.media.MediaCodec;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HLSChunkSource;
import com.google.android.exoplayer.hls.MainPlaylist;
import com.google.android.exoplayer.upstream.AESDataSource;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.HttpDataSource;

public class HLSRendererBuilder implements DemoPlayer.RendererBuilder {
  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 60;

  private static final String TAG = "HLSRendererBuilder";

  private final String userAgent;
  private final TextView debugTextView;
  private DemoPlayer player;
  private RendererBuilderCallback callback;

  private String url;
  private MainPlaylist mainPlaylist;
  private Exception exception;

  private class PlaylistParsingTask extends AsyncTask<Void, Void, Exception> {
    protected Exception doInBackground(Void... params) {
      if (mainPlaylist == null) {
        try {
          mainPlaylist = MainPlaylist.parse(HLSRendererBuilder.this.url);
        } catch (Exception e) {
          Log.d(TAG, "cannot parse main playlist");
          e.printStackTrace();

        }
        if (mainPlaylist == null || mainPlaylist.entries.size() == 0) {
          // no main playlist: we fake one
          mainPlaylist = MainPlaylist.createVideoMainPlaylist(HLSRendererBuilder.this.url);
        }
      }
      try {
        mainPlaylist.parseVariants();
      } catch (Exception e) {
        return e;
      }
      return null;
    }

    protected void onPostExecute(Exception e) {
      if (e == null) {
        HLSRendererBuilder.this.gotMainPlaylist();
      } else {
        HLSRendererBuilder.this.gotError(e);
      }
    }
  }

  public HLSRendererBuilder(String userAgent, String url,
                            TextView debugTextView, MainPlaylist mainPlaylist) {
    this.url = url;
    this.userAgent = userAgent;
    this.debugTextView = debugTextView;
    this.mainPlaylist = mainPlaylist;
  }

  public HLSRendererBuilder(String userAgent, String url,
                            TextView debugTextView) {
    this(userAgent, url, debugTextView, null);
  }

  public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
    this.player = player;
    this.callback = callback;

    PlaylistParsingTask task = new PlaylistParsingTask();
    task.execute();

  }

  public void gotMainPlaylist() {
    String[][] trackNames = new String[DemoPlayer.RENDERER_COUNT][];
    // trackNames[DemoPlayer.TYPE_AUDIO] = audioTrackNames;
    // trackNames[DemoPlayer.TYPE_TEXT] = textTrackNames;
    Handler mainHandler = player.getMainHandler();
    LoadControl loadControl = new DefaultLoadControl(new BufferPool(BUFFER_SEGMENT_SIZE));
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);

        /*
         * do not reject text content types, https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_16x9/gear0/main.aac
         * is actual text
         */
    DataSource HTTPDataSource = new HttpDataSource(userAgent, null/*HttpDataSource.REJECT_PAYWALL_TYPES*/,
            bandwidthMeter);
    DataSource dataSource = new AESDataSource(userAgent, HTTPDataSource);
    ChunkSource chunkSource = new HLSChunkSource(url, this.mainPlaylist, dataSource,
            new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter));

    ChunkSampleSource sampleSource = new ChunkSampleSource(chunkSource, loadControl,
            VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player,
            DemoPlayer.TYPE_VIDEO);

    MediaCodecVideoTrackRenderer videoRenderer = null;
    MediaCodecAudioTrackRenderer audioRenderer = null;
    TrackRenderer debugRenderer = null;

    int trackCount = chunkSource.getTrackCount();
    for (int i = 0; i < trackCount; i++) {
      TrackInfo info = chunkSource.getTrackInfo(i);
      if (audioRenderer == null && info.mimeType.startsWith("audio")) {
        audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource, null, true, mainHandler, player);
      } else if (videoRenderer == null && info.mimeType.startsWith("video")) {
        videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
                mainHandler, player, 1);
      }
    }


    //debugRenderer = null;
    //videoRenderer = null;
    if (videoRenderer != null && debugTextView != null) {
      debugRenderer = new DebugTrackRenderer(debugTextView, videoRenderer, sampleSource);
    }


    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_TEXT] = null;
    renderers[DemoPlayer.TYPE_DEBUG] = debugRenderer;
    callback.onRenderers(null, null, renderers);
  }

  public void gotError(Exception e) {
    Log.d(TAG, "ManifestError");
    e.printStackTrace();
  }
}
