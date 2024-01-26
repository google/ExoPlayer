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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableBiMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@link ExportException} instead.
 */
@SuppressWarnings("deprecation") // Deprecated usages of own type
@Deprecated
@UnstableApi
public final class TransformationException extends Exception {

  /**
   * Error codes that identify causes of {@link Transformer} errors.
   *
   * <p>This list of errors may be extended in future versions. The underlying values may also
   * change, so it is best to avoid relying on them directly without using the constants.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      open = true,
      value = {
        ERROR_CODE_UNSPECIFIED,
        ERROR_CODE_FAILED_RUNTIME_CHECK,
        ERROR_CODE_IO_UNSPECIFIED,
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        ERROR_CODE_IO_BAD_HTTP_STATUS,
        ERROR_CODE_IO_FILE_NOT_FOUND,
        ERROR_CODE_IO_NO_PERMISSION,
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        ERROR_CODE_DECODER_INIT_FAILED,
        ERROR_CODE_DECODING_FAILED,
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_ENCODER_INIT_FAILED,
        ERROR_CODE_ENCODING_FAILED,
        ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
        ERROR_CODE_AUDIO_PROCESSING_FAILED,
        ERROR_CODE_MUXING_FAILED,
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

  // Input/Output errors (2xxx).

  /** Caused by an Input/Output error which could not be identified. */
  public static final int ERROR_CODE_IO_UNSPECIFIED = 2000;

  /**
   * Caused by a network connection failure.
   *
   * <p>The following is a non-exhaustive list of possible reasons:
   *
   * <ul>
   *   <li>There is no network connectivity.
   *   <li>The URL's domain is misspelled or does not exist.
   *   <li>The target host is unreachable.
   *   <li>The server unexpectedly closes the connection.
   * </ul>
   */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;

  /** Caused by a network timeout, meaning the server is taking too long to fulfill a request. */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;

  /**
   * Caused by a server returning a resource with an invalid "Content-Type" HTTP header value.
   *
   * <p>For example, this can happen when the {@link AssetLoader} is expecting a piece of media, but
   * the server returns a paywall HTML page, with content type "text/html".
   */
  public static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;

  /** Caused by an HTTP server returning an unexpected HTTP response status code. */
  public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;

  /** Caused by a non-existent file. */
  public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 2005;

  /**
   * Caused by lack of permission to perform an IO operation. For example, lack of permission to
   * access internet or external storage.
   */
  public static final int ERROR_CODE_IO_NO_PERMISSION = 2006;

  /**
   * Caused by the {@link AssetLoader} trying to access cleartext HTTP traffic (meaning http://
   * rather than https://) when the app's Network Security Configuration does not permit it.
   */
  public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007;

  /** Caused by reading data out of the data bound. */
  public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;

  // Decoding errors (3xxx).

  /** Caused by a decoder initialization failure. */
  public static final int ERROR_CODE_DECODER_INIT_FAILED = 3001;

  /** Caused by a failure while trying to decode media samples. */
  public static final int ERROR_CODE_DECODING_FAILED = 3002;

  /** Caused by trying to decode content whose format is not supported. */
  public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 3003;

  // Encoding errors (4xxx).

  /** Caused by an encoder initialization failure. */
  public static final int ERROR_CODE_ENCODER_INIT_FAILED = 4001;

  /** Caused by a failure while trying to encode media samples. */
  public static final int ERROR_CODE_ENCODING_FAILED = 4002;

  /**
   * Caused by trying to encode content whose format is not supported. *
   *
   * <p>Supported output formats are limited by the {@linkplain Codec.DecoderFactory encoders}
   * available.
   */
  public static final int ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 4003;

  // Video editing errors (5xxx).

  /** Caused by a video frame processing failure. */
  public static final int ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED = 5001;

  // Audio processing errors (6xxx).

  /** Caused by an audio processing failure. */
  public static final int ERROR_CODE_AUDIO_PROCESSING_FAILED = 6001;

  // Muxing errors (7xxx).

  /** Caused by a failure while muxing media samples. */
  public static final int ERROR_CODE_MUXING_FAILED = 7001;

  /* package */ static final ImmutableBiMap<String, @ErrorCode Integer> NAME_TO_ERROR_CODE =
      new ImmutableBiMap.Builder<String, @ErrorCode Integer>()
          .put("ERROR_CODE_FAILED_RUNTIME_CHECK", ERROR_CODE_FAILED_RUNTIME_CHECK)
          .put("ERROR_CODE_IO_UNSPECIFIED", ERROR_CODE_IO_UNSPECIFIED)
          .put("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
          .put("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
          .put("ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE", ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE)
          .put("ERROR_CODE_IO_BAD_HTTP_STATUS", ERROR_CODE_IO_BAD_HTTP_STATUS)
          .put("ERROR_CODE_IO_FILE_NOT_FOUND", ERROR_CODE_IO_FILE_NOT_FOUND)
          .put("ERROR_CODE_IO_NO_PERMISSION", ERROR_CODE_IO_NO_PERMISSION)
          .put("ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED", ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED)
          .put("ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE", ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
          .put("ERROR_CODE_DECODER_INIT_FAILED", ERROR_CODE_DECODER_INIT_FAILED)
          .put("ERROR_CODE_DECODING_FAILED", ERROR_CODE_DECODING_FAILED)
          .put("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED", ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
          .put("ERROR_CODE_ENCODER_INIT_FAILED", ERROR_CODE_ENCODER_INIT_FAILED)
          .put("ERROR_CODE_ENCODING_FAILED", ERROR_CODE_ENCODING_FAILED)
          .put("ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED", ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED)
          .put("ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED", ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)
          .put("ERROR_CODE_AUDIO_PROCESSING_FAILED", ERROR_CODE_AUDIO_PROCESSING_FAILED)
          .put("ERROR_CODE_MUXING_FAILED", ERROR_CODE_MUXING_FAILED)
          .buildOrThrow();

  /** Returns the name of a given {@code errorCode}. */
  public static String getErrorCodeName(@ErrorCode int errorCode) {
    return NAME_TO_ERROR_CODE.inverse().getOrDefault(errorCode, "invalid error code");
  }

  /**
   * Equivalent to {@link TransformationException#getErrorCodeName(int)
   * TransformationException.getErrorCodeName(this.errorCode)}.
   */
  public String getErrorCodeName() {
    return getErrorCodeName(errorCode);
  }

  /**
   * Creates an instance for an {@link AssetLoader} related exception.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  public static TransformationException createForAssetLoader(Throwable cause, int errorCode) {
    return new TransformationException("Asset loader error", cause, errorCode);
  }

  /**
   * Creates an instance for a {@link Codec} related exception.
   *
   * <p>This method should be used when the {@code cause} occurs before the {@link Codec} is
   * initialized.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @param isVideo Whether the {@link Codec} is configured for video.
   * @param isDecoder Whether the exception is created for a decoder.
   * @param format The {@link Format} used for configuring the {@link Codec}.
   * @return The created instance.
   */
  public static TransformationException createForCodec(
      Throwable cause,
      @ErrorCode int errorCode,
      boolean isVideo,
      boolean isDecoder,
      Format format) {
    String details = "format=" + format;
    return createForCodec(cause, errorCode, isVideo, isDecoder, details);
  }

  /**
   * Creates an instance for a {@link Codec} related exception.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @param isVideo Whether the {@link Codec} is configured for video.
   * @param isDecoder Whether the exception is created for a decoder.
   * @param details The details associated with this exception.
   * @return The created instance.
   */
  public static TransformationException createForCodec(
      Throwable cause,
      @ErrorCode int errorCode,
      boolean isVideo,
      boolean isDecoder,
      String details) {
    String componentName = (isVideo ? "Video" : "Audio") + (isDecoder ? "Decoder" : "Encoder");
    String errorMessage = componentName + " error: " + details;
    return new TransformationException(errorMessage, cause, errorCode);
  }

  /**
   * Creates an instance for an audio processing related exception.
   *
   * @param cause The cause of the failure.
   * @param audioFormat The {@link AudioFormat} used.
   * @return The created instance.
   */
  public static TransformationException createForAudioProcessing(
      Throwable cause, AudioFormat audioFormat) {
    return new TransformationException(
        "Audio processing error, audio_format=" + audioFormat,
        cause,
        ERROR_CODE_AUDIO_PROCESSING_FAILED);
  }

  /**
   * Creates an instance for a {@link VideoFrameProcessor} related exception.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  /* package */ static TransformationException createForVideoFrameProcessingException(
      VideoFrameProcessingException cause, int errorCode) {
    return new TransformationException("Video frame processing error", cause, errorCode);
  }

  /**
   * Creates an instance for a muxer related exception.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  /* package */ static TransformationException createForMuxer(Throwable cause, int errorCode) {
    return new TransformationException("Muxer error", cause, errorCode);
  }

  /**
   * Creates an instance for an unexpected exception.
   *
   * <p>If the exception is a runtime exception, error code {@link #ERROR_CODE_FAILED_RUNTIME_CHECK}
   * is used. Otherwise, the created instance has error code {@link #ERROR_CODE_UNSPECIFIED}.
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
  private TransformationException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    super(message, cause);
    this.errorCode = errorCode;
    this.timestampMs = Clock.DEFAULT.elapsedRealtime();
  }

  /* package */ TransformationException(ExportException exportException) {
    super(exportException.getMessage(), exportException.getCause());
    errorCode = exportException.errorCode;
    timestampMs = exportException.timestampMs;
  }

  /**
   * Returns whether the error data associated to this exception equals the error data associated to
   * {@code other}.
   *
   * <p>Note that this method does not compare the exceptions' stack traces.
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
