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
import com.google.common.collect.ImmutableList;
import java.util.List;

/** A notification for media playbacks. */
public final class MediaNotification {

  /**
   * Creates {@linkplain NotificationCompat.Action actions} and {@linkplain PendingIntent pending
   * intents} for notifications.
   *
   * <p>All methods will be called on the {@link Player#getApplicationLooper() application thread}
   * of the {@link Player} associated with the {@link MediaSession} the notification is provided
   * for.
   */
  @UnstableApi
  public interface ActionFactory {

    /**
     * Creates a {@link NotificationCompat.Action} for a notification. These actions will be handled
     * by the library.
     *
     * @param mediaSession The media session to which the action will be sent.
     * @param icon The icon to show for this action.
     * @param title The title of the action.
     * @param command A command to send when users trigger this action.
     */
    NotificationCompat.Action createMediaAction(
        MediaSession mediaSession,
        IconCompat icon,
        CharSequence title,
        @Player.Command int command);

    /**
     * Creates a {@link NotificationCompat.Action} for a notification with a custom action. Actions
     * created with this method are not expected to be handled by the library and will be forwarded
     * to the {@linkplain MediaNotification.Provider#handleCustomCommand notification provider} that
     * provided them.
     *
     * @param mediaSession The media session to which the action will be sent.
     * @param icon The icon to show for this action.
     * @param title The title of the action.
     * @param customAction The custom action set.
     * @param extras Extras to be included in the action.
     * @see MediaNotification.Provider#handleCustomCommand
     */
    NotificationCompat.Action createCustomAction(
        MediaSession mediaSession,
        IconCompat icon,
        CharSequence title,
        String customAction,
        Bundle extras);

    /**
     * Creates a {@link NotificationCompat.Action} for a notification from a custom command button.
     * Actions created with this method are not expected to be handled by the library and will be
     * forwarded to the {@linkplain MediaNotification.Provider#handleCustomCommand notification
     * provider} that provided them.
     *
     * <p>The returned {@link NotificationCompat.Action} will have a {@link PendingIntent} with the
     * extras from {@link SessionCommand#customExtras}. Accordingly the {@linkplain
     * SessionCommand#customExtras command's extras} will be passed to {@link
     * Provider#handleCustomCommand(MediaSession, String, Bundle)} when the action is executed.
     *
     * @param mediaSession The media session to which the action will be sent.
     * @param customCommandButton A {@linkplain CommandButton custom command button}.
     * @see MediaNotification.Provider#handleCustomCommand
     */
    NotificationCompat.Action createCustomActionFromCustomCommandButton(
        MediaSession mediaSession, CommandButton customCommandButton);

    /**
     * Creates a {@link PendingIntent} for a media action that will be handled by the library.
     *
     * @param mediaSession The media session to which the action will be sent.
     * @param command The intent's command.
     */
    PendingIntent createMediaActionPendingIntent(
        MediaSession mediaSession, @Player.Command long command);
  }

  /**
   * Provides {@linkplain MediaNotification media notifications} to be posted as notifications that
   * reflect the state of a {@link MediaController} and to send media commands to a {@link
   * MediaSession}.
   *
   * <p>The provider is required to create a {@linkplain androidx.core.app.NotificationChannelCompat
   * notification channel}, which is required to show notification for {@code SDK_INT >= 26}.
   *
   * <p>All methods will be called on the {@link Player#getApplicationLooper() application thread}
   * of the {@link Player} associated with the {@link MediaSession} the notification is provided
   * for.
   */
  @UnstableApi
  public interface Provider {
    /**
     * Receives updates for a notification.
     *
     * <p>All methods will be called on the {@link Player#getApplicationLooper() application thread}
     * of the {@link Player} associated with the {@link MediaSession} the notification is provided
     * for.
     */
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
     * @param mediaSession The media session.
     * @param actionFactory The {@link ActionFactory} for creating notification {@link
     *     NotificationCompat.Action actions}.
     * @param customLayout The custom layout {@linkplain MediaSession#setCustomLayout(List) set by
     *     the session}.
     * @param onNotificationChangedCallback A callback that the provider needs to notify when the
     *     notification has changed and needs to be posted again, for example after a bitmap has
     *     been loaded asynchronously.
     */
    MediaNotification createNotification(
        MediaSession mediaSession,
        ImmutableList<CommandButton> customLayout,
        ActionFactory actionFactory,
        Callback onNotificationChangedCallback);

    /**
     * Handles a notification's custom command.
     *
     * @param session The media session.
     * @param action The custom command action.
     * @param extras A bundle {@linkplain SessionCommand#customExtras set in the custom command},
     *     otherwise {@link Bundle#EMPTY}.
     * @return {@code false} if the action should be delivered to the session as a custom command or
     *     {@code true} if the action has been handled completely by the provider.
     * @see ActionFactory#createCustomAction
     */
    boolean handleCustomCommand(MediaSession session, String action, Bundle extras);
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
   * @param notification A {@link Notification} that reflects the state of a {@link MediaController}
   *     and to send media commands to a {@link MediaSession}. The notification may be used to start
   *     a service in the <a
   *     href="https://developer.android.com/guide/components/foreground-services">foreground</a>.
   *     It's highly recommended to use a {@linkplain
   *     androidx.media3.session.MediaStyleNotificationHelper.MediaStyle media style} {@linkplain
   *     Notification notification}.
   */
  public MediaNotification(@IntRange(from = 1) int notificationId, Notification notification) {
    this.notificationId = notificationId;
    this.notification = checkNotNull(notification);
  }
}
