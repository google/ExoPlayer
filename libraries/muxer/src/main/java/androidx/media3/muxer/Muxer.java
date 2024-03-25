/*
 * Copyright 2024 The Android Open Source Project
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

import android.media.MediaCodec;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;
import java.nio.ByteBuffer;

/** The muxer for producing media container files. */
@UnstableApi
public interface Muxer {
  /** A token representing an added track. */
  interface TrackToken {}

  /** Adds a track of the given media format. */
  TrackToken addTrack(Format format);

  /** Writes encoded sample data. */
  void writeSampleData(
      TrackToken trackToken, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
      throws IOException;

  /** Adds metadata for the output file. */
  void addMetadata(Metadata.Entry metadata);

  /** Closes the file. */
  void close() throws IOException;
}
