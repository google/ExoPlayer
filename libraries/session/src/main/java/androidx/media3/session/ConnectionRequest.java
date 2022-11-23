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
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import androidx.media3.common.MediaLibraryInfo;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by {@link MediaController} to send its state to the {@link MediaSession} to request to
 * connect.
 */
/* package */ class ConnectionRequest implements Bundleable {

  public final int libraryVersion;

  public final int controllerInterfaceVersion;

  public final String packageName;

  public final int pid;

  public final Bundle connectionHints;

  public ConnectionRequest(String packageName, int pid, Bundle connectionHints) {
    this(
        MediaLibraryInfo.VERSION_INT,
        MediaControllerStub.VERSION_INT,
        packageName,
        pid,
        new Bundle(connectionHints));
  }

  private ConnectionRequest(
      int libraryVersion,
      int controllerInterfaceVersion,
      String packageName,
      int pid,
      Bundle connectionHints) {
    this.libraryVersion = libraryVersion;
    this.controllerInterfaceVersion = controllerInterfaceVersion;
    this.packageName = packageName;
    this.pid = pid;
    this.connectionHints = connectionHints;
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FIELD_LIBRARY_VERSION,
    FIELD_PACKAGE_NAME,
    FIELD_PID,
    FIELD_CONNECTION_HINTS,
    FIELD_CONTROLLER_INTERFACE_VERSION
  })
  private @interface FieldNumber {}

  private static final int FIELD_LIBRARY_VERSION = 0;
  private static final int FIELD_PACKAGE_NAME = 1;
  private static final int FIELD_PID = 2;
  private static final int FIELD_CONNECTION_HINTS = 3;
  private static final int FIELD_CONTROLLER_INTERFACE_VERSION = 4;
  // Next id: 5

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(keyForField(FIELD_LIBRARY_VERSION), libraryVersion);
    bundle.putString(keyForField(FIELD_PACKAGE_NAME), packageName);
    bundle.putInt(keyForField(FIELD_PID), pid);
    bundle.putBundle(keyForField(FIELD_CONNECTION_HINTS), connectionHints);
    bundle.putInt(keyForField(FIELD_CONTROLLER_INTERFACE_VERSION), controllerInterfaceVersion);
    return bundle;
  }

  /** Object that can restore {@link ConnectionRequest} from a {@link Bundle}. */
  public static final Creator<ConnectionRequest> CREATOR =
      bundle -> {
        int libraryVersion =
            bundle.getInt(keyForField(FIELD_LIBRARY_VERSION), /* defaultValue= */ 0);
        int controllerInterfaceVersion =
            bundle.getInt(keyForField(FIELD_CONTROLLER_INTERFACE_VERSION), /* defaultValue= */ 0);
        String packageName = checkNotNull(bundle.getString(keyForField(FIELD_PACKAGE_NAME)));
        int pid = bundle.getInt(keyForField(FIELD_PID), /* defaultValue= */ 0);
        checkArgument(pid != 0);
        @Nullable Bundle connectionHints = bundle.getBundle(keyForField(FIELD_CONNECTION_HINTS));
        return new ConnectionRequest(
            libraryVersion,
            controllerInterfaceVersion,
            packageName,
            pid,
            connectionHints == null ? Bundle.EMPTY : connectionHints);
      };

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
