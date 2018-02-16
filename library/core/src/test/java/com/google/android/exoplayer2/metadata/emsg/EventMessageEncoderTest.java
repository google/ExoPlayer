/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.metadata.emsg;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit test for {@link EventMessageEncoder}.
 */
@RunWith(RobolectricTestRunner.class)
public final class EventMessageEncoderTest {

  @Test
  public void testEncodeEventStream() throws IOException {
    EventMessage eventMessage = new EventMessage("urn:test", "123", 3000, 1000403,
        new byte[] {0, 1, 2, 3, 4}, 1000000);
    byte[] expectedEmsgBody = new byte[] {
        117, 114, 110, 58, 116, 101, 115, 116, 0, // scheme_id_uri = "urn:test"
        49, 50, 51, 0, // value = "123"
        0, 0, -69, -128, // timescale = 48000
        0, 0, -69, -128, // presentation_time_delta = 48000
        0, 2, 50, -128, // event_duration = 144000
        0, 15, 67, -45, // id = 1000403
        0, 1, 2, 3, 4}; // message_data = {0, 1, 2, 3, 4}
    byte[] encodedByteArray = new EventMessageEncoder().encode(eventMessage, 48000);
    assertThat(encodedByteArray).isEqualTo(expectedEmsgBody);
  }

  @Test
  public void testEncodeDecodeEventStream() throws IOException {
    EventMessage expectedEmsg = new EventMessage("urn:test", "123", 3000, 1000403,
        new byte[] {0, 1, 2, 3, 4}, 1000000);
    byte[] encodedByteArray = new EventMessageEncoder().encode(expectedEmsg, 48000);
    MetadataInputBuffer buffer = new MetadataInputBuffer();
    buffer.data = ByteBuffer.allocate(encodedByteArray.length).put(encodedByteArray);

    EventMessageDecoder decoder = new EventMessageDecoder();
    Metadata metadata = decoder.decode(buffer);
    assertThat(metadata.length()).isEqualTo(1);
    assertThat(metadata.get(0)).isEqualTo(expectedEmsg);
  }

  @Test
  public void testEncodeEventStreamMultipleTimesWorkingCorrectly() throws IOException {
    EventMessage eventMessage = new EventMessage("urn:test", "123", 3000, 1000403,
        new byte[] {0, 1, 2, 3, 4}, 1000000);
    byte[] expectedEmsgBody = new byte[] {
        117, 114, 110, 58, 116, 101, 115, 116, 0, // scheme_id_uri = "urn:test"
        49, 50, 51, 0, // value = "123"
        0, 0, -69, -128, // timescale = 48000
        0, 0, -69, -128, // presentation_time_delta = 48000
        0, 2, 50, -128, // event_duration = 144000
        0, 15, 67, -45, // id = 1000403
        0, 1, 2, 3, 4}; // message_data = {0, 1, 2, 3, 4}
    EventMessage eventMessage1 = new EventMessage("urn:test", "123", 3000, 1000402,
        new byte[] {4, 3, 2, 1, 0}, 1000000);
    byte[] expectedEmsgBody1 = new byte[] {
        117, 114, 110, 58, 116, 101, 115, 116, 0, // scheme_id_uri = "urn:test"
        49, 50, 51, 0, // value = "123"
        0, 0, -69, -128, // timescale = 48000
        0, 0, -69, -128, // presentation_time_delta = 48000
        0, 2, 50, -128, // event_duration = 144000
        0, 15, 67, -46, // id = 1000402
        4, 3, 2, 1, 0}; // message_data = {4, 3, 2, 1, 0}
    EventMessageEncoder eventMessageEncoder = new EventMessageEncoder();
    byte[] encodedByteArray = eventMessageEncoder.encode(eventMessage, 48000);
    assertThat(encodedByteArray).isEqualTo(expectedEmsgBody);
    byte[] encodedByteArray1 = eventMessageEncoder.encode(eventMessage1, 48000);
    assertThat(encodedByteArray1).isEqualTo(expectedEmsgBody1);
  }

}
