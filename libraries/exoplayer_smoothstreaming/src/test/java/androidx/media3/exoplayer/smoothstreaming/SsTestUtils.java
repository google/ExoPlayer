/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.exoplayer.smoothstreaming;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifest;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifest.ProtectionElement;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifest.StreamElement;
import androidx.media3.extractor.mp4.TrackEncryptionBox;
import java.util.Collections;

/** Util methods for SmoothStreaming tests. */
public class SsTestUtils {

  private static final int TEST_MAJOR_VERSION = 1;
  private static final int TEST_MINOR_VERSION = 2;
  private static final int TEST_TIMESCALE = 1000;
  private static final int TEST_DURATION = 5000;
  private static final int TEST_DVR_WINDOW_LENGTH = 0;
  private static final int TEST_LOOKAHEAD_COUNT = 0;
  private static final boolean TEST_IS_LIVE = false;
  private static final String TEST_BASE_URI = "baseUri";
  private static final String TEST_CHUNK_TEMPLATE = "chunkTemplate";
  private static final String TEST_SUB_TYPE = "subType";
  private static final int TEST_MAX_WIDTH = 1024;
  private static final int TEST_MAX_HEIGHT = 768;
  private static final String TEST_LANGUAGE = "eng";
  private static final ProtectionElement TEST_PROTECTION_ELEMENT =
      new ProtectionElement(C.WIDEVINE_UUID, new byte[0], new TrackEncryptionBox[0]);

  private SsTestUtils() {}

  /** Creates test manifest with the given stream elements. */
  public static SsManifest createSsManifest(StreamElement... streamElements) {
    return new SsManifest(
        TEST_MAJOR_VERSION,
        TEST_MINOR_VERSION,
        TEST_TIMESCALE,
        TEST_DURATION,
        TEST_DVR_WINDOW_LENGTH,
        TEST_LOOKAHEAD_COUNT,
        TEST_IS_LIVE,
        TEST_PROTECTION_ELEMENT,
        streamElements);
  }

  /** Creates test video stream element with the given name, track type and formats. */
  public static StreamElement createStreamElement(
      String name, @C.TrackType int trackType, Format... formats) {
    return new StreamElement(
        TEST_BASE_URI,
        TEST_CHUNK_TEMPLATE,
        trackType,
        TEST_SUB_TYPE,
        TEST_TIMESCALE,
        name,
        TEST_MAX_WIDTH,
        TEST_MAX_HEIGHT,
        TEST_MAX_WIDTH,
        TEST_MAX_HEIGHT,
        TEST_LANGUAGE,
        formats,
        /* chunkStartTimes= */ Collections.emptyList(),
        /* lastChunkDuration= */ 0);
  }
}
