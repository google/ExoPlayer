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

/**
 * Thrown when a non-recoverable playback failure occurs.
 * <p>
 * Where possible, the cause returned by {@link #getCause()} will indicate the reason for failure.
 */
public class ExoPlaybackException extends Exception {

  public ExoPlaybackException(String message) {
    super(message);
  }

  public ExoPlaybackException(Throwable cause) {
    super(cause);
  }

  public ExoPlaybackException(String message, Throwable cause) {
    super(message, cause);
  }

}
