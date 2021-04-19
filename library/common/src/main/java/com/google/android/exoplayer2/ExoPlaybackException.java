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

import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C.FormatSupport;
import com.google.android.exoplayer2.source.MediaPeriodId;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Thrown when a non locally recoverable playback failure occurs. */
public final class ExoPlaybackException extends Exception implements Bundleable {

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
   * If {@link #type} is {@link #TYPE_RENDERER}, this field indicates whether the error may be
   * recoverable by disabling and re-enabling (but <em>not</em> resetting) the renderers. For other
   * {@link Type types} this field will always be {@code false}.
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
      String message,
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
    Assertions.checkArgument(!isRecoverable || type == TYPE_RENDERER);
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
        Util.castNonNull(getMessage()),
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

  private static String deriveMessage(
      @Type int type,
      @Nullable String customMessage,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport) {
    String message;
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

  // Bundleable implementation.
  // TODO(b/145954241): Revisit bundling fields when this class is split for Player and ExoPlayer.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_MESSAGE,
    FIELD_TYPE,
    FIELD_RENDERER_NAME,
    FIELD_RENDERER_INDEX,
    FIELD_RENDERER_FORMAT,
    FIELD_RENDERER_FORMAT_SUPPORT,
    FIELD_TIME_STAMP_MS,
    FIELD_IS_RECOVERABLE,
    FIELD_CAUSE_CLASS_NAME,
    FIELD_CAUSE_MESSAGE
  })
  private @interface FieldNumber {}

  private static final int FIELD_MESSAGE = 0;
  private static final int FIELD_TYPE = 1;
  private static final int FIELD_RENDERER_NAME = 2;
  private static final int FIELD_RENDERER_INDEX = 3;
  private static final int FIELD_RENDERER_FORMAT = 4;
  private static final int FIELD_RENDERER_FORMAT_SUPPORT = 5;
  private static final int FIELD_TIME_STAMP_MS = 6;
  private static final int FIELD_IS_RECOVERABLE = 7;
  private static final int FIELD_CAUSE_CLASS_NAME = 8;
  private static final int FIELD_CAUSE_MESSAGE = 9;

  /**
   * {@inheritDoc}
   *
   * <p>It omits the {@link #mediaPeriodId} field. The {@link #mediaPeriodId} of an instance
   * restored by {@link #CREATOR} will always be {@code null}.
   */
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(keyForField(FIELD_MESSAGE), getMessage());
    bundle.putInt(keyForField(FIELD_TYPE), type);
    bundle.putString(keyForField(FIELD_RENDERER_NAME), rendererName);
    bundle.putInt(keyForField(FIELD_RENDERER_INDEX), rendererIndex);
    bundle.putParcelable(keyForField(FIELD_RENDERER_FORMAT), rendererFormat);
    bundle.putInt(keyForField(FIELD_RENDERER_FORMAT_SUPPORT), rendererFormatSupport);
    bundle.putLong(keyForField(FIELD_TIME_STAMP_MS), timestampMs);
    bundle.putBoolean(keyForField(FIELD_IS_RECOVERABLE), isRecoverable);
    if (cause != null) {
      bundle.putString(keyForField(FIELD_CAUSE_CLASS_NAME), cause.getClass().getName());
      bundle.putString(keyForField(FIELD_CAUSE_MESSAGE), cause.getMessage());
    }
    return bundle;
  }

  /** Object that can restore {@link ExoPlaybackException} from a {@link Bundle}. */
  public static final Creator<ExoPlaybackException> CREATOR = ExoPlaybackException::fromBundle;

  private static ExoPlaybackException fromBundle(Bundle bundle) {
    int type = bundle.getInt(keyForField(FIELD_TYPE), /* defaultValue= */ TYPE_UNEXPECTED);
    @Nullable String rendererName = bundle.getString(keyForField(FIELD_RENDERER_NAME));
    int rendererIndex =
        bundle.getInt(keyForField(FIELD_RENDERER_INDEX), /* defaultValue= */ C.INDEX_UNSET);
    @Nullable Format rendererFormat = bundle.getParcelable(keyForField(FIELD_RENDERER_FORMAT));
    int rendererFormatSupport =
        bundle.getInt(
            keyForField(FIELD_RENDERER_FORMAT_SUPPORT), /* defaultValue= */ C.FORMAT_HANDLED);
    long timestampMs =
        bundle.getLong(
            keyForField(FIELD_TIME_STAMP_MS), /* defaultValue= */ SystemClock.elapsedRealtime());
    boolean isRecoverable =
        bundle.getBoolean(keyForField(FIELD_IS_RECOVERABLE), /* defaultValue= */ false);
    @Nullable String message = bundle.getString(keyForField(FIELD_MESSAGE));
    if (message == null) {
      message =
          deriveMessage(
              type,
              /* customMessage= */ null,
              rendererName,
              rendererIndex,
              rendererFormat,
              rendererFormatSupport);
    }

    @Nullable String causeClassName = bundle.getString(keyForField(FIELD_CAUSE_CLASS_NAME));
    @Nullable String causeMessage = bundle.getString(keyForField(FIELD_CAUSE_MESSAGE));
    @Nullable Throwable cause = null;
    if (!TextUtils.isEmpty(causeClassName)) {
      final Class<?> clazz;
      try {
        clazz =
            Class.forName(
                causeClassName,
                /* initialize= */ true,
                ExoPlaybackException.class.getClassLoader());
        if (Throwable.class.isAssignableFrom(clazz)) {
          cause = createThrowable(clazz, causeMessage);
        }
      } catch (Throwable e) {
        // Intentionally catch Throwable to catch both Exception and Error.
        cause = createRemoteException(causeMessage);
      }
    }

    return new ExoPlaybackException(
        message,
        cause,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        /* mediaPeriodId= */ null,
        timestampMs,
        isRecoverable);
  }

  // Creates a new {@link Throwable} with possibly @{code null} message.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private static Throwable createThrowable(Class<?> throwableClazz, @Nullable String message)
      throws Exception {
    return (Throwable) throwableClazz.getConstructor(String.class).newInstance(message);
  }

  // Creates a new {@link RemoteException} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private static RemoteException createRemoteException(@Nullable String message) {
    return new RemoteException(message);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
