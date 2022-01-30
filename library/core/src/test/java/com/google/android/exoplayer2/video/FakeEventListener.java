package com.google.android.exoplayer2.video;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.decoder.DecoderCounters;

public class FakeEventListener implements VideoRendererEventListener {
  @Nullable
  VideoSize videoSize;
  @Nullable
  DecoderCounters decoderCounters;

  private long firstFrameRenderMs = Long.MIN_VALUE;

  private int droppedFrames;

  private Exception videoCodecError;

  @Override
  public void onVideoSizeChanged(VideoSize videoSize) {
    this.videoSize = videoSize;
  }

  public boolean isVideoEnabled() {
    return decoderCounters != null;
  }

  @Override
  public void onVideoEnabled(DecoderCounters counters) {
    decoderCounters = counters;
  }

  @Override
  public void onVideoDisabled(DecoderCounters counters) {
    decoderCounters = null;
  }

  public long getFirstFrameRenderMs() {
    return firstFrameRenderMs;
  }

  @Override
  public void onRenderedFirstFrame(Object output, long renderTimeMs) {
    firstFrameRenderMs = renderTimeMs;
  }

  public int getDroppedFrames() {
    return droppedFrames;
  }

  @Override
  public void onDroppedFrames(int count, long elapsedMs) {
    droppedFrames+=count;
  }

  public Exception getVideoCodecError() {
    return videoCodecError;
  }

  @Override
  public void onVideoCodecError(Exception videoCodecError) {
    this.videoCodecError = videoCodecError;
  }

}
