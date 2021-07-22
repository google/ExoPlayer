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
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Util;

/** DRM-related utility methods. */
public final class DrmUtil {

  /**
   * Returns the {@link PlaybackException.ErrorCode} that corresponds to the given DRM-related
   * exception.
   *
   * @param exception The DRM-related exception for which to obtain a corresponding {@link
   *     PlaybackException.ErrorCode}.
   * @param thrownByExoMediaDrm Whether the given exception originated in a {@link ExoMediaDrm}
   *     method. Exceptions that did not originate in {@link ExoMediaDrm} are assumed to originate
   *     in the license request.
   * @return The {@link PlaybackException.ErrorCode} that corresponds to the given DRM-related
   *     exception.
   */
  @PlaybackException.ErrorCode
  public static int getErrorCodeForMediaDrmException(
      Exception exception, boolean thrownByExoMediaDrm) {
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
    } else if (thrownByExoMediaDrm) {
      // A MediaDrm exception was thrown but it was impossible to determine the cause. Because no
      // better diagnosis tools were provided, we treat this as a system error.
      return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR;
    } else {
      // The error happened during the license request.
      return PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED;
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
