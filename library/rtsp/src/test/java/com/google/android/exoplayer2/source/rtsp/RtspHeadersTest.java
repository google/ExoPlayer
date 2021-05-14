/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspHeaders}. */
@RunWith(AndroidJUnit4.class)
public final class RtspHeadersTest {

  @Test
  public void build_withHeaderLines() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "Accept: application/sdp ", // Extra space after header value.
                    "CSeq:3", // No space after colon.
                    "Content-Length: 707",
                    "Transport: RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();

    assertThat(headers.get("Accept")).isEqualTo("application/sdp");
    assertThat(headers.get("CSeq")).isEqualTo("3");
    assertThat(headers.get("Content-Length")).isEqualTo("707");
    assertThat(headers.get("Transport")).isEqualTo("RTP/AVP;unicast;client_port=65458-65459");
  }

  @Test
  public void build_withHeaderLinesAsMap() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableMap.of(
                    "Accept", " application/sdp ", // Extra space after header value.
                    "CSeq", "3", // No space after colon.
                    "Content-Length", "707",
                    "Transport", "RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();

    assertThat(headers.get("Accept")).isEqualTo("application/sdp");
    assertThat(headers.get("CSeq")).isEqualTo("3");
    assertThat(headers.get("Content-Length")).isEqualTo("707");
    assertThat(headers.get("Transport")).isEqualTo("RTP/AVP;unicast;client_port=65458-65459");
  }

  @Test
  public void get_getsHeaderValuesCaseInsensitively() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "ACCEPT: application/sdp ", // Extra space after header value.
                    "Cseq:3", // No space after colon.
                    "Content-LENGTH: 707",
                    "transport: RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();

    assertThat(headers.get("Accept")).isEqualTo("application/sdp");
    assertThat(headers.get("CSeq")).isEqualTo("3");
    assertThat(headers.get("Content-Length")).isEqualTo("707");
    assertThat(headers.get("Transport")).isEqualTo("RTP/AVP;unicast;client_port=65458-65459");
  }

  @Test
  public void asMap() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "Accept: application/sdp ", // Extra space after header value.
                    "CSeq:3", // No space after colon.
                    "Content-Length: 707",
                    "Transport: RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();
    assertThat(headers.asMap())
        .containsExactly(
            "Accept", "application/sdp",
            "CSeq", "3",
            "Content-Length", "707",
            "Transport", "RTP/AVP;unicast;client_port=65458-65459");
  }
}
