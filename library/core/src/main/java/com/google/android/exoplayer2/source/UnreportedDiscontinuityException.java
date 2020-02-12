package com.google.android.exoplayer2.source;

import java.io.IOException;

/**
 * Thrown from the loader thread when an attempt is made to commit a sample that is far
 * deviant from the expected sequence of timestamps in the SampleStream.   This is likely
 * caused by a discontinuity in a segment that was not split and reported by metadata in
 * an HLS (EXT-X-DISCONTINUITY) or DASH stream.
 */
public class UnreportedDiscontinuityException extends RuntimeException {

  public final long timesUs;
  public final int sourceId;

  /**
   * Consturct the exception
   *
   * @param timesUs last timestamp before attempted commit of the deviant sample
   * @param sourceId source (e.g. HLS segment) id of the segment with the issue
   */
  public UnreportedDiscontinuityException(long timesUs, int sourceId) {
    this.timesUs = timesUs;
    this.sourceId = sourceId;
  }
}
