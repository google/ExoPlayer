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
package androidx.media3.session;

import static android.Manifest.permission.MEDIA_CONTENT_CONTROL;
import static androidx.core.app.NotificationCompat.COLOR_DEFAULT;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.DoNotInline;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationBuilderWithBuilderAccessor;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Class containing media specfic {@link androidx.core.app.NotificationCompat.Style styles} that you
 * can use with {@link androidx.core.app.NotificationCompat.Builder#setStyle}.
 */
@UnstableApi
public class MediaStyleNotificationHelper {

  public static final String EXTRA_MEDIA3_SESSION = "androidx.media3.session";

  private MediaStyleNotificationHelper() {}

  /**
   * Notification style for media playback notifications.
   *
   * <p>In the expanded form, up to 5 {@link androidx.core.app.NotificationCompat.Action actions}
   * specified with {@link androidx.core.app.NotificationCompat.Builder #addAction(int,
   * CharSequence, PendingIntent) addAction} will be shown as icon-only pushbuttons, suitable for
   * transport controls. The Bitmap given to {@link androidx.core.app.NotificationCompat.Builder
   * #setLargeIcon(android.graphics.Bitmap) setLargeIcon()} will be treated as album artwork.
   *
   * <p>Unlike the other styles provided here, MediaStyle can also modify the standard-size content
   * view; by providing action indices to {@link #setShowActionsInCompactView(int...)} you can
   * promote up to 3 actions to be displayed in the standard view alongside the usual content.
   *
   * <p>Notifications created with MediaStyle will have their category set to {@link
   * androidx.core.app.NotificationCompat#CATEGORY_TRANSPORT CATEGORY_TRANSPORT} unless you set a
   * different category using {@link
   * androidx.core.app.NotificationCompat.Builder#setCategory(String) setCategory()}.
   *
   * <p>Finally, the System UI can identify this as a notification representing an active media
   * session and respond accordingly (by showing album artwork in the lockscreen, for example).
   *
   * <p>To use this style with your Notification, feed it to {@link
   * androidx.core.app.NotificationCompat.Builder#setStyle} like so:
   *
   * <pre class="prettyprint">
   * Notification noti = new NotificationCompat.Builder()
   *     .setSmallIcon(androidx.media.R.drawable.ic_stat_player)
   *     .setContentTitle(&quot;Track title&quot;)
   *     .setContentText(&quot;Artist - Album&quot;)
   *     .setLargeIcon(albumArtBitmap)
   *     .setStyle(<b>new MediaStyleNotificationHelper.MediaStyle(mySession)</b>)
   *     .build();
   * </pre>
   *
   * @see Notification#bigContentView
   */
  public static class MediaStyle extends androidx.core.app.NotificationCompat.Style {

    /**
     * Extracts a {@link SessionToken} from the extra values in the {@link MediaStyle} {@link
     * Notification notification}.
     *
     * @param notification The notification to extract a {@link MediaSession} from.
     * @return The {@link SessionToken} in the {@code notification} if it contains, null otherwise.
     */
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public static SessionToken getSessionToken(Notification notification) {
      Bundle extras = androidx.core.app.NotificationCompat.getExtras(notification);
      if (extras == null) {
        return null;
      }
      Bundle sessionTokenBundle = extras.getBundle(EXTRA_MEDIA3_SESSION);
      return sessionTokenBundle == null ? null : SessionToken.fromBundle(sessionTokenBundle);
    }

    private static final int MAX_MEDIA_BUTTONS_IN_COMPACT = 3;
    private static final int MAX_MEDIA_BUTTONS = 5;

    /* package */ final MediaSession session;

    private boolean showCancelButton;
    /* package */ int @NullableType [] actionsToShowInCompact;
    @Nullable /* package */ PendingIntent cancelButtonIntent;
    /* package */ @MonotonicNonNull CharSequence remoteDeviceName;
    /* package */ int remoteDeviceIconRes;
    @Nullable /* package */ PendingIntent remoteDeviceIntent;

    /**
     * Creates a new instance with a {@link MediaSession} to this Notification to provide additional
     * playback information and control to the SystemUI.
     */
    public MediaStyle(MediaSession session) {
      this.session = session;
    }

    /**
     * Requests up to 3 actions (by index in the order of addition) to be shown in the compact
     * notification view.
     *
     * @param actions the indices of the actions to show in the compact notification view
     */
    @CanIgnoreReturnValue
    public MediaStyle setShowActionsInCompactView(int... actions) {
      actionsToShowInCompact = actions;
      return this;
    }

    /**
     * Sets whether a cancel button at the top right should be shown in the notification on
     * platforms before Lollipop.
     *
     * <p>Prior to Lollipop, there was a bug in the framework which prevented the developer to make
     * a notification dismissable again after having used the same notification as the ongoing
     * notification for a foreground service. When the notification was posted by {@link
     * android.app.Service#startForeground}, but then the service exited foreground mode via {@link
     * android.app.Service#stopForeground}, without removing the notification, the notification
     * stayed ongoing, and thus not dismissable.
     *
     * <p>This is a common scenario for media notifications, as this is exactly the service
     * lifecycle that happens when playing/pausing media. Thus, a workaround is provided by the
     * support library: Instead of making the notification ongoing depending on the playback state,
     * the support library provides the ability to add an explicit cancel button to the
     * notification.
     *
     * <p>Note that the notification is enforced to be ongoing if a cancel button is shown to
     * provide a consistent user experience.
     *
     * <p>Also note that this method is a no-op when running on Lollipop and later.
     *
     * @param show whether to show a cancel button
     */
    @CanIgnoreReturnValue
    public MediaStyle setShowCancelButton(boolean show) {
      if (Build.VERSION.SDK_INT < 21) {
        showCancelButton = show;
      }
      return this;
    }

    /**
     * Sets the pending intent to be sent when the cancel button is pressed. See {@link
     * #setShowCancelButton}.
     *
     * @param pendingIntent the intent to be sent when the cancel button is pressed
     */
    @CanIgnoreReturnValue
    public MediaStyle setCancelButtonIntent(PendingIntent pendingIntent) {
      cancelButtonIntent = pendingIntent;
      return this;
    }

    /**
     * For media notifications associated with playback on a remote device, provide device
     * information that will replace the default values for the output switcher chip on the media
     * control, as well as an intent to use when the output switcher chip is tapped, on devices
     * where this is supported.
     *
     * <p>Most apps should integrate with {@link android.media.MediaRouter2} instead. This method is
     * only intended for system applications to provide information and/or functionality that would
     * otherwise be unavailable to the default output switcher because the media originated on a
     * remote device.
     *
     * <p>Also note that this method is a no-op when running on API 33 or lower.
     *
     * @param deviceName The name of the remote device to display.
     * @param iconResource Icon resource, of size 12, representing the device.
     * @param chipIntent PendingIntent to send when the output switcher is tapped. May be {@code
     *     null}, in which case the output switcher will be disabled. This intent should open an
     *     {@link android.app.Activity} or it will be ignored.
     */
    @CanIgnoreReturnValue
    @RequiresPermission(MEDIA_CONTENT_CONTROL)
    public MediaStyle setRemotePlaybackInfo(
        CharSequence deviceName,
        @DrawableRes int iconResource,
        @Nullable PendingIntent chipIntent) {
      checkArgument(deviceName != null);
      this.remoteDeviceName = deviceName;
      this.remoteDeviceIconRes = iconResource;
      this.remoteDeviceIntent = chipIntent;
      return this;
    }

    @Override
    public void apply(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 34 && remoteDeviceName != null) {
        Api21Impl.setMediaStyle(
            builder.getBuilder(),
            Api21Impl.fillInMediaStyle(
                Api34Impl.setRemotePlaybackInfo(
                    Api21Impl.createMediaStyle(),
                    remoteDeviceName,
                    remoteDeviceIconRes,
                    remoteDeviceIntent),
                actionsToShowInCompact,
                session));
      } else if (Util.SDK_INT >= 21) {
        Api21Impl.setMediaStyle(
            builder.getBuilder(),
            Api21Impl.fillInMediaStyle(
                Api21Impl.createMediaStyle(), actionsToShowInCompact, session));
        Bundle bundle = new Bundle();
        bundle.putBundle(EXTRA_MEDIA3_SESSION, session.getToken().toBundle());
        builder.getBuilder().addExtras(bundle);
      } else if (showCancelButton) {
        builder.getBuilder().setOngoing(true);
      }
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 21) {
        // No custom content view required
        return null;
      }
      return generateContentView();
    }

    /* package */ RemoteViews generateContentView() {
      RemoteViews view =
          applyStandardTemplate(
              /* showSmallIcon= */ false, getContentViewLayoutResource(), /* fitIn1U= */ true);

      final int numActions = mBuilder.mActions.size();
      if (actionsToShowInCompact != null) {
        int[] actions = actionsToShowInCompact;
        final int numActionsInCompact = Math.min(actions.length, MAX_MEDIA_BUTTONS_IN_COMPACT);
        view.removeAllViews(androidx.media.R.id.media_actions);
        if (numActionsInCompact > 0) {
          for (int i = 0; i < numActionsInCompact; i++) {
            if (i >= numActions) {
              throw new IllegalArgumentException(
                  String.format(
                      "setShowActionsInCompactView: action %d out of bounds (max %d)",
                      i, numActions - 1));
            }

            final androidx.core.app.NotificationCompat.Action action =
                mBuilder.mActions.get(actions[i]);
            final RemoteViews button = generateMediaActionButton(action);
            view.addView(androidx.media.R.id.media_actions, button);
          }
        }
      }
      if (showCancelButton) {
        view.setViewVisibility(androidx.media.R.id.end_padder, View.GONE);
        view.setViewVisibility(androidx.media.R.id.cancel_action, View.VISIBLE);
        view.setOnClickPendingIntent(androidx.media.R.id.cancel_action, cancelButtonIntent);
        view.setInt(
            androidx.media.R.id.cancel_action,
            "setAlpha",
            mBuilder
                .mContext
                .getResources()
                .getInteger(androidx.media.R.integer.cancel_button_image_alpha));
      } else {
        view.setViewVisibility(androidx.media.R.id.end_padder, View.VISIBLE);
        view.setViewVisibility(androidx.media.R.id.cancel_action, View.GONE);
      }
      return view;
    }

    private RemoteViews generateMediaActionButton(
        androidx.core.app.NotificationCompat.Action action) {
      final boolean tombstone = (action.getActionIntent() == null);
      RemoteViews button =
          new RemoteViews(
              mBuilder.mContext.getPackageName(),
              androidx.media.R.layout.notification_media_action);
      IconCompat iconCompat = action.getIconCompat();
      if (iconCompat != null) {
        button.setImageViewResource(androidx.media.R.id.action0, iconCompat.getResId());
      }
      if (!tombstone) {
        button.setOnClickPendingIntent(androidx.media.R.id.action0, action.getActionIntent());
      }
      button.setContentDescription(androidx.media.R.id.action0, action.getTitle());
      return button;
    }

    /* package */ int getContentViewLayoutResource() {
      return androidx.media.R.layout.notification_template_media;
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeBigContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 21) {
        // No custom content view required
        return null;
      }
      return generateBigContentView();
    }

    /* package */ RemoteViews generateBigContentView() {
      final int actionCount = Math.min(mBuilder.mActions.size(), MAX_MEDIA_BUTTONS);
      RemoteViews big =
          applyStandardTemplate(
              /* showSmallIcon= */ false,
              getBigContentViewLayoutResource(actionCount),
              /* fitIn1U= */ false);

      big.removeAllViews(androidx.media.R.id.media_actions);
      if (actionCount > 0) {
        for (int i = 0; i < actionCount; i++) {
          final RemoteViews button = generateMediaActionButton(mBuilder.mActions.get(i));
          big.addView(androidx.media.R.id.media_actions, button);
        }
      }
      if (showCancelButton) {
        big.setViewVisibility(androidx.media.R.id.cancel_action, View.VISIBLE);
        big.setInt(
            androidx.media.R.id.cancel_action,
            "setAlpha",
            mBuilder
                .mContext
                .getResources()
                .getInteger(androidx.media.R.integer.cancel_button_image_alpha));
        big.setOnClickPendingIntent(androidx.media.R.id.cancel_action, cancelButtonIntent);
      } else {
        big.setViewVisibility(androidx.media.R.id.cancel_action, View.GONE);
      }
      return big;
    }

    /* package */ int getBigContentViewLayoutResource(int actionCount) {
      return actionCount <= 3
          ? androidx.media.R.layout.notification_template_big_media_narrow
          : androidx.media.R.layout.notification_template_big_media;
    }
  }

  /**
   * Notification style for media custom views that are decorated by the system.
   *
   * <p>Instead of providing a media notification that is completely custom, a developer can set
   * this style and still obtain system decorations like the notification header with the expand
   * affordance and actions.
   *
   * <p>Use {@link androidx.core.app.NotificationCompat.Builder #setCustomContentView(RemoteViews)},
   * {@link androidx.core.app.NotificationCompat.Builder #setCustomBigContentView(RemoteViews)} and
   * {@link androidx.core.app.NotificationCompat.Builder #setCustomHeadsUpContentView(RemoteViews)}
   * to set the corresponding custom views to display.
   *
   * <p>To use this style with your Notification, feed it to {@link
   * androidx.core.app.NotificationCompat.Builder
   * #setStyle(androidx.core.app.NotificationCompat.Style)} like so:
   *
   * <pre class="prettyprint">
   * Notification noti = new NotificationCompat.Builder()
   *     .setSmallIcon(androidx.media.R.drawable.ic_stat_player)
   *     .setLargeIcon(albumArtBitmap))
   *     .setCustomContentView(contentView)
   *     .setStyle(<b>new NotificationCompat.DecoratedMediaCustomViewStyle()</b>
   *          .setMediaSession(mySession))
   *     .build();
   * </pre>
   *
   * <p>If you are using this style, consider using the corresponding styles like {@link
   * androidx.media.R.style#TextAppearance_Compat_Notification_Media} or {@link
   * androidx.media.R.style#TextAppearance_Compat_Notification_Title_Media} in your custom views in
   * order to get the correct styling on each platform version.
   *
   * @see androidx.core.app.NotificationCompat.DecoratedCustomViewStyle
   * @see MediaStyle
   */
  public static class DecoratedMediaCustomViewStyle extends MediaStyle {

    public DecoratedMediaCustomViewStyle(MediaSession session) {
      super(session);
    }

    @Override
    public void apply(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 34 && remoteDeviceName != null) {
        Api21Impl.setMediaStyle(
            builder.getBuilder(),
            Api21Impl.fillInMediaStyle(
                Api34Impl.setRemotePlaybackInfo(
                    Api24Impl.createDecoratedMediaCustomViewStyle(),
                    remoteDeviceName,
                    remoteDeviceIconRes,
                    remoteDeviceIntent),
                actionsToShowInCompact,
                session));
      } else if (Util.SDK_INT >= 24) {
        Api21Impl.setMediaStyle(
            builder.getBuilder(),
            Api21Impl.fillInMediaStyle(
                Api24Impl.createDecoratedMediaCustomViewStyle(), actionsToShowInCompact, session));
        Bundle bundle = new Bundle();
        bundle.putBundle(EXTRA_MEDIA3_SESSION, session.getToken().toBundle());
        builder.getBuilder().addExtras(bundle);
      } else {
        super.apply(builder);
      }
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 24) {
        // No custom content view required
        return null;
      }
      boolean hasContentView = mBuilder.getContentView() != null;
      if (Util.SDK_INT >= 21) {
        // If we are on L/M the media notification will only be colored if the expanded
        // version is of media style, so we have to create a custom view for the collapsed
        // version as well in that case.
        boolean createCustomContent = hasContentView || mBuilder.getBigContentView() != null;
        if (createCustomContent) {
          RemoteViews contentView = generateContentView();
          if (hasContentView) {
            buildIntoRemoteViews(contentView, mBuilder.getContentView());
          }
          setBackgroundColor(contentView);
          return contentView;
        }
      } else {
        RemoteViews contentView = generateContentView();
        if (hasContentView) {
          buildIntoRemoteViews(contentView, mBuilder.getContentView());
          return contentView;
        }
      }
      return null;
    }

    @Override
    /* package */ int getContentViewLayoutResource() {
      return mBuilder.getContentView() != null
          ? androidx.media.R.layout.notification_template_media_custom
          : super.getContentViewLayoutResource();
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeBigContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 24) {
        // No custom big content view required
        return null;
      }
      RemoteViews innerView =
          mBuilder.getBigContentView() != null
              ? mBuilder.getBigContentView()
              : mBuilder.getContentView();
      if (innerView == null) {
        // No expandable notification
        return null;
      }
      RemoteViews bigContentView = generateBigContentView();
      buildIntoRemoteViews(bigContentView, innerView);
      if (Util.SDK_INT >= 21) {
        setBackgroundColor(bigContentView);
      }
      return bigContentView;
    }

    @Override
    /* package */ int getBigContentViewLayoutResource(int actionCount) {
      return actionCount <= 3
          ? androidx.media.R.layout.notification_template_big_media_narrow_custom
          : androidx.media.R.layout.notification_template_big_media_custom;
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeHeadsUpContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 24) {
        // No custom heads up content view required
        return null;
      }
      RemoteViews innerView =
          mBuilder.getHeadsUpContentView() != null
              ? mBuilder.getHeadsUpContentView()
              : mBuilder.getContentView();
      if (innerView == null) {
        // No expandable notification
        return null;
      }
      RemoteViews headsUpContentView = generateBigContentView();
      buildIntoRemoteViews(headsUpContentView, innerView);
      if (Util.SDK_INT >= 21) {
        setBackgroundColor(headsUpContentView);
      }
      return headsUpContentView;
    }

    private void setBackgroundColor(RemoteViews views) {
      int color =
          mBuilder.getColor() != COLOR_DEFAULT
              ? mBuilder.getColor()
              : mBuilder
                  .mContext
                  .getResources()
                  .getColor(
                      androidx.media.R.color.notification_material_background_media_default_color);
      views.setInt(
          androidx.media.R.id.status_bar_latest_event_content, "setBackgroundColor", color);
    }
  }

  @RequiresApi(21)
  private static class Api21Impl {
    private Api21Impl() {}

    @DoNotInline
    static void setMediaStyle(Notification.Builder builder, Notification.MediaStyle style) {
      builder.setStyle(style);
    }

    @DoNotInline
    public static Notification.MediaStyle createMediaStyle() {
      return new Notification.MediaStyle();
    }

    @DoNotInline
    public static Notification.MediaStyle fillInMediaStyle(
        Notification.MediaStyle style,
        @Nullable int[] actionsToShowInCompact,
        MediaSession session) {
      checkNotNull(style);
      checkNotNull(session);
      if (actionsToShowInCompact != null) {
        setShowActionsInCompactView(style, actionsToShowInCompact);
      }
      MediaSessionCompat.Token legacyToken = session.getSessionCompatToken();
      style.setMediaSession((android.media.session.MediaSession.Token) legacyToken.getToken());
      return style;
    }

    @DoNotInline
    public static void setShowActionsInCompactView(Notification.MediaStyle style, int... actions) {
      style.setShowActionsInCompactView(actions);
    }
  }

  @RequiresApi(24)
  private static class Api24Impl {
    private Api24Impl() {}

    @DoNotInline
    public static Notification.DecoratedMediaCustomViewStyle createDecoratedMediaCustomViewStyle() {
      return new Notification.DecoratedMediaCustomViewStyle();
    }
  }

  @RequiresApi(34)
  private static class Api34Impl {

    private Api34Impl() {}

    // MEDIA_CONTENT_CONTROL permission is required by setRemotePlaybackInfo
    @CanIgnoreReturnValue
    @SuppressLint({"MissingPermission"})
    @DoNotInline
    public static Notification.MediaStyle setRemotePlaybackInfo(
        Notification.MediaStyle style,
        CharSequence remoteDeviceName,
        @DrawableRes int remoteDeviceIconRes,
        @Nullable PendingIntent remoteDeviceIntent) {
      style.setRemotePlaybackInfo(remoteDeviceName, remoteDeviceIconRes, remoteDeviceIntent);
      return style;
    }
  }
}
