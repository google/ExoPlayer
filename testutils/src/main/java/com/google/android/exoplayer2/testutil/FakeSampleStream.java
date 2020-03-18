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
import java.util.Arrays;

/**
 * Fake {@link SampleStream} that outputs a given {@link Format}, any amount of {@link
 * FakeSampleStreamItem items}, then end of stream.
 */
public class FakeSampleStream implements SampleStream {

  /** Item to customize a return value of {@link FakeSampleStream#readData}. */
  public static final class FakeSampleStreamItem {
    @Nullable Format format;
    @Nullable byte[] sampleData;
    int flags;

    /**
     * Item that designates the end of stream has been reached.
     *
     * <p>When this item is read, readData will repeatedly return end of stream.
     */
    public static final FakeSampleStreamItem END_OF_STREAM_ITEM =
        new FakeSampleStreamItem(new byte[] {}, C.BUFFER_FLAG_END_OF_STREAM);

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

  /** Constant array for use when a single sample is to be output, followed by the end of stream. */
  public static final FakeSampleStreamItem[] SINGLE_SAMPLE_THEN_END_OF_STREAM =
      new FakeSampleStreamItem[] {
        new FakeSampleStreamItem(new byte[] {0}), FakeSampleStreamItem.END_OF_STREAM_ITEM
      };

  private final ArrayDeque<FakeSampleStreamItem> fakeSampleStreamItems;
  private final int timeUsIncrement;

  @Nullable private final EventDispatcher eventDispatcher;

  private Format format;
  private long timeUs;
  private boolean readFormat;
  private boolean readEOSBuffer;

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
        /* firstSampleTimeUs= */ 0,
        /* timeUsIncrement= */ 0,
        shouldOutputSample
            ? SINGLE_SAMPLE_THEN_END_OF_STREAM
            : new FakeSampleStreamItem[] {FakeSampleStreamItem.END_OF_STREAM_ITEM});
  }

  /**
   * Creates a fake sample stream which outputs the given {@link Format}, any amount of {@link
   * FakeSampleStreamItem items}, then end of stream.
   *
   * @param format The {@link Format} to output.
   * @param eventDispatcher An {@link EventDispatcher} to notify of read events.
   * @param firstSampleTimeUs The time at which samples will start being output, in microseconds.
   * @param timeUsIncrement The time each sample should increase by, in microseconds.
   * @param fakeSampleStreamItems The {@link FakeSampleStreamItem items} to customize the return
   *     values of {@link #readData(FormatHolder, DecoderInputBuffer, boolean)}. Note that once an
   *     EOS buffer has been read, that will return every time readData is called.
   */
  public FakeSampleStream(
      Format format,
      @Nullable EventDispatcher eventDispatcher,
      long firstSampleTimeUs,
      int timeUsIncrement,
      FakeSampleStreamItem... fakeSampleStreamItems) {
    this.format = format;
    this.eventDispatcher = eventDispatcher;
    this.fakeSampleStreamItems = new ArrayDeque<>(Arrays.asList(fakeSampleStreamItems));
    this.timeUs = firstSampleTimeUs;
    this.timeUsIncrement = timeUsIncrement;
  }

  /**
   * Clears and assigns new samples provided by this sample stream.
   *
   * @param timeUs The time at which samples will start being output, in microseconds.
   * @param fakeSampleStreamItems The {@link FakeSampleStreamItem items} to provide.
   */
  public void resetSampleStreamItems(long timeUs, FakeSampleStreamItem... fakeSampleStreamItems) {
    this.fakeSampleStreamItems.clear();
    this.fakeSampleStreamItems.addAll(Arrays.asList(fakeSampleStreamItems));
    this.timeUs = timeUs;
    readEOSBuffer = false;
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
    return !readFormat || readEOSBuffer || !fakeSampleStreamItems.isEmpty();
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
    // Once an EOS buffer has been read, send EOS every time.
    if (readEOSBuffer) {
      buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
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
          readEOSBuffer = buffer.isEndOfStream();
        }
        return C.RESULT_BUFFER_READ;
      }
    }
    return C.RESULT_NOTHING_READ;
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
