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

import android.net.Uri;
import android.util.Log;
import com.google.android.exoplayer2.upstream.Constants;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;

/** Utility methods for RTP. */
public final class RtpUtils {
  private static String TAG = Constants.TAG+ " RtpUtils.java";
  //private static final String RTP_ANY_INCOMING_IPV4 = "rtp://10.2.0.19"; // TODO: used to be 0.0.0.0
  private static final String RTP_ANY_INCOMING_IPV4 = "rtp://0.0.0.0";

  /** Returns the {@link DataSpec} with the {@link Uri} for incoming RTP connection. */
  public static DataSpec getIncomingRtpDataSpec(int portNumber) {
    Log.i(TAG, "Port Recieved: " + portNumber + "  Incoming IPV4 : "+ RTP_ANY_INCOMING_IPV4);
    DataSpec spec = new DataSpec(Uri.parse(Util.formatInvariant("%s:%d", RTP_ANY_INCOMING_IPV4, portNumber)));
    Log.i(TAG, "DataSpec: " + spec);
    return spec;  // print resultant . LOG.
  }

  private RtpUtils() {}
}
