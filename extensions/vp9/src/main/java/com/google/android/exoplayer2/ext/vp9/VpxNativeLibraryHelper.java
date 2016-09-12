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
package com.google.android.exoplayer2.ext.vp9;

import com.google.android.exoplayer2.util.Assertions;

/**
 * Configure the native libraries that are used by the {@link VpxDecoder}.
 */
public final class VpxNativeLibraryHelper {

  private static boolean loadAttempted;
  private static boolean isAvailable;
  private static String[] nativeLibs = { "vpx", "vpxJNI" };

  private static final Object lock = new Object();

  private VpxNativeLibraryHelper() {}

  /**
   * Override the default set of native libraries loaded for Vpx decoder support.
   * If an application wishes to call this method, it must do so before calling any other method
   * defined by this class, and before instantiating a {@link LibvpxVideoRenderer} instance.
   */
  public static void setNativeLibraries(String... libs) {
    synchronized (lock) {
      Assertions.checkState(!loadAttempted,
          "Vpx native libs must be set earlier, they have already been loaded.");
      nativeLibs = libs;
    }
  }

  /**
   * Returns whether the underlying libvpx library is available.
   */
  public static boolean isLibvpxAvailable() {
    return loadNativeLibraries();
  }

  /**
   * Returns the version of the underlying libvpx library if available, otherwise {@code null}.
   */
  public static String getLibvpxVersion() {
    return isLibvpxAvailable() ? nativeGetLibvpxConfig() : null;
  }

  /**
   * Returns the configuration string with which the underlying libvpx library was built.
   */
  public static String getLibvpxConfig() {
    return isLibvpxAvailable() ? nativeGetLibvpxVersion() : null;
  }

  private static boolean loadNativeLibraries() {
    synchronized (lock) {
      if (loadAttempted) {
        return isAvailable;
      }

      loadAttempted = true;
      try {
        for (String lib : VpxNativeLibraryHelper.nativeLibs) {
          System.loadLibrary(lib);
        }
        isAvailable = true;
      } catch (UnsatisfiedLinkError exception) {
        isAvailable = false;
      }
      return isAvailable;
    }
  }

  private static native String nativeGetLibvpxConfig();
  private static native String nativeGetLibvpxVersion();
}
