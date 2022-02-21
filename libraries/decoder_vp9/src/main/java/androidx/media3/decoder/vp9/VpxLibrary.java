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
package androidx.media3.decoder.vp9;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.util.LibraryLoader;
import androidx.media3.common.util.UnstableApi;

/** Configures and queries the underlying native library. */
@UnstableApi
public final class VpxLibrary {

  static {
    MediaLibraryInfo.registerModule("media3.decoder.vpx");
  }

  private static final LibraryLoader LOADER =
      new LibraryLoader("vpx", "vpxV2JNI") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  private static @C.CryptoType int cryptoType = C.CRYPTO_TYPE_UNSUPPORTED;

  private VpxLibrary() {}

  /**
   * Override the names of the Vpx native libraries. If an application wishes to call this method,
   * it must do so before calling any other method defined by this class, and before instantiating a
   * {@link LibvpxVideoRenderer} instance.
   *
   * @param cryptoType The {@link C.CryptoType} for which the decoder library supports decrypting
   *     protected content, or {@link C#CRYPTO_TYPE_UNSUPPORTED} if the library does not support
   *     decryption.
   * @param libraries The names of the Vpx native libraries.
   */
  public static void setLibraries(@C.CryptoType int cryptoType, String... libraries) {
    VpxLibrary.cryptoType = cryptoType;
    LOADER.setLibraries(libraries);
  }

  /** Returns whether the underlying library is available, loading it if necessary. */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }

  /** Returns the version of the underlying library if available, or null otherwise. */
  @Nullable
  public static String getVersion() {
    return isAvailable() ? vpxGetVersion() : null;
  }

  /**
   * Returns the configuration string with which the underlying library was built if available, or
   * null otherwise.
   */
  @Nullable
  public static String getBuildConfig() {
    return isAvailable() ? vpxGetBuildConfig() : null;
  }

  /** Returns true if the underlying libvpx library supports high bit depth. */
  public static boolean isHighBitDepthSupported() {
    String config = getBuildConfig();
    int indexHbd = config != null ? config.indexOf("--enable-vp9-highbitdepth") : -1;
    return indexHbd >= 0;
  }

  /** Returns whether the library supports the given {@link C.CryptoType}. */
  public static boolean supportsCryptoType(@C.CryptoType int cryptoType) {
    return cryptoType == C.CRYPTO_TYPE_NONE
        || (cryptoType != C.CRYPTO_TYPE_UNSUPPORTED && cryptoType == VpxLibrary.cryptoType);
  }

  private static native String vpxGetVersion();

  private static native String vpxGetBuildConfig();

  public static native boolean vpxIsSecureDecodeSupported();
}
