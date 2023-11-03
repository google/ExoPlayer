/*
 * Copyright 2021 The Android Open Source Project
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

import android.os.Bundle;
import androidx.media3.common.util.UnstableApi;

/**
 * @deprecated Interface not needed, call {@code toBundle()} on the target object directly.
 */
@Deprecated
@UnstableApi
public interface Bundleable {

  /** Returns a {@link Bundle} representing the information stored in this object. */
  Bundle toBundle();

  /**
   * @deprecated Interface not needed, call {@code fromBundle()} on the target type directly.
   */
  @Deprecated
  interface Creator<T extends Bundleable> {

    /**
     * Restores a {@link Bundleable} instance from a {@link Bundle} produced by {@link
     * Bundleable#toBundle()}.
     *
     * <p>It guarantees the compatibility of {@link Bundle} representations produced by different
     * versions of {@link Bundleable#toBundle()} by providing best default values for missing
     * fields. It throws an exception if any essential fields are missing.
     */
    T fromBundle(Bundle bundle);
  }
}
