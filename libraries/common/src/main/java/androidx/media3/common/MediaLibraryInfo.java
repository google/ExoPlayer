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
package androidx.media3.common;

import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.HashSet;

/** Information about the media libraries. */
@UnstableApi
public final class MediaLibraryInfo {

  /** A tag to use when logging library information. */
  public static final String TAG = "AndroidXMedia3";

  /** The version of the library expressed as a string, for example "1.2.3" or "1.2.3-beta01". */
  // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION_INT) or vice versa.
  public static final String VERSION = "1.2.0";

  /** The version of the library expressed as {@code TAG + "/" + VERSION}. */
  // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION) or vice versa.
  public static final String VERSION_SLASHY = "AndroidXMedia3/1.2.0";

  /**
   * The version of the library expressed as an integer, for example 1002003300.
   *
   * <p>Three digits are used for each of the first three components of {@link #VERSION}, then a
   * single digit represents the cycle of this version: alpha (0), beta (1), rc (2) or stable (3).
   * Finally two digits are used for the cycle number (always 00 for stable releases).
   *
   * <p>For example "1.2.3-alpha05" has the corresponding integer version 1002003005
   * (001-002-003-0-05), and "123.45.6" has the corresponding integer version 123045006300
   * (123-045-006-3-00).
   */
  // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION) or vice versa.
  public static final int VERSION_INT = 1_002_000_3_00;

  /** Whether the library was compiled with {@link Assertions} checks enabled. */
  public static final boolean ASSERTIONS_ENABLED = true;

  /** Whether the library was compiled with {@link TraceUtil} trace enabled. */
  public static final boolean TRACE_ENABLED = true;

  private static final HashSet<String> registeredModules = new HashSet<>();
  private static String registeredModulesString = "media3.common";

  private MediaLibraryInfo() {} // Prevents instantiation.

  /** Returns a string consisting of registered module names separated by ", ". */
  public static synchronized String registeredModules() {
    return registeredModulesString;
  }

  /**
   * Registers a module to be returned in the {@link #registeredModules()} string.
   *
   * @param name The name of the module being registered.
   */
  public static synchronized void registerModule(String name) {
    if (registeredModules.add(name)) {
      registeredModulesString = registeredModulesString + ", " + name;
    }
  }
}
