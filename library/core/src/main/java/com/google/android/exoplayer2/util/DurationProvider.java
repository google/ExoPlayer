package com.google.android.exoplayer2.util;

import com.google.android.exoplayer2.C;

public interface DurationProvider {

  /**
   * Return the duration in milliseconds.
   * @return duration is milliseconds, or {@link C#TIME_UNSET} is not known.
   */
  long getDurationMs();
}
