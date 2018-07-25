/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.telephony.TelephonyManager;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ClosedSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowTelephonyManager;

/** Unit test for {@link CountryAndNetworkTypeBandwidthMeter}. */
@ClosedSource(reason = "Not ready yet")
@RunWith(RobolectricTestRunner.class)
public final class CountryAndNetworkTypeBandwidthMeterTest {

  private static final String FAST_COUNTRY_ISO = "EE";
  private static final String SLOW_COUNTRY_ISO = "PG";

  private ShadowTelephonyManager shadowTelephonyManager;
  private ShadowConnectivityManager shadowConnectivityManager;
  private NetworkInfo networkInfoOffline;
  private NetworkInfo networkInfoWifi;
  private NetworkInfo networkInfo2g;
  private NetworkInfo networkInfo3g;
  private NetworkInfo networkInfo4g;
  private NetworkInfo networkInfoEthernet;

  @Before
  public void setUp() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager)
            RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
    shadowConnectivityManager = Shadows.shadowOf(connectivityManager);
    TelephonyManager telephonyManager =
        (TelephonyManager)
            RuntimeEnvironment.application.getSystemService(Context.TELEPHONY_SERVICE);
    shadowTelephonyManager = Shadows.shadowOf(telephonyManager);
    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    networkInfoOffline =
        ShadowNetworkInfo.newInstance(
            DetailedState.DISCONNECTED,
            ConnectivityManager.TYPE_WIFI,
            /* subType= */ 0,
            /* isAvailable= */ false,
            /* isConnected= */ false);
    networkInfoWifi =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            /* subType= */ 0,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfo2g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfo3g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfo4g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_LTE,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfoEthernet =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_ETHERNET,
            /* subType= */ 0,
            /* isAvailable= */ true,
            /* isConnected= */ true);
  }

  @Test
  public void defaultInitialBitrateEstimate_forWifi_isGreaterThanEstimateFor2G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterWifi =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateWifi = bandwidthMeterWifi.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter2g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimateWifi).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forWifi_isGreaterThanEstimateFor3G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterWifi =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateWifi = bandwidthMeterWifi.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo3g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter3g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimateWifi).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forEthernet_isGreaterThanEstimateFor2G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoEthernet);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterEthernet =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateEthernet = bandwidthMeterEthernet.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter2g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forEthernet_isGreaterThanEstimateFor3G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoEthernet);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterEthernet =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateEthernet = bandwidthMeterEthernet.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo3g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter3g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for4G_isGreaterThanEstimateFor2G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo4g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter4g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter2g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimate4g).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for4G_isGreaterThanEstimateFor3G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo4g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter4g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo3g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter3g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimate4g).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for3G_isGreaterThanEstimateFor2G() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo3g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter3g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter2g =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimate3g).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forOffline_isReasonable() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoOffline);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isGreaterThan(100_000L);
    assertThat(initialEstimate).isLessThan(50_000_000L);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_forWifi_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterFast =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    shadowTelephonyManager.setNetworkCountryIso(SLOW_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterSlow =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_forEthernet_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoEthernet);
    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterFast =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    shadowTelephonyManager.setNetworkCountryIso(SLOW_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterSlow =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for2G_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterFast =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    shadowTelephonyManager.setNetworkCountryIso(SLOW_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterSlow =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for3G_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo3g);
    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterFast =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    shadowTelephonyManager.setNetworkCountryIso(SLOW_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterSlow =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for4g_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo4g);
    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterFast =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    shadowTelephonyManager.setNetworkCountryIso(SLOW_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterSlow =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void initialBitrateEstimateOverwrite_whileConnectedToNetwork_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_whileOffline_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoOffline);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forWifi_whileConnectedToWifi_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forWifi_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forEthernet_whileConnectedToEthernet_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoEthernet);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_ETHERNET, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forEthernet_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for2G_whileConnectedTo2G_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo2g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for2G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for3G_whileConnectedTo3G_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo3g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for3G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for4G_whileConnectedTo4G_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo4g);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for4G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forOffline_whileOffline_setsInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoOffline);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_OFFLINE, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forOffline_whileConnectedToNetwork_doesNotSetInitialEstimate() {
    shadowConnectivityManager.setActiveNetworkInfo(networkInfoWifi);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(C.NETWORK_TYPE_OFFLINE, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forCountry_usesDefaultValuesForCountry() {
    shadowTelephonyManager.setNetworkCountryIso(SLOW_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterSlow =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    shadowTelephonyManager.setNetworkCountryIso(FAST_COUNTRY_ISO);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterFastWithSlowOverwrite =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application)
            .setInitialBitrateEstimate(SLOW_COUNTRY_ISO)
            .build();
    long initialEstimateFastWithSlowOverwrite =
        bandwidthMeterFastWithSlowOverwrite.getBitrateEstimate();

    assertThat(initialEstimateFastWithSlowOverwrite).isEqualTo(initialEstimateSlow);
  }

  @Test
  public void defaultInitialBitrateEstimate_withoutContext_isReasonable() {
    CountryAndNetworkTypeBandwidthMeter bandwidthMeterWithBuilder =
        new CountryAndNetworkTypeBandwidthMeter.Builder().build();
    long initialEstimateWithBuilder = bandwidthMeterWithBuilder.getBitrateEstimate();

    CountryAndNetworkTypeBandwidthMeter bandwidthMeterWithoutBuilder =
        new CountryAndNetworkTypeBandwidthMeter();
    long initialEstimateWithoutBuilder = bandwidthMeterWithoutBuilder.getBitrateEstimate();

    assertThat(initialEstimateWithBuilder).isGreaterThan(100_000L);
    assertThat(initialEstimateWithBuilder).isLessThan(50_000_000L);
    assertThat(initialEstimateWithoutBuilder).isGreaterThan(100_000L);
    assertThat(initialEstimateWithoutBuilder).isLessThan(50_000_000L);
  }

  @Test
  public void defaultInitialBitrateEstimate_withoutAccessNetworkStatePermission_isReasonable() {
    ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
    shadowApplication.denyPermissions(ACCESS_NETWORK_STATE);
    CountryAndNetworkTypeBandwidthMeter bandwidthMeter =
        new CountryAndNetworkTypeBandwidthMeter.Builder(RuntimeEnvironment.application).build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isGreaterThan(100_000L);
    assertThat(initialEstimate).isLessThan(50_000_000L);
  }
}
