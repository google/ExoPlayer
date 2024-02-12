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
package androidx.media3.test.utils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fake {@link Renderer} that supports any format with the matching track type.
 *
 * <p>The renderer verifies that all the formats it reads have the provided track type.
 */
@UnstableApi
public class FakeRenderer extends BaseRenderer {

  private static final String TAG = "FakeRenderer";

  /**
   * The amount of time ahead of the current playback position that the renderer reads from the
   * source. A real renderer will typically read ahead by a small amount due to pipelining through
   * decoders and the media output path.
   */
  private static final long SOURCE_READAHEAD_US = 250_000;

  private final DecoderInputBuffer buffer;

  @Nullable private DrmSession currentDrmSession;

  private long playbackPositionUs;
  private long lastSamplePositionUs;
  private boolean hasPendingBuffer;
  private List<Format> formatsRead;

  public boolean isInitialized;
  public boolean isEnded;
  public boolean isReleased;
  public int positionResetCount;
  public int sampleBufferReadCount;
  public int enabledCount;
  public int resetCount;

  public FakeRenderer(@C.TrackType int trackType) {
    super(trackType);
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    lastSamplePositionUs = Long.MIN_VALUE;
    formatsRead = new ArrayList<>();
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    playbackPositionUs = positionUs;
    lastSamplePositionUs = Long.MIN_VALUE;
    hasPendingBuffer = false;
    positionResetCount++;
    isEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (isEnded) {
      return;
    }
    playbackPositionUs = positionUs;
    while (true) {
      if (!hasPendingBuffer) {
        FormatHolder formatHolder = getFormatHolder();
        buffer.clear();
        @ReadDataResult int result = readSource(formatHolder, buffer, /* readFlags= */ 0);

        if (result == C.RESULT_FORMAT_READ) {
          DrmSession.replaceSession(currentDrmSession, formatHolder.drmSession);
          currentDrmSession = formatHolder.drmSession;
          Format format = Assertions.checkNotNull(formatHolder.format);
          if (MimeTypes.getTrackType(format.sampleMimeType) != getTrackType()) {
            throw ExoPlaybackException.createForRenderer(
                new IllegalStateException(
                    Util.formatInvariant(
                        "Format track type (%s) doesn't match renderer track type (%s).",
                        MimeTypes.getTrackType(format.sampleMimeType), getTrackType())),
                getName(),
                getIndex(),
                format,
                C.FORMAT_UNSUPPORTED_TYPE,
                /* isRecoverable= */ false,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
          }
          formatsRead.add(format);
          onFormatChanged(format);
        } else if (result == C.RESULT_BUFFER_READ) {
          if (buffer.isEndOfStream()) {
            isEnded = true;
            return;
          }
          hasPendingBuffer = true;
        } else {
          Assertions.checkState(result == C.RESULT_NOTHING_READ);
          return;
        }
      }
      if (hasPendingBuffer) {
        if (!shouldProcessBuffer(buffer.timeUs, positionUs)) {
          return;
        }
        lastSamplePositionUs = buffer.timeUs;
        sampleBufferReadCount++;
        hasPendingBuffer = false;
      }
    }
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    enabledCount++;
  }

  @Override
  protected void onReset() {
    resetCount++;
  }

  @Override
  public boolean isReady() {
    return lastSamplePositionUs >= playbackPositionUs || hasPendingBuffer || isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Override
  public @Capabilities int supportsFormat(Format format) throws ExoPlaybackException {
    int trackType = MimeTypes.getTrackType(format.sampleMimeType);
    return trackType != C.TRACK_TYPE_UNKNOWN && trackType == getTrackType()
        ? RendererCapabilities.create(C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED)
        : RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  protected void onDisabled() {
    if (currentDrmSession != null) {
      currentDrmSession.release(/* eventDispatcher= */ null);
      currentDrmSession = null;
    }
  }

  /** Called when the renderer reads a new format. */
  protected void onFormatChanged(Format format) {}

  /** Returns the list of formats read by the renderer. */
  public List<Format> getFormatsRead() {
    return Collections.unmodifiableList(formatsRead);
  }

  /**
   * Called before the renderer processes a buffer.
   *
   * @param bufferTimeUs The buffer timestamp, in microseconds.
   * @param playbackPositionUs The playback position, in microseconds
   * @return Whether the buffer should be processed.
   */
  protected boolean shouldProcessBuffer(long bufferTimeUs, long playbackPositionUs) {
    return bufferTimeUs < playbackPositionUs + SOURCE_READAHEAD_US;
  }

  @Override
  protected void onInit() {
    isInitialized = true;
  }

  @Override
  protected void onRelease() {
    isReleased = true;
  }
}
