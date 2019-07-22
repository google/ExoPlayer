/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Reads from a {@link SampleQueue} and attaches {@link DrmSession} references to the {@link Format
 * Formats} of encrypted regions.
 */
public final class DecryptableSampleQueueReader {

  private final SampleQueue upstream;
  private final DrmSessionManager<?> sessionManager;
  private final FormatHolder formatHolder;
  private final boolean playClearSamplesWithoutKeys;
  private @MonotonicNonNull Format currentFormat;
  @Nullable private DrmSession<?> currentSession;

  /**
   * Creates a sample queue reader.
   *
   * @param upstream The {@link SampleQueue} from which the created reader will read samples.
   * @param sessionManager The {@link DrmSessionManager} that will provide {@link DrmSession
   *     DrmSessions} for the encrypted regions.
   */
  public DecryptableSampleQueueReader(SampleQueue upstream, DrmSessionManager<?> sessionManager) {
    this.upstream = upstream;
    this.sessionManager = sessionManager;
    formatHolder = new FormatHolder();
    playClearSamplesWithoutKeys =
        (sessionManager.getFlags() & DrmSessionManager.FLAG_PLAY_CLEAR_SAMPLES_WITHOUT_KEYS) != 0;
  }

  /** Releases any resources acquired by this reader. */
  public void release() {
    if (currentSession != null) {
      currentSession.releaseReference();
      currentSession = null;
    }
  }

  /**
   * Throws an error that's preventing data from being read. Does nothing if no such error exists.
   *
   * @throws IOException The underlying error.
   */
  public void maybeThrowError() throws IOException {
    // TODO: Avoid throwing if the DRM error is not preventing a read operation.
    if (currentSession != null && currentSession.getState() == DrmSession.STATE_ERROR) {
      throw Assertions.checkNotNull(currentSession.getError());
    }
  }

  /**
   * Reads from the upstream {@link SampleQueue}, populating {@link FormatHolder#drmSession} if the
   * current {@link Format#drmInitData} is not null.
   *
   * <p>This reader guarantees that any read results are usable by clients. An encrypted sample will
   * only be returned along with a {@link FormatHolder#drmSession} that has available keys.
   *
   * @param outputFormatHolder A {@link FormatHolder} to populate in the case of reading a format.
   *     {@link FormatHolder#drmSession} will be populated if the read format's {@link
   *     Format#drmInitData} is not null.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the {@link
   *     C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer. If a {@link
   *     DecoderInputBuffer#isFlagsOnly() flags-only} buffer is passed, only the buffer flags may be
   *     populated by this method and the read position of the queue will not change.
   * @param formatRequired Whether the caller requires that the format of the stream be read even if
   *     it's not changing. A sample will never be read if set to true, however it is still possible
   *     for the end of stream or nothing to be read.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @param decodeOnlyUntilUs If a buffer is read, the {@link C#BUFFER_FLAG_DECODE_ONLY} flag will
   *     be set if the buffer's timestamp is less than this value.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   */
  @SuppressWarnings("ReferenceEquality")
  public int read(
      FormatHolder outputFormatHolder,
      DecoderInputBuffer buffer,
      boolean formatRequired,
      boolean loadingFinished,
      long decodeOnlyUntilUs) {

    boolean readFlagFormatRequired = false;
    boolean readFlagAllowOnlyClearBuffers = false;
    boolean onlyPropagateFormatChanges = false;

    if (currentFormat == null || formatRequired) {
      readFlagFormatRequired = true;
    } else if (sessionManager != DrmSessionManager.DUMMY
        && currentFormat.drmInitData != null
        && Assertions.checkNotNull(currentSession).getState()
            != DrmSession.STATE_OPENED_WITH_KEYS) {
      if (playClearSamplesWithoutKeys) {
        // Content is encrypted and keys are not available, but clear samples are ok for reading.
        readFlagAllowOnlyClearBuffers = true;
      } else {
        // We must not read any samples, but we may still read a format or the end of stream.
        // However, because the formatRequired argument is false, we should not propagate a read
        // format unless it is different than the current format.
        onlyPropagateFormatChanges = true;
        readFlagFormatRequired = true;
      }
    }

    int result =
        upstream.read(
            formatHolder,
            buffer,
            readFlagFormatRequired,
            readFlagAllowOnlyClearBuffers,
            loadingFinished,
            decodeOnlyUntilUs);
    if (result == C.RESULT_FORMAT_READ) {
      if (onlyPropagateFormatChanges && currentFormat == formatHolder.format) {
        return C.RESULT_NOTHING_READ;
      }
      onFormat(Assertions.checkNotNull(formatHolder.format), outputFormatHolder);
    }
    return result;
  }

  /**
   * Updates the current format and manages any necessary DRM resources.
   *
   * @param format The format read from upstream.
   * @param outputFormatHolder The output {@link FormatHolder}.
   */
  private void onFormat(Format format, FormatHolder outputFormatHolder) {
    outputFormatHolder.format = format;
    DrmInitData oldDrmInitData = currentFormat != null ? currentFormat.drmInitData : null;
    currentFormat = format;
    if (sessionManager == DrmSessionManager.DUMMY) {
      // Avoid attempting to acquire a session using the dummy DRM session manager. It's likely that
      // the media source creation has not yet been migrated and the renderer can acquire the
      // session for the read DRM init data.
      // TODO: Remove once renderers are migrated [Internal ref: b/122519809].
      return;
    }
    outputFormatHolder.includesDrmSession = true;
    outputFormatHolder.drmSession = currentSession;
    if (Util.areEqual(oldDrmInitData, format.drmInitData)) {
      // Nothing to do.
      return;
    }
    // Ensure we acquire the new session before releasing the previous one in case the same session
    // can be used for both DrmInitData.
    DrmSession<?> previousSession = currentSession;
    DrmInitData drmInitData = currentFormat.drmInitData;
    if (drmInitData != null) {
      currentSession =
          sessionManager.acquireSession(Assertions.checkNotNull(Looper.myLooper()), drmInitData);
    } else {
      currentSession = null;
    }
    outputFormatHolder.drmSession = currentSession;

    if (previousSession != null) {
      previousSession.releaseReference();
    }
  }

  /** Returns whether there is data available for reading. */
  public boolean isReady(boolean loadingFinished) {
    @SampleQueue.PeekResult int nextInQueue = upstream.peekNext();
    if (nextInQueue == SampleQueue.PEEK_RESULT_NOTHING) {
      return loadingFinished;
    } else if (nextInQueue == SampleQueue.PEEK_RESULT_FORMAT) {
      return true;
    } else if (nextInQueue == SampleQueue.PEEK_RESULT_BUFFER_CLEAR) {
      return currentSession == null || playClearSamplesWithoutKeys;
    } else if (nextInQueue == SampleQueue.PEEK_RESULT_BUFFER_ENCRYPTED) {
      return sessionManager == DrmSessionManager.DUMMY
          || Assertions.checkNotNull(currentSession).getState()
              == DrmSession.STATE_OPENED_WITH_KEYS;
    } else {
      throw new IllegalStateException();
    }
  }
}
