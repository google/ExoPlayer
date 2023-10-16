/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.extractor.mp4;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.metadata.mp4.SmtaMetadataEntry;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Utility methods for handling SMTA atoms.
 *
 * <p>See [Internal: b/150138465#comment76], [Internal: b/301273734#comment17].
 */
@UnstableApi
public final class SmtaAtomUtil {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      open = true,
      value = {
        NO_VALUE,
        CAMCORDER_NORMAL,
        CAMCORDER_SINGLE_SUPERSLOW_MOTION,
        CAMCORDER_FRC_SUPERSLOW_MOTION,
        CAMCORDER_SLOW_MOTION_V2,
        CAMCORDER_SLOW_MOTION_V2_120,
        CAMCORDER_SLOW_MOTION_V2_HEVC,
        CAMCORDER_FRC_SUPERSLOW_MOTION_HEVC,
        CAMCORDER_QFRC_SUPERSLOW_MOTION,
      })
  private @interface RecordingMode {}

  private static final int NO_VALUE = -1;
  private static final int CAMCORDER_NORMAL = 0;
  private static final int CAMCORDER_SINGLE_SUPERSLOW_MOTION = 7;
  private static final int CAMCORDER_FRC_SUPERSLOW_MOTION = 9;
  private static final int CAMCORDER_SLOW_MOTION_V2 = 12;
  private static final int CAMCORDER_SLOW_MOTION_V2_120 = 13;
  private static final int CAMCORDER_SLOW_MOTION_V2_HEVC = 21;
  private static final int CAMCORDER_FRC_SUPERSLOW_MOTION_HEVC = 22;
  private static final int CAMCORDER_QFRC_SUPERSLOW_MOTION = 23;

  private SmtaAtomUtil() {}

  /** Parses metadata from a Samsung smta atom. */
  @Nullable
  public static Metadata parseSmta(ParsableByteArray smta, int limit) {
    smta.skipBytes(Atom.FULL_HEADER_SIZE);
    while (smta.getPosition() < limit) {
      int atomPosition = smta.getPosition();
      int atomSize = smta.readInt();
      int atomType = smta.readInt();
      if (atomType == Atom.TYPE_saut) {
        // Size (4), Type (4), Author (4), Recording mode (2), SVC layer count (2).
        if (atomSize < 16) {
          return null;
        }
        smta.skipBytes(4); // Author (4)

        // Each field is stored as a key (1  byte) value (1 byte) pairs.
        // The order of the fields is not guaranteed.
        @RecordingMode int recordingMode = NO_VALUE;
        int svcTemporalLayerCount = 0;
        for (int i = 0; i < 2; i++) {
          int key = smta.readUnsignedByte();
          int value = smta.readUnsignedByte();
          if (key == 0x00) { // recordingMode key
            recordingMode = value;
          } else if (key == 0x01) { // svcTemporalLayerCount key
            svcTemporalLayerCount = value;
          }
        }

        int captureFrameRate = getCaptureFrameRate(recordingMode, smta, limit);
        if (captureFrameRate == C.RATE_UNSET_INT) {
          return null;
        }

        return new Metadata(new SmtaMetadataEntry(captureFrameRate, svcTemporalLayerCount));
      }
      smta.setPosition(atomPosition + atomSize);
    }
    return null;
  }

  /**
   * Returns the capture frame rate for the given recording mode, if supported.
   *
   * <p>For {@link #CAMCORDER_SLOW_MOTION_V2_HEVC}, this is done by parsing the Samsung 'srfr' atom.
   *
   * @return The capture frame rate value, or {@link C#RATE_UNSET_INT} if unavailable.
   */
  private static int getCaptureFrameRate(
      @RecordingMode int recordingMode, ParsableByteArray smta, int limit) {
    // V2 and V2_120 have fixed capture frame rates.
    if (recordingMode == CAMCORDER_SLOW_MOTION_V2) {
      return 240;
    } else if (recordingMode == CAMCORDER_SLOW_MOTION_V2_120) {
      return 120;
    } else if (recordingMode != CAMCORDER_SLOW_MOTION_V2_HEVC) {
      return C.RATE_UNSET_INT;
    }

    if (smta.bytesLeft() < Atom.HEADER_SIZE || smta.getPosition() + Atom.HEADER_SIZE > limit) {
      return C.RATE_UNSET_INT;
    }

    int atomSize = smta.readInt();
    int atomType = smta.readInt();
    if (atomSize < 12 || atomType != Atom.TYPE_srfr) {
      return C.RATE_UNSET_INT;
    }
    // Capture frame rate is in Q16 format.
    return smta.readUnsignedFixedPoint1616();
  }
}
