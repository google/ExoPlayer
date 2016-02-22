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
package com.google.android.exoplayer.metadata.id3;

import junit.framework.TestCase;

import java.util.List;

/**
 * Test for {@link Id3Parser}
 */
public class Id3ParserTest extends TestCase {

  public void testParseTxxxFrames() {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 41, 84, 88, 88, 88, 0, 0, 0, 31,
        0, 0, 3, 0, 109, 100, 105, 97, 108, 111, 103, 95, 86, 73, 78, 68, 73, 67, 79, 49, 53, 50,
        55, 54, 54, 52, 95, 115, 116, 97, 114, 116, 0};
    Id3Parser parser = new Id3Parser();
    try {
      List<Id3Frame> id3Frames = parser.parse(rawId3, rawId3.length);
      assertNotNull(id3Frames);
      assertEquals(1, id3Frames.size());
      TxxxFrame txxxFrame = (TxxxFrame) id3Frames.get(0);
      assertEquals("", txxxFrame.description);
      assertEquals("mdialog_VINDICO1527664_start", txxxFrame.value);
    } catch (Exception exception) {
      fail(exception.getMessage());
    }
  }

}
