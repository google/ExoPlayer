/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate a thumbnail strip (i.e. tile frames horizontally) containing frames at given {@link
 * #setTimestampsMs timestamps}.
 */
@UnstableApi
/* package */ final class ThumbnailStripEffect implements GlEffect {

  /* package */ final int stripWidth;
  /* package */ final int stripHeight;
  private final List<Long> timestampsMs;
  private int currentThumbnailIndex;

  /**
   * Creates a new instance with the given size. No thumbnails are drawn by default, call {@link
   * #setTimestampsMs} to change how many to draw and their timestamp.
   *
   * @param stripWidth The width of the thumbnail strip.
   * @param stripHeight The height of the thumbnail strip.
   */
  public ThumbnailStripEffect(int stripWidth, int stripHeight) {
    this.stripWidth = stripWidth;
    this.stripHeight = stripHeight;
    timestampsMs = new ArrayList<>();
  }

  @Override
  public ThumbnailStripShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new ThumbnailStripShaderProgram(context, useHdr, this);
  }

  /**
   * Sets the timestamps of the frames to draw, in milliseconds.
   *
   * <p>The timestamp represents the minimum presentation time of the next frame added to the strip.
   * For example, if the timestamp is 10, a frame with a time of 100 will be drawn but one with a
   * time of 9 will be ignored.
   */
  public void setTimestampsMs(List<Long> timestampsMs) {
    this.timestampsMs.clear();
    this.timestampsMs.addAll(timestampsMs);
    currentThumbnailIndex = 0;
  }

  /** Returns whether all the thumbnails have already been drawn. */
  public boolean isDone() {
    return currentThumbnailIndex >= timestampsMs.size();
  }

  /** Returns the index of the next thumbnail to draw. */
  public int getNextThumbnailIndex() {
    return currentThumbnailIndex;
  }

  /** Returns the timestamp in milliseconds of the next thumbnail to draw. */
  public long getNextTimestampMs() {
    return isDone() ? C.TIME_END_OF_SOURCE : timestampsMs.get(currentThumbnailIndex);
  }

  /** Returns the total number of thumbnails to be drawn in the strip. */
  public int getNumberOfThumbnails() {
    return timestampsMs.size();
  }

  /* package */ void onThumbnailDrawn() {
    currentThumbnailIndex++;
  }
}
