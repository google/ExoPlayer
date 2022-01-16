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
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.source.rtsp.RtspMessageUtil.RtspAuthUserInfo;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspMessageUtil}. */
@RunWith(AndroidJUnit4.class)
public final class RtspMessageUtilTest {

  @Test
  public void parseRequest_withOptionsRequest_succeeds() {
    List<String> requestLines =
        Arrays.asList(
            "OPTIONS rtsp://localhost:554/foo.bar RTSP/1.0",
            "CSeq: 2",
            "User-Agent: LibVLC/3.0.11",
            "");
    RtspRequest request = RtspMessageUtil.parseRequest(requestLines);

    assertThat(request.method).isEqualTo(RtspRequest.METHOD_OPTIONS);
    assertThat(request.headers.asMultiMap())
        .containsExactly(
            RtspHeaders.CSEQ, "2",
            RtspHeaders.USER_AGENT, "LibVLC/3.0.11");
    assertThat(request.messageBody).isEmpty();
  }

  @Test
  public void parseResponse_withOptionsResponse_succeeds() {
    List<String> responseLines =
        Arrays.asList(
            "RTSP/1.0 200 OK",
            "CSeq: 2",
            "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER",
            "");
    RtspResponse response = RtspMessageUtil.parseResponse(responseLines);

    assertThat(response.status).isEqualTo(200);
    assertThat(response.headers.asMultiMap())
        .containsExactly(
            RtspHeaders.CSEQ,
            "2",
            RtspHeaders.PUBLIC,
            "OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER," + " SET_PARAMETER");
    assertThat(response.messageBody).isEmpty();
  }

  @Test
  public void parseRequest_withDescribeRequest_succeeds() {
    List<String> requestLines =
        Arrays.asList(
            "DESCRIBE rtsp://localhost:554/foo.bar RTSP/1.0",
            "CSeq: 3",
            "User-Agent: LibVLC/3.0.11",
            "Accept: application/sdp",
            "");
    RtspRequest request = RtspMessageUtil.parseRequest(requestLines);

    assertThat(request.method).isEqualTo(RtspRequest.METHOD_DESCRIBE);
    assertThat(request.headers.asMultiMap())
        .containsExactly(
            RtspHeaders.CSEQ, "3",
            RtspHeaders.USER_AGENT, "LibVLC/3.0.11",
            RtspHeaders.ACCEPT, "application/sdp");
    assertThat(request.messageBody).isEmpty();
  }

  @Test
  public void parseResponse_withDescribeResponse_succeeds() {
    List<String> responseLines =
        Arrays.asList(
            "RTSP/1.0 200 OK",
            "CSeq: 3",
            "Content-Base: rtsp://127.0.0.1/test.mkv/",
            "Content-Type: application/sdp",
            "Content-Length: 707",
            "",
            "v=0",
            "o=- 1606776316530225 1 IN IP4 192.168.2.176",
            "i=imax.mkv",
            "m=video 0 RTP/AVP 96",
            "a=rtpmap:96 H264/90000",
            "a=control:track1",
            "m=audio 0 RTP/AVP 97",
            "a=rtpmap:97 AC3/48000",
            "a=control:track2");
    RtspResponse response = RtspMessageUtil.parseResponse(responseLines);

    assertThat(response.status).isEqualTo(200);
    assertThat(response.headers.asMultiMap())
        .containsExactly(
            RtspHeaders.CSEQ, "3",
            RtspHeaders.CONTENT_BASE, "rtsp://127.0.0.1/test.mkv/",
            RtspHeaders.CONTENT_TYPE, "application/sdp",
            RtspHeaders.CONTENT_LENGTH, "707");

    assertThat(response.messageBody)
        .isEqualTo(
            "v=0\r\n"
                + "o=- 1606776316530225 1 IN IP4 192.168.2.176\r\n"
                + "i=imax.mkv\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:track1\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=rtpmap:97 AC3/48000\r\n"
                + "a=control:track2");
  }

  @Test
  public void parseResponse_with401DescribeResponse_succeeds() {
    List<String> responseLines =
        Arrays.asList(
            "RTSP/1.0 401 Unauthorized",
            "CSeq: 3",
            "WWW-Authenticate: BASIC realm=\"wow\"",
            "WWW-Authenticate: DIGEST realm=\"wow\", nonce=\"nonce\"",
            "");
    RtspResponse response = RtspMessageUtil.parseResponse(responseLines);
    ListMultimap<String, String> headersMap = response.headers.asMultiMap();

    assertThat(response.status).isEqualTo(401);

    assertThat(headersMap.keySet())
        .containsExactly(RtspHeaders.CSEQ, RtspHeaders.WWW_AUTHENTICATE)
        .inOrder();
    assertThat(headersMap).valuesForKey(RtspHeaders.CSEQ).containsExactly("3");
    assertThat(headersMap)
        .valuesForKey(RtspHeaders.WWW_AUTHENTICATE)
        .containsExactly("BASIC realm=\"wow\"", "DIGEST realm=\"wow\", nonce=\"nonce\"")
        .inOrder();

    assertThat(response.messageBody).isEmpty();
  }

  @Test
  public void parseRequest_withSetParameterRequest_succeeds() {
    List<String> requestLines =
        Arrays.asList(
            "SET_PARAMETER rtsp://localhost:554/foo.bar RTSP/1.0",
            "CSeq: 3",
            "User-Agent: LibVLC/3.0.11",
            "Content-Length: 20",
            "Content-Type: text/parameters",
            "",
            "param: stuff");
    RtspRequest request = RtspMessageUtil.parseRequest(requestLines);

    assertThat(request.method).isEqualTo(RtspRequest.METHOD_SET_PARAMETER);
    assertThat(request.headers.asMultiMap())
        .containsExactly(
            RtspHeaders.CSEQ, "3",
            RtspHeaders.USER_AGENT, "LibVLC/3.0.11",
            RtspHeaders.CONTENT_LENGTH, "20",
            RtspHeaders.CONTENT_TYPE, "text/parameters");
    assertThat(request.messageBody).isEqualTo("param: stuff");
  }

  @Test
  public void parseResponse_withGetParameterResponse_succeeds() {
    List<String> responseLines =
        Arrays.asList(
            "RTSP/1.0 200 OK",
            "CSeq: 431",
            "Content-Length: 46",
            "Content-Type: text/parameters",
            "",
            "packets_received: 10",
            "jitter: 0.3838");
    RtspResponse response = RtspMessageUtil.parseResponse(responseLines);

    assertThat(response.status).isEqualTo(200);
    assertThat(response.headers.asMultiMap())
        .containsExactly(
            RtspHeaders.CSEQ, "431",
            RtspHeaders.CONTENT_LENGTH, "46",
            RtspHeaders.CONTENT_TYPE, "text/parameters");

    assertThat(response.messageBody).isEqualTo("packets_received: 10\r\n" + "jitter: 0.3838");
  }

  @Test
  public void serialize_setupRequest_succeeds() {
    RtspRequest request =
        new RtspRequest(
            Uri.parse("rtsp://127.0.0.1/test.mkv/track1"),
            RtspRequest.METHOD_SETUP,
            new RtspHeaders.Builder()
                .addAll(
                    ImmutableMap.of(
                        RtspHeaders.CSEQ, "4",
                        RtspHeaders.TRANSPORT, "RTP/AVP;unicast;client_port=65458-65459"))
                .build(),
            /* messageBody= */ "");
    List<String> messageLines = RtspMessageUtil.serializeRequest(request);

    List<String> expectedLines =
        Arrays.asList(
            "SETUP rtsp://127.0.0.1/test.mkv/track1 RTSP/1.0",
            "CSeq: 4",
            "Transport: RTP/AVP;unicast;client_port=65458-65459",
            "",
            "");
    String expectedRtspMessage =
        "SETUP rtsp://127.0.0.1/test.mkv/track1 RTSP/1.0\r\n"
            + "CSeq: 4\r\n"
            + "Transport: RTP/AVP;unicast;client_port=65458-65459\r\n"
            + "\r\n";

    assertThat(messageLines).isEqualTo(expectedLines);
    assertThat(RtspMessageUtil.convertMessageToByteArray(messageLines))
        .isEqualTo(expectedRtspMessage.getBytes(RtspMessageChannel.CHARSET));
  }

  @Test
  public void serialize_setupResponse_succeeds() {
    RtspResponse response =
        new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder()
                .addAll(
                    ImmutableMap.of(
                        RtspHeaders.CSEQ,
                        "4",
                        RtspHeaders.TRANSPORT,
                        "RTP/AVP;unicast;client_port=65458-65459;server_port=5354-5355"))
                .build());
    List<String> messageLines = RtspMessageUtil.serializeResponse(response);

    List<String> expectedLines =
        Arrays.asList(
            "RTSP/1.0 200 OK",
            "CSeq: 4",
            "Transport: RTP/AVP;unicast;client_port=65458-65459;server_port=5354-5355",
            "",
            "");
    String expectedRtspMessage =
        "RTSP/1.0 200 OK\r\n"
            + "CSeq: 4\r\n"
            + "Transport: RTP/AVP;unicast;client_port=65458-65459;server_port=5354-5355\r\n"
            + "\r\n";
    assertThat(messageLines).isEqualTo(expectedLines);
    assertThat(RtspMessageUtil.convertMessageToByteArray(messageLines))
        .isEqualTo(expectedRtspMessage.getBytes(RtspMessageChannel.CHARSET));
  }

  @Test
  public void serialize_describeResponse_succeeds() {

    RtspResponse response =
        new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder()
                .addAll(
                    ImmutableMap.of(
                        RtspHeaders.CSEQ, "4",
                        RtspHeaders.CONTENT_BASE, "rtsp://127.0.0.1/test.mkv/",
                        RtspHeaders.CONTENT_TYPE, "application/sdp",
                        RtspHeaders.CONTENT_LENGTH, "707"))
                .build(),
            /* messageBody= */ "v=0\r\n"
                + "o=- 1606776316530225 1 IN IP4 192.168.2.176\r\n"
                + "i=test.mkv\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:track1\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=rtpmap:97 AC3/48000\r\n"
                + "a=control:track2");
    List<String> messageLines = RtspMessageUtil.serializeResponse(response);

    List<String> expectedLines =
        Arrays.asList(
            "RTSP/1.0 200 OK",
            "CSeq: 4",
            "Content-Base: rtsp://127.0.0.1/test.mkv/",
            "Content-Type: application/sdp",
            "Content-Length: 707",
            "",
            "v=0\r\n"
                + "o=- 1606776316530225 1 IN IP4 192.168.2.176\r\n"
                + "i=test.mkv\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:track1\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=rtpmap:97 AC3/48000\r\n"
                + "a=control:track2");

    String expectedRtspMessage =
        "RTSP/1.0 200 OK\r\n"
            + "CSeq: 4\r\n"
            + "Content-Base: rtsp://127.0.0.1/test.mkv/\r\n"
            + "Content-Type: application/sdp\r\n"
            + "Content-Length: 707\r\n"
            + "\r\n"
            + "v=0\r\n"
            + "o=- 1606776316530225 1 IN IP4 192.168.2.176\r\n"
            + "i=test.mkv\r\n"
            + "m=video 0 RTP/AVP 96\r\n"
            + "a=rtpmap:96 H264/90000\r\n"
            + "a=control:track1\r\n"
            + "m=audio 0 RTP/AVP 97\r\n"
            + "a=rtpmap:97 AC3/48000\r\n"
            + "a=control:track2";

    assertThat(messageLines).isEqualTo(expectedLines);
    assertThat(RtspMessageUtil.convertMessageToByteArray(messageLines))
        .isEqualTo(expectedRtspMessage.getBytes(RtspMessageChannel.CHARSET));
  }

  @Test
  public void serialize_requestWithoutCseqHeader_throwsIllegalArgumentException() {
    RtspRequest request =
        new RtspRequest(
            Uri.parse("rtsp://127.0.0.1/test.mkv/track1"),
            RtspRequest.METHOD_OPTIONS,
            RtspHeaders.EMPTY,
            /* messageBody= */ "");

    assertThrows(IllegalArgumentException.class, () -> RtspMessageUtil.serializeRequest(request));
  }

  @Test
  public void serialize_responseWithoutCseqHeader_throwsIllegalArgumentException() {
    RtspResponse response = new RtspResponse(/* status= */ 200, RtspHeaders.EMPTY);

    assertThrows(IllegalArgumentException.class, () -> RtspMessageUtil.serializeResponse(response));
  }

  @Test
  public void isRtspResponse_withSuccessfulRtspResponse_returnsTrue() {
    List<String> responseLines =
        Arrays.asList(
            "RTSP/1.0 200 OK",
            "CSeq: 2",
            "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER",
            "");

    assertThat(RtspMessageUtil.isRtspResponse(responseLines)).isTrue();
  }

  @Test
  public void isRtspResponse_withUnsuccessfulRtspResponse_returnsTrue() {
    List<String> responseLines = Arrays.asList("RTSP/1.0 405 Method Not Allowed", "CSeq: 2", "");

    assertThat(RtspMessageUtil.isRtspResponse(responseLines)).isTrue();
  }

  @Test
  public void isRtspResponse_withRtspRequest_returnsFalse() {
    List<String> requestLines =
        Arrays.asList("OPTIONS rtsp://localhost:554/foo.bar RTSP/1.0", "CSeq: 2", "");

    assertThat(RtspMessageUtil.isRtspResponse(requestLines)).isFalse();
  }

  @Test
  public void serialize_failedResponse_succeeds() {
    RtspResponse response =
        new RtspResponse(
            /* status= */ 454, new RtspHeaders.Builder().add(RtspHeaders.CSEQ, "4").build());
    List<String> messageLines = RtspMessageUtil.serializeResponse(response);

    List<String> expectedLines = Arrays.asList("RTSP/1.0 454 Session Not Found", "CSeq: 4", "", "");
    String expectedRtspMessage = "RTSP/1.0 454 Session Not Found\r\n" + "CSeq: 4\r\n" + "\r\n";

    assertThat(RtspMessageUtil.serializeResponse(response)).isEqualTo(expectedLines);
    assertThat(RtspMessageUtil.convertMessageToByteArray(messageLines))
        .isEqualTo(expectedRtspMessage.getBytes(RtspMessageChannel.CHARSET));
  }

  @Test
  public void parseSessionHeader_withSessionIdContainingSpecialCharacters_succeeds()
      throws Exception {
    String sessionHeaderString = "610a63df-9b57.4856_97ac$665f+56e9c04";
    RtspMessageUtil.RtspSessionHeader sessionHeader =
        RtspMessageUtil.parseSessionHeader(sessionHeaderString);
    assertThat(sessionHeader.sessionId).isEqualTo("610a63df-9b57.4856_97ac$665f+56e9c04");
  }

  @Test
  public void parseSessionHeader_withSessionIdContainingSpecialCharactersAndTimeout_succeeds()
      throws Exception {
    String sessionHeaderString = "610a63df-9b57.4856_97ac$665f+56e9c04;timeout=60";
    RtspMessageUtil.RtspSessionHeader sessionHeader =
        RtspMessageUtil.parseSessionHeader(sessionHeaderString);
    assertThat(sessionHeader.sessionId).isEqualTo("610a63df-9b57.4856_97ac$665f+56e9c04");
    assertThat(sessionHeader.timeoutMs).isEqualTo(60_000);
  }

  @Test
  public void removeUserInfo_withUserInfo() {
    Uri uri = Uri.parse("rtsp://user:pass@foo.bar/foo.mkv");
    assertThat(RtspMessageUtil.removeUserInfo(uri)).isEqualTo(Uri.parse("rtsp://foo.bar/foo.mkv"));
  }

  @Test
  public void removeUserInfo_withUserInfoAndPortNumber() {
    Uri uri = Uri.parse("rtsp://user:pass@foo.bar:5050/foo.mkv");
    assertThat(RtspMessageUtil.removeUserInfo(uri))
        .isEqualTo(Uri.parse("rtsp://foo.bar:5050/foo.mkv"));
  }

  @Test
  public void removeUserInfo_withEmptyUserInfoAndPortNumber() {
    Uri uri = Uri.parse("rtsp://@foo.bar:5050/foo.mkv");
    assertThat(RtspMessageUtil.removeUserInfo(uri))
        .isEqualTo(Uri.parse("rtsp://foo.bar:5050/foo.mkv"));
  }

  @Test
  public void removeUserInfo_withNoUserInfo() {
    Uri uri = Uri.parse("rtsp://foo.bar:5050/foo.mkv");
    assertThat(RtspMessageUtil.removeUserInfo(uri))
        .isEqualTo(Uri.parse("rtsp://foo.bar:5050/foo.mkv"));
  }

  @Test
  public void parseContentLengthHeader_withContentLengthOver31Bits_succeeds() throws Exception {
    String line = "Content-Length: 1000000000000000";
    long contentLength = RtspMessageUtil.parseContentLengthHeader(line);
    assertThat(contentLength).isEqualTo(1000000000000000L);
  }

  @Test
  public void isRtspStartLine_onValidRequestLine_succeeds() {
    assertThat(RtspMessageUtil.isRtspStartLine("OPTIONS rtsp://localhost/test RTSP/1.0")).isTrue();
  }

  @Test
  public void isRtspStartLine_onValidResponseLine_succeeds() {
    assertThat(RtspMessageUtil.isRtspStartLine("RTSP/1.0 456 Header Field Not Valid for Resource"))
        .isTrue();
  }

  @Test
  public void isRtspStartLine_onValidHeaderLine_succeeds() {
    assertThat(RtspMessageUtil.isRtspStartLine("Transport: RTP/AVP;unicast;client_port=1000-1001"))
        .isFalse();
  }

  @Test
  public void extractUserInfo_withoutPassword_returnsNull() {
    @Nullable
    RtspAuthUserInfo authUserInfo =
        RtspMessageUtil.parseUserInfo(Uri.parse("rtsp://username@mediaserver.com/stream1"));

    assertThat(authUserInfo).isNull();
  }

  @Test
  public void extractUserInfo_withoutUserInfo_returnsNull() {
    @Nullable
    RtspAuthUserInfo authUserInfo =
        RtspMessageUtil.parseUserInfo(Uri.parse("rtsp://mediaserver.com/stream1"));
    assertThat(authUserInfo).isNull();
  }

  @Test
  public void extractUserInfo_withProperlyFormattedUri_succeeds() {
    @Nullable
    RtspAuthUserInfo authUserInfo =
        RtspMessageUtil.parseUserInfo(
            Uri.parse("rtsp://username:pass:word@mediaserver.com/stream1"));

    assertThat(authUserInfo).isNotNull();
    assertThat(authUserInfo.username).isEqualTo("username");
    assertThat(authUserInfo.password).isEqualTo("pass:word");
  }

  @Test
  public void parseWWWAuthenticateHeader_withBasicAuthentication_succeeds() throws Exception {
    RtspAuthenticationInfo authenticationInfo =
        RtspMessageUtil.parseWwwAuthenticateHeader("Basic realm=\"Wally - World\"");
    assertThat(authenticationInfo.authenticationMechanism).isEqualTo(RtspAuthenticationInfo.BASIC);
    assertThat(authenticationInfo.nonce).isEmpty();
    assertThat(authenticationInfo.realm).isEqualTo("Wally - World");
  }

  @Test
  public void parseWWWAuthenticateHeader_withDigestAuthenticationWithDomain_succeeds()
      throws Exception {
    RtspAuthenticationInfo authenticationInfo =
        RtspMessageUtil.parseWwwAuthenticateHeader(
            "Digest realm=\"test-realm@host.com\", domain=\"host.com\","
                + " nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
                + " opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");

    assertThat(authenticationInfo.authenticationMechanism).isEqualTo(RtspAuthenticationInfo.DIGEST);
    assertThat(authenticationInfo.nonce).isEqualTo("dcd98b7102dd2f0e8b11d0f600bfb0c093");
    assertThat(authenticationInfo.realm).isEqualTo("test-realm@host.com");
    assertThat(authenticationInfo.opaque).isEmpty();
  }

  @Test
  public void parseWWWAuthenticateHeader_withDigestAuthenticationWithOptionalParameters_succeeds()
      throws Exception {
    RtspAuthenticationInfo authenticationInfo =
        RtspMessageUtil.parseWwwAuthenticateHeader(
            "Digest realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\","
                + " opaque=\"5ccc069c403ebaf9f0171e9517f40e41\", stale=\"stalev\","
                + " algorithm=\"md5\"");

    assertThat(authenticationInfo.authenticationMechanism).isEqualTo(RtspAuthenticationInfo.DIGEST);
    assertThat(authenticationInfo.nonce).isEqualTo("dcd98b7102dd2f0e8b11d0f600bfb0c093");
    assertThat(authenticationInfo.realm).isEqualTo("testrealm@host.com");
    assertThat(authenticationInfo.opaque).isEqualTo("5ccc069c403ebaf9f0171e9517f40e41");
  }

  @Test
  public void parseWWWAuthenticateHeader_withDigestAuthentication_succeeds() throws Exception {
    RtspAuthenticationInfo authenticationInfo =
        RtspMessageUtil.parseWwwAuthenticateHeader(
            "Digest realm=\"RTSP server\", nonce=\"0cdfe9719e7373b7d5bb2913e2115f3f\"");
    assertThat(authenticationInfo.authenticationMechanism).isEqualTo(RtspAuthenticationInfo.DIGEST);
    assertThat(authenticationInfo.nonce).isEqualTo("0cdfe9719e7373b7d5bb2913e2115f3f");
    assertThat(authenticationInfo.realm).isEqualTo("RTSP server");
    assertThat(authenticationInfo.opaque).isEmpty();
  }

  @Test
  public void splitRtspMessageBody_withCrLfLineTerminatorMessageBody_splitsMessageBody() {
    String[] lines = RtspMessageUtil.splitRtspMessageBody("line1\r\nline2\r\nline3");

    assertThat(lines).asList().containsExactly("line1", "line2", "line3").inOrder();
  }

  @Test
  public void splitRtspMessageBody_withLfLineTerminatorMessageBody_splitsMessageBody() {
    String[] lines = RtspMessageUtil.splitRtspMessageBody("line1\nline2\nline3");

    assertThat(lines).asList().containsExactly("line1", "line2", "line3").inOrder();
  }
}
