/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.view.KeyEvent;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A media button receiver receives hardware media playback button intent, such as those sent by
 * wired and wireless headsets.
 *
 * <p>You can add this MediaButtonReceiver to your app by adding it directly to your
 * AndroidManifest.xml:
 *
 * <pre>
 * &lt;receiver
 *     android:name="androidx.media3.session.MediaButtonReceiver"
 *     android:exported="true"&gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="android.intent.action.MEDIA_BUTTON" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/receiver&gt;
 * </pre>
 *
 * <p>Apps that add this receiver to the manifest, must implement {@link
 * MediaSession.Callback#onPlaybackResumption} or active automatic playback resumption (Note: If you
 * choose to make this receiver start your own service that is not a {@link MediaSessionService} or
 * {@link MediaLibraryService}, then you need to fulfill all requirements around starting a service
 * in the foreground on all API levels your app should properly work on).
 *
 * <h2>Service discovery</h2>
 *
 * <p>This class assumes you have a {@link Service} in your app's manifest that controls media
 * playback via a {@link MediaSession}. Once a key event is received by this receiver, it tries to
 * find a {@link Service} that can handle the action {@link Intent#ACTION_MEDIA_BUTTON}, {@link
 * MediaSessionService#SERVICE_INTERFACE} or {@link MediaSessionService#SERVICE_INTERFACE}. If an
 * appropriate service is found, this class starts the service as a foreground service and sends the
 * key event to the service by an {@link Intent} with action {@link Intent#ACTION_MEDIA_BUTTON}. If
 * neither is available or more than one valid service is found for one of the actions, an {@link
 * IllegalStateException} is thrown.
 *
 * <h3>Service handling ACTION_MEDIA_BUTTON</h3>
 *
 * <p>A service can receive a key event by including an intent filter that handles {@code
 * android.intent.action.MEDIA_BUTTON}.
 *
 * <pre>
 * &lt;service android:name="com.example.android.MediaPlaybackService" &gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="android.intent.action.MEDIA_BUTTON" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/service&gt;
 * </pre>
 *
 * <h3>Service handling action {@link MediaSessionService} or {@link MediaLibraryService}</h3>
 *
 * <p>If you are using a {@link MediaSessionService} or {@link MediaLibraryService}, the service
 * interface name is already used as the intent action. In this case, no further configuration is
 * required.
 *
 * <pre>
 * &lt;service android:name="com.example.android.MediaPlaybackService" &gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="androidx.media3.session.MediaLibraryService" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/service&gt;
 * </pre>
 */
@UnstableApi
public class MediaButtonReceiver extends BroadcastReceiver {

  private static final String TAG = "MediaButtonReceiver";
  private static final String[] ACTIONS = {
    Intent.ACTION_MEDIA_BUTTON,
    MediaLibraryService.SERVICE_INTERFACE,
    MediaSessionService.SERVICE_INTERFACE
  };

  @Override
  public final void onReceive(Context context, @Nullable Intent intent) {
    if (intent == null
        || !Objects.equals(intent.getAction(), Intent.ACTION_MEDIA_BUTTON)
        || !intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
      android.util.Log.d(TAG, "Ignore unsupported intent: " + intent);
      return;
    }

    if (Util.SDK_INT >= 26) {
      @Nullable
      KeyEvent keyEvent = checkNotNull(intent.getExtras()).getParcelable(Intent.EXTRA_KEY_EVENT);
      if (keyEvent != null
          && keyEvent.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY
          && keyEvent.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
        // Starting with Android 8 (API 26), the service must be started immediately in the
        // foreground when being started. Also starting with Android 8, the system sends media
        // button intents to this receiver only when the session is released or not active, meaning
        // the service is not running. Hence we only accept a PLAY command here that ensures that
        // playback is started and the MediaSessionService/MediaLibraryService is put into the
        // foreground (see https://developer.android.com/media/legacy/media-buttons and
        // https://developer.android.com/about/versions/oreo/android-8.0-changes#back-all).
        android.util.Log.w(
            TAG,
            "Ignore key event that is not a `play` command on API 26 or above to avoid an"
                + " 'ForegroundServiceDidNotStartInTimeException'");
        return;
      }
    }

    for (String action : ACTIONS) {
      ComponentName mediaButtonServiceComponentName = getServiceComponentByAction(context, action);
      if (mediaButtonServiceComponentName != null) {
        intent.setComponent(mediaButtonServiceComponentName);
        try {
          ContextCompat.startForegroundService(context, intent);
        } catch (/* ForegroundServiceStartNotAllowedException */ IllegalStateException e) {
          if (Build.VERSION.SDK_INT >= 31
              && Api31.instanceOfForegroundServiceStartNotAllowedException(e)) {
            onForegroundServiceStartNotAllowedException(
                intent, Api31.castToForegroundServiceStartNotAllowedException(e));
          } else {
            throw e;
          }
        }
        return;
      }
    }
    throw new IllegalStateException(
        "Could not find any Service that handles any of the actions " + Arrays.toString(ACTIONS));
  }

  /**
   * This method is called when an exception is thrown when calling {@link
   * Context#startForegroundService(Intent)} as a result of receiving a media button event.
   *
   * <p>By default, this method only logs the exception and it can be safely overridden. Apps that
   * find that such a media button event has been legitimately sent, may choose to override this
   * method and take the opportunity to post a notification from where the user journey can
   * continue.
   *
   * <p>This exception can be thrown if a broadcast media button event is received and a media
   * service is found in the manifest that is registered to handle {@link
   * Intent#ACTION_MEDIA_BUTTON}. If this happens on API 31+ and the app is in the background then
   * an exception is thrown.
   *
   * <p>With the exception of devices that are running API 20 and below, a media button intent is
   * only required to be sent to this receiver for a Bluetooth media button event that wants to
   * restart the service. In such a case the app gets an exemption and is allowed to start the
   * foreground service. In this case this method will never be called.
   *
   * <p>In all other cases of attempting to start a Media3 service or to send a media button event,
   * apps must use a {@link MediaBrowser} or {@link MediaController} to bind to the service instead
   * of broadcasting an intent.
   *
   * @param intent The intent that was used {@linkplain Context#startForegroundService(Intent) for
   *     starting the foreground service}.
   * @param e The exception thrown by the system and caught by this broadcast receiver.
   */
  @RequiresApi(31)
  protected void onForegroundServiceStartNotAllowedException(
      Intent intent, ForegroundServiceStartNotAllowedException e) {
    Log.e(
        TAG,
        "caught exception when trying to start a foreground service from the "
            + "background: "
            + e.getMessage());
  }

  @SuppressWarnings("QueryPermissionsNeeded") // Needs to be provided in the app manifest.
  @Nullable
  private static ComponentName getServiceComponentByAction(Context context, String action) {
    PackageManager pm = context.getPackageManager();
    Intent queryIntent = new Intent(action);
    queryIntent.setPackage(context.getPackageName());
    List<ResolveInfo> resolveInfos = pm.queryIntentServices(queryIntent, /* flags= */ 0);
    if (resolveInfos.size() == 1) {
      ResolveInfo resolveInfo = resolveInfos.get(0);
      return new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
    } else if (resolveInfos.isEmpty()) {
      return null;
    } else {
      throw new IllegalStateException(
          "Expected 1 service that handles " + action + ", found " + resolveInfos.size());
    }
  }

  @RequiresApi(31)
  private static final class Api31 {
    /**
     * Returns true if the passed exception is a {@link ForegroundServiceStartNotAllowedException}.
     */
    @DoNotInline
    public static boolean instanceOfForegroundServiceStartNotAllowedException(
        IllegalStateException e) {
      return e instanceof ForegroundServiceStartNotAllowedException;
    }

    /**
     * Casts the {@link IllegalStateException} to a {@link
     * ForegroundServiceStartNotAllowedException} and throws an exception if the cast fails.
     */
    @DoNotInline
    public static ForegroundServiceStartNotAllowedException
        castToForegroundServiceStartNotAllowedException(IllegalStateException e) {
      return (ForegroundServiceStartNotAllowedException) e;
    }
  }
}
