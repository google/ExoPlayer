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


import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.BufferFlags;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Fake {@link SampleStream} that outputs a given {@link Format}, any amount of {@link
 * FakeSampleStreamItem items}, then end of stream.
 */
public class FakeSampleStream implements SampleStream {

  private static class SampleInfo {
    private final byte[] data;
    @C.BufferFlags private final int flags;
    private final long timeUs;

    private SampleInfo(byte[] data, @C.BufferFlags int flags, long timeUs) {
      this.data = Arrays.copyOf(data, data.length);
      this.flags = flags;
      this.timeUs = timeUs;
    }
  }

  /** Item to customize a return value of {@link FakeSampleStream#readData}. */
  public static final class FakeSampleStreamItem {

    /**
     * Item that designates the end of stream has been reached.
     *
     * <p>When this item is read, readData will repeatedly return end of stream.
     */
    public static final FakeSampleStreamItem END_OF_STREAM_ITEM =
        sample(
            /* timeUs= */ Long.MAX_VALUE,
            C.BUFFER_FLAG_END_OF_STREAM,
            /* sampleData= */ new byte[] {});

    /** Creates an item representing the provided format. */
    public static FakeSampleStreamItem format(Format format) {
      return new FakeSampleStreamItem(format, /* sampleInfo= */ null);
    }

    /**
     * Creates an item representing a sample with the provided timestamp.
     *
     * <p>The sample will contain a single byte of data.
     *
     * @param timeUs The timestamp of the sample.
     */
    public static FakeSampleStreamItem oneByteSample(long timeUs) {
      return oneByteSample(timeUs, /* flags= */ 0);
    }

    /**
     * Creates an item representing a sample with the provided timestamp and flags.
     *
     * <p>The sample will contain a single byte of data.
     *
     * @param timeUs The timestamp of the sample.
     * @param flags The buffer flags that will be set when reading this sample through {@link
     *     FakeSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)}.
     */
    public static FakeSampleStreamItem oneByteSample(long timeUs, @BufferFlags int flags) {
      return sample(timeUs, flags, new byte[] {0});
    }

    /**
     * Creates an item representing a sample with the provided timestamp, flags and data.
     *
     * @param timeUs The timestamp of the sample.
     * @param flags The buffer flags that will be set when reading this sample through {@link
     *     FakeSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)}.
     * @param sampleData The sample data.
     */
    public static FakeSampleStreamItem sample(
        long timeUs, @BufferFlags int flags, byte[] sampleData) {
      return new FakeSampleStreamItem(
          /* format= */ null, new SampleInfo(sampleData.clone(), flags, timeUs));
    }

    @Nullable private final Format format;
    @Nullable private final SampleInfo sampleInfo;

    /**
     * Creates an instance. Exactly one of {@code format} or {@code sampleInfo} must be non-null.
     */
    private FakeSampleStreamItem(@Nullable Format format, @Nullable SampleInfo sampleInfo) {
      Assertions.checkArgument((format == null) != (sampleInfo == null));
      this.format = format;
      this.sampleInfo = sampleInfo;
    }
  }

  @Nullable private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final Format initialFormat;
  private final List<FakeSampleStreamItem> fakeSampleStreamItems;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;

  private int sampleItemIndex;
  private @MonotonicNonNull Format downstreamFormat;
  private boolean readEOSBuffer;
  @Nullable private DrmSession currentDrmSession;

  /**
   * Creates a fake sample stream which outputs the given {@link Format} followed by the provided
   * {@link FakeSampleStreamItem items}.
   *
   * @param mediaSourceEventDispatcher A {@link MediaSourceEventListener.EventDispatcher} to notify
   *     of media events.
   * @param drmSessionManager A {@link DrmSessionManager} for DRM interactions.
   * @param drmEventDispatcher A {@link DrmSessionEventListener.EventDispatcher} to notify of DRM
   *     events.
   * @param initialFormat The first {@link Format} to output.
   * @param fakeSampleStreamItems The {@link FakeSampleStreamItem items} to customize the return
   *     values of {@link #readData(FormatHolder, DecoderInputBuffer, boolean)}. This is assumed to
   *     be in ascending order of sampleTime. Note that once an EOS buffer has been read, that will
   *     return every time readData is called. This should usually end with {@link
   *     FakeSampleStreamItem#END_OF_STREAM_ITEM}.
   */
  public FakeSampleStream(
      @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      Format initialFormat,
      List<FakeSampleStreamItem> fakeSampleStreamItems) {
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.initialFormat = initialFormat;
    this.fakeSampleStreamItems = new ArrayList<>(fakeSampleStreamItems);
  }

  /**
   * Seeks inside this sample stream.
   *
   * <p>Seeks to just before the first sample with {@code sampleTime >= timeUs}, or to the end of
   * the stream otherwise.
   */
  public void seekTo(long timeUs) {
    Format applicableFormat = initialFormat;
    for (int i = 0; i < fakeSampleStreamItems.size(); i++) {
      @Nullable SampleInfo sampleInfo = fakeSampleStreamItems.get(i).sampleInfo;
      if (sampleInfo == null) {
        applicableFormat = Assertions.checkNotNull(fakeSampleStreamItems.get(i).format);
        continue;
      }
      if (sampleInfo.timeUs >= timeUs) {
        sampleItemIndex = i;
        readEOSBuffer = false;
        if (downstreamFormat != null && !applicableFormat.equals(downstreamFormat)) {
          notifyEventDispatcher(applicableFormat);
        }
        return;
      }
    }
    sampleItemIndex = fakeSampleStreamItems.size();
    @Nullable
    FakeSampleStreamItem lastItem =
        Iterables.getLast(fakeSampleStreamItems, /* defaultValue= */ null);
    readEOSBuffer =
        lastItem != null
            && lastItem.sampleInfo != null
            && ((lastItem.sampleInfo.flags & C.BUFFER_FLAG_END_OF_STREAM) != 0);
  }

  /**
   * Adds an item to the end of the queue of {@link FakeSampleStreamItem items}.
   *
   * @param item The item to add.
   */
  public void addFakeSampleStreamItem(FakeSampleStreamItem item) {
    this.fakeSampleStreamItems.add(item);
  }

  @Override
  public boolean isReady() {
    if (sampleItemIndex == fakeSampleStreamItems.size()) {
      return readEOSBuffer || downstreamFormat == null;
    }
    if (fakeSampleStreamItems.get(sampleItemIndex).format != null) {
      // A format can be read.
      return true;
    }
    return mayReadSample();
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
    if (downstreamFormat == null || formatRequired) {
      onFormatResult(downstreamFormat == null ? initialFormat : downstreamFormat, formatHolder);
      return C.RESULT_FORMAT_READ;
    }
    // Once an EOS buffer has been read, send EOS every time.
    if (readEOSBuffer) {
      buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
    }

    if (sampleItemIndex < fakeSampleStreamItems.size()) {
      FakeSampleStreamItem fakeSampleStreamItem = fakeSampleStreamItems.get(sampleItemIndex);
      sampleItemIndex++;
      if (fakeSampleStreamItem.format != null) {
        onFormatResult(fakeSampleStreamItem.format, formatHolder);
        return C.RESULT_FORMAT_READ;
      } else {
        SampleInfo sampleInfo = Assertions.checkNotNull(fakeSampleStreamItem.sampleInfo);
        if (sampleInfo.flags != 0) {
          buffer.setFlags(sampleInfo.flags);
          if (buffer.isEndOfStream()) {
            readEOSBuffer = true;
            return C.RESULT_BUFFER_READ;
          }
        }
        if (!mayReadSample()) {
          sampleItemIndex--;
          return C.RESULT_NOTHING_READ;
        }
        buffer.timeUs = sampleInfo.timeUs;
        buffer.ensureSpaceForWrite(sampleInfo.data.length);
        buffer.data.put(sampleInfo.data);
        return C.RESULT_BUFFER_READ;
      }
    }
    return C.RESULT_NOTHING_READ;
  }

  private void onFormatResult(Format newFormat, FormatHolder outputFormatHolder) {
    outputFormatHolder.format = newFormat;
    @Nullable
    DrmInitData oldDrmInitData = downstreamFormat == null ? null : downstreamFormat.drmInitData;
    boolean isFirstFormat = downstreamFormat == null;
    downstreamFormat = newFormat;
    @Nullable DrmInitData newDrmInitData = newFormat.drmInitData;
    outputFormatHolder.drmSession = currentDrmSession;
    notifyEventDispatcher(newFormat);
    if (!isFirstFormat && Util.areEqual(oldDrmInitData, newDrmInitData)) {
      // Nothing to do.
      return;
    }
    // Ensure we acquire the new session before releasing the previous one in case the same session
    // is being used for both DrmInitData.
    @Nullable DrmSession previousSession = currentDrmSession;
    Looper playbackLooper = Assertions.checkNotNull(Looper.myLooper());
    currentDrmSession =
        drmSessionManager.acquireSession(playbackLooper, drmEventDispatcher, newFormat);
    outputFormatHolder.drmSession = currentDrmSession;

    if (previousSession != null) {
      previousSession.release(drmEventDispatcher);
    }
  }

  private boolean mayReadSample() {
    @Nullable DrmSession drmSession = this.currentDrmSession;
    @Nullable
    FakeSampleStreamItem nextSample =
        Iterables.get(fakeSampleStreamItems, sampleItemIndex, /* defaultValue= */ null);
    boolean nextSampleIsClear =
        nextSample != null
            && nextSample.sampleInfo != null
            && (nextSample.sampleInfo.flags & C.BUFFER_FLAG_ENCRYPTED) == 0;
    return drmSession == null
        || drmSession.getState() == DrmSession.STATE_OPENED_WITH_KEYS
        || (nextSampleIsClear && drmSession.playClearSamplesWithoutKeys());
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (currentDrmSession != null && currentDrmSession.getState() == DrmSession.STATE_ERROR) {
      throw Assertions.checkNotNull(currentDrmSession.getError());
    }
  }

  @Override
  public int skipData(long positionUs) {
    // TODO: Implement this.
    return 0;
  }

  /** Release this SampleStream and all underlying resources. */
  public void release() {
    if (currentDrmSession != null) {
      currentDrmSession.release(drmEventDispatcher);
      currentDrmSession = null;
    }
  }

  private void notifyEventDispatcher(Format format) {
    if (mediaSourceEventDispatcher != null) {
      @Nullable SampleInfo sampleInfo = null;
      for (int i = sampleItemIndex; i < fakeSampleStreamItems.size(); i++) {
        sampleInfo = fakeSampleStreamItems.get(i).sampleInfo;
        if (sampleInfo != null) {
          break;
        }
      }
      long nextSampleTimeUs = sampleInfo != null ? sampleInfo.timeUs : C.TIME_END_OF_SOURCE;
      mediaSourceEventDispatcher.downstreamFormatChanged(
          C.TRACK_TYPE_UNKNOWN,
          format,
          C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaTimeUs= */ nextSampleTimeUs);
    }
  }
}
