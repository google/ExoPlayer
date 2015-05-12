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
package com.google.android.exoplayer.upstream;

import java.io.IOException;

/**
 * Thrown when the length of some data does not match an expected length.
 */
@Deprecated
public final class UnexpectedLengthException extends IOException {

  /**
   * The length that was expected, in bytes.
   */
  public final long expectedLength;

  /**
   * The actual length encountered, in bytes.
   */
  public final long actualLength;

  /**
   * @param expectedLength The length that was expected, in bytes.
   * @param actualLength The actual length encountered, in bytes.
   */
  public UnexpectedLengthException(long expectedLength, long actualLength) {
    super("Expected: " + expectedLength + ", got: " + actualLength);
    this.expectedLength = expectedLength;
    this.actualLength = actualLength;
  }

}
