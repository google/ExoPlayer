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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A result to be used with {@link ListenableFuture} for asynchronous calls between {@link
 * MediaSession} and {@link MediaController}.
 */
public final class SessionResult implements Bundleable {

  /**
   * Result codes.
   *
   * <ul>
   *   <li>Error code: Negative integer
   *   <li>Success code: 0
   *   <li>Info code: Positive integer
   * </ul>
   *
   * <ul>
   *   <li>{@code 0 < |code| < 100} : Reserved for Player specific code.
   *   <li>{@code 100 <= |code| < 500} : Session/Controller specific code.
   *   <li>{@code 500 <= |code| < 1000} : Browser/Library session specific code.
   *   <li>{@code 1000 <= |code|} : Reserved for Player custom code.
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    RESULT_SUCCESS,
    RESULT_ERROR_UNKNOWN,
    RESULT_ERROR_INVALID_STATE,
    RESULT_ERROR_BAD_VALUE,
    RESULT_ERROR_PERMISSION_DENIED,
    RESULT_ERROR_IO,
    RESULT_INFO_SKIPPED,
    RESULT_ERROR_SESSION_DISCONNECTED,
    RESULT_ERROR_NOT_SUPPORTED,
    RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED,
    RESULT_ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED,
    RESULT_ERROR_SESSION_CONCURRENT_STREAM_LIMIT,
    RESULT_ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED,
    RESULT_ERROR_SESSION_NOT_AVAILABLE_IN_REGION,
    RESULT_ERROR_SESSION_SKIP_LIMIT_REACHED,
    RESULT_ERROR_SESSION_SETUP_REQUIRED
  })
  public @interface Code {}

  /**
   * Result code representing that the command is successfully completed.
   *
   * <p>Interoperability: This code is also used to tell that the command was successfully sent, but
   * the result is unknown when connected with {@link MediaSessionCompat} or {@link
   * MediaControllerCompat}.
   */
  public static final int RESULT_SUCCESS = 0;

  /** Result code representing that the command is ended with an unknown error. */
  public static final int RESULT_ERROR_UNKNOWN = -1;

  /**
   * Result code representing that the command cannot be completed because the current state is not
   * valid for the command.
   */
  public static final int RESULT_ERROR_INVALID_STATE = -2;

  /** Result code representing that an argument is illegal. */
  public static final int RESULT_ERROR_BAD_VALUE = -3;

  /** Result code representing that the command is not allowed. */
  public static final int RESULT_ERROR_PERMISSION_DENIED = -4;

  /** Result code representing that a file or network related error happened. */
  public static final int RESULT_ERROR_IO = -5;

  /** Result code representing that the command is not supported. */
  public static final int RESULT_ERROR_NOT_SUPPORTED = -6;

  /** Result code representing that the command is skipped. */
  public static final int RESULT_INFO_SKIPPED = 1;

  /** Result code representing that the session and controller were disconnected. */
  public static final int RESULT_ERROR_SESSION_DISCONNECTED = -100;

  /** Result code representing that the authentication has expired. */
  public static final int RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED = -102;

  /** Result code representing that a premium account is required. */
  public static final int RESULT_ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED = -103;

  /** Result code representing that too many concurrent streams are detected. */
  public static final int RESULT_ERROR_SESSION_CONCURRENT_STREAM_LIMIT = -104;

  /** Result code representing that the content is blocked due to parental controls. */
  public static final int RESULT_ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED = -105;

  /** Result code representing that the content is blocked due to being regionally unavailable. */
  public static final int RESULT_ERROR_SESSION_NOT_AVAILABLE_IN_REGION = -106;

  /**
   * Result code representing that the application cannot skip any more because the skip limit is
   * reached.
   */
  public static final int RESULT_ERROR_SESSION_SKIP_LIMIT_REACHED = -107;

  /** Result code representing that the session needs user's manual intervention. */
  public static final int RESULT_ERROR_SESSION_SETUP_REQUIRED = -108;

  /** The {@link Code} of this result. */
  public final @Code int resultCode;

  /** The extra {@link Bundle} for the result. */
  public final Bundle extras;

  /**
   * The completion time of the command. It's the same as {@link SystemClock#elapsedRealtime()} when
   * the command is completed.
   */
  public final long completionTimeMs;

  /**
   * Creates an instance with a result code.
   *
   * @param resultCode The result code.
   */
  public SessionResult(@Code int resultCode) {
    this(resultCode, /* extras= */ Bundle.EMPTY);
  }

  /**
   * Creates an instance with a result code and an extra {@link Bundle}.
   *
   * @param resultCode The result code.
   * @param extras The extra {@link Bundle}.
   */
  public SessionResult(@Code int resultCode, Bundle extras) {
    this(resultCode, extras, SystemClock.elapsedRealtime());
  }

  private SessionResult(@Code int resultCode, Bundle extras, long completionTimeMs) {
    this.resultCode = resultCode;
    this.extras = new Bundle(extras);
    this.completionTimeMs = completionTimeMs;
  }

  // Bundleable implementation.

  private static final String FIELD_RESULT_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(1);
  private static final String FIELD_COMPLETION_TIME_MS = Util.intToStringMaxRadix(2);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RESULT_CODE, resultCode);
    bundle.putBundle(FIELD_EXTRAS, extras);
    bundle.putLong(FIELD_COMPLETION_TIME_MS, completionTimeMs);
    return bundle;
  }

  /**
   * Object that can restore a {@link SessionResult} from a {@link Bundle}.
   *
   * @deprecated Use {@link #fromBundle} instead.
   */
  @UnstableApi
  @Deprecated
  @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
  public static final Creator<SessionResult> CREATOR = SessionResult::fromBundle;

  /** Restores a {@code SessionResult} from a {@link Bundle}. */
  @UnstableApi
  public static SessionResult fromBundle(Bundle bundle) {
    int resultCode = bundle.getInt(FIELD_RESULT_CODE, /* defaultValue= */ RESULT_ERROR_UNKNOWN);
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    long completionTimeMs =
        bundle.getLong(FIELD_COMPLETION_TIME_MS, /* defaultValue= */ SystemClock.elapsedRealtime());
    return new SessionResult(resultCode, extras == null ? Bundle.EMPTY : extras, completionTimeMs);
  }
}
