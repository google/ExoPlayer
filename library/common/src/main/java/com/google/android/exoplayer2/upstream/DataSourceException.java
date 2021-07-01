/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Used to specify reason of a DataSource error. */
public class DataSourceException extends IOException {

  /**
   * The type of operation that produced the error. One of {@link #TYPE_READ}, {@link #TYPE_OPEN}
   * {@link #TYPE_CLOSE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_OPEN, TYPE_READ, TYPE_CLOSE})
  public @interface Type {}

  /** The error occurred reading data from a {@link DataSource}. */
  public static final int TYPE_OPEN = 1;
  /** The error occurred in opening a {@link DataSource}. */
  public static final int TYPE_READ = 2;
  /** The error occurred in closing a {@link DataSource}. */
  public static final int TYPE_CLOSE = 3;

  /**
   * Returns whether the given {@link IOException} was caused by a {@link DataSourceException} whose
   * {@link #reason} is {@link #POSITION_OUT_OF_RANGE} in its cause stack.
   */
  public static boolean isCausedByPositionOutOfRange(IOException e) {
    @Nullable Throwable cause = e;
    while (cause != null) {
      if (cause instanceof DataSourceException) {
        int reason = ((DataSourceException) cause).reason;
        if (reason == DataSourceException.POSITION_OUT_OF_RANGE) {
          return true;
        }
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * Indicates that the {@link DataSpec#position starting position} of the request was outside the
   * bounds of the data.
   */
  public static final int POSITION_OUT_OF_RANGE = 0;

  /** Indicates that the error reason is unknown. */
  public static final int REASON_UNKNOWN = 1;

  /**
   * The reason of this {@link DataSourceException}. It can only be {@link #POSITION_OUT_OF_RANGE},
   * or {@link #REASON_UNKNOWN}.
   */
  public final int reason;

  /** The {@link Type} of the operation that caused the playback failure. */
  @Type public final int type;

  /**
   * Constructs a DataSourceException with type {@link #TYPE_READ}.
   *
   * @deprecated Use the constructor {@link #DataSourceException(String, Throwable, int, int)}.
   * @param reason Reason of the error. It can only be {@link #POSITION_OUT_OF_RANGE} or {@link
   *     #REASON_UNKNOWN}.
   */
  @Deprecated
  public DataSourceException(int reason) {
    this.reason = reason;
    this.type = TYPE_READ;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param message The error message.
   * @param cause The error cause.
   * @param reason Reason of the error. It can only be {@link #POSITION_OUT_OF_RANGE} or {@link
   *     #REASON_UNKNOWN}.
   * @param type See {@link Type}.
   */
  public DataSourceException(String message, Throwable cause, int reason, @Type int type) {
    super(message, cause);
    this.reason = reason;
    this.type = type;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param cause The error cause.
   * @param reason Reason of the error. It can only be {@link #POSITION_OUT_OF_RANGE} or {@link
   *     #REASON_UNKNOWN}.
   * @param type See {@link Type}.
   */
  public DataSourceException(Throwable cause, int reason, @Type int type) {
    super(cause);
    this.reason = reason;
    this.type = type;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param message The error message.
   * @param reason Reason of the error. It can only be {@link #POSITION_OUT_OF_RANGE} or {@link
   *     #REASON_UNKNOWN}.
   * @param type See {@link Type}.
   */
  public DataSourceException(String message, int reason, @Type int type) {
    super(message);
    this.reason = reason;
    this.type = type;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param reason Reason of the error. It can only be {@link #POSITION_OUT_OF_RANGE} or {@link
   *     #REASON_UNKNOWN}.
   * @param type See {@link Type}.
   */
  public DataSourceException(int reason, @Type int type) {
    this.reason = reason;
    this.type = type;
  }
}
