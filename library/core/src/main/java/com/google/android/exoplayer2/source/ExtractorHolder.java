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
package com.google.android.exoplayer2.source;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;

/** Stores a list of extractors and a selected extractor when the format has been detected. */
/* package */ final class ExtractorHolder {

  private final Extractor[] extractors;

  @Nullable private Extractor extractor;
  @Nullable private ExtractorInput extractorInput;

  /**
   * Creates a holder that will select an extractor and initialize it using the specified output.
   *
   * @param extractors One or more extractors to choose from.
   */
  public ExtractorHolder(Extractor[] extractors) {
    this.extractors = extractors;
  }

  /**
   * Disables seeking in MP3 streams.
   *
   * <p>MP3 live streams commonly have seekable metadata, despite being unseekable.
   */
  public void disableSeekingOnMp3Streams() {
    if (extractor instanceof Mp3Extractor) {
      ((Mp3Extractor) extractor).disableSeeking();
    }
  }

  /**
   * Initializes any necessary resources for extraction.
   *
   * @param dataSource The {@link DataSource} from which data should be read.
   * @param position The initial position of the {@code dataSource} in the stream.
   * @param length The length of the stream, or {@link C#LENGTH_UNSET} if it is unknown.
   * @param output The {@link ExtractorOutput} that will be used to initialize the selected
   *     extractor.
   * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
   * @throws IOException Thrown if the input could not be read.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public void init(DataSource dataSource, long position, long length, ExtractorOutput output)
      throws IOException, InterruptedException {
    extractorInput = new DefaultExtractorInput(dataSource, position, length);
    if (extractor != null) {
      return;
    }
    if (extractors.length == 1) {
      this.extractor = extractors[0];
    } else {
      for (Extractor extractor : extractors) {
        try {
          if (extractor.sniff(extractorInput)) {
            this.extractor = extractor;
            break;
          }
        } catch (EOFException e) {
          // Do nothing.
        } finally {
          extractorInput.resetPeekPosition();
        }
      }
      if (extractor == null) {
        throw new UnrecognizedInputFormatException(
            "None of the available extractors ("
                + Util.getCommaDelimitedSimpleClassNames(extractors)
                + ") could read the stream.",
            Assertions.checkNotNull(dataSource.getUri()));
      }
    }
    extractor.init(output);
  }

  /**
   * Returns the current read position in the input stream, or {@link C#POSITION_UNSET} if no input
   * is available.
   */
  public long getCurrentInputPosition() {
    return extractorInput != null ? extractorInput.getPosition() : C.POSITION_UNSET;
  }

  /**
   * Notifies the underlying extractor that a seek has occurred.
   *
   * @param position The byte offset in the stream from which data will be provided.
   * @param seekTimeUs The seek time in microseconds.
   */
  public void seek(long position, long seekTimeUs) {
    Assertions.checkNotNull(extractor).seek(position, seekTimeUs);
  }

  /**
   * Extracts data starting at the current input stream position.
   *
   * @param positionHolder If {@link Extractor#RESULT_SEEK} is returned, this holder is updated to
   *     hold the position of the required data.
   * @return One of the {@link Extractor}{@code .RESULT_*} values.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public int read(PositionHolder positionHolder) throws IOException, InterruptedException {
    return Assertions.checkNotNull(extractor)
        .read(Assertions.checkNotNull(extractorInput), positionHolder);
  }

  /** Releases any held resources. */
  public void release() {
    if (extractor != null) {
      extractor.release();
      extractor = null;
    }
    extractorInput = null;
  }
}
