/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.session.vct.common;

import static android.content.Context.KEYGUARD_SERVICE;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.google.android.exoplayer2.util.Util;
import java.util.Locale;

/** Provides utility methods for testing purpose. */
public class TestUtils {

  public static final long TIMEOUT_MS = 5_000;
  public static final long NO_RESPONSE_TIMEOUT_MS = 500;
  public static final long SERVICE_CONNECTION_TIMEOUT_MS = 3_000;
  public static final long VOLUME_CHANGE_TIMEOUT_MS = 5_000;
  public static final long LONG_TIMEOUT_MS = 10_000;

  /**
   * Compares contents of two throwables for both message and class.
   *
   * @param a a throwable
   * @param b another throwable
   * @return {@code true} if two throwables are the same class and same messages. {@code false}
   *     otherwise.
   */
  public static boolean equals(@Nullable Throwable a, @Nullable Throwable b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.getClass() == b.getClass() && TextUtils.equals(a.getMessage(), b.getMessage());
  }

  /**
   * Compares contents of two bundles.
   *
   * @param a a bundle
   * @param b another bundle
   * @return {@code true} if two bundles are the same. {@code false} otherwise. This may be
   *     incorrect if any bundle contains a bundle.
   */
  public static boolean equals(Bundle a, Bundle b) {
    return contains(a, b) && contains(b, a);
  }

  /**
   * Checks whether a Bundle contains another bundle.
   *
   * @param a a bundle
   * @param b another bundle
   * @return {@code true} if a contains b. {@code false} otherwise. This may be incorrect if any
   *     bundle contains a bundle.
   */
  public static boolean contains(Bundle a, Bundle b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return b == null;
    }
    if (!a.keySet().containsAll(b.keySet())) {
      return false;
    }
    for (String key : b.keySet()) {
      if (!Util.areEqual(a.get(key), b.get(key))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Create a bundle for testing purpose.
   *
   * @return the newly created bundle.
   */
  public static Bundle createTestBundle() {
    Bundle bundle = new Bundle();
    bundle.putString("test_key", "test_value");
    return bundle;
  }

  /** Gets the expected mediaId for the windowIndex when testing with a fake timeline. */
  public static String getMediaIdInFakeTimeline(int windowIndex) {
    return String.format(Locale.US, "%08d", windowIndex);
  }

  @UiThread
  static void setKeepScreenOn(Activity activity) {
    if (Build.VERSION.SDK_INT >= 27) {
      activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      activity.setTurnScreenOn(true);
      activity.setShowWhenLocked(true);
      KeyguardManager keyguardManager =
          (KeyguardManager) activity.getSystemService(KEYGUARD_SERVICE);
      keyguardManager.requestDismissKeyguard(activity, null);
    } else {
      activity
          .getWindow()
          .addFlags(
              WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
  }

  private TestUtils() {}
}
