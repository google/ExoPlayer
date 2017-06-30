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
import junit.framework.Assert;

/**
 * Fake {@link Renderer} that supports any format with the matching MIME type. The renderer
 * verifies that it reads a given {@link Format}.
 */
public class FakeRenderer extends BaseRenderer {

  private final Format expectedFormat;

  public int positionResetCount;
  public int formatReadCount;
  public int bufferReadCount;
  public boolean isEnded;

  public FakeRenderer(Format expectedFormat) {
    super(expectedFormat == null ? C.TRACK_TYPE_UNKNOWN
        : MimeTypes.getTrackType(expectedFormat.sampleMimeType));
    this.expectedFormat = expectedFormat;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    positionResetCount++;
    isEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (isEnded) {
      return;
    }

    // Verify the format matches the expected format.
    FormatHolder formatHolder = new FormatHolder();
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    int result = readSource(formatHolder, buffer, false);
    if (result == C.RESULT_FORMAT_READ) {
      formatReadCount++;
      Assert.assertEquals(expectedFormat, formatHolder.format);
    } else if (result == C.RESULT_BUFFER_READ) {
      bufferReadCount++;
      if (buffer.isEndOfStream()) {
        isEnded = true;
      }
    }
  }

  @Override
  public boolean isReady() {
    return isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    return getTrackType() == MimeTypes.getTrackType(format.sampleMimeType) ? FORMAT_HANDLED
        : FORMAT_UNSUPPORTED_TYPE;
  }

}
