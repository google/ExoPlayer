/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.util.Assertions;

import java.io.EOFException;
import java.io.IOException;

/**
 * Used to seek in an Ogg stream.
 */
/* package */ final class DefaultOggSeeker implements OggSeeker {

  private static final int STATE_SEEK_TO_END = 0;
  private static final int STATE_READ_LAST_PAGE = 1;
  private static final int STATE_SEEK = 2;
  private static final int STATE_IDLE = 3;

  //@VisibleForTesting
  public static final int MATCH_RANGE = 72000;
  private static final int DEFAULT_OFFSET = 30000;

  private final OggPageHeader pageHeader = new OggPageHeader();
  private final long startPosition;
  private final long endPosition;
  private final StreamReader streamReader;

  private int state;
  private long totalGranules;
  private volatile long queriedGranule;
  private long positionBeforeSeekToEnd;
  private long targetGranule;
  private long elapsedSamples;

  public static DefaultOggSeeker createOggSeekerForTesting(long startPosition, long endPosition,
      long totalGranules) {
    Assertions.checkArgument(totalGranules > 0);
    DefaultOggSeeker oggSeeker = new DefaultOggSeeker(startPosition, endPosition, null);
    oggSeeker.totalGranules = totalGranules;
    return oggSeeker;
  }

  /**
   * Constructs an OggSeeker.
   * @param startPosition Start position of the payload.
   * @param endPosition End position of the payload.
   * @param streamReader StreamReader instance which owns this OggSeeker
   */
  public DefaultOggSeeker(long startPosition, long endPosition, StreamReader streamReader) {
    this.streamReader = streamReader;
    Assertions.checkArgument(startPosition >= 0 && endPosition > startPosition);
    this.startPosition = startPosition;
    this.endPosition = endPosition;
    this.queriedGranule = 0;
    this.state = STATE_SEEK_TO_END;
  }

  @Override
  public long read(ExtractorInput input) throws IOException, InterruptedException {
    switch (state) {
      case STATE_IDLE:
        return -1;

      case STATE_SEEK_TO_END:
        positionBeforeSeekToEnd = input.getPosition();
        state = STATE_READ_LAST_PAGE;
        // seek to the end just before the last page of stream to get the duration
        long lastPagePosition = input.getLength() - OggPageHeader.MAX_PAGE_SIZE;
        if (lastPagePosition > 0) {
          return Math.max(lastPagePosition, 0);
        }
        // fall through

      case STATE_READ_LAST_PAGE:
        totalGranules = readGranuleOfLastPage(input);
        state = STATE_IDLE;
        return positionBeforeSeekToEnd;

      case STATE_SEEK:
        long currentGranule;
        if (targetGranule == 0) {
          currentGranule = 0;
        } else {
          long position = getNextSeekPosition(targetGranule, input);
          if (position != -1) {
            return position;
          } else {
            currentGranule = skipToPageOfGranule(input, targetGranule);
          }
        }
        state = STATE_IDLE;
        return -currentGranule - 2;

      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  @Override
  public long startSeek() {
    Assertions.checkArgument(state == STATE_IDLE || state == STATE_SEEK);
    targetGranule = queriedGranule;
    state = STATE_SEEK;
    return targetGranule;
  }

  @Override
  public OggSeekMap createSeekMap() {
    return totalGranules != 0 ? new OggSeekMap() : null;
  }

  /**
   * Returns a position converging to the {@code targetGranule} to which the {@link ExtractorInput}
   * has to seek and then be passed for another call until -1 is return. If -1 is returned the
   * input is at a position which is before the start of the page before the target page and at
   * which it is sensible to just skip pages to the target granule and pre-roll instead of doing
   * another seek request.
   *
   * @param targetGranule the target granule position to seek to.
   * @param input the {@link ExtractorInput} to read from.
   * @return the position to seek the {@link ExtractorInput} to for a next call or -1 if it's close
   *    enough to skip to the target page.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  //@VisibleForTesting
  public long getNextSeekPosition(long targetGranule, ExtractorInput input)
      throws IOException, InterruptedException {
    long previousPosition = input.getPosition();
    skipToNextPage(input);
    pageHeader.populate(input, false);
    long granuleDistance = targetGranule - pageHeader.granulePosition;
    if (granuleDistance <= 0 || granuleDistance > MATCH_RANGE) {
      // estimated position too high or too low
      long offset = (pageHeader.bodySize + pageHeader.headerSize)
          * (granuleDistance <= 0 ? 2 : 1);
      long estimatedPosition = getEstimatedPosition(input.getPosition(), granuleDistance, offset);
      if (estimatedPosition != previousPosition) { // Temporary prevention for simple loops
        return estimatedPosition;
      }
    }
    // position accepted (below target granule and within MATCH_RANGE)
    input.resetPeekPosition();
    return -1;
  }

  private long getEstimatedPosition(long position, long granuleDistance, long offset) {
    position += (granuleDistance * (endPosition - startPosition) / totalGranules) - offset;
    if (position < startPosition) {
      position = startPosition;
    }
    if (position >= endPosition) {
      position = endPosition - 1;
    }
    return position;
  }

  private class OggSeekMap implements SeekMap {

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public long getPosition(long timeUs) {
      if (timeUs == 0) {
        queriedGranule = 0;
        return startPosition;
      }
      queriedGranule = streamReader.convertTimeToGranule(timeUs);
      return getEstimatedPosition(startPosition, queriedGranule, DEFAULT_OFFSET);
    }

    @Override
    public long getDurationUs() {
      return streamReader.convertGranuleToTime(totalGranules);
    }

  }

  /**
   * Skips to the next page.
   *
   * @param input The {@code ExtractorInput} to skip to the next page.
   * @throws IOException thrown if peeking/reading from the input fails.
   * @throws InterruptedException thrown if interrupted while peeking/reading from the input.
   */
  //@VisibleForTesting
  static void skipToNextPage(ExtractorInput input)
      throws IOException, InterruptedException {

    byte[] buffer = new byte[2048];
    int peekLength = buffer.length;
    long length = input.getLength();
    while (true) {
      if (length != C.LENGTH_UNBOUNDED && input.getPosition() + peekLength > length) {
        // Make sure to not peek beyond the end of the input.
        peekLength = (int) (length - input.getPosition());
        if (peekLength < 4) {
          // Not found until eof.
          throw new EOFException();
        }
      }
      input.peekFully(buffer, 0, peekLength, false);
      for (int i = 0; i < peekLength - 3; i++) {
        if (buffer[i] == 'O' && buffer[i + 1] == 'g' && buffer[i + 2] == 'g'
            && buffer[i + 3] == 'S') {
          // Match! Skip to the start of the pattern.
          input.skipFully(i);
          return;
        }
      }
      // Overlap by not skipping the entire peekLength.
      input.skipFully(peekLength - 3);
    }
  }

  /**
   * Skips to the last Ogg page in the stream and reads the header's granule field which is the
   * total number of samples per channel.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @return the total number of samples of this input.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  //@VisibleForTesting
  long readGranuleOfLastPage(ExtractorInput input)
      throws IOException, InterruptedException {
    Assertions.checkArgument(input.getLength() != C.LENGTH_UNBOUNDED); // never read forever!
    skipToNextPage(input);
    pageHeader.reset();
    while ((pageHeader.type & 0x04) != 0x04 && input.getPosition() < input.getLength()) {
      pageHeader.populate(input, false);
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
    }
    return pageHeader.granulePosition;
  }

  /**
   * Skips to the position of the start of the page containing the {@code targetGranule} and
   * returns the elapsed samples which is the granule of the page previous to the target page.
   * <p>
   * Note that the position of the {@code input} must be before the start of the page previous to
   * the page containing the targetGranule to get the correct number of elapsed samples.
   * Which is in short like: {@code pos(input) <= pos(targetPage.pageSequence - 1)}.
   *
   * @param input the {@link ExtractorInput} to read from.
   * @param targetGranule the target granule (number of frames per channel).
   * @return the number of elapsed samples at the start of the target page.
   * @throws ParserException thrown if populating the page header fails.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  //@VisibleForTesting
  long skipToPageOfGranule(ExtractorInput input, long targetGranule)
      throws IOException, InterruptedException {
    skipToNextPage(input);
    pageHeader.populate(input, false);
    while (pageHeader.granulePosition < targetGranule) {
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
      // Store in a member field to be able to resume after IOExceptions.
      elapsedSamples = pageHeader.granulePosition;
      // Peek next header.
      pageHeader.populate(input, false);
    }
    if (elapsedSamples == 0) {
      throw new ParserException();
    }
    input.resetPeekPosition();
    long returnValue = elapsedSamples;
    // Reset member state.
    elapsedSamples = 0;
    return returnValue;
  }

}
