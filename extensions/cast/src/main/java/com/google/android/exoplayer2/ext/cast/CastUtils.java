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
package com.google.android.exoplayer2.ext.cast;

import com.google.android.exoplayer2.Format;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaTrack;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for ExoPlayer/Cast integration.
 */
/* package */ final class CastUtils {

  private static final Map<Integer, String> CAST_STATUS_CODE_TO_STRING;

  /**
   * Returns a descriptive log string for the given {@code statusCode}, or "Unknown." if not one of
   * {@link CastStatusCodes}.
   *
   * @param statusCode A Cast API status code.
   * @return A descriptive log string for the given {@code statusCode}, or "Unknown." if not one of
   *     {@link CastStatusCodes}.
   */
  public static String getLogString(int statusCode) {
    String description = CAST_STATUS_CODE_TO_STRING.get(statusCode);
    return description != null ? description : "Unknown.";
  }

  /**
   * Creates a {@link Format} instance containing all information contained in the given
   * {@link MediaTrack} object.
   *
   * @param mediaTrack The {@link MediaTrack}.
   * @return The equivalent {@link Format}.
   */
  public static Format mediaTrackToFormat(MediaTrack mediaTrack) {
    return Format.createContainerFormat(mediaTrack.getContentId(), mediaTrack.getContentType(),
        null, null, Format.NO_VALUE, 0, mediaTrack.getLanguage());
  }

  static {
    HashMap<Integer, String> statusCodeToString = new HashMap<>();
    statusCodeToString.put(CastStatusCodes.APPLICATION_NOT_FOUND,
        "A requested application could not be found.");
    statusCodeToString.put(CastStatusCodes.APPLICATION_NOT_RUNNING,
        "A requested application is not currently running.");
    statusCodeToString.put(CastStatusCodes.AUTHENTICATION_FAILED, "Authentication failure.");
    statusCodeToString.put(CastStatusCodes.CANCELED, "An in-progress request has been "
        + "canceled, most likely because another action has preempted it.");
    statusCodeToString.put(CastStatusCodes.ERROR_SERVICE_CREATION_FAILED,
        "The Cast Remote Display service could not be created.");
    statusCodeToString.put(CastStatusCodes.ERROR_SERVICE_DISCONNECTED,
        "The Cast Remote Display service was disconnected.");
    statusCodeToString.put(CastStatusCodes.FAILED, "The in-progress request failed.");
    statusCodeToString.put(CastStatusCodes.INTERNAL_ERROR, "An internal error has occurred.");
    statusCodeToString.put(CastStatusCodes.INTERRUPTED,
        "A blocking call was interrupted while waiting and did not run to completion.");
    statusCodeToString.put(CastStatusCodes.INVALID_REQUEST, "An invalid request was made.");
    statusCodeToString.put(CastStatusCodes.MESSAGE_SEND_BUFFER_TOO_FULL, "A message could "
        + "not be sent because there is not enough room in the send buffer at this time.");
    statusCodeToString.put(CastStatusCodes.MESSAGE_TOO_LARGE,
        "A message could not be sent because it is too large.");
    statusCodeToString.put(CastStatusCodes.NETWORK_ERROR, "Network I/O error.");
    statusCodeToString.put(CastStatusCodes.NOT_ALLOWED,
        "The request was disallowed and could not be completed.");
    statusCodeToString.put(CastStatusCodes.REPLACED,
        "The request's progress is no longer being tracked because another request of the same type"
            + " has been made before the first request completed.");
    statusCodeToString.put(CastStatusCodes.SUCCESS, "Success.");
    statusCodeToString.put(CastStatusCodes.TIMEOUT, "An operation has timed out.");
    statusCodeToString.put(CastStatusCodes.UNKNOWN_ERROR,
        "An unknown, unexpected error has occurred.");
    CAST_STATUS_CODE_TO_STRING = Collections.unmodifiableMap(statusCodeToString);
  }

  private CastUtils() {}

}
