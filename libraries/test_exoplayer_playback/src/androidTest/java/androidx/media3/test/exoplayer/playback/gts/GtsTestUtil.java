/*
 * Copyright (C) 2020 The Android Open Source Project
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

package androidx.media3.test.exoplayer.playback.gts;

import static androidx.media3.common.C.WIDEVINE_UUID;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaDrm;

/** Utility methods for GTS tests. */
public final class GtsTestUtil {

  private GtsTestUtil() {}

  /** Returns true if the device doesn't support Widevine and this is permitted. */
  public static boolean shouldSkipWidevineTest(Context context) {
    if (isGmsInstalled(context)) {
      // GMS devices are required to support Widevine.
      return false;
    }
    // For non-GMS devices Widevine is optional.
    return !MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID);
  }

  private static boolean isGmsInstalled(Context context) {
    try {
      context
          .getPackageManager()
          .getPackageInfo("com.google.android.gms", PackageManager.GET_SIGNATURES);
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
    return true;
  }
}
