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

import android.os.Handler;
import android.os.SystemClock;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;

/** A {@link FakeRenderer} that supports {@link C#TRACK_TYPE_AUDIO}. */
public class FakeAudioRenderer extends FakeRenderer {

  private final AudioRendererEventListener.EventDispatcher eventDispatcher;
  private final DecoderCounters decoderCounters;
  private boolean notifiedAudioSessionId;

  public FakeAudioRenderer(Handler handler, AudioRendererEventListener eventListener) {
    super(C.TRACK_TYPE_AUDIO);
    eventDispatcher = new AudioRendererEventListener.EventDispatcher(handler, eventListener);
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    eventDispatcher.enabled(decoderCounters);
    notifiedAudioSessionId = false;
  }

  @Override
  protected void onDisabled() {
    super.onDisabled();
    eventDispatcher.disabled(decoderCounters);
  }

  @Override
  protected void onFormatChanged(Format format) {
    eventDispatcher.inputFormatChanged(format);
    eventDispatcher.decoderInitialized(
        /* decoderName= */ "fake.audio.decoder",
        /* initializedTimestampMs= */ SystemClock.elapsedRealtime(),
        /* initializationDurationMs= */ 0);
  }

  @Override
  protected boolean shouldProcessBuffer(long bufferTimeUs, long playbackPositionUs) {
    boolean shouldProcess = super.shouldProcessBuffer(bufferTimeUs, playbackPositionUs);
    if (shouldProcess && !notifiedAudioSessionId) {
      eventDispatcher.audioSessionId(/* audioSessionId= */ 1);
      notifiedAudioSessionId = true;
    }
    return shouldProcess;
  }
}
