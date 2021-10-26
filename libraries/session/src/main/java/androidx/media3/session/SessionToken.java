/*
 * Copyright 2019 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ResultReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media3.common.Bundleable;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * A token that represents an ongoing {@link MediaSession} or a service ({@link
 * MediaSessionService}, {@link MediaLibraryService}, or {@link MediaBrowserServiceCompat}). If it
 * represents a service, it may not be ongoing.
 *
 * <p>This may be passed to apps by the session owner to allow them to create a {@link
 * MediaController} or a {@link MediaBrowser} to communicate with the session.
 *
 * <p>It can also be obtained by {@link #getAllServiceTokens(Context)}.
 */
// New version of MediaSession.Token for following reasons
//   - Stop implementing Parcelable for updatable support
//   - Represent session and library service (formerly browser service) in one class.
//     Previously MediaSession.Token was for session and ComponentName was for service.
//     This helps controller apps to keep target of dispatching media key events in uniform way.
//     For details about the reason, see following. (Android O+)
//         android.media.session.MediaSessionManager.Callback#onAddressedPlayerChanged
public final class SessionToken implements Bundleable {

  private static final long WAIT_TIME_MS_FOR_SESSION3_TOKEN = 500;

  /** Types of {@link SessionToken}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {TYPE_SESSION, TYPE_SESSION_SERVICE, TYPE_LIBRARY_SERVICE})
  public @interface TokenType {}

  /** Type for {@link MediaSession}. */
  public static final int TYPE_SESSION = 0;

  /** Type for {@link MediaSessionService}. */
  public static final int TYPE_SESSION_SERVICE = 1;

  /** Type for {@link MediaLibraryService}. */
  public static final int TYPE_LIBRARY_SERVICE = 2;

  /** Type for {@link MediaSessionCompat}. */
  /* package */ static final int TYPE_SESSION_LEGACY = 100;

  /** Type for {@link MediaBrowserServiceCompat}. */
  /* package */ static final int TYPE_BROWSER_SERVICE_LEGACY = 101;

  private final SessionTokenImpl impl;

  /**
   * Creates a token for {@link MediaController} or {@link MediaBrowser} to connect to one of {@link
   * MediaSessionService}, {@link MediaLibraryService}, or {@link MediaBrowserServiceCompat}.
   *
   * @param context The context.
   * @param serviceComponent The component name of the service.
   */
  public SessionToken(Context context, ComponentName serviceComponent) {
    checkNotNull(context, "context must not be null");
    checkNotNull(serviceComponent, "serviceComponent must not be null");
    PackageManager manager = context.getPackageManager();
    int uid = getUid(manager, serviceComponent.getPackageName());

    int type;
    if (isInterfaceDeclared(manager, MediaLibraryService.SERVICE_INTERFACE, serviceComponent)) {
      type = TYPE_LIBRARY_SERVICE;
    } else if (isInterfaceDeclared(
        manager, MediaSessionService.SERVICE_INTERFACE, serviceComponent)) {
      type = TYPE_SESSION_SERVICE;
    } else if (isInterfaceDeclared(
        manager, MediaBrowserServiceCompat.SERVICE_INTERFACE, serviceComponent)) {
      type = TYPE_BROWSER_SERVICE_LEGACY;
    } else {
      throw new IllegalArgumentException(
          serviceComponent
              + " doesn't implement none of"
              + " MediaSessionService, MediaLibraryService, MediaBrowserService nor"
              + " MediaBrowserServiceCompat. Use service's full name");
    }
    if (type != TYPE_BROWSER_SERVICE_LEGACY) {
      impl = new SessionTokenImplBase(serviceComponent, uid, type);
    } else {
      impl = new SessionTokenImplLegacy(serviceComponent, uid);
    }
  }

  /* package */ SessionToken(
      int uid,
      int type,
      int version,
      String packageName,
      IMediaSession iSession,
      Bundle tokenExtras) {
    impl = new SessionTokenImplBase(uid, type, version, packageName, iSession, tokenExtras);
  }

  /* package */ SessionToken(Context context, MediaSessionCompat.Token compatToken) {
    checkNotNull(context, "context must not be null");
    checkNotNull(compatToken, "compatToken must not be null");

    MediaControllerCompat controller = createMediaControllerCompat(context, compatToken);

    String packageName = controller.getPackageName();
    int uid = getUid(context.getPackageManager(), packageName);
    Bundle extras = controller.getSessionInfo();

    impl = new SessionTokenImplLegacy(compatToken, packageName, uid, extras);
  }

  /* package */ SessionToken(SessionTokenImpl impl) {
    this.impl = impl;
  }

  private SessionToken(Bundle bundle) {
    checkArgument(bundle.containsKey(keyForField(FIELD_IMPL_TYPE)), "Impl type needs to be set.");
    @SessionTokenImplType int implType = bundle.getInt(keyForField(FIELD_IMPL_TYPE));
    Bundle implBundle = checkNotNull(bundle.getBundle(keyForField(FIELD_IMPL)));
    if (implType == IMPL_TYPE_BASE) {
      impl = SessionTokenImplBase.CREATOR.fromBundle(implBundle);
    } else {
      impl = SessionTokenImplLegacy.CREATOR.fromBundle(implBundle);
    }
  }

  @Override
  public int hashCode() {
    return impl.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof SessionToken)) {
      return false;
    }
    SessionToken other = (SessionToken) obj;
    return impl.equals(other.impl);
  }

  @Override
  public String toString() {
    return impl.toString();
  }

  /** Returns the uid of the session */
  public int getUid() {
    return impl.getUid();
  }

  /** Returns the package name of the session */
  public String getPackageName() {
    return impl.getPackageName();
  }

  /**
   * Returns the service name of the session. It will be an empty string if the {@link #getType()
   * type} is {@link #TYPE_SESSION}.
   */
  public String getServiceName() {
    return impl.getServiceName();
  }

  /**
   * Returns the component name of the session. It will be {@code null} if the {@link #getType()
   * type} is {@link #TYPE_SESSION}.
   */
  @Nullable
  /* package */ ComponentName getComponentName() {
    return impl.getComponentName();
  }

  /**
   * Returns the type of this token. One of {@link #TYPE_SESSION}, {@link #TYPE_SESSION_SERVICE}, or
   * {@link #TYPE_LIBRARY_SERVICE}.
   */
  @TokenType
  public int getType() {
    return impl.getType();
  }

  /**
   * Returns the library version of the session if the {@link #getType() type} is {@link
   * #TYPE_SESSION}. Otherwise, it returns {@code 0}.
   *
   * <p>It will be the same as {@link MediaLibraryInfo#VERSION_INT} of the session, or less than
   * {@code 1000000} if the session is a legacy session.
   */
  public int getSessionVersion() {
    return impl.getSessionVersion();
  }

  /**
   * Returns the extra {@link Bundle} of this token.
   *
   * @see MediaSession.Builder#setExtras(Bundle)
   */
  public Bundle getExtras() {
    return impl.getExtras();
  }

  /* package */ boolean isLegacySession() {
    return impl.isLegacySession();
  }

  @Nullable
  /* package */ Object getBinder() {
    return impl.getBinder();
  }

  /**
   * Creates a token from {@link MediaSessionCompat.Token}.
   *
   * @return a {@link ListenableFuture} of {@link SessionToken}
   */
  @UnstableApi
  public static ListenableFuture<SessionToken> createSessionToken(
      Context context, Object compatToken) {
    checkNotNull(context, "context must not be null");
    checkNotNull(compatToken, "compatToken must not be null");
    checkArgument(compatToken instanceof MediaSessionCompat.Token);

    HandlerThread thread = new HandlerThread("SessionTokenThread");
    thread.start();

    SettableFuture<SessionToken> future = SettableFuture.create();
    // Try retrieving media3 token by connecting to the session.
    MediaControllerCompat controller =
        createMediaControllerCompat(context, (MediaSessionCompat.Token) compatToken);
    String packageName = controller.getPackageName();
    int uid = getUid(context.getPackageManager(), packageName);
    Handler handler = new Handler(thread.getLooper());
    controller.sendCommand(
        MediaConstants.SESSION_COMMAND_REQUEST_SESSION3_TOKEN,
        /* params= */ null,
        new ResultReceiver(handler) {
          @Override
          protected void onReceiveResult(int resultCode, Bundle resultData) {
            handler.removeCallbacksAndMessages(null);
            future.set(SessionToken.CREATOR.fromBundle(resultData));
          }
        });

    handler.postDelayed(
        () -> {
          // Timed out getting session3 token. Handle this as a legacy token.
          SessionToken resultToken =
              new SessionToken(
                  new SessionTokenImplLegacy(
                      (MediaSessionCompat.Token) compatToken,
                      packageName,
                      uid,
                      controller.getSessionInfo()));
          future.set(resultToken);
        },
        WAIT_TIME_MS_FOR_SESSION3_TOKEN);
    future.addListener(() -> thread.quit(), MoreExecutors.directExecutor());
    return future;
  }

  /**
   * Returns a {@link ImmutableSet} of {@link SessionToken} for media session services; {@link
   * MediaSessionService}, {@link MediaLibraryService}, and {@link MediaBrowserServiceCompat}
   * regardless of their activeness. This set represents media apps that publish {@link
   * MediaSession}.
   *
   * <p>The app targeting API level 30 or higher must include a {@code <queries>} element in their
   * manifest to get service tokens of other apps. See the following example and <a
   * href="//developer.android.com/training/package-visibility">this guide</a> for more information.
   *
   * <pre>{@code
   * <intent>
   *   <action android:name="androidx.media3.session.MediaSessionService" />
   * </intent>
   * <intent>
   *   <action android:name="androidx.media3.session.MediaLibraryService" />
   * </intent>
   * <intent>
   *   <action android:name="android.media.browse.MediaBrowserService" />
   * </intent>
   * }</pre>
   */
  public static ImmutableSet<SessionToken> getAllServiceTokens(Context context) {
    PackageManager pm = context.getPackageManager();
    List<ResolveInfo> services = new ArrayList<>();
    // If multiple actions are declared for a service, browser gets higher priority.
    List<ResolveInfo> libraryServices =
        pm.queryIntentServices(
            new Intent(MediaLibraryService.SERVICE_INTERFACE), PackageManager.GET_META_DATA);
    if (libraryServices != null) {
      services.addAll(libraryServices);
    }
    List<ResolveInfo> sessionServices =
        pm.queryIntentServices(
            new Intent(MediaSessionService.SERVICE_INTERFACE), PackageManager.GET_META_DATA);
    if (sessionServices != null) {
      services.addAll(sessionServices);
    }
    List<ResolveInfo> browserServices =
        pm.queryIntentServices(
            new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE), PackageManager.GET_META_DATA);
    if (browserServices != null) {
      services.addAll(browserServices);
    }

    ImmutableSet.Builder<SessionToken> sessionServiceTokens = ImmutableSet.builder();
    for (ResolveInfo service : services) {
      if (service == null || service.serviceInfo == null) {
        continue;
      }
      ServiceInfo serviceInfo = service.serviceInfo;
      SessionToken token =
          new SessionToken(context, new ComponentName(serviceInfo.packageName, serviceInfo.name));
      sessionServiceTokens.add(token);
    }
    return sessionServiceTokens.build();
  }

  private static boolean isInterfaceDeclared(
      PackageManager manager, String serviceInterface, ComponentName serviceComponent) {
    Intent serviceIntent = new Intent(serviceInterface);
    // Use queryIntentServices to find services with MediaLibraryService.SERVICE_INTERFACE.
    // We cannot use resolveService with intent specified class name, because resolveService
    // ignores actions if Intent.setClassName() is specified.
    serviceIntent.setPackage(serviceComponent.getPackageName());

    List<ResolveInfo> list =
        manager.queryIntentServices(serviceIntent, PackageManager.GET_META_DATA);
    if (list != null) {
      for (int i = 0; i < list.size(); i++) {
        ResolveInfo resolveInfo = list.get(i);
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
          continue;
        }
        if (TextUtils.equals(resolveInfo.serviceInfo.name, serviceComponent.getClassName())) {
          return true;
        }
      }
    }
    return false;
  }

  private static int getUid(PackageManager manager, String packageName) {
    try {
      return manager.getApplicationInfo(packageName, 0).uid;
    } catch (PackageManager.NameNotFoundException e) {
      throw new IllegalArgumentException("Cannot find package " + packageName);
    }
  }

  private static MediaControllerCompat createMediaControllerCompat(
      Context context, MediaSessionCompat.Token sessionToken) {
    return new MediaControllerCompat(context, sessionToken);
  }

  /* package */ interface SessionTokenImpl extends Bundleable {

    boolean isLegacySession();

    int getUid();

    String getPackageName();

    String getServiceName();

    @Nullable
    ComponentName getComponentName();

    @TokenType
    int getType();

    int getSessionVersion();

    Bundle getExtras();

    @Nullable
    Object getBinder();
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FIELD_IMPL_TYPE, FIELD_IMPL})
  private @interface FieldNumber {}

  private static final int FIELD_IMPL_TYPE = 0;
  private static final int FIELD_IMPL = 1;

  /** Types of {@link SessionTokenImpl} */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({IMPL_TYPE_BASE, IMPL_TYPE_LEGACY})
  private @interface SessionTokenImplType {}

  private static final int IMPL_TYPE_BASE = 0;
  private static final int IMPL_TYPE_LEGACY = 1;

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (impl instanceof SessionTokenImplBase) {
      bundle.putInt(keyForField(FIELD_IMPL_TYPE), IMPL_TYPE_BASE);
    } else {
      bundle.putInt(keyForField(FIELD_IMPL_TYPE), IMPL_TYPE_LEGACY);
    }
    bundle.putBundle(keyForField(FIELD_IMPL), impl.toBundle());
    return bundle;
  }

  /** Object that can restore {@link SessionToken} from a {@link Bundle}. */
  @UnstableApi public static final Creator<SessionToken> CREATOR = SessionToken::fromBundle;

  private static SessionToken fromBundle(Bundle bundle) {
    return new SessionToken(bundle);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
