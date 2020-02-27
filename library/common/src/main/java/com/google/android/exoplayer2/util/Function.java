/*
 * Copyright 2020 The Android Open Source Project
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

/**
 * A functional interface representing a function taking one argument and returning a result.
 *
 * @param <T> The input type of the function.
 * @param <R> The output type of the function.
 */
public interface Function<T, R> {

  /**
   * Applies this function to the given argument.
   *
   * @param t The function argument.
   * @return The function result, which may be {@code null}.
   */
  R apply(T t);
}
