/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.dash;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SingleSegmentBase;

import junit.framework.TestCase;

/**
 * Tests {@link DashChunkSource}.
 */
public class DashChunkSourceTest extends TestCase {

  public void testMaxVideoDimensions() {
    SingleSegmentBase segmentBase1 = new SingleSegmentBase("https://example.com/1.mp4");
    Format format1 = new Format("1", "video/mp4", 100, 200, -1, -1, 1000);
    Representation representation1 =
        Representation.newInstance(0, 0, null, 0, format1, segmentBase1);

    SingleSegmentBase segmentBase2 = new SingleSegmentBase("https://example.com/2.mp4");
    Format format2 = new Format("2", "video/mp4", 400, 50, -1, -1, 1000);
    Representation representation2 =
        Representation.newInstance(0, 0, null, 0, format2, segmentBase2);

    DashChunkSource chunkSource = new DashChunkSource(null, null, representation1, representation2);
    MediaFormat out = MediaFormat.createVideoFormat("video/h264", 1, 1, 1, 1, null);
    chunkSource.getMaxVideoDimensions(out);

    assertEquals(400, out.getMaxVideoWidth());
    assertEquals(200, out.getMaxVideoHeight());
  }

}
