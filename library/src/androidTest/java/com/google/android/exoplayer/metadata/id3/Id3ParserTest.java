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

import android.test.MoreAsserts;
import com.google.android.exoplayer.ParserException;
import java.util.List;
import junit.framework.TestCase;

/**
 * Test for {@link Id3Parser}
 */
public class Id3ParserTest extends TestCase {

  public void testParseTxxxFrame() throws ParserException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 41, 84, 88, 88, 88, 0, 0, 0, 31, 0, 0,
        3, 0, 109, 100, 105, 97, 108, 111, 103, 95, 86, 73, 78, 68, 73, 67, 79, 49, 53, 50, 55, 54,
        54, 52, 95, 115, 116, 97, 114, 116, 0};
    Id3Parser parser = new Id3Parser();
    List<Id3Frame> id3Frames = parser.parse(rawId3, rawId3.length);
    assertEquals(1, id3Frames.size());
    TxxxFrame txxxFrame = (TxxxFrame) id3Frames.get(0);
    assertEquals("", txxxFrame.description);
    assertEquals("mdialog_VINDICO1527664_start", txxxFrame.value);
  }

  public void testParseApicFrame() throws ParserException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 45, 65, 80, 73, 67, 0, 0, 0, 35, 0, 0,
        3, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 16, 72, 101, 108, 108, 111, 32, 87,
        111, 114, 108, 100, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
    Id3Parser parser = new Id3Parser();
    List<Id3Frame> id3Frames = parser.parse(rawId3, rawId3.length);
    assertEquals(1, id3Frames.size());
    ApicFrame apicFrame = (ApicFrame) id3Frames.get(0);
    assertEquals("image/jpeg", apicFrame.mimeType);
    assertEquals(16, apicFrame.pictureType);
    assertEquals("Hello World", apicFrame.description);
    assertEquals(10, apicFrame.pictureData.length);
    MoreAsserts.assertEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, apicFrame.pictureData);
  }

  public void testParseTextInformationFrame() throws ParserException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 23, 84, 73, 84, 50, 0, 0, 0, 13, 0, 0,
        3, 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 0};
    Id3Parser parser = new Id3Parser();
    List<Id3Frame> id3Frames = parser.parse(rawId3, rawId3.length);
    assertEquals(1, id3Frames.size());
    TextInformationFrame textInformationFrame = (TextInformationFrame) id3Frames.get(0);
    assertEquals("TIT2", textInformationFrame.id);
    assertEquals("Hello World", textInformationFrame.description);
  }

}
