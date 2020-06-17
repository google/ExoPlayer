package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Holds and updates {@link Format format} data to a {@link TrackOutput}
 */
public class FormatHolder {

  private final TrackOutput output;
  private @MonotonicNonNull Format format;

  public FormatHolder(TrackOutput output) {
    this.output = output;
  }

  /**
   * @return if this {@link FormatHolder} has had a format set
   */
  public boolean hasFormat() {
    return format != null;
  }

  /**
   * Access the currently held format.
   * <p>
   * <b>NOTE</b>: Call {@link FormatHolder#hasFormat()} first to make sure a format is
   * currently held.
   *
   * @return the {@link Format} currently held.
   */
  public Format getFormat() {
    Assertions.checkState(hasFormat(), "Held format is null, did you call hasFormat()?");
    return format;
  }

  /**
   * Updates the {@link FormatHolder#output} with the specified {@link Format}
   */
  public void update(Format newFormat) {
    if (!Util.areEqual(format, newFormat)) {
      format = newFormat;
      output.format(newFormat);
    }
  }

  /**
   * Updates the {@link FormatHolder#output} with the {@link Format} derived from the passed in
   * orientationData
   */
  public void update(DisplayOrientationSeiReader.DisplayOrientationData orientationData) {
    Assertions.checkState(hasFormat(), "This FormatHolder has not had a format set");
    int clockwiseRotation = 360 - orientationData.anticlockwiseRotation;
    update(format.buildUpon()
        .setRotationDegrees(clockwiseRotation)
        .build());
  }
}
