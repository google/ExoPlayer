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
import com.google.android.exoplayer2.util.Assertions;
import junit.framework.TestCase;

/**
 * Test for {@link Id3Decoder}.
 */
public final class Id3DecoderTest extends TestCase {

  private static final byte[] TAG_HEADER = new byte[] {73, 68, 51, 4, 0, 0, 0, 0, 0, 0};
  private static final int FRAME_HEADER_LENGTH = 10;
  private static final int ID3_TEXT_ENCODING_UTF_8 = 3;

  public void testDecodeTxxxFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("TXXX", new byte[] {3, 0, 109, 100, 105, 97, 108, 111, 103,
        95, 86, 73, 78, 68, 73, 67, 79, 49, 53, 50, 55, 54, 54, 52, 95, 115, 116, 97, 114, 116, 0});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertEquals("TXXX", textInformationFrame.id);
    assertEquals("", textInformationFrame.description);
    assertEquals("mdialog_VINDICO1527664_start", textInformationFrame.value);

    // Test empty.
    rawId3 = buildSingleFrameTag("TXXX", new byte[0]);
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(0, metadata.length());

    // Test encoding byte only.
    rawId3 = buildSingleFrameTag("TXXX", new byte[] {ID3_TEXT_ENCODING_UTF_8});
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertEquals("TXXX", textInformationFrame.id);
    assertEquals("", textInformationFrame.description);
    assertEquals("", textInformationFrame.value);
  }

  public void testDecodeTextInformationFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("TIT2", new byte[] {3, 72, 101, 108, 108, 111, 32, 87, 111,
        114, 108, 100, 0});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertEquals("TIT2", textInformationFrame.id);
    assertNull(textInformationFrame.description);
    assertEquals("Hello World", textInformationFrame.value);

    // Test empty.
    rawId3 = buildSingleFrameTag("TIT2", new byte[0]);
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(0, metadata.length());

    // Test encoding byte only.
    rawId3 = buildSingleFrameTag("TIT2", new byte[] {ID3_TEXT_ENCODING_UTF_8});
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertEquals("TIT2", textInformationFrame.id);
    assertEquals(null, textInformationFrame.description);
    assertEquals("", textInformationFrame.value);
  }

  public void testDecodeWxxxFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("WXXX", new byte[] {ID3_TEXT_ENCODING_UTF_8, 116, 101, 115,
        116, 0, 104, 116, 116, 112, 115, 58, 47, 47, 116, 101, 115, 116, 46, 99, 111, 109, 47, 97,
        98, 99, 63, 100, 101, 102});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    UrlLinkFrame urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertEquals("WXXX", urlLinkFrame.id);
    assertEquals("test", urlLinkFrame.description);
    assertEquals("https://test.com/abc?def", urlLinkFrame.url);

    // Test empty.
    rawId3 = buildSingleFrameTag("WXXX", new byte[0]);
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(0, metadata.length());

    // Test encoding byte only.
    rawId3 = buildSingleFrameTag("WXXX", new byte[] {ID3_TEXT_ENCODING_UTF_8});
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertEquals("WXXX", urlLinkFrame.id);
    assertEquals("", urlLinkFrame.description);
    assertEquals("", urlLinkFrame.url);
  }

  public void testDecodeUrlLinkFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("WCOM", new byte[] {104, 116, 116, 112, 115, 58, 47, 47,
        116, 101, 115, 116, 46, 99, 111, 109, 47, 97, 98, 99, 63, 100, 101, 102});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    UrlLinkFrame urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertEquals("WCOM", urlLinkFrame.id);
    assertEquals(null, urlLinkFrame.description);
    assertEquals("https://test.com/abc?def", urlLinkFrame.url);

    // Test empty.
    rawId3 = buildSingleFrameTag("WCOM", new byte[0]);
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertEquals("WCOM", urlLinkFrame.id);
    assertEquals(null, urlLinkFrame.description);
    assertEquals("", urlLinkFrame.url);
  }

  public void testDecodePrivFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("PRIV", new byte[] {116, 101, 115, 116, 0, 1, 2, 3, 4});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    PrivFrame privFrame = (PrivFrame) metadata.get(0);
    assertEquals("test", privFrame.owner);
    MoreAsserts.assertEquals(new byte[] {1, 2, 3, 4}, privFrame.privateData);

    // Test empty.
    rawId3 = buildSingleFrameTag("PRIV", new byte[0]);
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    privFrame = (PrivFrame) metadata.get(0);
    assertEquals("", privFrame.owner);
    MoreAsserts.assertEquals(new byte[0], privFrame.privateData);
  }

  public void testDecodeApicFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("APIC", new byte[] {3, 105, 109, 97, 103, 101, 47, 106, 112,
        101, 103, 0, 16, 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 0, 1, 2, 3, 4, 5, 6, 7,
        8, 9, 0});
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

  public void testDecodeCommentFrame() throws MetadataDecoderException {
    byte[] rawId3 = buildSingleFrameTag("COMM", new byte[] {ID3_TEXT_ENCODING_UTF_8, 101, 110, 103,
        100, 101, 115, 99, 114, 105, 112, 116, 105, 111, 110, 0, 116, 101, 120, 116, 0});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    CommentFrame commentFrame = (CommentFrame) metadata.get(0);
    assertEquals("eng", commentFrame.language);
    assertEquals("description", commentFrame.description);
    assertEquals("text", commentFrame.text);

    // Test empty.
    rawId3 = buildSingleFrameTag("COMM", new byte[0]);
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(0, metadata.length());

    // Test language only.
    rawId3 = buildSingleFrameTag("COMM", new byte[] {ID3_TEXT_ENCODING_UTF_8, 101, 110, 103});
    metadata = decoder.decode(rawId3, rawId3.length);
    assertEquals(1, metadata.length());
    commentFrame = (CommentFrame) metadata.get(0);
    assertEquals("eng", commentFrame.language);
    assertEquals("", commentFrame.description);
    assertEquals("", commentFrame.text);
  }

  private static byte[] buildSingleFrameTag(String frameId, byte[] frameData) {
    byte[] frameIdBytes = frameId.getBytes();
    Assertions.checkState(frameIdBytes.length == 4);

    byte[] tagData = new byte[TAG_HEADER.length + FRAME_HEADER_LENGTH + frameData.length];
    System.arraycopy(TAG_HEADER, 0, tagData, 0, TAG_HEADER.length);
    // Fill in the size part of the tag header.
    int offset = TAG_HEADER.length - 4;
    int tagSize = frameData.length + FRAME_HEADER_LENGTH;
    tagData[offset++] = (byte) ((tagSize >> 21) & 0x7F);
    tagData[offset++] = (byte) ((tagSize >> 14) & 0x7F);
    tagData[offset++] = (byte) ((tagSize >> 7) & 0x7F);
    tagData[offset++] = (byte) (tagSize & 0x7F);
    // Fill in the frame header.
    tagData[offset++] = frameIdBytes[0];
    tagData[offset++] = frameIdBytes[1];
    tagData[offset++] = frameIdBytes[2];
    tagData[offset++] = frameIdBytes[3];
    tagData[offset++] = (byte) ((frameData.length >> 24) & 0xFF);
    tagData[offset++] = (byte) ((frameData.length >> 16) & 0xFF);
    tagData[offset++] = (byte) ((frameData.length >> 8) & 0xFF);
    tagData[offset++] = (byte) (frameData.length & 0xFF);
    offset += 2; // Frame flags set to 0
    // Fill in the frame data.
    System.arraycopy(frameData, 0, tagData, offset, frameData.length);

    return tagData;
  }

}
