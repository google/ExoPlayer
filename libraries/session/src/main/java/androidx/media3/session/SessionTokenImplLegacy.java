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
import static androidx.media3.common.util.Assertions.checkNotEmpty;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.SessionToken.TYPE_BROWSER_SERVICE_LEGACY;
import static androidx.media3.session.SessionToken.TYPE_LIBRARY_SERVICE;
import static androidx.media3.session.SessionToken.TYPE_SESSION;
import static androidx.media3.session.SessionToken.TYPE_SESSION_LEGACY;

import android.content.ComponentName;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import androidx.media3.session.SessionToken.SessionTokenImpl;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/* package */ final class SessionTokenImplLegacy implements SessionTokenImpl {

  @Nullable private final MediaSessionCompat.Token legacyToken;

  private final int uid;

  private final int type;

  @Nullable private final ComponentName componentName;

  private final String packageName;

  private final Bundle extras;

  public SessionTokenImplLegacy(
      MediaSessionCompat.Token token, String packageName, int uid, Bundle extras) {
    legacyToken = checkNotNull(token);
    this.uid = uid;
    this.packageName = checkNotEmpty(packageName);
    componentName = null;
    type = TYPE_SESSION_LEGACY;
    this.extras = checkNotNull(extras);
  }

  public SessionTokenImplLegacy(ComponentName serviceComponent, int uid) {
    legacyToken = null;
    this.uid = uid;
    type = TYPE_BROWSER_SERVICE_LEGACY;
    packageName = checkNotNull(serviceComponent).getPackageName();
    componentName = serviceComponent;
    extras = Bundle.EMPTY;
  }

  private SessionTokenImplLegacy(
      @Nullable MediaSessionCompat.Token legacyToken,
      int uid,
      int type,
      @Nullable ComponentName componentName,
      String packageName,
      Bundle extras) {
    this.legacyToken = legacyToken;
    this.uid = uid;
    this.type = type;
    this.componentName = componentName;
    this.packageName = packageName;
    this.extras = extras;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, componentName, legacyToken);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof SessionTokenImplLegacy)) {
      return false;
    }
    SessionTokenImplLegacy other = (SessionTokenImplLegacy) obj;
    if (type != other.type) {
      return false;
    }
    switch (type) {
      case TYPE_SESSION_LEGACY:
        return Util.areEqual(legacyToken, other.legacyToken);
      case TYPE_BROWSER_SERVICE_LEGACY:
        return Util.areEqual(componentName, other.componentName);
    }
    return false;
  }

  @Override
  public boolean isLegacySession() {
    return true;
  }

  @Override
  public String toString() {
    return "SessionToken {legacyToken=" + legacyToken + "}";
  }

  @Override
  public int getUid() {
    return uid;
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public String getServiceName() {
    return componentName == null ? "" : componentName.getClassName();
  }

  @Override
  @Nullable
  public ComponentName getComponentName() {
    return componentName;
  }

  @Override
  @SessionToken.TokenType
  public int getType() {
    switch (type) {
      case TYPE_SESSION_LEGACY:
        return TYPE_SESSION;
      case TYPE_BROWSER_SERVICE_LEGACY:
        return TYPE_LIBRARY_SERVICE;
    }
    return TYPE_SESSION;
  }

  @Override
  public int getSessionVersion() {
    return 0;
  }

  @Override
  public Bundle getExtras() {
    return new Bundle(extras);
  }

  @Override
  @Nullable
  public Object getBinder() {
    return legacyToken;
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_LEGACY_TOKEN,
    FIELD_UID,
    FIELD_TYPE,
    FIELD_COMPONENT_NAME,
    FIELD_PACKAGE_NAME,
    FIELD_EXTRAS
  })
  private @interface FieldNumber {}

  private static final int FIELD_LEGACY_TOKEN = 0;
  private static final int FIELD_UID = 1;
  private static final int FIELD_TYPE = 2;
  private static final int FIELD_COMPONENT_NAME = 3;
  private static final int FIELD_PACKAGE_NAME = 4;
  private static final int FIELD_EXTRAS = 5;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putBundle(
        keyForField(FIELD_LEGACY_TOKEN), legacyToken == null ? null : legacyToken.toBundle());
    bundle.putInt(keyForField(FIELD_UID), uid);
    bundle.putInt(keyForField(FIELD_TYPE), type);
    bundle.putParcelable(keyForField(FIELD_COMPONENT_NAME), componentName);
    bundle.putString(keyForField(FIELD_PACKAGE_NAME), packageName);
    bundle.putBundle(keyForField(FIELD_EXTRAS), extras);
    return bundle;
  }

  /** Object that can restore {@link SessionTokenImplLegacy} from a {@link Bundle}. */
  public static final Creator<SessionTokenImplLegacy> CREATOR = SessionTokenImplLegacy::fromBundle;

  private static SessionTokenImplLegacy fromBundle(Bundle bundle) {
    @Nullable Bundle legacyTokenBundle = bundle.getBundle(keyForField(FIELD_LEGACY_TOKEN));
    @Nullable
    MediaSessionCompat.Token legacyToken =
        legacyTokenBundle == null ? null : MediaSessionCompat.Token.fromBundle(legacyTokenBundle);
    checkArgument(bundle.containsKey(keyForField(FIELD_UID)), "uid should be set.");
    int uid = bundle.getInt(keyForField(FIELD_UID));
    checkArgument(bundle.containsKey(keyForField(FIELD_TYPE)), "type should be set.");
    int type = bundle.getInt(keyForField(FIELD_TYPE));
    @Nullable ComponentName componentName = bundle.getParcelable(keyForField(FIELD_COMPONENT_NAME));
    String packageName =
        checkNotEmpty(
            bundle.getString(keyForField(FIELD_PACKAGE_NAME)), "package name should be set.");
    @Nullable Bundle extras = bundle.getBundle(keyForField(FIELD_EXTRAS));
    return new SessionTokenImplLegacy(
        legacyToken, uid, type, componentName, packageName, extras == null ? Bundle.EMPTY : extras);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
