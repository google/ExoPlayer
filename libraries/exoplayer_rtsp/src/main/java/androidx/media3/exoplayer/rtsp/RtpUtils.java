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

import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSpec;

/** Utility methods for RTP. */
@UnstableApi
public final class RtpUtils {

  private static final String RTP_ANY_INCOMING_IPV4 = "rtp://0.0.0.0";

  /** Returns the {@link DataSpec} with the {@link Uri} for incoming RTP connection. */
  public static DataSpec getIncomingRtpDataSpec(int portNumber) {
    return new DataSpec(
        Uri.parse(Util.formatInvariant("%s:%d", RTP_ANY_INCOMING_IPV4, portNumber)));
  }

  private RtpUtils() {}
}
