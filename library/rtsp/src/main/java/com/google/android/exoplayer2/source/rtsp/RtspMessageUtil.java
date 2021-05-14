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

import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_ANNOUNCE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_DESCRIBE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_GET_PARAMETER;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_OPTIONS;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PAUSE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PLAY;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PLAY_NOTIFY;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_RECORD;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_REDIRECT;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_SETUP;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_SET_PARAMETER;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_TEARDOWN;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_UNSET;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility methods for RTSP messages. */
/* package */ final class RtspMessageUtil {
  /** Represents a RTSP Session header (RFC2326 Section 12.37). */
  public static final class RtspSessionHeader {
    /** The session ID. */
    public final String sessionId;
    /**
     * The session timeout, measured in milliseconds, {@link #DEFAULT_RTSP_TIMEOUT_MS} if not
     * specified in the Session header.
     */
    public final long timeoutMs;

    /** Creates a new instance. */
    public RtspSessionHeader(String sessionId, long timeoutMs) {
      this.sessionId = sessionId;
      this.timeoutMs = timeoutMs;
    }
  }

  /** The default timeout, in milliseconds, defined for RTSP (RFC2326 Section 12.37). */
  public static final long DEFAULT_RTSP_TIMEOUT_MS = 60_000;

  // Status line pattern, see RFC2326 Section 6.1.
  private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("([A-Z_]+) (.*) RTSP/1\\.0");

  // Status line pattern, see RFC2326 Section 7.1.
  private static final Pattern STATUS_LINE_PATTERN = Pattern.compile("RTSP/1\\.0 (\\d+) (.+)");

  // Content length header pattern, see RFC2326 Section 12.14.
  private static final Pattern CONTENT_LENGTH_HEADER_PATTERN =
      Pattern.compile("Content-Length:\\s?(\\d+)", CASE_INSENSITIVE);

  // Session header pattern, see RFC2326 Section 12.37.
  private static final Pattern SESSION_HEADER_PATTERN =
      Pattern.compile("(\\w+)(?:;\\s?timeout=(\\d+))?");

  private static final String RTSP_VERSION = "RTSP/1.0";

  /**
   * Serializes an {@link RtspRequest} to an {@link ImmutableList} of strings.
   *
   * @param request The {@link RtspRequest}.
   * @return A list of the lines of the {@link RtspRequest}, without line terminators (CRLF).
   */
  public static ImmutableList<String> serializeRequest(RtspRequest request) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    // Request line.
    builder.add(
        Util.formatInvariant(
            "%s %s %s", toMethodString(request.method), request.uri, RTSP_VERSION));
    ImmutableMap<String, String> headers = request.headers.asMap();
    for (String headerName : headers.keySet()) {
      builder.add(
          Util.formatInvariant(
              "%s: %s", headerName, checkNotNull(request.headers.get(headerName))));
    }
    // Empty line after headers.
    builder.add("");
    builder.add(request.messageBody);
    return builder.build();
  }

  /**
   * Serializes an {@link RtspResponse} to an {@link ImmutableList} of strings.
   *
   * @param response The {@link RtspResponse}.
   * @return A list of the lines of the {@link RtspResponse}, without line terminators (CRLF).
   */
  public static ImmutableList<String> serializeResponse(RtspResponse response) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    // Request line.
    builder.add(
        Util.formatInvariant(
            "%s %s %s", RTSP_VERSION, response.status, getRtspStatusReasonPhrase(response.status)));

    ImmutableMap<String, String> headers = response.headers.asMap();
    for (String headerName : headers.keySet()) {
      builder.add(
          Util.formatInvariant(
              "%s: %s", headerName, checkNotNull(response.headers.get(headerName))));
    }
    // Empty line after headers.
    builder.add("");
    builder.add(response.messageBody);
    return builder.build();
  }

  /**
   * Converts an RTSP message to a byte array.
   *
   * @param message The non-empty list of the lines of an RTSP message, with line terminators
   *     removed.
   */
  public static byte[] convertMessageToByteArray(List<String> message) {
    return Joiner.on("\r\n").join(message).getBytes(Charsets.UTF_8);
  }

  /** Removes the user info from the supplied {@link Uri}. */
  public static Uri removeUserInfo(Uri uri) {
    if (uri.getUserInfo() == null) {
      return uri;
    }

    // The Uri must include a "@" if the user info is non-null.
    String authorityWithUserInfo = checkNotNull(uri.getAuthority());
    checkArgument(authorityWithUserInfo.contains("@"));
    String authority = Util.split(authorityWithUserInfo, "@")[1];
    return uri.buildUpon().encodedAuthority(authority).build();
  }

  /** Returns the corresponding String representation of the {@link RtspRequest.Method} argument. */
  public static String toMethodString(@RtspRequest.Method int method) {
    switch (method) {
      case RtspRequest.METHOD_ANNOUNCE:
        return "ANNOUNCE";
      case METHOD_DESCRIBE:
        return "DESCRIBE";
      case METHOD_GET_PARAMETER:
        return "GET_PARAMETER";
      case METHOD_OPTIONS:
        return "OPTIONS";
      case METHOD_PAUSE:
        return "PAUSE";
      case METHOD_PLAY:
        return "PLAY";
      case METHOD_PLAY_NOTIFY:
        return "PLAY_NOTIFY";
      case METHOD_RECORD:
        return "RECORD";
      case METHOD_REDIRECT:
        return "REDIRECT";
      case METHOD_SETUP:
        return "SETUP";
      case METHOD_SET_PARAMETER:
        return "SET_PARAMETER";
      case METHOD_TEARDOWN:
        return "TEARDOWN";
      case METHOD_UNSET:
      default:
        throw new IllegalStateException();
    }
  }

  @RtspRequest.Method
  private static int parseMethodString(String method) {
    switch (method) {
      case "ANNOUNCE":
        return METHOD_ANNOUNCE;
      case "DESCRIBE":
        return METHOD_DESCRIBE;
      case "GET_PARAMETER":
        return METHOD_GET_PARAMETER;
      case "OPTIONS":
        return METHOD_OPTIONS;
      case "PAUSE":
        return METHOD_PAUSE;
      case "PLAY":
        return METHOD_PLAY;
      case "PLAY_NOTIFY":
        return METHOD_PLAY_NOTIFY;
      case "RECORD":
        return METHOD_RECORD;
      case "REDIRECT":
        return METHOD_REDIRECT;
      case "SETUP":
        return METHOD_SETUP;
      case "SET_PARAMETER":
        return METHOD_SET_PARAMETER;
      case "TEARDOWN":
        return METHOD_TEARDOWN;
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * Parses lines of a received RTSP response into an {@link RtspResponse} instance.
   *
   * @param lines The non-empty list of received lines, with line terminators removed.
   * @return The parsed {@link RtspResponse} object.
   */
  public static RtspResponse parseResponse(List<String> lines) {
    Matcher statusLineMatcher = STATUS_LINE_PATTERN.matcher(lines.get(0));
    checkArgument(statusLineMatcher.matches());

    int statusCode = Integer.parseInt(checkNotNull(statusLineMatcher.group(1)));
    // An empty line marks the boundary between header and body.
    int messageBodyOffset = lines.indexOf("");
    checkArgument(messageBodyOffset > 0);

    List<String> headerLines = lines.subList(1, messageBodyOffset);
    RtspHeaders headers = new RtspHeaders.Builder().addAll(headerLines).build();

    String messageBody = Joiner.on("\r\n").join(lines.subList(messageBodyOffset + 1, lines.size()));
    return new RtspResponse(statusCode, headers, messageBody);
  }

  /**
   * Parses lines of a received RTSP request into an {@link RtspRequest} instance.
   *
   * @param lines The non-empty list of received lines, with line terminators removed.
   * @return The parsed {@link RtspRequest} object.
   */
  public static RtspRequest parseRequest(List<String> lines) {
    Matcher requestMatcher = REQUEST_LINE_PATTERN.matcher(lines.get(0));
    checkArgument(requestMatcher.matches());

    @RtspRequest.Method int method = parseMethodString(checkNotNull(requestMatcher.group(1)));
    Uri requestUri = Uri.parse(checkNotNull(requestMatcher.group(2)));
    // An empty line marks the boundary between header and body.
    int messageBodyOffset = lines.indexOf("");
    checkArgument(messageBodyOffset > 0);

    List<String> headerLines = lines.subList(1, messageBodyOffset);
    RtspHeaders headers = new RtspHeaders.Builder().addAll(headerLines).build();

    String messageBody = Joiner.on("\r\n").join(lines.subList(messageBodyOffset + 1, lines.size()));
    return new RtspRequest(requestUri, method, headers, messageBody);
  }

  /** Returns whether the line is a valid RTSP start line. */
  public static boolean isRtspStartLine(String line) {
    return REQUEST_LINE_PATTERN.matcher(line).matches()
        || STATUS_LINE_PATTERN.matcher(line).matches();
  }

  /**
   * Returns the length in bytes if the line contains a Content-Length header, otherwise {@link
   * C#LENGTH_UNSET}.
   *
   * @throws ParserException If Content-Length cannot be parsed to an integer.
   */
  public static long parseContentLengthHeader(String line) throws ParserException {
    try {
      Matcher matcher = CONTENT_LENGTH_HEADER_PATTERN.matcher(line);
      if (matcher.find()) {
        return Long.parseLong(checkNotNull(matcher.group(1)));
      } else {
        return C.LENGTH_UNSET;
      }
    } catch (NumberFormatException e) {
      throw new ParserException(e);
    }
  }

  /**
   * Parses the RTSP PUBLIC header into a list of RTSP methods.
   *
   * @param publicHeader The PUBLIC header content, null if not available.
   * @return The list of supported RTSP methods, encoded in {@link RtspRequest.Method}, or an empty
   *     list if the PUBLIC header is null.
   */
  public static ImmutableList<Integer> parsePublicHeader(@Nullable String publicHeader) {
    if (publicHeader == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Integer> methodListBuilder = new ImmutableList.Builder<>();
    for (String method : Util.split(publicHeader, ",\\s?")) {
      methodListBuilder.add(parseMethodString(method));
    }
    return methodListBuilder.build();
  }

  /**
   * Parses a Session header in an RTSP message to {@link RtspSessionHeader}.
   *
   * <p>The format of the Session header is
   *
   * <pre>
   * Session: session-id[;timeout=delta-seconds]
   * </pre>
   *
   * @param headerValue The string represent the content without the header name (Session: ).
   * @return The parsed {@link RtspSessionHeader}.
   * @throws ParserException When the input header value does not follow the Session header format.
   */
  public static RtspSessionHeader parseSessionHeader(String headerValue) throws ParserException {
    Matcher matcher = SESSION_HEADER_PATTERN.matcher(headerValue);
    if (!matcher.matches()) {
      throw new ParserException(headerValue);
    }

    String sessionId = checkNotNull(matcher.group(1));
    // Optional parameter timeout.
    long timeoutMs = DEFAULT_RTSP_TIMEOUT_MS;
    @Nullable String timeoutString;
    if ((timeoutString = matcher.group(2)) != null) {
      try {
        timeoutMs = Integer.parseInt(timeoutString) * C.MILLIS_PER_SECOND;
      } catch (NumberFormatException e) {
        throw new ParserException(e);
      }
    }

    return new RtspSessionHeader(sessionId, timeoutMs);
  }

  private static String getRtspStatusReasonPhrase(int statusCode) {
    switch (statusCode) {
      case 200:
        return "OK";
      case 400:
        return "Bad Request";
      case 401:
        return "Unauthorized";
      case 404:
        return "Not Found";
      case 405:
        return "Method Not Allowed";
      case 454:
        return "Session Not Found";
      case 455:
        return "Method Not Valid In This State";
      case 456:
        return "Header Field Not Valid";
      case 457:
        return "Invalid Range";
      case 461:
        return "Unsupported Transport";
      case 500:
        return "Internal Server Error";
      case 505:
        return "RTSP Version Not Supported";
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * Parses the string argument as an integer, wraps the potential {@link NumberFormatException} in
   * {@link ParserException}.
   */
  public static int parseInt(String intString) throws ParserException {
    try {
      return Integer.parseInt(intString);
    } catch (NumberFormatException e) {
      throw new ParserException(e);
    }
  }

  private RtspMessageUtil() {}
}
