/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.session;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** The default {@link MediaNotification.ActionFactory}. */
@UnstableApi
/* package */ final class DefaultActionFactory implements MediaNotification.ActionFactory {

  private static final String ACTION_CUSTOM = "androidx.media3.session.CUSTOM_NOTIFICATION_ACTION";
  private static final String EXTRAS_KEY_ACTION_CUSTOM =
      "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION";
  public static final String EXTRAS_KEY_ACTION_CUSTOM_EXTRAS =
      "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION_EXTRAS";

  private final Context context;

  public DefaultActionFactory(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public NotificationCompat.Action createMediaAction(
      IconCompat icon, CharSequence title, @Command long command) {
    return new NotificationCompat.Action(icon, title, createMediaActionPendingIntent(command));
  }

  @Override
  public NotificationCompat.Action createCustomAction(
      IconCompat icon, CharSequence title, String customAction, Bundle extras) {
    return new NotificationCompat.Action(
        icon, title, createCustomActionPendingIntent(customAction, extras));
  }

  @Override
  public PendingIntent createMediaActionPendingIntent(@Command long command) {
    int keyCode = PlaybackStateCompat.toKeyCode(command);
    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    intent.setComponent(new ComponentName(context, context.getClass()));
    intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    if (Util.SDK_INT >= 26 && command != COMMAND_PAUSE && command != COMMAND_STOP) {
      return Api26.createPendingIntent(context, /* requestCode= */ keyCode, intent);
    } else {
      return PendingIntent.getService(
          context,
          /* requestCode= */ keyCode,
          intent,
          Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
  }

  private PendingIntent createCustomActionPendingIntent(String action, Bundle extras) {
    Intent intent = new Intent(ACTION_CUSTOM);
    intent.setComponent(new ComponentName(context, context.getClass()));
    intent.putExtra(EXTRAS_KEY_ACTION_CUSTOM, action);
    intent.putExtra(EXTRAS_KEY_ACTION_CUSTOM_EXTRAS, extras);
    if (Util.SDK_INT >= 26) {
      return Api26.createPendingIntent(
          context, /* requestCode= */ KeyEvent.KEYCODE_UNKNOWN, intent);
    } else {
      return PendingIntent.getService(
          context,
          /* requestCode= */ KeyEvent.KEYCODE_UNKNOWN,
          intent,
          Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
  }

  /** Returns whether {@code intent} was part of a {@link #createMediaAction media action}. */
  public boolean isMediaAction(Intent intent) {
    return Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction());
  }

  /** Returns whether {@code intent} was part of a {@link #createCustomAction custom action }. */
  public boolean isCustomAction(Intent intent) {
    return ACTION_CUSTOM.equals(intent.getAction());
  }

  /**
   * Returns the {@link KeyEvent} that was included in the media action, or {@code null} if no
   * {@link KeyEvent} is found in the {@code intent}.
   */
  @Nullable
  public KeyEvent getKeyEvent(Intent intent) {
    @Nullable Bundle extras = intent.getExtras();
    if (extras != null && extras.containsKey(Intent.EXTRA_KEY_EVENT)) {
      return extras.getParcelable(Intent.EXTRA_KEY_EVENT);
    }
    return null;
  }

  /**
   * Returns the custom action that was included in the {@link #createCustomAction custom action},
   * or {@code null} if no custom action is found in the {@code intent}.
   */
  @Nullable
  public String getCustomAction(Intent intent) {
    @Nullable Bundle extras = intent.getExtras();
    @Nullable Object customAction = extras != null ? extras.get(EXTRAS_KEY_ACTION_CUSTOM) : null;
    return customAction instanceof String ? (String) customAction : null;
  }

  /**
   * Returns extras that were included in the {@link #createCustomAction custom action}, or {@link
   * Bundle#EMPTY} is no extras are found.
   */
  public Bundle getCustomActionExtras(Intent intent) {
    @Nullable Bundle extras = intent.getExtras();
    @Nullable
    Object customExtras = extras != null ? extras.get(EXTRAS_KEY_ACTION_CUSTOM_EXTRAS) : null;
    return customExtras instanceof Bundle ? (Bundle) customExtras : Bundle.EMPTY;
  }

  @RequiresApi(26)
  private static final class Api26 {
    private Api26() {}

    public static PendingIntent createPendingIntent(Context context, int keyCode, Intent intent) {
      return PendingIntent.getForegroundService(
          context, /* requestCode= */ keyCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }
  }
}
