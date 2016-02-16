/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/**
 * Thrown when a non-recoverable playback failure occurs.
 */
public final class ExoPlaybackException extends Exception {

  /**
   * The error occurred loading data from a {@link SampleSource}.
   * <p>
   * Call {@link #getSourceException()} to retrieve the underlying cause.
   */
  public static final int TYPE_SOURCE = 0;
  /**
   * The error occurred in a {@link TrackRenderer}.
   * <p>
   * Call {@link #getRendererException()} to retrieve the underlying cause.
   */
  public static final int TYPE_RENDERER = 1;
  /**
   * The error was an unexpected {@link RuntimeException}.
   * <p>
   * Call {@link #getUnexpectedException()} to retrieve the underlying cause.
   */
  public static final int TYPE_UNEXPECTED = 2;

  /**
   * The type of the playback failure. One of {@link #TYPE_SOURCE}, {@link #TYPE_RENDERER} and
   * {@link #TYPE_UNEXPECTED}.
   */
  public final int type;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the index of the renderer.
   */
  public final int rendererIndex;

  public static ExoPlaybackException createForRenderer(Exception cause, int rendererIndex) {
    return new ExoPlaybackException(TYPE_RENDERER, null, cause, rendererIndex);
  }

  public static ExoPlaybackException createForSource(IOException cause) {
    return new ExoPlaybackException(TYPE_SOURCE, null, cause, -1);
  }

  /* package */ static ExoPlaybackException createForUnexpected(RuntimeException cause) {
    return new ExoPlaybackException(TYPE_UNEXPECTED, null, cause, -1);
  }

  private ExoPlaybackException(int type, String message, Throwable cause, int rendererIndex) {
    super(message, cause);
    this.type = type;
    this.rendererIndex = rendererIndex;
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_SOURCE}.
   */
  public IOException getSourceException() {
    Assertions.checkState(type == TYPE_SOURCE);
    return (IOException) getCause();
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_RENDERER}.
   */
  public Exception getRendererException() {
    Assertions.checkState(type == TYPE_RENDERER);
    return (Exception) getCause();
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_UNEXPECTED}.
   */
  public RuntimeException getUnexpectedException() {
    Assertions.checkState(type == TYPE_UNEXPECTED);
    return (RuntimeException) getCause();
  }

}
