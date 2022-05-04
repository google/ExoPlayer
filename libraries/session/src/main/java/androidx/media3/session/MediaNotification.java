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
 * limitations under the License
 */
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Bundle;
import androidx.annotation.IntRange;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

/** A notification for media playbacks. */
public final class MediaNotification {

  /**
   * Creates {@link NotificationCompat.Action actions} and {@link PendingIntent pending intents} for
   * notifications.
   */
  @UnstableApi
  public interface ActionFactory {

    /**
     * Creates a {@link NotificationCompat.Action} for a notification. These actions will be handled
     * by the library.
     *
     * @param icon The icon to show for this action.
     * @param title The title of the action.
     * @param command A command to send when users trigger this action.
     */
    NotificationCompat.Action createMediaAction(
        IconCompat icon, CharSequence title, @Player.Command long command);

    /**
     * Creates a {@link NotificationCompat.Action} for a notification with a custom action. Actions
     * created with this method are not expected to be handled by the library and will be forwarded
     * to the {@link MediaNotification.Provider#handleCustomAction notification provider} that
     * provided them.
     *
     * @param icon The icon to show for this action.
     * @param title The title of the action.
     * @param customAction The custom action set.
     * @param extras Extras to be included in the action.
     * @see MediaNotification.Provider#handleCustomAction
     */
    NotificationCompat.Action createCustomAction(
        IconCompat icon, CharSequence title, String customAction, Bundle extras);

    /**
     * Creates a {@link PendingIntent} for a media action that will be handled by the library.
     *
     * @param command The intent's command.
     */
    PendingIntent createMediaActionPendingIntent(@Player.Command long command);
  }

  /**
   * Provides {@link MediaNotification media notifications} to be posted as notifications that
   * reflect the state of a {@link MediaController} and to send media commands to a {@link
   * MediaSession}.
   *
   * <p>The provider is required to create a {@link androidx.core.app.NotificationChannelCompat
   * notification channel}, which is required to show notification for {@code SDK_INT >= 26}.
   */
  @UnstableApi
  public interface Provider {
    /** Receives updates for a notification. */
    interface Callback {
      /**
       * Called when a {@link MediaNotification} is changed.
       *
       * <p>This callback is called when notifications are updated, for example after a bitmap is
       * loaded asynchronously and needs to be displayed.
       *
       * @param notification The updated {@link MediaNotification}
       */
      void onNotificationChanged(MediaNotification notification);
    }

    /**
     * Creates a new {@link MediaNotification}.
     *
     * @param mediaController The controller of the session.
     * @param actionFactory The {@link ActionFactory} for creating notification {@link
     *     NotificationCompat.Action actions}.
     * @param onNotificationChangedCallback A callback that the provider needs to notify when the
     *     notification has changed and needs to be posted again, for example after a bitmap has
     *     been loaded asynchronously.
     */
    MediaNotification createNotification(
        MediaController mediaController,
        ActionFactory actionFactory,
        Callback onNotificationChangedCallback);

    /**
     * Handles a notification's custom action.
     *
     * @param mediaController The controller of the session.
     * @param action The custom action.
     * @param extras Extras set in the custom action, otherwise {@link Bundle#EMPTY}.
     * @see ActionFactory#createCustomAction
     */
    void handleCustomAction(MediaController mediaController, String action, Bundle extras);
  }

  /** The notification id. */
  @IntRange(from = 1)
  public final int notificationId;

  /** The {@link Notification}. */
  public final Notification notification;

  /**
   * Creates an instance.
   *
   * @param notificationId The notification id to be used for {@link NotificationManager#notify(int,
   *     Notification)}.
   * @param notification A {@link Notification} that reflects the sate of a {@link MediaController}
   *     and to send media commands to a {@link MediaSession}. The notification may be used to start
   *     a service in the <a
   *     href="https://developer.android.com/guide/components/foreground-services">foreground</a>.
   *     It's highly recommended to use a {@link androidx.media.app.NotificationCompat.MediaStyle
   *     media style} {@link Notification notification}.
   */
  public MediaNotification(@IntRange(from = 1) int notificationId, Notification notification) {
    this.notificationId = notificationId;
    this.notification = checkNotNull(notification);
  }
}
