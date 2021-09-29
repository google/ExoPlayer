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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ParserException;

/** Represents an RTSP DESCRIBE response. */
/* package */ final class RtspDescribeResponse {
  /** The response's status code. */
  public final int status;
  /** The {@link SessionDescription} (see RFC2327) in the DESCRIBE response. */
  public final SessionDescription sessionDescription;
  /** The {@link RtspHeaders#CONTENT_BASE} header from the response */
  @Nullable public final String contentBase;
  /** The {@link RtspHeaders#CONTENT_LOCATION} header from the response */
  @Nullable public final String contentLocation;

  /**
   * Creates a new instance from a response
   */
  public RtspDescribeResponse(RtspResponse response) throws ParserException {
    this.status = response.status;
    this.sessionDescription = SessionDescriptionParser.parse(response.messageBody);
    this.contentBase = response.headers.get(RtspHeaders.CONTENT_BASE);
    this.contentLocation = response.headers.get(RtspHeaders.CONTENT_LOCATION);
  }
}
