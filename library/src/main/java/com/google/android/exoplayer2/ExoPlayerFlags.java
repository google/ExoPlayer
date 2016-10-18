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

/**
 * Global configuration flags. Applications may toggle these flags, but should do so prior to any
 * other use of the library.
 */
public final class ExoPlayerFlags {

  /**
   * If set, indicates to {@link com.google.android.exoplayer2.upstream.HttpDataSource}
   * implementations that they should reject non-HTTPS requests.
   */
  public static boolean REQUIRE_HTTPS = false;

  private ExoPlayerFlags() {}

}
