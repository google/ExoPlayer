/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.testutil;

import android.os.SystemClock;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.HandlerWrapper;

/** A {@link FakeRenderer} that supports {@link C#TRACK_TYPE_AUDIO}. */
public class FakeAudioRenderer extends FakeRenderer {

  private final HandlerWrapper handler;
  private final AudioRendererEventListener eventListener;
  private final DecoderCounters decoderCounters;
  private boolean notifiedPositionAdvancing;

  public FakeAudioRenderer(HandlerWrapper handler, AudioRendererEventListener eventListener) {
    super(C.TRACK_TYPE_AUDIO);
    this.handler = handler;
    this.eventListener = eventListener;
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    handler.post(() -> eventListener.onAudioEnabled(decoderCounters));
    notifiedPositionAdvancing = false;
  }

  @Override
  protected void onDisabled() {
    super.onDisabled();
    handler.post(() -> eventListener.onAudioDisabled(decoderCounters));
  }

  @Override
  protected void onFormatChanged(Format format) {
    handler.post(
        () -> eventListener.onAudioInputFormatChanged(format, /* decoderReuseEvaluation= */ null));
    handler.post(
        () ->
            eventListener.onAudioDecoderInitialized(
                /* decoderName= */ "fake.audio.decoder",
                /* initializedTimestampMs= */ SystemClock.elapsedRealtime(),
                /* initializationDurationMs= */ 0));
  }

  @Override
  protected boolean shouldProcessBuffer(long bufferTimeUs, long playbackPositionUs) {
    boolean shouldProcess = super.shouldProcessBuffer(bufferTimeUs, playbackPositionUs);
    if (shouldProcess && !notifiedPositionAdvancing) {
      handler.post(() -> eventListener.onAudioPositionAdvancing(System.currentTimeMillis()));
      notifiedPositionAdvancing = true;
    }
    return shouldProcess;
  }
}
