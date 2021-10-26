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
package androidx.media3.exoplayer.rtsp;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
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
  public void buildUpon_createEqualHeaders() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableMap.of(
                    "Content-Length", "707",
                    "Transport", "RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();

    assertThat(headers.buildUpon().build()).isEqualTo(headers);
  }

  @Test
  public void buildUpon_buildsUponExistingHeaders() {
    RtspHeaders headers = new RtspHeaders.Builder().add("Content-Length", "707").build();

    assertThat(headers.buildUpon().add("Content-Encoding", "utf-8").build())
        .isEqualTo(
            new RtspHeaders.Builder()
                .add("Content-Length", "707")
                .add("Content-Encoding", "utf-8")
                .build());
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
  public void get_withMultipleValuesMappedToTheSameName_getsTheMostRecentValue() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "WWW-Authenticate: Digest realm=\"2857be52f47f\","
                        + " nonce=\"f4cba07ad14b5bf181ac77c5a92ba65f\", stale=\"FALSE\"",
                    "WWW-Authenticate: Basic realm=\"2857be52f47f\""))
            .build();

    assertThat(headers.get("WWW-Authenticate")).isEqualTo("Basic realm=\"2857be52f47f\"");
  }

  @Test
  public void values_withNoHeaders_returnsAnEmptyList() {
    RtspHeaders headers = new RtspHeaders.Builder().build();

    assertThat(headers.values("WWW-Authenticate")).isEmpty();
  }

  @Test
  public void values_withMultipleValuesMappedToTheSameName_returnsAllMappedValues() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "WWW-Authenticate: Digest realm=\"2857be52f47f\","
                        + " nonce=\"f4cba07ad14b5bf181ac77c5a92ba65f\", stale=\"FALSE\"",
                    "WWW-Authenticate: Basic realm=\"2857be52f47f\""))
            .build();

    assertThat(headers.values("WWW-Authenticate"))
        .containsExactly(
            "Digest realm=\"2857be52f47f\", nonce=\"f4cba07ad14b5bf181ac77c5a92ba65f\","
                + " stale=\"FALSE\"",
            "Basic realm=\"2857be52f47f\"")
        .inOrder();
  }

  @Test
  public void asMultiMap_withoutValuesMappedToTheSameName_getsTheMappedValuesInAdditionOrder() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "Accept: application/sdp ", // Extra space after header value.
                    "CSeq:3", // No space after colon.
                    "Content-Length: 707",
                    "Transport: RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();
    assertThat(headers.asMultiMap())
        .containsExactly(
            "Accept", "application/sdp",
            "CSeq", "3",
            "Content-Length", "707",
            "Transport", "RTP/AVP;unicast;client_port=65458-65459");
  }

  @Test
  public void
      asMultiMap_withMultipleValuesMappedToTheSameName_getsTheMappedValuesInAdditionOrder() {
    RtspHeaders headers =
        new RtspHeaders.Builder()
            .addAll(
                ImmutableList.of(
                    "Accept: application/sdp ", // Extra space after header value.
                    "Accept: application/sip ", // Extra space after header value.
                    "CSeq:3", // No space after colon.
                    "CSeq:5", // No space after colon.
                    "Transport: RTP/AVP;unicast;client_port=65456-65457",
                    "Transport: RTP/AVP;unicast;client_port=65458-65459\r\n"))
            .build();
    ListMultimap<String, String> headersMap = headers.asMultiMap();

    assertThat(headersMap.keySet())
        .containsExactly(RtspHeaders.ACCEPT, RtspHeaders.CSEQ, RtspHeaders.TRANSPORT)
        .inOrder();
    assertThat(headersMap)
        .valuesForKey(RtspHeaders.ACCEPT)
        .containsExactly("application/sdp", "application/sip")
        .inOrder();
    assertThat(headersMap).valuesForKey(RtspHeaders.CSEQ).containsExactly("3", "5").inOrder();
    assertThat(headersMap)
        .valuesForKey(RtspHeaders.TRANSPORT)
        .containsExactly(
            "RTP/AVP;unicast;client_port=65456-65457", "RTP/AVP;unicast;client_port=65458-65459")
        .inOrder();
  }
}
