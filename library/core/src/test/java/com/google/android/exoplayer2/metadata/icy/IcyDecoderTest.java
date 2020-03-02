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
package com.google.android.exoplayer2.metadata.icy;

import static com.google.android.exoplayer2.testutil.TestUtil.createByteArray;
import static com.google.android.exoplayer2.testutil.TestUtil.createMetadataInputBuffer;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.testutil.TestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link IcyDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class IcyDecoderTest {

  private final IcyDecoder decoder = new IcyDecoder();

  @Test
  public void decode() {
    byte[] icyContent = "StreamTitle='test title';StreamURL='test_url';".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("test title");
    assertThat(streamInfo.url).isEqualTo("test_url");
  }

  @Test
  // Check the decoder is reading MetadataInputBuffer.data.limit() correctly.
  public void decode_respectsLimit() {
    byte[] icyTitle = "StreamTitle='test title';".getBytes(UTF_8);
    byte[] icyUrl = "StreamURL='test_url';".getBytes(UTF_8);
    byte[] paddedRawBytes = TestUtil.joinByteArrays(icyTitle, icyUrl);
    MetadataInputBuffer metadataBuffer = createMetadataInputBuffer(paddedRawBytes);
    // Stop before the stream URL.
    metadataBuffer.data.limit(icyTitle.length);
    Metadata metadata = decoder.decode(metadataBuffer);

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyTitle);
    assertThat(streamInfo.title).isEqualTo("test title");
    assertThat(streamInfo.url).isNull();
  }

  @Test
  public void decode_titleOnly() {
    byte[] icyContent = "StreamTitle='test title';".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("test title");
    assertThat(streamInfo.url).isNull();
  }

  @Test
  public void decode_extraTags() {
    byte[] icyContent =
        "StreamTitle='test title';StreamURL='test_url';CustomTag|withWeirdSeparator"
            .getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("test title");
    assertThat(streamInfo.url).isEqualTo("test_url");
  }

  @Test
  public void decode_emptyTitle() {
    byte[] icyContent = "StreamTitle='';StreamURL='test_url';".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEmpty();
    assertThat(streamInfo.url).isEqualTo("test_url");
  }

  @Test
  public void decode_semiColonInTitle() {
    byte[] icyContent = "StreamTitle='test; title';StreamURL='test_url';".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("test; title");
    assertThat(streamInfo.url).isEqualTo("test_url");
  }

  @Test
  public void decode_quoteInTitle() {
    byte[] icyContent = "StreamTitle='test' title';StreamURL='test_url';".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("test' title");
    assertThat(streamInfo.url).isEqualTo("test_url");
  }

  @Test
  public void decode_lineTerminatorInTitle() {
    byte[] icyContent = "StreamTitle='test\r\ntitle';StreamURL='test_url';".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("test\r\ntitle");
    assertThat(streamInfo.url).isEqualTo("test_url");
  }

  @Test
  public void decode_iso885911() {
    // Create an invalid UTF-8 string by using 'é'.
    byte[] icyContent = "StreamTitle='tést';StreamURL='tést_url';".getBytes(ISO_8859_1);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isEqualTo("tést");
    assertThat(streamInfo.url).isEqualTo("tést_url");
  }

  @Test
  public void decode_unrecognisedEncoding() {
    // Create an invalid UTF-8 and ISO-88591-1 string by using 'é'.
    byte[] icyContent = "StreamTitle='tést';StreamURL='tést_url';".getBytes(UTF_16);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isNull();
    assertThat(streamInfo.url).isNull();
  }

  @Test
  public void decode_noRecognisedHeaders() {
    byte[] icyContent = "NotIcyData".getBytes(UTF_8);

    Metadata metadata = decoder.decode(createMetadataInputBuffer(icyContent));

    assertThat(metadata.length()).isEqualTo(1);
    IcyInfo streamInfo = (IcyInfo) metadata.get(0);
    assertThat(streamInfo.rawMetadata).isEqualTo(icyContent);
    assertThat(streamInfo.title).isNull();
    assertThat(streamInfo.url).isNull();
  }

  @Test
  public void decode_failsIfPositionNonZero() {
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data.position(1);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  @Test
  public void decode_failsIfBufferHasNoArray() {
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data = buffer.data.asReadOnlyBuffer();

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  @Test
  public void decode_failsIfArrayOffsetNonZero() {
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data.position(1);
    buffer.data = buffer.data.slice();

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }
}
