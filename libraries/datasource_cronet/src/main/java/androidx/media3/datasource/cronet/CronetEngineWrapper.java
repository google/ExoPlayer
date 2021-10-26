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
package androidx.media3.datasource.cronet;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;

/**
 * A wrapper class for a {@link CronetEngine}.
 *
 * @deprecated Use {@link CronetEngine} directly. See the <a
 *     href="https://developer.android.com/guide/topics/connectivity/cronet/start">Android developer
 *     guide</a> to learn how to instantiate a {@link CronetEngine} for use by your application. You
 *     can also use {@link CronetUtil#buildCronetEngine} to build a {@link CronetEngine} suitable
 *     for use with {@link CronetDataSource}.
 */
@Deprecated
@UnstableApi
public final class CronetEngineWrapper {

  @Nullable private final CronetEngine cronetEngine;

  /**
   * Creates a wrapper for a {@link CronetEngine} built using the most suitable {@link
   * CronetProvider}. When natively bundled Cronet and GMSCore Cronet are both available, the
   * natively bundled provider is preferred.
   *
   * @param context A context.
   */
  public CronetEngineWrapper(Context context) {
    this(context, /* userAgent= */ null, /* preferGMSCoreCronet= */ false);
  }

  /**
   * Creates a wrapper for a {@link CronetEngine} built using the most suitable {@link
   * CronetProvider}. When natively bundled Cronet and GMSCore Cronet are both available, {@code
   * preferGMSCoreCronet} determines which is preferred.
   *
   * @param context A context.
   * @param userAgent A default user agent, or {@code null} to use a default user agent of the
   *     {@link CronetEngine}.
   * @param preferGooglePlayServices Whether Cronet from Google Play Services should be preferred
   *     over Cronet Embedded, if both are available.
   */
  public CronetEngineWrapper(
      Context context, @Nullable String userAgent, boolean preferGooglePlayServices) {
    cronetEngine = CronetUtil.buildCronetEngine(context, userAgent, preferGooglePlayServices);
  }

  /**
   * Creates a wrapper for an existing {@link CronetEngine}.
   *
   * @param cronetEngine The CronetEngine to wrap.
   */
  public CronetEngineWrapper(CronetEngine cronetEngine) {
    this.cronetEngine = cronetEngine;
  }

  /**
   * Returns the wrapped {@link CronetEngine}.
   *
   * @return The CronetEngine, or null if no CronetEngine is available.
   */
  @Nullable
  /* package */ CronetEngine getCronetEngine() {
    return cronetEngine;
  }
}
