/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.util.Pair;

/**
 * Converts exceptions into error codes and user readable error messages.
 */
public interface ErrorMessageProvider<T extends Exception> {

  /**
   * Returns a pair consisting of an error code and a user readable error message for the given
   * exception.
   *
   * @param exception The exception for which an error code and message should be generated.
   */
  Pair<Integer, String> getErrorMessage(T exception);

}
