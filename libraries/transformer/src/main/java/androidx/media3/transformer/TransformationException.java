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
package androidx.media3.transformer;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Thrown when a non-locally recoverable transformation failure occurs. */
@UnstableApi
public final class TransformationException extends Exception {

  /**
   * Creates an instance for an unexpected exception.
   *
   * <p>If the exception is a runtime exception, error code {@link ERROR_CODE_FAILED_RUNTIME_CHECK}
   * is used. Otherwise, the created instance has error code {@link ERROR_CODE_UNSPECIFIED}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static TransformationException createForUnexpected(Exception cause) {
    if (cause instanceof RuntimeException) {
      return new TransformationException(
          "Unexpected runtime error", cause, ERROR_CODE_FAILED_RUNTIME_CHECK);
    }
    return new TransformationException("Unexpected error", cause, ERROR_CODE_UNSPECIFIED);
  }

  /**
   * Codes that identify causes of {@link Transformer} errors.
   *
   * <p>This list of errors may be extended in future versions.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      open = true,
      value = {
        ERROR_CODE_UNSPECIFIED,
        ERROR_CODE_FAILED_RUNTIME_CHECK,
        ERROR_CODE_DECODER_INIT_FAILED,
        ERROR_CODE_DECODING_FAILED,
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_ENCODER_INIT_FAILED,
        ERROR_CODE_ENCODING_FAILED,
        ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_GL_INIT_FAILED,
        ERROR_CODE_GL_PROCESSING_FAILED,
      })
  public @interface ErrorCode {}

  // Miscellaneous errors (1xxx).

  /** Caused by an error whose cause could not be identified. */
  public static final int ERROR_CODE_UNSPECIFIED = 1000;
  /**
   * Caused by a failed runtime check.
   *
   * <p>This can happen when transformer reaches an invalid state.
   */
  public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 1001;

  // Decoding errors (2xxx).

  /** Caused by a decoder initialization failure. */
  public static final int ERROR_CODE_DECODER_INIT_FAILED = 2001;
  /** Caused by a failure while trying to decode media samples. */
  public static final int ERROR_CODE_DECODING_FAILED = 2002;
  /** Caused by trying to decode content whose format is not supported. */
  public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 2003;

  // Encoding errors (3xxx).

  /** Caused by an encoder initialization failure. */
  public static final int ERROR_CODE_ENCODER_INIT_FAILED = 3001;
  /** Caused by a failure while trying to encode media samples. */
  public static final int ERROR_CODE_ENCODING_FAILED = 3002;
  /** Caused by requesting to encode content in a format that is not supported by the device. */
  public static final int ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 3003;

  // GL errors (4xxx).

  /** Caused by a GL initialization failure. */
  public static final int ERROR_CODE_GL_INIT_FAILED = 4001;
  /** Caused by a failure while using or releasing a GL program. */
  public static final int ERROR_CODE_GL_PROCESSING_FAILED = 4002;

  /** Returns the name of a given {@code errorCode}. */
  public static String getErrorCodeName(@ErrorCode int errorCode) {
    switch (errorCode) {
      case ERROR_CODE_UNSPECIFIED:
        return "ERROR_CODE_UNSPECIFIED";
      case ERROR_CODE_FAILED_RUNTIME_CHECK:
        return "ERROR_CODE_FAILED_RUNTIME_CHECK";
      case ERROR_CODE_DECODER_INIT_FAILED:
        return "ERROR_CODE_DECODER_INIT_FAILED";
      case ERROR_CODE_DECODING_FAILED:
        return "ERROR_CODE_DECODING_FAILED";
      case ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
        return "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED";
      case ERROR_CODE_ENCODER_INIT_FAILED:
        return "ERROR_CODE_ENCODER_INIT_FAILED";
      case ERROR_CODE_ENCODING_FAILED:
        return "ERROR_CODE_ENCODING_FAILED";
      case ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED:
        return "ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED";
      case ERROR_CODE_GL_INIT_FAILED:
        return "ERROR_CODE_GL_INIT_FAILED";
      case ERROR_CODE_GL_PROCESSING_FAILED:
        return "ERROR_CODE_GL_PROCESSING_FAILED";
      default:
        return "invalid error code";
    }
  }

  /**
   * Equivalent to {@link TransformationException#getErrorCodeName(int)
   * TransformationException.getErrorCodeName(this.errorCode)}.
   */
  public final String getErrorCodeName() {
    return getErrorCodeName(errorCode);
  }

  /** An error code which identifies the cause of the transformation failure. */
  public final @ErrorCode int errorCode;

  /** The value of {@link SystemClock#elapsedRealtime()} when this exception was created. */
  public final long timestampMs;

  /**
   * Creates an instance.
   *
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   * @param errorCode A number which identifies the cause of the error. May be one of the {@link
   *     ErrorCode ErrorCodes}.
   */
  public TransformationException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    super(message, cause);
    this.errorCode = errorCode;
    this.timestampMs = Clock.DEFAULT.elapsedRealtime();
  }

  /**
   * Returns whether the error data associated to this exception equals the error data associated to
   * {@code other}.
   *
   * <p>Note that this method does not compare the exceptions' stacktraces.
   */
  public boolean errorInfoEquals(@Nullable TransformationException other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    @Nullable Throwable thisCause = getCause();
    @Nullable Throwable thatCause = other.getCause();
    if (thisCause != null && thatCause != null) {
      if (!Util.areEqual(thisCause.getMessage(), thatCause.getMessage())) {
        return false;
      }
      if (!Util.areEqual(thisCause.getClass(), thatCause.getClass())) {
        return false;
      }
    } else if (thisCause != null || thatCause != null) {
      return false;
    }
    return errorCode == other.errorCode
        && Util.areEqual(getMessage(), other.getMessage())
        && timestampMs == other.timestampMs;
  }
}
