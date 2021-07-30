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
package com.google.android.exoplayer2.drm;

import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.MediaDrmResetException;
import android.media.NotProvisionedException;
import androidx.annotation.DoNotInline;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** DRM-related utility methods. */
public final class DrmUtil {

  /** Identifies the operation which caused a DRM-related error. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
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
  @PlaybackException.ErrorCode
  public static int getErrorCodeForMediaDrmException(
      Exception exception, @ErrorSource int errorSource) {
    if (Util.SDK_INT >= 21 && PlatformOperationsWrapperV21.isMediaDrmStateException(exception)) {
      return PlatformOperationsWrapperV21.mediaDrmStateExceptionToErrorCode(exception);
    } else if (Util.SDK_INT >= 23
        && PlatformOperationsWrapperV23.isMediaDrmResetException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR;
    } else if (Util.SDK_INT >= 18
        && PlatformOperationsWrapperV18.isNotProvisionedException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED;
    } else if (Util.SDK_INT >= 18
        && PlatformOperationsWrapperV18.isDeniedByServerException(exception)) {
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

  // Internal classes.

  @RequiresApi(18)
  private static final class PlatformOperationsWrapperV18 {

    @DoNotInline
    public static boolean isNotProvisionedException(@Nullable Throwable throwable) {
      return throwable instanceof NotProvisionedException;
    }

    @DoNotInline
    public static boolean isDeniedByServerException(@Nullable Throwable throwable) {
      return throwable instanceof DeniedByServerException;
    }
  }

  @RequiresApi(21)
  private static final class PlatformOperationsWrapperV21 {

    @DoNotInline
    public static boolean isMediaDrmStateException(@Nullable Throwable throwable) {
      return throwable instanceof MediaDrm.MediaDrmStateException;
    }

    @DoNotInline
    @PlaybackException.ErrorCode
    public static int mediaDrmStateExceptionToErrorCode(Throwable throwable) {
      @Nullable
      String diagnosticsInfo = ((MediaDrm.MediaDrmStateException) throwable).getDiagnosticInfo();
      int drmErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo);
      return C.getErrorCodeForMediaDrmErrorCode(drmErrorCode);
    }
  }

  @RequiresApi(23)
  private static final class PlatformOperationsWrapperV23 {

    @DoNotInline
    public static boolean isMediaDrmResetException(@Nullable Throwable throwable) {
      return throwable instanceof MediaDrmResetException;
    }
  }

  // Prevent instantiation.

  private DrmUtil() {}
}
