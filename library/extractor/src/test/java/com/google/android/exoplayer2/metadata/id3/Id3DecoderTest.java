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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link Id3Decoder}. */
@RunWith(AndroidJUnit4.class)
public final class Id3DecoderTest {

  private static final byte[] TAG_HEADER = new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 0};
  private static final int FRAME_HEADER_LENGTH = 10;
  private static final int ID3_TEXT_ENCODING_UTF_8 = 3;

  @Test
  public void decodeTxxxFrame_utf8() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "TXXX",
            new byte[] {
              3, 0, 109, 100, 105, 97, 108, 111, 103, 95, 86, 73, 78, 68, 73, 67, 79, 49, 53, 50,
              55, 54, 54, 52, 95, 115, 116, 97, 114, 116, 0
            });
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.id).isEqualTo("TXXX");
    assertThat(textInformationFrame.description).isEmpty();
    assertThat(textInformationFrame.values.get(0)).isEqualTo("mdialog_VINDICO1527664_start");
  }

  @Test
  public void decodeTxxxFrame_utf16() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "TXXX",
            new byte[] {
              1, 0, 72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 32, 0, 87, 0, 111, 0, 114, 0, 108, 0,
              100, 0, 0
            });
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.id).isEqualTo("TXXX");
    assertThat(textInformationFrame.description).isEqualTo("Hello World");
    assertThat(textInformationFrame.values).containsExactly("");
  }

  @Test
  public void decodeTxxxFrame_multipleValues() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "TXXX",
            new byte[] {
              1, 0, 72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 32, 0, 87, 0, 111, 0, 114, 0, 108, 0,
              100, 0, 0, 0, 70, 0, 111, 0, 111, 0, 0, 0, 66, 0, 97, 0, 114, 0, 0
            });
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.description).isEqualTo("Hello World");
    assertThat(textInformationFrame.values).containsExactly("Foo", "Bar").inOrder();
  }

  @Test
  public void decodeTxxxFrame_empty() {
    byte[] rawId3 = buildSingleFrameTag("TXXX", new byte[0]);
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(0);
  }

  @Test
  public void decodeTxxxFrame_encodingByteOnly() {
    byte[] rawId3 = buildSingleFrameTag("TXXX", new byte[] {ID3_TEXT_ENCODING_UTF_8});
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.id).isEqualTo("TXXX");
    assertThat(textInformationFrame.description).isEmpty();
    assertThat(textInformationFrame.values).containsExactly("");
  }

  @Test
  public void decodeTextInformationFrame() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "TIT2", new byte[] {3, 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 0});
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.id).isEqualTo("TIT2");
    assertThat(textInformationFrame.description).isNull();
    assertThat(textInformationFrame.values.size()).isEqualTo(1);
    assertThat(textInformationFrame.values.get(0)).isEqualTo("Hello World");
  }

  @Test
  public void decodeTextInformationFrame_multipleValues() {
    // Test multiple values.
    byte[] rawId3 = buildSingleFrameTag("TIT2", new byte[] {3, 70, 111, 111, 0, 66, 97, 114, 0});
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.values).containsExactly("Foo", "Bar").inOrder();
  }

  @Test
  public void decodeTextInformationFrame_empty() {
    byte[] rawId3 = buildSingleFrameTag("TIT2", new byte[0]);
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(0);
  }

  @Test
  public void decodeTextInformationFrame_encodingByteOnly() {
    byte[] rawId3 = buildSingleFrameTag("TIT2", new byte[] {ID3_TEXT_ENCODING_UTF_8});
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    TextInformationFrame textInformationFrame = (TextInformationFrame) metadata.get(0);
    assertThat(textInformationFrame.id).isEqualTo("TIT2");
    assertThat(textInformationFrame.description).isNull();
    assertThat(textInformationFrame.values).containsExactly("");
  }

  @Test
  public void decodeWxxxFrame() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "WXXX",
            new byte[] {
              ID3_TEXT_ENCODING_UTF_8,
              116,
              101,
              115,
              116,
              0,
              104,
              116,
              116,
              112,
              115,
              58,
              47,
              47,
              116,
              101,
              115,
              116,
              46,
              99,
              111,
              109,
              47,
              97,
              98,
              99,
              63,
              100,
              101,
              102
            });
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    UrlLinkFrame urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertThat(urlLinkFrame.id).isEqualTo("WXXX");
    assertThat(urlLinkFrame.description).isEqualTo("test");
    assertThat(urlLinkFrame.url).isEqualTo("https://test.com/abc?def");
  }

  @Test
  public void decodeWxxxFrame_empty() {
    byte[] rawId3 = buildSingleFrameTag("WXXX", new byte[0]);
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(0);
  }

  @Test
  public void decodeWxxxFrame_encodingByteOnly() {
    byte[] rawId3 = buildSingleFrameTag("WXXX", new byte[] {ID3_TEXT_ENCODING_UTF_8});
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    UrlLinkFrame urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertThat(urlLinkFrame.id).isEqualTo("WXXX");
    assertThat(urlLinkFrame.description).isEmpty();
    assertThat(urlLinkFrame.url).isEmpty();
  }

  @Test
  public void decodeUrlLinkFrame() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "WCOM",
            new byte[] {
              104, 116, 116, 112, 115, 58, 47, 47, 116, 101, 115, 116, 46, 99, 111, 109, 47, 97, 98,
              99, 63, 100, 101, 102
            });
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertThat(metadata.length()).isEqualTo(1);
    UrlLinkFrame urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertThat(urlLinkFrame.id).isEqualTo("WCOM");
    assertThat(urlLinkFrame.description).isNull();
    assertThat(urlLinkFrame.url).isEqualTo("https://test.com/abc?def");
  }

  @Test
  public void decodeUrlLinkFrame_empty() {
    byte[] rawId3 = buildSingleFrameTag("WCOM", new byte[0]);
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    UrlLinkFrame urlLinkFrame = (UrlLinkFrame) metadata.get(0);
    assertThat(urlLinkFrame.id).isEqualTo("WCOM");
    assertThat(urlLinkFrame.description).isNull();
    assertThat(urlLinkFrame.url).isEmpty();
  }

  @Test
  public void decodePrivFrame() {
    byte[] rawId3 = buildSingleFrameTag("PRIV", new byte[] {116, 101, 115, 116, 0, 1, 2, 3, 4});
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertThat(metadata.length()).isEqualTo(1);
    PrivFrame privFrame = (PrivFrame) metadata.get(0);
    assertThat(privFrame.owner).isEqualTo("test");
    assertThat(privFrame.privateData).isEqualTo(new byte[] {1, 2, 3, 4});
  }

  @Test
  public void decodePrivFrame_empty() {
    byte[] rawId3 = buildSingleFrameTag("PRIV", new byte[0]);
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    PrivFrame privFrame = (PrivFrame) metadata.get(0);
    assertThat(privFrame.owner).isEmpty();
    assertThat(privFrame.privateData).isEqualTo(new byte[0]);
  }

  @Test
  public void decodeApicFrame() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "APIC",
            new byte[] {
              3, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 16, 72, 101, 108, 108, 111, 32,
              87, 111, 114, 108, 100, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0
            });
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertThat(metadata.length()).isEqualTo(1);
    ApicFrame apicFrame = (ApicFrame) metadata.get(0);
    assertThat(apicFrame.mimeType).isEqualTo("image/jpeg");
    assertThat(apicFrame.pictureType).isEqualTo(16);
    assertThat(apicFrame.description).isEqualTo("Hello World");
    assertThat(apicFrame.pictureData).hasLength(10);
    assertThat(apicFrame.pictureData).isEqualTo(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
  }

  @Test
  public void decodeApicFrame_utf16DescriptionEvenOffset() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "APIC",
            new byte[] {
              1, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 16, 0, 72, 0, 101, 0, 108, 0,
              108, 0, 111, 0, 32, 0, 87, 0, 111, 0, 114, 0, 108, 0, 100, 0, 0, 1, 2, 3, 4, 5, 6, 7,
              8, 9, 0
            });
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    ApicFrame apicFrame = (ApicFrame) metadata.get(0);
    assertThat(apicFrame.mimeType).isEqualTo("image/jpeg");
    assertThat(apicFrame.pictureType).isEqualTo(16);
    assertThat(apicFrame.description).isEqualTo("Hello World");
    assertThat(apicFrame.pictureData).hasLength(10);
    assertThat(apicFrame.pictureData).isEqualTo(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
  }

  @Test
  public void decodeApicFrame_utf16DescriptionOddOffset() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "APIC",
            new byte[] {
              1, 105, 109, 97, 103, 101, 47, 112, 110, 103, 0, 16, 0, 72, 0, 101, 0, 108, 0, 108, 0,
              111, 0, 32, 0, 87, 0, 111, 0, 114, 0, 108, 0, 100, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0
            });
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    ApicFrame apicFrame = (ApicFrame) metadata.get(0);
    assertThat(apicFrame.mimeType).isEqualTo("image/png");
    assertThat(apicFrame.pictureType).isEqualTo(16);
    assertThat(apicFrame.description).isEqualTo("Hello World");
    assertThat(apicFrame.pictureData).hasLength(10);
    assertThat(apicFrame.pictureData).isEqualTo(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
  }

  @Test
  public void decodeCommentFrame() {
    byte[] rawId3 =
        buildSingleFrameTag(
            "COMM",
            new byte[] {
              ID3_TEXT_ENCODING_UTF_8,
              101,
              110,
              103,
              100,
              101,
              115,
              99,
              114,
              105,
              112,
              116,
              105,
              111,
              110,
              0,
              116,
              101,
              120,
              116,
              0
            });
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertThat(metadata.length()).isEqualTo(1);
    CommentFrame commentFrame = (CommentFrame) metadata.get(0);
    assertThat(commentFrame.language).isEqualTo("eng");
    assertThat(commentFrame.description).isEqualTo("description");
    assertThat(commentFrame.text).isEqualTo("text");
  }

  @Test
  public void decodeCommentFrame_empty() {
    byte[] rawId3 = buildSingleFrameTag("COMM", new byte[0]);
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(0);
  }

  @Test
  public void decodeCommentFrame_languageOnly() {
    byte[] rawId3 =
        buildSingleFrameTag("COMM", new byte[] {ID3_TEXT_ENCODING_UTF_8, 101, 110, 103});
    Id3Decoder decoder = new Id3Decoder();

    Metadata metadata = decoder.decode(rawId3, rawId3.length);

    assertThat(metadata.length()).isEqualTo(1);
    CommentFrame commentFrame = (CommentFrame) metadata.get(0);
    assertThat(commentFrame.language).isEqualTo("eng");
    assertThat(commentFrame.description).isEmpty();
    assertThat(commentFrame.text).isEmpty();
  }

  @Test
  public void decodeMultiFrames() {
    byte[] rawId3 =
        buildMultiFramesTag(
            new FrameSpec(
                "COMM",
                new byte[] {
                  3, 101, 110, 103, 100, 101, 115, 99, 114, 105, 112, 116, 105, 111, 110, 0, 116,
                  101, 120, 116, 0
                }),
            new FrameSpec(
                "APIC",
                new byte[] {
                  3, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 16, 72, 101, 108, 108, 111,
                  32, 87, 111, 114, 108, 100, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0
                }));
    Id3Decoder decoder = new Id3Decoder();
    Metadata metadata = decoder.decode(rawId3, rawId3.length);
    assertThat(metadata.length()).isEqualTo(2);
    CommentFrame commentFrame = (CommentFrame) metadata.get(0);
    ApicFrame apicFrame = (ApicFrame) metadata.get(1);

    assertThat(commentFrame.language).isEqualTo("eng");
    assertThat(commentFrame.description).isEqualTo("description");
    assertThat(commentFrame.text).isEqualTo("text");

    assertThat(apicFrame.mimeType).isEqualTo("image/jpeg");
    assertThat(apicFrame.pictureType).isEqualTo(16);
    assertThat(apicFrame.description).isEqualTo("Hello World");
    assertThat(apicFrame.pictureData).hasLength(10);
    assertThat(apicFrame.pictureData).isEqualTo(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
  }

  @Test
  public void decodeFailsIfPositionNonZero() {
    Id3Decoder decoder = new Id3Decoder();
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data.position(1);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  @Test
  public void decodeFailsIfBufferHasNoArray() {
    Id3Decoder decoder = new Id3Decoder();
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data = buffer.data.asReadOnlyBuffer();

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  @Test
  public void decodeFailsIfArrayOffsetNonZero() {
    Id3Decoder decoder = new Id3Decoder();
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data.position(1);
    buffer.data = buffer.data.slice();

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  public static byte[] buildSingleFrameTag(String frameId, byte[] frameData) {
    return buildMultiFramesTag(new FrameSpec(frameId, frameData));
  }

  public static byte[] buildMultiFramesTag(FrameSpec... frames) {
    int totalLength = TAG_HEADER.length;
    for (FrameSpec frame : frames) {
      byte[] frameData = frame.frameData;
      totalLength += FRAME_HEADER_LENGTH + frameData.length;
    }
    byte[] tagData = Arrays.copyOf(TAG_HEADER, totalLength);
    // Fill in the size part of the tag header.
    int offset = TAG_HEADER.length - 4;
    int tagSize = totalLength - TAG_HEADER.length;
    tagData[offset++] = (byte) ((tagSize >> 21) & 0x7F);
    tagData[offset++] = (byte) ((tagSize >> 14) & 0x7F);
    tagData[offset++] = (byte) ((tagSize >> 7) & 0x7F);
    tagData[offset++] = (byte) (tagSize & 0x7F);

    for (FrameSpec frame : frames) {
      byte[] frameData = frame.frameData;
      String frameId = frame.frameId;
      byte[] frameIdBytes = frameId.getBytes(Charsets.UTF_8);
      Assertions.checkState(frameIdBytes.length == 4);

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
      offset += frameData.length;
    }
    return tagData;
  }

  /** Specify an ID3 frame. */
  public static final class FrameSpec {
    public final String frameId;
    public final byte[] frameData;

    public FrameSpec(String frameId, byte[] frameData) {
      this.frameId = frameId;
      this.frameData = frameData;
    }
  }

  /** Converts an array of integers in the range [0, 255] into an equivalent byte array. */
  // TODO(internal b/161804035): Move to a single file.
  private static byte[] createByteArray(int... bytes) {
    byte[] byteArray = new byte[bytes.length];
    for (int i = 0; i < byteArray.length; i++) {
      Assertions.checkState(0x00 <= bytes[i] && bytes[i] <= 0xFF);
      byteArray[i] = (byte) bytes[i];
    }
    return byteArray;
  }

  /**
   * Create a new {@link MetadataInputBuffer} and copy {@code data} into the backing {@link
   * ByteBuffer}.
   */
  // TODO(internal b/161804035): Use TestUtils when it's available in a dependency we can use here.
  private static MetadataInputBuffer createMetadataInputBuffer(byte[] data) {
    MetadataInputBuffer buffer = new MetadataInputBuffer();
    buffer.data = ByteBuffer.allocate(data.length).put(data);
    buffer.data.flip();
    return buffer;
  }
}
