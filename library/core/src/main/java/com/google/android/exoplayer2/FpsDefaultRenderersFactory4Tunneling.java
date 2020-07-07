package com.google.android.exoplayer2;

import android.content.Context;
import android.os.Handler;

import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.FpsMediaCodecVideoRenderer4Tunneling2;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import javax.annotation.Nonnull;

public class FpsDefaultRenderersFactory4Tunneling extends DefaultRenderersFactory {
  public FpsDefaultRenderersFactory4Tunneling(Context context) {
    super(context);
  }

  @Nonnull
  @Override
  protected Renderer buildPrimaryRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      Handler eventHandler,
      VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs) {
    return new FpsMediaCodecVideoRenderer4Tunneling2(
        context,
        mediaCodecSelector,
        allowedVideoJoiningTimeMs,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
  }
}
