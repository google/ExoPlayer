/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.extractor.jpeg;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ForwardingSeekMap;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;

/**
 * An extractor output that wraps another extractor output and applies a give start byte offset to
 * seek positions.
 *
 * <p>This is useful for extracting from a container that's concatenated after some prefix data but
 * where the container's extractor doesn't handle a non-zero start offset (for example, because it
 * seeks to absolute positions read from the container data).
 */
@UnstableApi
public final class StartOffsetExtractorOutput implements ExtractorOutput {

  private final long startOffset;
  private final ExtractorOutput extractorOutput;

  /** Creates a new wrapper reading from the given start byte offset. */
  public StartOffsetExtractorOutput(long startOffset, ExtractorOutput extractorOutput) {
    this.startOffset = startOffset;
    this.extractorOutput = extractorOutput;
  }

  @Override
  public TrackOutput track(int id, int type) {
    return extractorOutput.track(id, type);
  }

  @Override
  public void endTracks() {
    extractorOutput.endTracks();
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    extractorOutput.seekMap(
        new ForwardingSeekMap(seekMap) {
          @Override
          public SeekPoints getSeekPoints(long timeUs) {
            SeekPoints seekPoints = seekMap.getSeekPoints(timeUs);
            return new SeekPoints(
                new SeekPoint(seekPoints.first.timeUs, seekPoints.first.position + startOffset),
                new SeekPoint(seekPoints.second.timeUs, seekPoints.second.position + startOffset));
          }
        });
  }
}
