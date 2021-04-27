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
package com.google.android.exoplayer2;

import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Clock;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Thrown when a non locally recoverable playback failure occurs. */
public class PlaybackException extends Exception implements Bundleable {

  /**
   * An error code identifying the source of the playback failure. Note that new types may be added
   * in the future and error handling should handle unknown type values.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ERROR_CODE_UNKNOWN})
  public @interface ErrorCode {}

  public static final int ERROR_CODE_UNKNOWN = 0;

  /** An error code which identifies the source of the playback failure. */
  @ErrorCode public final int errorCode;

  /** The value of {@link SystemClock#elapsedRealtime()} when this exception was created. */
  public final long timestampMs;

  /**
   * Creates an instance.
   *
   * @param errorCode An {@link ErrorCode} which identifies the cause of the error.
   * @param cause See {@link #getCause()}.
   * @param message See {@link #getMessage()}.
   */
  public PlaybackException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    this(message, cause, errorCode, Clock.DEFAULT.elapsedRealtime());
  }

  private PlaybackException(
      @Nullable String message,
      @Nullable Throwable cause,
      @ErrorCode int errorCode,
      long timestampMs) {
    super(message, cause);
    this.errorCode = errorCode;
    this.timestampMs = timestampMs;
  }

  // Bundleable implementation.

  private static final int FIELD_INT_ERROR_CODE = 0;
  private static final int FIELD_LONG_TIME_STAMP_MS = 1;
  private static final int FIELD_STRING_MESSAGE = 2;
  private static final int FIELD_STRING_CAUSE_CLASS_NAME = 3;
  private static final int FIELD_STRING_CAUSE_MESSAGE = 4;

  /** Object that can restore {@link PlaybackException} from a {@link Bundle}. */
  public static final Creator<PlaybackException> CREATOR = PlaybackException::fromBundle;

  /**
   * Defines a minimum field id value for subclasses to use when implementing {@link #toBundle()}
   * and {@link Bundleable.Creator}.
   *
   * <p>Subclasses should obtain their {@link Bundle Bundle's} field keys by applying a non-negative
   * offset on this constant and passing the result to {@link #keyForField(int)}.
   */
  protected static final int FIELD_CUSTOM_ID_BASE = 1000;

  @CallSuper
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(keyForField(FIELD_INT_ERROR_CODE), errorCode);
    bundle.putLong(keyForField(FIELD_LONG_TIME_STAMP_MS), timestampMs);
    bundle.putString(keyForField(FIELD_STRING_MESSAGE), getMessage());
    @Nullable Throwable cause = getCause();
    if (cause != null) {
      bundle.putString(keyForField(FIELD_STRING_CAUSE_CLASS_NAME), cause.getClass().getName());
      bundle.putString(keyForField(FIELD_STRING_CAUSE_MESSAGE), cause.getMessage());
    }
    return bundle;
  }

  /**
   * Creates and returns a new instance using the field data in the given {@link Bundle}.
   *
   * @param bundle The {@link Bundle} from which to obtain the returned instance's contents.
   * @return The created instance.
   */
  protected static PlaybackException fromBundle(Bundle bundle) {
    int type =
        bundle.getInt(keyForField(FIELD_INT_ERROR_CODE), /* defaultValue= */ ERROR_CODE_UNKNOWN);
    long timestampMs =
        bundle.getLong(
            keyForField(FIELD_LONG_TIME_STAMP_MS),
            /* defaultValue= */ SystemClock.elapsedRealtime());
    @Nullable String message = bundle.getString(keyForField(FIELD_STRING_MESSAGE));
    @Nullable String causeClassName = bundle.getString(keyForField(FIELD_STRING_CAUSE_CLASS_NAME));
    @Nullable String causeMessage = bundle.getString(keyForField(FIELD_STRING_CAUSE_MESSAGE));
    @Nullable Throwable cause = null;
    if (!TextUtils.isEmpty(causeClassName)) {
      try {
        Class<?> clazz =
            Class.forName(
                causeClassName, /* initialize= */ true, PlaybackException.class.getClassLoader());
        if (Throwable.class.isAssignableFrom(clazz)) {
          cause = createThrowable(clazz, causeMessage);
        }
      } catch (Throwable e) {
        // There was an error while creating the cause using reflection, do nothing here and let the
        // finally block handle the issue.
      } finally {
        if (cause == null) {
          // The bundle has fields to represent the cause, but we were unable to re-create the
          // exception using reflection. We instantiate a RemoteException to reflect this problem.
          cause = createRemoteException(causeMessage);
        }
      }
    }
    return new PlaybackException(message, cause, type, timestampMs);
  }

  /**
   * Converts the given {@code field} to a string which can be used as a field key when implementing
   * {@link #toBundle()} and {@link Bundleable.Creator}.
   */
  protected static String keyForField(int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }

  // Creates a new {@link Throwable} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private static Throwable createThrowable(Class<?> clazz, @Nullable String message)
      throws Exception {
    return (Throwable) clazz.getConstructor(String.class).newInstance(message);
  }

  // Creates a new {@link RemoteException} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private static RemoteException createRemoteException(@Nullable String message) {
    return new RemoteException(message);
  }
}
