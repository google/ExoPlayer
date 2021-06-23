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
package com.google.android.exoplayer2.ext.cronet;

import static java.lang.Math.min;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;

/** A wrapper class for a {@link CronetEngine}. */
public final class CronetEngineWrapper {

  private static final String TAG = "CronetEngineWrapper";

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
   * @param preferGMSCoreCronet Whether Cronet from GMSCore should be preferred over natively
   *     bundled Cronet if both are available.
   */
  public CronetEngineWrapper(
      Context context, @Nullable String userAgent, boolean preferGMSCoreCronet) {
    @Nullable CronetEngine cronetEngine = null;
    List<CronetProvider> cronetProviders = new ArrayList<>(CronetProvider.getAllProviders(context));
    // Remove disabled and fallback Cronet providers from list
    for (int i = cronetProviders.size() - 1; i >= 0; i--) {
      if (!cronetProviders.get(i).isEnabled()
          || CronetProvider.PROVIDER_NAME_FALLBACK.equals(cronetProviders.get(i).getName())) {
        cronetProviders.remove(i);
      }
    }
    // Sort remaining providers by type and version.
    CronetProviderComparator providerComparator = new CronetProviderComparator(preferGMSCoreCronet);
    Collections.sort(cronetProviders, providerComparator);
    for (int i = 0; i < cronetProviders.size() && cronetEngine == null; i++) {
      String providerName = cronetProviders.get(i).getName();
      try {
        CronetEngine.Builder cronetEngineBuilder = cronetProviders.get(i).createBuilder();
        if (userAgent != null) {
          cronetEngineBuilder.setUserAgent(userAgent);
        }
        cronetEngine = cronetEngineBuilder.build();
        Log.d(TAG, "CronetEngine built using " + providerName);
      } catch (SecurityException e) {
        Log.w(
            TAG,
            "Failed to build CronetEngine. Please check if current process has "
                + "android.permission.ACCESS_NETWORK_STATE.");
      } catch (UnsatisfiedLinkError e) {
        Log.w(
            TAG,
            "Failed to link Cronet binaries. Please check if native Cronet binaries are "
                + "bundled into your app.");
      }
    }
    if (cronetEngine == null) {
      Log.w(TAG, "CronetEngine could not be built.");
    }
    this.cronetEngine = cronetEngine;
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

  private static class CronetProviderComparator implements Comparator<CronetProvider> {

    /*
     * Copy of com.google.android.gms.net.CronetProviderInstaller.PROVIDER_NAME. We have our own
     * copy because GMSCore CronetProvider classes are unavailable in some (internal to Google)
     * build configurations.
     */
    private static final String GMS_CORE_PROVIDER_NAME = "Google-Play-Services-Cronet-Provider";

    private final boolean preferGMSCoreCronet;

    public CronetProviderComparator(boolean preferGMSCoreCronet) {
      this.preferGMSCoreCronet = preferGMSCoreCronet;
    }

    @Override
    public int compare(CronetProvider providerLeft, CronetProvider providerRight) {
      int providerComparison = getPriority(providerLeft) - getPriority(providerRight);
      if (providerComparison != 0) {
        return providerComparison;
      }
      return -compareVersionStrings(providerLeft.getVersion(), providerRight.getVersion());
    }

    /**
     * Returns the priority score for a Cronet provider, where a smaller score indicates higher
     * priority.
     */
    private int getPriority(CronetProvider provider) {
      String providerName = provider.getName();
      if (CronetProvider.PROVIDER_NAME_APP_PACKAGED.equals(providerName)) {
        return 1;
      } else if (GMS_CORE_PROVIDER_NAME.equals(providerName)) {
        return preferGMSCoreCronet ? 0 : 2;
      } else {
        return 3;
      }
    }

    /** Compares version strings of format "12.123.35.23". */
    private static int compareVersionStrings(
        @Nullable String versionLeft, @Nullable String versionRight) {
      if (versionLeft == null || versionRight == null) {
        return 0;
      }
      String[] versionStringsLeft = Util.split(versionLeft, "\\.");
      String[] versionStringsRight = Util.split(versionRight, "\\.");
      int minLength = min(versionStringsLeft.length, versionStringsRight.length);
      for (int i = 0; i < minLength; i++) {
        if (!versionStringsLeft[i].equals(versionStringsRight[i])) {
          try {
            int versionIntLeft = Integer.parseInt(versionStringsLeft[i]);
            int versionIntRight = Integer.parseInt(versionStringsRight[i]);
            return versionIntLeft - versionIntRight;
          } catch (NumberFormatException e) {
            return 0;
          }
        }
      }
      return 0;
    }
  }
}
