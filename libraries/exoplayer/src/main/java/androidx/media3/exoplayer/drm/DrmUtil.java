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
package androidx.media3.exoplayer.drm;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.MediaDrmResetException;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import androidx.annotation.DoNotInline;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** DRM-related utility methods. */
@UnstableApi
public final class DrmUtil {

  /** Identifies the operation which caused a DRM-related error. */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      value = {
        ERROR_SOURCE_EXO_MEDIA_DRM,
        ERROR_SOURCE_LICENSE_ACQUISITION,
        ERROR_SOURCE_PROVISIONING
      })
  public @interface ErrorSource {}

  /** Corresponds to failures caused by an {@link ExoMediaDrm} method call. */
  public static final int ERROR_SOURCE_EXO_MEDIA_DRM = 1;

  /** Corresponds to failures caused by an operation related to obtaining DRM licenses. */
  public static final int ERROR_SOURCE_LICENSE_ACQUISITION = 2;

  /** Corresponds to failures caused by an operation related to provisioning the device. */
  public static final int ERROR_SOURCE_PROVISIONING = 3;

  /**
   * Returns the {@link PlaybackException.ErrorCode} that corresponds to the given DRM-related
   * exception.
   *
   * @param exception The DRM-related exception for which to obtain a corresponding {@link
   *     PlaybackException.ErrorCode}.
   * @param errorSource The {@link ErrorSource} for the given {@code exception}.
   * @return The {@link PlaybackException.ErrorCode} that corresponds to the given DRM-related
   *     exception.
   */
  public static @PlaybackException.ErrorCode int getErrorCodeForMediaDrmException(
      Throwable exception, @ErrorSource int errorSource) {
    if (Util.SDK_INT >= 21 && Api21.isMediaDrmStateException(exception)) {
      return Api21.mediaDrmStateExceptionToErrorCode(exception);
    } else if (Util.SDK_INT >= 23 && Api23.isMediaDrmResetException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR;
    } else if (exception instanceof NotProvisionedException
        || isFailureToConstructNotProvisionedException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED;
    } else if (exception instanceof DeniedByServerException) {
      return PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED;
    } else if (exception instanceof UnsupportedDrmException) {
      return PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED;
    } else if (exception instanceof DefaultDrmSessionManager.MissingSchemeDataException) {
      return PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR;
    } else if (exception instanceof KeysExpiredException) {
      return PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED;
    } else if (errorSource == ERROR_SOURCE_EXO_MEDIA_DRM) {
      // A MediaDrm exception was thrown but it was impossible to determine the cause. Because no
      // better diagnosis tools were provided, we treat this as a system error.
      return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR;
    } else if (errorSource == ERROR_SOURCE_LICENSE_ACQUISITION) {
      return PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED;
    } else if (errorSource == ERROR_SOURCE_PROVISIONING) {
      return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED;
    } else {
      // Should never happen.
      throw new IllegalArgumentException();
    }
  }

  /**
   * Returns true if {@code e} represents a failure to construct a {@link NotProvisionedException}.
   * See b/291440132.
   */
  public static boolean isFailureToConstructNotProvisionedException(@Nullable Throwable e) {
    return Util.SDK_INT == 34
        && e instanceof NoSuchMethodError
        && e.getMessage() != null
        && e.getMessage().contains("Landroid/media/NotProvisionedException;.<init>(");
  }

  /**
   * Returns true if {@code e} represents a failure to construct a {@link ResourceBusyException}.
   * See b/291440132.
   */
  public static boolean isFailureToConstructResourceBusyException(@Nullable Throwable e) {
    return Util.SDK_INT == 34
        && e instanceof NoSuchMethodError
        && e.getMessage() != null
        && e.getMessage().contains("Landroid/media/ResourceBusyException;.<init>(");
  }

  // Internal classes.

  @RequiresApi(21)
  private static final class Api21 {

    @DoNotInline
    public static boolean isMediaDrmStateException(@Nullable Throwable throwable) {
      return throwable instanceof MediaDrm.MediaDrmStateException;
    }

    @DoNotInline
    public static @PlaybackException.ErrorCode int mediaDrmStateExceptionToErrorCode(
        Throwable throwable) {
      @Nullable
      String diagnosticsInfo = ((MediaDrm.MediaDrmStateException) throwable).getDiagnosticInfo();
      int drmErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo);
      return Util.getErrorCodeForMediaDrmErrorCode(drmErrorCode);
    }
  }

  @RequiresApi(23)
  private static final class Api23 {

    @DoNotInline
    public static boolean isMediaDrmResetException(@Nullable Throwable throwable) {
      return throwable instanceof MediaDrmResetException;
    }
  }

  // Prevent instantiation.

  private DrmUtil() {}
}
