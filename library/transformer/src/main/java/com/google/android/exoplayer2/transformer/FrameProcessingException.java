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
package com.google.android.exoplayer2.transformer;

import com.google.android.exoplayer2.C;

/** Thrown when an exception occurs while applying effects to video frames. */
public final class FrameProcessingException extends Exception {

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
  public FrameProcessingException(String message) {
    this(message, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
   */
  public FrameProcessingException(String message, long presentationTimeUs) {
    super(message);
    this.presentationTimeUs = presentationTimeUs;
  }

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   * @param cause The cause of this exception.
   */
  public FrameProcessingException(String message, Throwable cause) {
    this(message, cause, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Creates an instance.
   *
   * @param message The detail message for this exception.
   * @param cause The cause of this exception.
   * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
   */
  public FrameProcessingException(String message, Throwable cause, long presentationTimeUs) {
    super(message, cause);
    this.presentationTimeUs = presentationTimeUs;
  }

  /**
   * Creates an instance.
   *
   * @param cause The cause of this exception.
   */
  public FrameProcessingException(Throwable cause) {
    this(cause, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * Creates an instance.
   *
   * @param cause The cause of this exception.
   * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
   */
  public FrameProcessingException(Throwable cause, long presentationTimeUs) {
    super(cause);
    this.presentationTimeUs = presentationTimeUs;
  }
}
