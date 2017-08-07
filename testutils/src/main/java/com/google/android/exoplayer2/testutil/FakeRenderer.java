/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.Assert;

/**
 * Fake {@link Renderer} that supports any format with the matching MIME type. The renderer
 * verifies that it reads one of the given {@link Format}s.
 */
public class FakeRenderer extends BaseRenderer {

  private final List<Format> expectedFormats;
  private final DecoderInputBuffer buffer;

  public int positionResetCount;
  public int formatReadCount;
  public int bufferReadCount;
  public boolean isEnded;
  public boolean isReady;

  public FakeRenderer(Format... expectedFormats) {
    super(expectedFormats.length == 0 ? C.TRACK_TYPE_UNKNOWN
        : MimeTypes.getTrackType(expectedFormats[0].sampleMimeType));
    this.expectedFormats = Collections.unmodifiableList(Arrays.asList(expectedFormats));
    this.buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    positionResetCount++;
    isEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!isEnded) {
      // Verify the format matches the expected format.
      FormatHolder formatHolder = new FormatHolder();
      int result = readSource(formatHolder, buffer, false);
      if (result == C.RESULT_FORMAT_READ) {
        formatReadCount++;
        Assert.assertTrue(expectedFormats.contains(formatHolder.format));
      } else if (result == C.RESULT_BUFFER_READ) {
        bufferReadCount++;
        if (buffer.isEndOfStream()) {
          isEnded = true;
        }
      }
    }
    isReady = buffer.timeUs >= positionUs;
  }

  @Override
  public boolean isReady() {
    return isReady || isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    return getTrackType() == MimeTypes.getTrackType(format.sampleMimeType)
        ? (FORMAT_HANDLED | ADAPTIVE_SEAMLESS) : FORMAT_UNSUPPORTED_TYPE;
  }

}
