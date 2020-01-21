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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;

/**
 * Fake {@link SampleStream} that outputs a given {@link Format}, any amount of {@link
 * FakeSampleStreamItem items}, then end of stream.
 */
public final class FakeSampleStream implements SampleStream {

  /** Item to customize a return value of {@link FakeSampleStream#readData}. */
  public static final class FakeSampleStreamItem {
    @Nullable Format format;
    @Nullable byte[] sampleData;
    int flags;

    /**
     * Item that, when {@link #readData(FormatHolder, DecoderInputBuffer, boolean)} is called, will
     * return {@link C#RESULT_FORMAT_READ} with the new format.
     *
     * @param format The format to be returned.
     */
    public FakeSampleStreamItem(Format format) {
      this.format = format;
    }

    /**
     * Item that, when {@link #readData(FormatHolder, DecoderInputBuffer, boolean)} is called, will
     * return {@link C#RESULT_BUFFER_READ} with the sample data.
     *
     * @param sampleData The sample data to be read.
     */
    public FakeSampleStreamItem(byte[] sampleData) {
      this.sampleData = sampleData.clone();
    }

    /**
     * Item that, when {@link #readData(FormatHolder, DecoderInputBuffer, boolean)} is called, will
     * return {@link C#RESULT_BUFFER_READ} with the sample data.
     *
     * @param sampleData The sample data to be read.
     * @param flags The buffer flags to be set.
     */
    public FakeSampleStreamItem(byte[] sampleData, int flags) {
      this.sampleData = sampleData.clone();
      this.flags = flags;
    }
  }

  private final ArrayDeque<FakeSampleStreamItem> fakeSampleStreamItems;
  private final int timeUsIncrement;

  @Nullable private final EventDispatcher eventDispatcher;

  private Format format;
  private int timeUs;
  private boolean readFormat;

  /**
   * Creates fake sample stream which outputs the given {@link Format}, optionally one sample with
   * zero bytes, then end of stream.
   *
   * @param format The {@link Format} to output.
   * @param eventDispatcher An {@link EventDispatcher} to notify of read events.
   * @param shouldOutputSample Whether the sample stream should output a sample.
   */
  public FakeSampleStream(
      Format format, @Nullable EventDispatcher eventDispatcher, boolean shouldOutputSample) {
    this(
        format,
        eventDispatcher,
        shouldOutputSample
            ? Collections.singletonList(new FakeSampleStreamItem(new byte[] {0}))
            : Collections.emptyList(),
        /* timeUsIncrement= */ 0);
  }

  /**
   * Creates a fake sample stream which outputs the given {@link Format}, any amount of {@link
   * FakeSampleStreamItem items}, then end of stream.
   *
   * @param format The {@link Format} to output.
   * @param eventDispatcher An {@link EventDispatcher} to notify of read events.
   * @param fakeSampleStreamItems The list of {@link FakeSampleStreamItem items} to customize the
   *     return values of {@link #readData(FormatHolder, DecoderInputBuffer, boolean)}.
   * @param timeUsIncrement The time each sample should increase by, in microseconds.
   */
  public FakeSampleStream(
      Format format,
      @Nullable EventDispatcher eventDispatcher,
      List<FakeSampleStreamItem> fakeSampleStreamItems,
      int timeUsIncrement) {
    this.format = format;
    this.eventDispatcher = eventDispatcher;
    this.fakeSampleStreamItems = new ArrayDeque<>(fakeSampleStreamItems);
    this.timeUsIncrement = timeUsIncrement;
  }

  /**
   * Resets the samples provided by this sample stream to the provided list.
   *
   * @param fakeSampleStreamItems The list of {@link FakeSampleStreamItem items} to provide.
   * @param timeUs The time at which samples will start being output, in microseconds.
   */
  public void resetSampleStreamItems(List<FakeSampleStreamItem> fakeSampleStreamItems, int timeUs) {
    this.fakeSampleStreamItems.clear();
    this.fakeSampleStreamItems.addAll(fakeSampleStreamItems);
    this.timeUs = timeUs;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
    if (!readFormat || formatRequired) {
      readFormat = true;
      formatHolder.format = format;
      notifyEventDispatcher(formatHolder);
      return C.RESULT_FORMAT_READ;
    }
    if (!fakeSampleStreamItems.isEmpty()) {
      FakeSampleStreamItem fakeSampleStreamItem = fakeSampleStreamItems.remove();
      if (fakeSampleStreamItem.format != null) {
        format = fakeSampleStreamItem.format;
        formatHolder.format = format;
        notifyEventDispatcher(formatHolder);
        return C.RESULT_FORMAT_READ;
      }
      if (fakeSampleStreamItem.sampleData != null) {
        byte[] sampleData = fakeSampleStreamItem.sampleData;
        buffer.timeUs = timeUs;
        timeUs += timeUsIncrement;
        buffer.ensureSpaceForWrite(sampleData.length);
        buffer.data.put(sampleData);
        if (fakeSampleStreamItem.flags != 0) {
          buffer.setFlags(fakeSampleStreamItem.flags);
        }
        return C.RESULT_BUFFER_READ;
      }
    }
    buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    return C.RESULT_BUFFER_READ;
  }

  @Override
  public void maybeThrowError() throws IOException {
    // Do nothing.
  }

  @Override
  public int skipData(long positionUs) {
    return 0;
  }

  private void notifyEventDispatcher(FormatHolder formatHolder) {
    if (eventDispatcher != null) {
      eventDispatcher.downstreamFormatChanged(
          C.TRACK_TYPE_UNKNOWN,
          formatHolder.format,
          C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaTimeUs= */ timeUs);
    }
  }
}
