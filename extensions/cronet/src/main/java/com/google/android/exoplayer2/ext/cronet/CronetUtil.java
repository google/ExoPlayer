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
package com.google.android.exoplayer2.ext.cronet;

import static java.lang.Math.min;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;

/** Cronet utility methods. */
public final class CronetUtil {

  private static final String TAG = "CronetUtil";

  /**
   * Builds a {@link CronetEngine} suitable for use with {@link CronetDataSource}. When choosing a
   * {@link CronetProvider Cronet provider} to build the {@link CronetEngine}, disabled providers
   * are not considered. Neither are fallback providers, since it's more efficient to use {@link
   * DefaultHttpDataSource} than it is to use {@link CronetDataSource} with a fallback {@link
   * CronetEngine}.
   *
   * <p>Note that it's recommended for applications to create only one instance of {@link
   * CronetEngine}, so if your application already has an instance for performing other networking,
   * then that instance should be used and calling this method is unnecessary. See the <a
   * href="https://developer.android.com/guide/topics/connectivity/cronet/start">Android developer
   * guide</a> to learn more about using Cronet for network operations.
   *
   * @param context A context.
   * @return The {@link CronetEngine}, or {@code null} if no suitable engine could be built.
   */
  @Nullable
  public static CronetEngine buildCronetEngine(Context context) {
    return buildCronetEngine(context, /* userAgent= */ null, /* preferGooglePlayServices= */ false);
  }

  /**
   * Builds a {@link CronetEngine} suitable for use with {@link CronetDataSource}. When choosing a
   * {@link CronetProvider Cronet provider} to build the {@link CronetEngine}, disabled providers
   * are not considered. Neither are fallback providers, since it's more efficient to use {@link
   * DefaultHttpDataSource} than it is to use {@link CronetDataSource} with a fallback {@link
   * CronetEngine}.
   *
   * <p>Note that it's recommended for applications to create only one instance of {@link
   * CronetEngine}, so if your application already has an instance for performing other networking,
   * then that instance should be used and calling this method is unnecessary. See the <a
   * href="https://developer.android.com/guide/topics/connectivity/cronet/start">Android developer
   * guide</a> to learn more about using Cronet for network operations.
   *
   * @param context A context.
   * @param userAgent A default user agent, or {@code null} to use a default user agent of the
   *     {@link CronetEngine}.
   * @param preferGooglePlayServices Whether Cronet from Google Play Services should be preferred
   *     over Cronet Embedded, if both are available.
   * @return The {@link CronetEngine}, or {@code null} if no suitable engine could be built.
   */
  @Nullable
  public static CronetEngine buildCronetEngine(
      Context context, @Nullable String userAgent, boolean preferGooglePlayServices) {
    List<CronetProvider> cronetProviders = new ArrayList<>(CronetProvider.getAllProviders(context));
    // Remove disabled and fallback Cronet providers from list.
    for (int i = cronetProviders.size() - 1; i >= 0; i--) {
      if (!cronetProviders.get(i).isEnabled()
          || CronetProvider.PROVIDER_NAME_FALLBACK.equals(cronetProviders.get(i).getName())) {
        cronetProviders.remove(i);
      }
    }
    // Sort remaining providers by type and version.
    CronetProviderComparator providerComparator =
        new CronetProviderComparator(preferGooglePlayServices);
    Collections.sort(cronetProviders, providerComparator);
    for (int i = 0; i < cronetProviders.size(); i++) {
      String providerName = cronetProviders.get(i).getName();
      try {
        CronetEngine.Builder cronetEngineBuilder = cronetProviders.get(i).createBuilder();
        if (userAgent != null) {
          cronetEngineBuilder.setUserAgent(userAgent);
        }
        CronetEngine cronetEngine = cronetEngineBuilder.build();
        Log.d(TAG, "CronetEngine built using " + providerName);
        return cronetEngine;
      } catch (SecurityException e) {
        Log.w(
            TAG,
            "Failed to build CronetEngine. Please check that the process has "
                + "android.permission.ACCESS_NETWORK_STATE.");
      } catch (UnsatisfiedLinkError e) {
        Log.w(
            TAG,
            "Failed to link Cronet binaries. Please check that native Cronet binaries are"
                + "bundled into your app.");
      }
    }
    Log.w(TAG, "CronetEngine could not be built.");
    return null;
  }

  private CronetUtil() {}

  private static class CronetProviderComparator implements Comparator<CronetProvider> {

    /*
     * Copy of com.google.android.gms.net.CronetProviderInstaller.PROVIDER_NAME. We have our own
     * copy because GMSCore CronetProvider classes are unavailable in some (internal to Google)
     * build configurations.
     */
    private static final String GOOGLE_PLAY_SERVICES_PROVIDER_NAME =
        "Google-Play-Services-Cronet-Provider";

    private final boolean preferGooglePlayServices;

    public CronetProviderComparator(boolean preferGooglePlayServices) {
      this.preferGooglePlayServices = preferGooglePlayServices;
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
      } else if (GOOGLE_PLAY_SERVICES_PROVIDER_NAME.equals(providerName)) {
        return preferGooglePlayServices ? 0 : 2;
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
