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
package com.google.android.exoplayer2.muxer;

import static com.google.android.exoplayer2.util.Assertions.checkState;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Collects and provides metadata: location, FPS, XMP data, etc.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class MetadataCollector {
  public int orientation;
  public @MonotonicNonNull Mp4Location location;
  public Map<String, Object> metadataPairs;
  public long modificationDateUnixMs;
  public @MonotonicNonNull ByteBuffer xmpData;

  public MetadataCollector() {
    orientation = 0;
    metadataPairs = new LinkedHashMap<>();
    modificationDateUnixMs = System.currentTimeMillis();
  }

  public void addXmp(ByteBuffer xmpData) {
    checkState(this.xmpData == null);
    this.xmpData = xmpData;
  }

  public void setOrientation(int orientation) {
    this.orientation = orientation;
  }

  public void setLocation(float latitude, float longitude) {
    location = new Mp4Location(latitude, longitude);
  }

  public void setCaptureFps(float captureFps) {
    metadataPairs.put("com.android.capture.fps", captureFps);
  }

  public void addMetadata(String key, Object value) {
    metadataPairs.put(key, value);
  }

  public void setModificationTime(long modificationDateUnixMs) {
    this.modificationDateUnixMs = modificationDateUnixMs;
  }
}
