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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSpec;

/**
 * Thrown from the loader thread when an attempt is made to commit a sample that is far
 * deviant from the expected sequence of timestamps in the SampleStream.
 *
 * This is likely caused by an intra-chunk timestamp discontinuity that was not handled by the
 * chunk source (the origin server).
 *
 * For HLS, the origin server is required to break segments at continuity boundaries by the HLS Pantos spec
 * (EXT-X-DISCONTINUITY {@see https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-4.4.2.3}).
 * In DASH, segments must be divided into periods when there are timestamp discontinuities
 * {@see https://www.w3.org/2018/12/webmediaguidelines.html#server-side-ad-insertion}
 *
 */
public final class UnexpectedDiscontinuityException extends IllegalStateException {

  /** the last in-bounds timestamp committed to the {@link SampleQueue}, or
   * {@link C#TIME_UNSET} if this was for the first committed sample
   */
  public final long lastValidTimeUs;

  /** The errant timestamp
   */
  public final long deviantSampleTimeUs;

  /** The source of the samples that resulted in this error
   */
  public final DataSpec dataSpec;

  /** The timeUs that the source of the samples starts (from HLS metadata)
   */
  public final long startTimeUs;

  /**
   * Construct an UnexpectedDiscontinuityException for a {@link MediaChunk} where an
   * unexpected timestamp discontinuity is detected within its sample source (e.g. segment for HLS)
   *
   * @param mediaChunk the {@link MediaChunk} with the unexpected timestamp value
   * @param lastValidTimeUs the last in-bounds timestamp committed to the {@link SampleQueue}, or
   *                        {@link C#TIME_UNSET} if this was for the first committed sample
   * @param deviantSampleTimeUs the timestamp that is out of bounds.
   */
  public UnexpectedDiscontinuityException(MediaChunk mediaChunk, long lastValidTimeUs, long deviantSampleTimeUs) {
    super("Unexpected discontinuity, timeMs: " + C.usToMs(deviantSampleTimeUs) + " loaded from dataSpec: " + mediaChunk.dataSpec);
    this.dataSpec = mediaChunk.dataSpec;
    this.startTimeUs = mediaChunk.startTimeUs;
    this.lastValidTimeUs = lastValidTimeUs;
    this.deviantSampleTimeUs = deviantSampleTimeUs;
  }
}
