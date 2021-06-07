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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fake {@link SampleStream} that outputs a given {@link Format} and any amount of {@link
 * FakeSampleStreamItem items}.
 */
public class FakeSampleStream implements SampleStream {

  /** Item to customize a return value of {@link SampleStream#readData}. */
  public static final class FakeSampleStreamItem {

    /** Item that designates the end of stream has been reached. */
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
     * @param flags The sample {@link C.BufferFlags}.
     */
    public static FakeSampleStreamItem oneByteSample(long timeUs, @C.BufferFlags int flags) {
      return sample(timeUs, flags, new byte[] {0});
    }

    /**
     * Creates an item representing a sample with the provided timestamp, flags and data.
     *
     * @param timeUs The timestamp of the sample.
     * @param flags The sample {@link C.BufferFlags}.
     * @param sampleData The sample data.
     */
    public static FakeSampleStreamItem sample(
        long timeUs, @C.BufferFlags int flags, byte[] sampleData) {
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

  private final SampleQueue sampleQueue;
  @Nullable private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final List<FakeSampleStreamItem> sampleStreamItems;

  private int sampleStreamItemsWritePosition;
  private boolean loadingFinished;
  @Nullable private Format downstreamFormat;
  @Nullable private Format notifiedDownstreamFormat;

  /**
   * Creates a fake sample stream which outputs the given {@link Format} followed by the provided
   * {@link FakeSampleStreamItem items}.
   *
   * @param allocator An {@link Allocator}.
   * @param mediaSourceEventDispatcher A {@link MediaSourceEventListener.EventDispatcher} to notify
   *     of media events.
   * @param drmSessionManager A {@link DrmSessionManager} for DRM interactions.
   * @param drmEventDispatcher A {@link DrmSessionEventListener.EventDispatcher} to notify of DRM
   *     events.
   * @param initialFormat The first {@link Format} to output.
   * @param fakeSampleStreamItems The {@link FakeSampleStreamItem items} to output.
   */
  public FakeSampleStream(
      Allocator allocator,
      @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      Format initialFormat,
      List<FakeSampleStreamItem> fakeSampleStreamItems) {
    this.sampleQueue =
        SampleQueue.createWithDrm(
            allocator,
            /* playbackLooper= */ checkNotNull(Looper.myLooper()),
            drmSessionManager,
            drmEventDispatcher);
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.sampleStreamItems = new ArrayList<>();
    sampleStreamItems.add(FakeSampleStreamItem.format(initialFormat));
    sampleStreamItems.addAll(fakeSampleStreamItems);
  }

  /**
   * Appends {@link FakeSampleStreamItem FakeSampleStreamItems} to the list of items that should be
   * written to the queue.
   *
   * <p>Note that this data is only written to the queue once {@link #writeData(long)} is called.
   *
   * @param items The items to append.
   */
  public void append(List<FakeSampleStreamItem> items) {
    sampleStreamItems.addAll(items);
  }

  /**
   * Writes all not yet written {@link FakeSampleStreamItem sample stream items} to the sample queue
   * starting at the given position.
   *
   * @param startPositionUs The start position, in microseconds.
   */
  public void writeData(long startPositionUs) {
    if (sampleStreamItemsWritePosition == 0) {
      sampleQueue.setStartTimeUs(startPositionUs);
    }
    boolean writtenFirstFormat = false;
    @Nullable Format pendingFirstFormat = null;
    for (int i = 0; i < sampleStreamItems.size(); i++) {
      FakeSampleStreamItem fakeSampleStreamItem = sampleStreamItems.get(i);
      @Nullable FakeSampleStream.SampleInfo sampleInfo = fakeSampleStreamItem.sampleInfo;
      if (sampleInfo == null) {
        if (writtenFirstFormat) {
          sampleQueue.format(checkNotNull(fakeSampleStreamItem.format));
        } else {
          pendingFirstFormat = checkNotNull(fakeSampleStreamItem.format);
        }
        continue;
      }
      if ((sampleInfo.flags & C.BUFFER_FLAG_END_OF_STREAM) != 0) {
        loadingFinished = true;
        break;
      }
      if (sampleInfo.timeUs >= startPositionUs && i >= sampleStreamItemsWritePosition) {
        if (!writtenFirstFormat) {
          sampleQueue.format(checkNotNull(pendingFirstFormat));
          writtenFirstFormat = true;
        }
        sampleQueue.sampleData(new ParsableByteArray(sampleInfo.data), sampleInfo.data.length);
        sampleQueue.sampleMetadata(
            sampleInfo.timeUs,
            sampleInfo.flags,
            sampleInfo.data.length,
            /* offset= */ 0,
            /* cryptoData= */ null);
      }
    }
    sampleStreamItemsWritePosition = sampleStreamItems.size();
  }

  /**
   * Seeks the stream to a new position using already available data in the queue.
   *
   * @param positionUs The new position, in microseconds.
   * @return Whether seeking inside the available data was possible.
   */
  public boolean seekToUs(long positionUs) {
    return sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ false);
  }

  /**
   * Resets the sample queue.
   *
   * <p>A new call to {@link #writeData(long)} is required to fill the queue again.
   */
  public void reset() {
    sampleQueue.reset();
    sampleStreamItemsWritePosition = 0;
    loadingFinished = false;
  }

  /** Returns whether data has been written to the sample queue until the end of stream signal. */
  public boolean isLoadingFinished() {
    return loadingFinished;
  }

  /**
   * Returns the timestamp of the largest queued sample in the queue, or {@link Long#MIN_VALUE} if
   * no samples are queued.
   */
  public long getLargestQueuedTimestampUs() {
    return sampleQueue.getLargestQueuedTimestampUs();
  }

  /**
   * Discards data from the queue.
   *
   * @param positionUs The position to discard to, in microseconds.
   * @param toKeyframe Whether to discard to keyframes only.
   */
  public void discardTo(long positionUs, boolean toKeyframe) {
    sampleQueue.discardTo(positionUs, toKeyframe, /* stopAtReadPosition= */ true);
  }

  /** Release the stream and its underlying sample queue. */
  public void release() {
    sampleQueue.release();
  }

  @Override
  public boolean isReady() {
    return sampleQueue.isReady(loadingFinished);
  }

  @Override
  public void maybeThrowError() throws IOException {
    sampleQueue.maybeThrowError();
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
    int result = sampleQueue.read(formatHolder, buffer, readFlags, loadingFinished);
    if (result == C.RESULT_FORMAT_READ) {
      downstreamFormat = checkNotNull(formatHolder.format);
    }
    if (result == C.RESULT_BUFFER_READ && (readFlags & FLAG_OMIT_SAMPLE_DATA) == 0) {
      maybeNotifyDownstreamFormat(buffer.timeUs);
    }
    return result;
  }

  @Override
  public int skipData(long positionUs) {
    int skipCount = sampleQueue.getSkipCount(positionUs, loadingFinished);
    sampleQueue.skip(skipCount);
    return skipCount;
  }

  private void maybeNotifyDownstreamFormat(long timeUs) {
    if (mediaSourceEventDispatcher != null
        && downstreamFormat != null
        && !downstreamFormat.equals(notifiedDownstreamFormat)) {
      mediaSourceEventDispatcher.downstreamFormatChanged(
          MimeTypes.getTrackType(downstreamFormat.sampleMimeType),
          downstreamFormat,
          C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          timeUs);
      notifiedDownstreamFormat = downstreamFormat;
    }
  }

  private static class SampleInfo {
    public final byte[] data;
    @C.BufferFlags public final int flags;
    public final long timeUs;

    public SampleInfo(byte[] data, @C.BufferFlags int flags, long timeUs) {
      this.data = Arrays.copyOf(data, data.length);
      this.flags = flags;
      this.timeUs = timeUs;
    }
  }
}
