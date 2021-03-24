/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.video.surfacecapturer;

import android.content.Context;
import android.media.MediaCodec;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import java.nio.ByteBuffer;

/**
 * Decodes and renders video using {@link MediaCodec}.
 *
 * <p>This video renderer will only render the first frame after position reset (seeking), or after
 * being re-enabled.
 */
public class SingleFrameMediaCodecVideoRenderer extends MediaCodecVideoRenderer {

  private static final String TAG = "SingleFrameMediaCodecVideoRenderer";
  private boolean hasRenderedFirstFrame;
  @Nullable private Surface surface;

  public SingleFrameMediaCodecVideoRenderer(
      Context context, MediaCodecSelector mediaCodecSelector) {
    super(context, mediaCodecSelector);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_SURFACE) {
      this.surface = (Surface) message;
    }
    super.handleMessage(messageType, message);
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    hasRenderedFirstFrame = false;
    super.onEnabled(joining, mayRenderStartOfStream);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    hasRenderedFirstFrame = false;
    super.onPositionReset(positionUs, joining);
  }

  @Override
  protected boolean processOutputBuffer(
      long positionUs,
      long elapsedRealtimeUs,
      @Nullable MediaCodecAdapter codec,
      @Nullable ByteBuffer buffer,
      int bufferIndex,
      int bufferFlags,
      int sampleCount,
      long bufferPresentationTimeUs,
      boolean isDecodeOnlyBuffer,
      boolean isLastBuffer,
      Format format)
      throws ExoPlaybackException {
    Assertions.checkNotNull(codec); // Can not render video without codec

    long presentationTimeUs = bufferPresentationTimeUs - getOutputStreamOffsetUs();
    if (isDecodeOnlyBuffer && !isLastBuffer) {
      skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
      return true;
    }
    if (surface == null || hasRenderedFirstFrame) {
      return false;
    }

    hasRenderedFirstFrame = true;
    if (Util.SDK_INT >= 21) {
      renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, System.nanoTime());
    } else {
      renderOutputBuffer(codec, bufferIndex, presentationTimeUs);
    }
    return true;
  }
}
