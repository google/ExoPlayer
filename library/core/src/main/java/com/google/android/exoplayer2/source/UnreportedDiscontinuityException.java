package com.google.android.exoplayer2.source;

import android.net.Uri;
import com.google.android.exoplayer2.C;

/**
 * Thrown from the loader thread when an attempt is made to commit a sample that is far
 * deviant from the expected sequence of timestamps in the SampleStream.   This is likely
 * caused by a discontinuity in a segment that was not split and reported by metadata in
 * an HLS (EXT-X-DISCONTINUITY) or DASH stream.
 */
public class UnreportedDiscontinuityException extends RuntimeException {

  public final long timesUs;

  /**
   * Consturct the exception
   *
   * @param timesUs last timestamp before attempted commit of the deviant sample
   * @param uri uri of the segment with the unreported discontinuity
   */
  public UnreportedDiscontinuityException(long timesUs, Uri uri) {
    super("Unreported discontinuity timeMs: " + C.usToMs(timesUs) + " in URI: " + uri);
    this.timesUs = timesUs;
  }
}
