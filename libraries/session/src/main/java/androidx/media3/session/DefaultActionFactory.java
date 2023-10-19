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

import static android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
import static android.view.KeyEvent.KEYCODE_MEDIA_REWIND;
import static android.view.KeyEvent.KEYCODE_MEDIA_STOP;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.Player;
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

  /**
   * Returns the {@link KeyEvent} that was included in the media action, or {@code null} if no
   * {@link KeyEvent} is found in the {@code intent}.
   */
  @Nullable
  public static KeyEvent getKeyEvent(Intent intent) {
    @Nullable Bundle extras = intent.getExtras();
    if (extras != null && extras.containsKey(Intent.EXTRA_KEY_EVENT)) {
      return extras.getParcelable(Intent.EXTRA_KEY_EVENT);
    }
    return null;
  }

  private final Service service;

  private int customActionPendingIntentRequestCode = 0;

  public DefaultActionFactory(Service service) {
    this.service = service;
  }

  @Override
  public NotificationCompat.Action createMediaAction(
      MediaSession mediaSession, IconCompat icon, CharSequence title, @Player.Command int command) {
    return new NotificationCompat.Action(
        icon, title, createMediaActionPendingIntent(mediaSession, command));
  }

  @Override
  public NotificationCompat.Action createCustomAction(
      MediaSession mediaSession,
      IconCompat icon,
      CharSequence title,
      String customAction,
      Bundle extras) {
    return new NotificationCompat.Action(
        icon, title, createCustomActionPendingIntent(mediaSession, customAction, extras));
  }

  @Override
  public NotificationCompat.Action createCustomActionFromCustomCommandButton(
      MediaSession mediaSession, CommandButton customCommandButton) {
    checkArgument(
        customCommandButton.sessionCommand != null
            && customCommandButton.sessionCommand.commandCode
                == SessionCommand.COMMAND_CODE_CUSTOM);
    SessionCommand customCommand = checkNotNull(customCommandButton.sessionCommand);
    return new NotificationCompat.Action(
        IconCompat.createWithResource(service, customCommandButton.iconResId),
        customCommandButton.displayName,
        createCustomActionPendingIntent(
            mediaSession, customCommand.customAction, customCommand.customExtras));
  }

  @SuppressWarnings("PendingIntentMutability") // We can't use SaferPendingIntent
  @Override
  public PendingIntent createMediaActionPendingIntent(
      MediaSession mediaSession, @Player.Command long command) {
    int keyCode = toKeyCode(command);
    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    intent.setData(mediaSession.getImpl().getUri());
    intent.setComponent(new ComponentName(service, service.getClass()));
    intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    if (Util.SDK_INT >= 26
        && command == COMMAND_PLAY_PAUSE
        && !mediaSession.getPlayer().getPlayWhenReady()) {
      return Api26.createForegroundServicePendingIntent(service, keyCode, intent);
    } else {
      return PendingIntent.getService(
          service,
          /* requestCode= */ keyCode,
          intent,
          Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
  }

  private int toKeyCode(@Player.Command long action) {
    if (action == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM || action == COMMAND_SEEK_TO_NEXT) {
      return KEYCODE_MEDIA_NEXT;
    } else if (action == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        || action == COMMAND_SEEK_TO_PREVIOUS) {
      return KEYCODE_MEDIA_PREVIOUS;
    } else if (action == Player.COMMAND_STOP) {
      return KEYCODE_MEDIA_STOP;
    } else if (action == COMMAND_SEEK_FORWARD) {
      return KEYCODE_MEDIA_FAST_FORWARD;
    } else if (action == COMMAND_SEEK_BACK) {
      return KEYCODE_MEDIA_REWIND;
    } else if (action == COMMAND_PLAY_PAUSE) {
      return KEYCODE_MEDIA_PLAY_PAUSE;
    }
    return KEYCODE_UNKNOWN;
  }

  @SuppressWarnings("PendingIntentMutability") // We can't use SaferPendingIntent
  private PendingIntent createCustomActionPendingIntent(
      MediaSession mediaSession, String action, Bundle extras) {
    Intent intent = new Intent(ACTION_CUSTOM);
    intent.setData(mediaSession.getImpl().getUri());
    intent.setComponent(new ComponentName(service, service.getClass()));
    intent.putExtra(EXTRAS_KEY_ACTION_CUSTOM, action);
    intent.putExtra(EXTRAS_KEY_ACTION_CUSTOM_EXTRAS, extras);
    // Custom actions always start the service in the background.
    return PendingIntent.getService(
        service,
        /* requestCode= */ ++customActionPendingIntentRequestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
            | (Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
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

    @SuppressWarnings("PendingIntentMutability") // We can't use SaferPendingIntent
    public static PendingIntent createForegroundServicePendingIntent(
        Service service, int keyCode, Intent intent) {
      return PendingIntent.getForegroundService(
          service, /* requestCode= */ keyCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }
  }
}
