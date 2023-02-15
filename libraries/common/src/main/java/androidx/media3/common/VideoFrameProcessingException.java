/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/**
 * Thrown when an exception occurs while preparing an {@link Effect}, or applying an {@link Effect}
 * to video frames.
 */
@UnstableApi
public final class VideoFrameProcessingException extends Exception {

  /**
   * Wraps the given exception in a {@code VideoFrameProcessingException} if it is not already a
   * {@code VideoFrameProcessingException} and returns the exception otherwise.
   */
  public static VideoFrameProcessingException from(Exception exception) {
    return from(exception, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Wraps the given exception in a {@code VideoFrameProcessingException} with the given timestamp
   * if it is not already a {@code VideoFrameProcessingException} and returns the exception
   * otherwise.
   */
  public static VideoFrameProcessingException from(Exception exception, long presentationTimeUs) {
    if (exception instanceof VideoFrameProcessingException) {
      return (VideoFrameProcessingException) exception;
    } else {
      return new VideoFrameProcessingException(exception, presentationTimeUs);
    }
  }

  /**
   * The microsecond timestamp of the frame being processed while the exception occurred or {@link
   * C#TIME_UNSET} if unknown.
   */
  public final long presentationTimeUs;

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   */
  public VideoFrameProcessingException(String message) {
    this(message, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
   */
  public VideoFrameProcessingException(String message, long presentationTimeUs) {
    super(message);
    this.presentationTimeUs = presentationTimeUs;
  }

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   * @param cause The cause of this exception.
   */
  public VideoFrameProcessingException(String message, Throwable cause) {
    this(message, cause, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   * @param cause The cause of this exception.
   * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
   */
  public VideoFrameProcessingException(String message, Throwable cause, long presentationTimeUs) {
    super(message, cause);
    this.presentationTimeUs = presentationTimeUs;
  }

  /**
   * Creates an instance.
   *
   * @param cause The cause of this exception.
   */
  public VideoFrameProcessingException(Throwable cause) {
    this(cause, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Creates an instance.
   *
   * @param cause The cause of this exception.
   * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
   */
  public VideoFrameProcessingException(Throwable cause, long presentationTimeUs) {
    super(cause);
    this.presentationTimeUs = presentationTimeUs;
  }
}
