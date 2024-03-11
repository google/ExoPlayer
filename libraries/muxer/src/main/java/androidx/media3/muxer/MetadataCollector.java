/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.muxer;

import static androidx.media3.container.Mp4TimestampData.unixTimeToMp4TimeSeconds;

import androidx.media3.common.Metadata;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.container.XmpData;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Collects and provides metadata: location, FPS, XMP data, etc. */
/* package */ final class MetadataCollector {
  public Mp4OrientationData orientationData;
  public @MonotonicNonNull Mp4LocationData locationData;
  public List<MdtaMetadataEntry> metadataEntries;
  public Mp4TimestampData timestampData;
  public @MonotonicNonNull XmpData xmpData;

  /** Creates an instance. */
  public MetadataCollector() {
    orientationData = new Mp4OrientationData(/* orientation= */ 0);
    metadataEntries = new ArrayList<>();
    long currentTimeInMp4TimeSeconds = unixTimeToMp4TimeSeconds(System.currentTimeMillis());
    timestampData =
        new Mp4TimestampData(
            /* creationTimestampSeconds= */ currentTimeInMp4TimeSeconds,
            /* modificationTimestampSeconds= */ currentTimeInMp4TimeSeconds);
  }

  /** Adds metadata for the output file. */
  public void addMetadata(Metadata.Entry metadata) {
    if (metadata instanceof Mp4OrientationData) {
      orientationData = (Mp4OrientationData) metadata;
    } else if (metadata instanceof Mp4LocationData) {
      locationData = (Mp4LocationData) metadata;
    } else if (metadata instanceof Mp4TimestampData) {
      timestampData = (Mp4TimestampData) metadata;
    } else if (metadata instanceof MdtaMetadataEntry) {
      metadataEntries.add((MdtaMetadataEntry) metadata);
    } else if (metadata instanceof XmpData) {
      xmpData = (XmpData) metadata;
    } else {
      throw new IllegalArgumentException("Unsupported metadata");
    }
  }
}
