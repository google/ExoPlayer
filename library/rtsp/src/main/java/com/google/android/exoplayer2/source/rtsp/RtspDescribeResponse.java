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

/**
 * Represents an RTSP DESCRIBE response.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class RtspDescribeResponse {
  /** The response's headers. */
  public final RtspHeaders headers;
  /** The response's status code. */
  public final int status;
  /** The {@link SessionDescription} (see RFC2327) in the DESCRIBE response. */
  public final SessionDescription sessionDescription;

  /**
   * Creates a new instance.
   *
   * @param headers The response's headers.
   * @param status The response's status code.
   * @param sessionDescription The {@link SessionDescription} in the DESCRIBE response.
   */
  public RtspDescribeResponse(
      RtspHeaders headers, int status, SessionDescription sessionDescription) {
    this.headers = headers;
    this.status = status;
    this.sessionDescription = sessionDescription;
  }
}
