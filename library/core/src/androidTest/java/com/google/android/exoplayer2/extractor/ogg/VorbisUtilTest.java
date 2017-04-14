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
package com.google.android.exoplayer2.extractor.ogg;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import junit.framework.TestCase;

/**
 * Unit test for {@link VorbisUtil}.
 */
public final class VorbisUtilTest extends TestCase {

  public void testILog() throws Exception {
    assertEquals(0, VorbisUtil.iLog(0));
    assertEquals(1, VorbisUtil.iLog(1));
    assertEquals(2, VorbisUtil.iLog(2));
    assertEquals(2, VorbisUtil.iLog(3));
    assertEquals(3, VorbisUtil.iLog(4));
    assertEquals(3, VorbisUtil.iLog(5));
    assertEquals(4, VorbisUtil.iLog(8));
    assertEquals(0, VorbisUtil.iLog(-1));
    assertEquals(0, VorbisUtil.iLog(-122));
  }

  public void testReadIdHeader() throws Exception {
    byte[] data = TestData.getIdentificationHeaderData();
    ParsableByteArray headerData = new ParsableByteArray(data, data.length);
    VorbisUtil.VorbisIdHeader vorbisIdHeader =
        VorbisUtil.readVorbisIdentificationHeader(headerData);

    assertEquals(22050, vorbisIdHeader.sampleRate);
    assertEquals(0, vorbisIdHeader.version);
    assertTrue(vorbisIdHeader.framingFlag);
    assertEquals(2, vorbisIdHeader.channels);
    assertEquals(512, vorbisIdHeader.blockSize0);
    assertEquals(1024, vorbisIdHeader.blockSize1);
    assertEquals(-1, vorbisIdHeader.bitrateMax);
    assertEquals(-1, vorbisIdHeader.bitrateMin);
    assertEquals(66666, vorbisIdHeader.bitrateNominal);
    assertEquals(66666, vorbisIdHeader.getApproximateBitrate());
  }

  public void testReadCommentHeader() throws ParserException {
    byte[] data = TestData.getCommentHeaderDataUTF8();
    ParsableByteArray headerData = new ParsableByteArray(data, data.length);
    VorbisUtil.CommentHeader commentHeader = VorbisUtil.readVorbisCommentHeader(headerData);

    assertEquals("Xiph.Org libVorbis I 20120203 (Omnipresent)", commentHeader.vendor);
    assertEquals(3, commentHeader.comments.length);
    assertEquals("ALBUM=äö", commentHeader.comments[0]);
    assertEquals("TITLE=A sample song", commentHeader.comments[1]);
    assertEquals("ARTIST=Google", commentHeader.comments[2]);
  }

  public void testReadVorbisModes() throws ParserException {
    byte[] data = TestData.getSetupHeaderData();
    ParsableByteArray headerData = new ParsableByteArray(data, data.length);
    VorbisUtil.Mode[] modes = VorbisUtil.readVorbisModes(headerData, 2);

    assertEquals(2, modes.length);
    assertEquals(false, modes[0].blockFlag);
    assertEquals(0, modes[0].mapping);
    assertEquals(0, modes[0].transformType);
    assertEquals(0, modes[0].windowType);
    assertEquals(true, modes[1].blockFlag);
    assertEquals(1, modes[1].mapping);
    assertEquals(0, modes[1].transformType);
    assertEquals(0, modes[1].windowType);
  }

  public void testVerifyVorbisHeaderCapturePattern() throws ParserException {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertEquals(true, VorbisUtil.verifyVorbisHeaderCapturePattern(0x01, header, false));
  }

  public void testVerifyVorbisHeaderCapturePatternInvalidHeader() {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    try {
      VorbisUtil.verifyVorbisHeaderCapturePattern(0x99, header, false);
      fail();
    } catch (ParserException e) {
      assertEquals("expected header type 99", e.getMessage());
    }
  }

  public void testVerifyVorbisHeaderCapturePatternInvalidHeaderQuite() throws ParserException {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertFalse(VorbisUtil.verifyVorbisHeaderCapturePattern(0x99, header, true));
  }

  public void testVerifyVorbisHeaderCapturePatternInvalidPattern() {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'x', 'v', 'o', 'r', 'b', 'i', 's'});
    try {
      VorbisUtil.verifyVorbisHeaderCapturePattern(0x01, header, false);
      fail();
    } catch (ParserException e) {
      assertEquals("expected characters 'vorbis'", e.getMessage());
    }
  }

  public void testVerifyVorbisHeaderCapturePatternQuiteInvalidPatternQuite()
      throws ParserException {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'x', 'v', 'o', 'r', 'b', 'i', 's'});
    assertFalse(VorbisUtil.verifyVorbisHeaderCapturePattern(0x01, header, true));
  }

}
