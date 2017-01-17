/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.id3;

import android.test.MoreAsserts;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataDecoderException;
import junit.framework.TestCase;

/**
 * Test for {@link Id3Decoder}.
 */
public final class Id3DecoderTest extends TestCase {

  public void testDecodeTxxxFrame() throws MetadataDecoderException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 41, 84, 88, 88, 88, 0, 0, 0, 31, 0, 0,
        3, 0, 109, 100, 105, 97, 108, 111, 103, 95, 86, 73, 78, 68, 73, 67, 79, 49, 53, 50, 55, 54,
        54, 52, 95, 115, 116, 97, 114, 116, 0};
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertEquals("TXXX", textInformationFrame.id);
    assertEquals("", textInformationFrame.description);
    assertEquals("mdialog_VINDICO1527664_start", textInformationFrame.value);
  }

  public void testDecodeApicFrame() throws MetadataDecoderException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 45, 65, 80, 73, 67, 0, 0, 0, 35, 0, 0,
        3, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 16, 72, 101, 108, 108, 111, 32, 87,
        111, 114, 108, 100, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    ApicFrame apicFrame = (ApicFrame) metadata.get(0);
    assertEquals("image/jpeg", apicFrame.mimeType);
    assertEquals(16, apicFrame.pictureType);
    assertEquals("Hello World", apicFrame.description);
    assertEquals(10, apicFrame.pictureData.length);
    MoreAsserts.assertEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, apicFrame.pictureData);
  }

  public void testDecodeTextInformationFrame() throws MetadataDecoderException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 23, 84, 73, 84, 50, 0, 0, 0, 13, 0, 0,
        3, 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 0};
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertEquals("TIT2", textInformationFrame.id);
    assertNull(textInformationFrame.description);
    assertEquals("Hello World", textInformationFrame.value);
  }

  public void testDecodePrivFrame() throws MetadataDecoderException {
    byte[] rawId3 = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 19, 80, 82, 73, 86, 0, 0, 0, 9, 0, 0,
        116, 101, 115, 116, 0, 1, 2, 3, 4};
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    PrivFrame privFrame = (PrivFrame) metadata.get(0);
    assertEquals("test", privFrame.owner);
    MoreAsserts.assertEquals(new byte[] {1, 2, 3, 4}, privFrame.privateData);
  }

}
