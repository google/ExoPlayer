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
package com.google.android.exoplayer2.source.hls;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.UnexpectedDiscontinuityException;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaSourceEventDispatcher;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.util.Map;

/**
 * Extend base SampleQueue to add HLS specific processing of the samples, including:
 *
 * <ul>
 *   <li>segment time boundary checks on timestamps of committed samples</li>
 *   <li>cleaning the {@link Format#metadata} to avoid excessive format changes</li>
 * </ul>
 *
 * The timestamp check verifies that the adjusted sample time (via {@link TimestampAdjuster}) does not
 * fall outside of a set percentage ({@link #MAX_TIMESTAMP_DEVIATION_PERCENTAGE}) of the time
 * boundaries of the segment as expressed by the segment duration ((@link HlsMediaChunk#endTimeUs} -
 * {@link HlsMediaChunk#startTimeUs}).  This is loosely mandated by the Pantos spec and checked by
 * Apple's mediastreamvalidator.
 *
 */
public class HlsSampleQueue extends SampleQueue {

  private static final String TAG = "HlsSampleQueue";

  /**
   * largest timestamp deviation from the segment time bounds expressed as a percentage of
   * the segment duration.
   */
  public static double MAX_TIMESTAMP_DEVIATION_PERCENTAGE = 0.50;

  private long lowestTimeUs = C.TIME_UNSET;
  private long highestTimeUs = C.TIME_UNSET;

  @Nullable private HlsMediaChunk chunk;
  private long lastValidTimeUs;

  private final Map<String, DrmInitData> overridingDrmInitData;
  @Nullable private DrmInitData drmInitData;

  public HlsSampleQueue(Allocator allocator,
          DrmSessionManager<?> drmSessionManager,
          MediaSourceEventDispatcher eventDispatcher,
          Map<String, DrmInitData> overridingDrmInitData) {
    super(allocator, drmSessionManager, eventDispatcher);
    this.overridingDrmInitData = overridingDrmInitData;
  }

  void setCurrentLoadingChunk(HlsMediaChunk chunk) {
    double tolerance = (chunk.endTimeUs - chunk.startTimeUs) * MAX_TIMESTAMP_DEVIATION_PERCENTAGE;
    this.lowestTimeUs = (long) (chunk.startTimeUs - tolerance);
    this.highestTimeUs = (long) (chunk.endTimeUs + tolerance);
    this.chunk = chunk;
    lastValidTimeUs = C.TIME_UNSET;
  }

  public void setDrmInitData(@Nullable DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
    invalidateUpstreamFormatAdjustment();
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public Format getAdjustedUpstreamFormat(Format format) {
    @Nullable
    DrmInitData drmInitData = this.drmInitData != null ? this.drmInitData : format.drmInitData;
    if (drmInitData != null) {
      @Nullable
      DrmInitData overridingDrmInitData = this.overridingDrmInitData.get(drmInitData.schemeType);
      if (overridingDrmInitData != null) {
        drmInitData = overridingDrmInitData;
      }
    }
    @Nullable Metadata metadata = getAdjustedMetadata(format.metadata);
    if (drmInitData != format.drmInitData || metadata != format.metadata) {
      format = format.buildUpon().setDrmInitData(drmInitData).setMetadata(metadata).build();
    }
    return super.getAdjustedUpstreamFormat(format);
  }

  /**
   * Strips the private timestamp frame from metadata, if present. See:
   * https://github.com/google/ExoPlayer/issues/5063
   */
  @Nullable
  private Metadata getAdjustedMetadata(@Nullable Metadata metadata) {
    if (metadata == null) {
      return null;
    }
    int length = metadata.length();
    int transportStreamTimestampMetadataIndex = C.INDEX_UNSET;
    for (int i = 0; i < length; i++) {
      Metadata.Entry metadataEntry = metadata.get(i);
      if (metadataEntry instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) metadataEntry;
        if (HlsMediaChunk.PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
          transportStreamTimestampMetadataIndex = i;
          break;
        }
      }
    }
    if (transportStreamTimestampMetadataIndex == C.INDEX_UNSET) {
      return metadata;
    }
    if (length == 1) {
      return null;
    }
    Metadata.Entry[] newMetadataEntries = new Metadata.Entry[length - 1];
    for (int i = 0; i < length; i++) {
      if (i != transportStreamTimestampMetadataIndex) {
        int newIndex = i < transportStreamTimestampMetadataIndex ? i : i - 1;
        newMetadataEntries[newIndex] = metadata.get(i);
      }
    }
    return new Metadata(newMetadataEntries);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset,  @Nullable CryptoData cryptoData) {
    // TODO - chunkless prepare, sampleQueue list is not yet initialized for first chunk
//    Assertions.checkNotNull(chunk, "sampleMetadata without a MediaChunk?");
    if (chunk == null) {
      super.sampleMetadata(timeUs, flags, size, offset, cryptoData);
    } else if (timeUs > highestTimeUs || timeUs < lowestTimeUs) {
      throw new UnexpectedDiscontinuityException(chunk, lastValidTimeUs, timeUs);
    } else {
      lastValidTimeUs = timeUs;
      super.sampleMetadata(timeUs, flags, size, offset, cryptoData);
    }
  }

}
