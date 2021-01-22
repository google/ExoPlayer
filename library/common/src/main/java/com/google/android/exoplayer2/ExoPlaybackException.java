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
package com.google.android.exoplayer2;

import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C.FormatSupport;
import com.google.android.exoplayer2.source.MediaPeriodId;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Thrown when a non locally recoverable playback failure occurs. */
public final class ExoPlaybackException extends Exception {

  /**
   * The type of source that produced the error. One of {@link #TYPE_SOURCE}, {@link #TYPE_RENDERER}
   * {@link #TYPE_UNEXPECTED} or {@link #TYPE_REMOTE}. Note that new types may be added in the
   * future and error handling should handle unknown type values.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_SOURCE, TYPE_RENDERER, TYPE_UNEXPECTED, TYPE_REMOTE})
  public @interface Type {}
  /**
   * The error occurred loading data from a {@code MediaSource}.
   *
   * <p>Call {@link #getSourceException()} to retrieve the underlying cause.
   */
  // TODO(b/172315872) MediaSource was a link. Link to equivalent concept or remove @code.
  public static final int TYPE_SOURCE = 0;
  /**
   * The error occurred in a {@code Renderer}.
   *
   * <p>Call {@link #getRendererException()} to retrieve the underlying cause.
   */
  // TODO(b/172315872) Renderer was a link. Link to equivalent concept or remove @code.
  public static final int TYPE_RENDERER = 1;
  /**
   * The error was an unexpected {@link RuntimeException}.
   * <p>
   * Call {@link #getUnexpectedException()} to retrieve the underlying cause.
   */
  public static final int TYPE_UNEXPECTED = 2;
  /**
   * The error occurred in a remote component.
   *
   * <p>Call {@link #getMessage()} to retrieve the message associated with the error.
   */
  public static final int TYPE_REMOTE = 3;

  /** The {@link Type} of the playback failure. */
  @Type public final int type;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the name of the renderer, or null if
   * unknown.
   */
  @Nullable public final String rendererName;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the index of the renderer, or {@link
   * C#INDEX_UNSET} if unknown.
   */
  public final int rendererIndex;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the {@link Format} the renderer was using
   * at the time of the exception, or null if the renderer wasn't using a {@link Format}.
   */
  @Nullable public final Format rendererFormat;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the level of {@link FormatSupport} of the
   * renderer for {@link #rendererFormat}. If {@link #rendererFormat} is null, this is {@link
   * C#FORMAT_HANDLED}.
   */
  @FormatSupport public final int rendererFormatSupport;

  /** The value of {@link SystemClock#elapsedRealtime()} when this exception was created. */
  public final long timestampMs;

  /** The {@link MediaPeriodId} of the media associated with this error, or null if undetermined. */
  @Nullable public final MediaPeriodId mediaPeriodId;

  /**
   * Whether the error may be recoverable.
   *
   * <p>This is only used internally by ExoPlayer to try to recover from some errors and should not
   * be used by apps.
   *
   * <p>If the {@link #type} is {@link #TYPE_RENDERER}, it may be possible to recover from the error
   * by disabling and re-enabling the renderers.
   */
  /* package */ final boolean isRecoverable;

  @Nullable private final Throwable cause;

  /**
   * Creates an instance of type {@link #TYPE_SOURCE}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static ExoPlaybackException createForSource(IOException cause) {
    return new ExoPlaybackException(TYPE_SOURCE, cause);
  }

  /**
   * Creates an instance of type {@link #TYPE_RENDERER} for an unknown renderer.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRenderer(Exception cause) {
    return new ExoPlaybackException(
        TYPE_RENDERER,
        cause,
        /* customMessage= */ null,
        /* rendererName */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  /**
   * Creates an instance of type {@link #TYPE_RENDERER}.
   *
   * @param cause The cause of the failure.
   * @param rendererIndex The index of the renderer in which the failure occurred.
   * @param rendererFormat The {@link Format} the renderer was using at the time of the exception,
   *     or null if the renderer wasn't using a {@link Format}.
   * @param rendererFormatSupport The {@link FormatSupport} of the renderer for {@code
   *     rendererFormat}. Ignored if {@code rendererFormat} is null.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRenderer(
      Throwable cause,
      String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport) {
    return createForRenderer(
        cause,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        /* isRecoverable= */ false);
  }

  /**
   * Creates an instance of type {@link #TYPE_RENDERER}.
   *
   * @param cause The cause of the failure.
   * @param rendererIndex The index of the renderer in which the failure occurred.
   * @param rendererFormat The {@link Format} the renderer was using at the time of the exception,
   *     or null if the renderer wasn't using a {@link Format}.
   * @param rendererFormatSupport The {@link FormatSupport} of the renderer for {@code
   *     rendererFormat}. Ignored if {@code rendererFormat} is null.
   * @param isRecoverable If the failure can be recovered by disabling and re-enabling the renderer.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRenderer(
      Throwable cause,
      String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      boolean isRecoverable) {
    return new ExoPlaybackException(
        TYPE_RENDERER,
        cause,
        /* customMessage= */ null,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormat == null ? C.FORMAT_HANDLED : rendererFormatSupport,
        isRecoverable);
  }

  /**
   * Creates an instance of type {@link #TYPE_UNEXPECTED}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static ExoPlaybackException createForUnexpected(RuntimeException cause) {
    return new ExoPlaybackException(TYPE_UNEXPECTED, cause);
  }

  /**
   * Creates an instance of type {@link #TYPE_REMOTE}.
   *
   * @param message The message associated with the error.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRemote(String message) {
    return new ExoPlaybackException(TYPE_REMOTE, message);
  }

  private ExoPlaybackException(@Type int type, Throwable cause) {
    this(
        type,
        cause,
        /* customMessage= */ null,
        /* rendererName= */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  private ExoPlaybackException(@Type int type, String message) {
    this(
        type,
        /* cause= */ null,
        /* customMessage= */ message,
        /* rendererName= */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  private ExoPlaybackException(
      @Type int type,
      @Nullable Throwable cause,
      @Nullable String customMessage,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      boolean isRecoverable) {
    this(
        deriveMessage(
            type,
            customMessage,
            rendererName,
            rendererIndex,
            rendererFormat,
            rendererFormatSupport),
        cause,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        /* mediaPeriodId= */ null,
        /* timestampMs= */ SystemClock.elapsedRealtime(),
        isRecoverable);
  }

  private ExoPlaybackException(
      @Nullable String message,
      @Nullable Throwable cause,
      @Type int type,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      @Nullable MediaPeriodId mediaPeriodId,
      long timestampMs,
      boolean isRecoverable) {
    super(message, cause);
    this.type = type;
    this.cause = cause;
    this.rendererName = rendererName;
    this.rendererIndex = rendererIndex;
    this.rendererFormat = rendererFormat;
    this.rendererFormatSupport = rendererFormatSupport;
    this.mediaPeriodId = mediaPeriodId;
    this.timestampMs = timestampMs;
    this.isRecoverable = isRecoverable;
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_SOURCE}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_SOURCE}.
   */
  public IOException getSourceException() {
    Assertions.checkState(type == TYPE_SOURCE);
    return (IOException) Assertions.checkNotNull(cause);
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_RENDERER}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_RENDERER}.
   */
  public Exception getRendererException() {
    Assertions.checkState(type == TYPE_RENDERER);
    return (Exception) Assertions.checkNotNull(cause);
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_UNEXPECTED}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_UNEXPECTED}.
   */
  public RuntimeException getUnexpectedException() {
    Assertions.checkState(type == TYPE_UNEXPECTED);
    return (RuntimeException) Assertions.checkNotNull(cause);
  }

  /**
   * Returns a copy of this exception with the provided {@link MediaPeriodId}.
   *
   * @param mediaPeriodId The {@link MediaPeriodId}.
   * @return The copied exception.
   */
  @CheckResult
  /* package */ ExoPlaybackException copyWithMediaPeriodId(@Nullable MediaPeriodId mediaPeriodId) {
    return new ExoPlaybackException(
        getMessage(),
        cause,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        mediaPeriodId,
        timestampMs,
        isRecoverable);
  }

  @Nullable
  private static String deriveMessage(
      @Type int type,
      @Nullable String customMessage,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport) {
    @Nullable String message;
    switch (type) {
      case TYPE_SOURCE:
        message = "Source error";
        break;
      case TYPE_RENDERER:
        message =
            rendererName
                + " error"
                + ", index="
                + rendererIndex
                + ", format="
                + rendererFormat
                + ", format_supported="
                + C.getFormatSupportString(rendererFormatSupport);
        break;
      case TYPE_REMOTE:
        message = "Remote error";
        break;
      case TYPE_UNEXPECTED:
      default:
        message = "Unexpected runtime error";
        break;
    }
    if (!TextUtils.isEmpty(customMessage)) {
      message += ": " + customMessage;
    }
    return message;
  }
}
