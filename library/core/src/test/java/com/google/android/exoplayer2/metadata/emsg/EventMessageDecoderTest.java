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
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Test for {@link EventMessageDecoder}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class EventMessageDecoderTest {

  @Test
  public void testDecodeEventMessage() {
    byte[] rawEmsgBody = new byte[] {
        117, 114, 110, 58, 116, 101, 115, 116, 0, // scheme_id_uri = "urn:test"
        49, 50, 51, 0, // value = "123"
        0, 0, -69, -128, // timescale = 48000
        0, 0, 0, 0, // presentation_time_delta (ignored) = 0
        0, 2, 50, -128, // event_duration = 144000
        0, 15, 67, -45, // id = 1000403
        0, 1, 2, 3, 4}; // message_data = {0, 1, 2, 3, 4}
    EventMessageDecoder decoder = new EventMessageDecoder();
    MetadataInputBuffer buffer = new MetadataInputBuffer();
    buffer.data = ByteBuffer.allocate(rawEmsgBody.length).put(rawEmsgBody);
    Metadata metadata = decoder.decode(buffer);
    assertThat(metadata.length()).isEqualTo(1);
    EventMessage eventMessage = (EventMessage) metadata.get(0);
    assertThat(eventMessage.schemeIdUri).isEqualTo("urn:test");
    assertThat(eventMessage.value).isEqualTo("123");
    assertThat(eventMessage.durationMs).isEqualTo(3000);
    assertThat(eventMessage.id).isEqualTo(1000403);
    assertThat(eventMessage.messageData).isEqualTo(new byte[]{0, 1, 2, 3, 4});
  }

}
