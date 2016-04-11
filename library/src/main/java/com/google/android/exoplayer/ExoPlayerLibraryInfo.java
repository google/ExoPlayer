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
 * Information about the ExoPlayer library.
 */
public final class ExoPlayerLibraryInfo {

  /**
   * The version of the library, expressed as a string.
   */
  public static final String VERSION = "1.5.7";

  /**
   * The version of the library, expressed as an integer.
   * <p>
   * Three digits are used for each component of {@link #VERSION}. For example "1.2.3" has the
   * corresponding integer version 001002003.
   */
  public static final int VERSION_INT = 001005007;

  /**
   * Whether the library was compiled with {@link com.google.android.exoplayer.util.Assertions}
   * checks enabled.
   */
  public static final boolean ASSERTIONS_ENABLED = true;

  /**
   * Whether the library was compiled with {@link com.google.android.exoplayer.util.TraceUtil}
   * trace enabled.
   */
  public static final boolean TRACE_ENABLED = true;

  private ExoPlayerLibraryInfo() {}

}
