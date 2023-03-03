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
package androidx.media3.test.session.common;

import static android.content.Context.KEYGUARD_SERVICE;
import static java.lang.Math.min;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Provides utility methods for testing purpose. */
public class TestUtils {

  public static final long TIMEOUT_MS = 5_000;
  public static final long NO_RESPONSE_TIMEOUT_MS = 500;
  public static final long SERVICE_CONNECTION_TIMEOUT_MS = 3_000;
  public static final long VOLUME_CHANGE_TIMEOUT_MS = 5_000;
  public static final long LONG_TIMEOUT_MS = 20_000;

  private static final int MAX_BITMAP_WIDTH = 500;
  private static final int MAX_BITMAP_HEIGHT = 500;

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
  public static boolean equals(@Nullable Bundle a, @Nullable Bundle b) {
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
  public static boolean contains(@Nullable Bundle a, @Nullable Bundle b) {
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
    if (Util.SDK_INT >= 27) {
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

  /**
   * Returns an {@link ImmutableList} with the {@linkplain Player.Event Events} contained in {@code
   * events}. The contents of the list are in matching order with the {@linkplain Player.Event
   * Events} returned by {@link Player.Events#get(int)}.
   */
  // TODO(b/254265256): Move this method off test-session-common.
  public static ImmutableList<@Player.Event Integer> getEventsAsList(Player.Events events) {
    ImmutableList.Builder<@Player.Event Integer> list = new ImmutableList.Builder<>();
    for (int i = 0; i < events.size(); i++) {
      list.add(events.get(i));
    }
    return list.build();
  }

  /**
   * Returns an {@link ImmutableList} with the {@linkplain Player.Command Commands} contained in
   * {@code commands}. The contents of the list are in matching order with the {@linkplain
   * Player.Command Commands} returned by {@link Player.Commands#get(int)}.
   */
  // TODO(b/254265256): Move this method off test-session-common.
  public static ImmutableList<@Player.Command Integer> getCommandsAsList(Player.Commands commands) {
    ImmutableList.Builder<@Player.Command Integer> list = new ImmutableList.Builder<>();
    for (int i = 0; i < commands.size(); i++) {
      list.add(commands.get(i));
    }
    return list.build();
  }

  /** Returns the bytes of a scaled asset file. */
  public static byte[] getByteArrayForScaledBitmap(Context context, String fileName)
      throws IOException {
    Bitmap bitmap = getBitmap(context, fileName);
    int width = min(bitmap.getWidth(), MAX_BITMAP_WIDTH);
    int height = min(bitmap.getHeight(), MAX_BITMAP_HEIGHT);
    return convertToByteArray(Bitmap.createScaledBitmap(bitmap, width, height, true));
  }

  /** Returns an {@link InputStream} for reading from an asset file. */
  public static InputStream getInputStream(Context context, String fileName) throws IOException {
    return context.getResources().getAssets().open(fileName);
  }

  /** Returns a {@link Bitmap} read from an asset file. */
  public static Bitmap getBitmap(Context context, String fileName) throws IOException {
    return BitmapFactory.decodeStream(getInputStream(context, fileName));
  }

  /** Converts the given {@link Bitmap} to an array of bytes. */
  public static byte[] convertToByteArray(Bitmap bitmap) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      bitmap.compress(Bitmap.CompressFormat.PNG, /* ignored */ 0, stream);
      return stream.toByteArray();
    }
  }

  private TestUtils() {}
}
