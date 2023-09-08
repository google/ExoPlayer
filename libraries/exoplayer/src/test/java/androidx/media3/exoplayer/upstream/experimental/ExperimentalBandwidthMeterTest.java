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
package androidx.media3.exoplayer.upstream.experimental;

import static android.net.NetworkInfo.State.CONNECTED;
import static android.net.NetworkInfo.State.DISCONNECTED;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import androidx.media3.common.C;
import androidx.media3.common.util.NetworkTypeObserver;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.test.utils.FakeDataSource;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.Duration;
import java.util.Random;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowTelephonyManager;

/** Unit test for {@link ExperimentalBandwidthMeter}. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = Config.ALL_SDKS) // Test all SDKs because network detection logic changed over time.
public final class ExperimentalBandwidthMeterTest {

  private static final String FAST_COUNTRY_ISO = "TW";
  private static final String SLOW_COUNTRY_ISO = "PG";

  private TelephonyManager telephonyManager;
  private ConnectivityManager connectivityManager;
  private NetworkInfo networkInfoOffline;
  private NetworkInfo networkInfoWifi;
  private NetworkInfo networkInfo2g;
  private NetworkInfo networkInfo3g;
  private NetworkInfo networkInfo4g;
  private NetworkInfo networkInfo5gSa;
  private NetworkInfo networkInfoEthernet;

  @Before
  public void setUp() {
    NetworkTypeObserver.resetForTests();
    connectivityManager =
        (ConnectivityManager)
            ApplicationProvider.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    telephonyManager =
        (TelephonyManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
    Shadows.shadowOf(telephonyManager).setNetworkCountryIso(FAST_COUNTRY_ISO);
    networkInfoOffline =
        ShadowNetworkInfo.newInstance(
            DetailedState.DISCONNECTED,
            ConnectivityManager.TYPE_WIFI,
            /* subType= */ 0,
            /* isAvailable= */ false,
            DISCONNECTED);
    networkInfoWifi =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            /* subType= */ 0,
            /* isAvailable= */ true,
            CONNECTED);
    networkInfo2g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            /* isAvailable= */ true,
            CONNECTED);
    networkInfo3g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            /* isAvailable= */ true,
            CONNECTED);
    networkInfo4g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_LTE,
            /* isAvailable= */ true,
            CONNECTED);
    networkInfo5gSa =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_NR,
            /* isAvailable= */ true,
            CONNECTED);
    networkInfoEthernet =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_ETHERNET,
            /* subType= */ 0,
            /* isAvailable= */ true,
            CONNECTED);
    setNetworkCountryIso("non-existent-country-to-force-default-values");
  }

  @Test
  public void defaultInitialBitrateEstimate_forWifi_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeterWifi =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateWifi = bandwidthMeterWifi.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter2g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimateWifi).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forWifi_isGreaterThanEstimateFor3G() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeterWifi =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateWifi = bandwidthMeterWifi.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo3g);
    ExperimentalBandwidthMeter bandwidthMeter3g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimateWifi).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forEthernet_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfoEthernet);
    ExperimentalBandwidthMeter bandwidthMeterEthernet =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateEthernet = bandwidthMeterEthernet.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter2g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forEthernet_isGreaterThanEstimateFor3G() {
    setActiveNetworkInfo(networkInfoEthernet);
    ExperimentalBandwidthMeter bandwidthMeterEthernet =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateEthernet = bandwidthMeterEthernet.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo3g);
    ExperimentalBandwidthMeter bandwidthMeter3g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for4G_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfo4g);
    ExperimentalBandwidthMeter bandwidthMeter4g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter2g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimate4g).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for4G_isGreaterThanEstimateFor3G() {
    setActiveNetworkInfo(networkInfo4g);
    ExperimentalBandwidthMeter bandwidthMeter4g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo3g);
    ExperimentalBandwidthMeter bandwidthMeter3g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimate4g).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for3G_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfo3g);
    ExperimentalBandwidthMeter bandwidthMeter3g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter2g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimate3g).isGreaterThan(initialEstimate2g);
  }

  @Test
  @Config(minSdk = 31) // 5G-NSA detection is supported from API 31.
  public void defaultInitialBitrateEstimate_for5gNsa_isGreaterThanEstimateFor4g() {
    setActiveNetworkInfo(networkInfo4g);
    ExperimentalBandwidthMeter bandwidthMeter4g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo4g, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA);
    ExperimentalBandwidthMeter bandwidthMeter5gNsa =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate5gNsa = bandwidthMeter5gNsa.getBitrateEstimate();

    assertThat(initialEstimate5gNsa).isGreaterThan(initialEstimate4g);
  }

  @Test
  @Config(minSdk = 29) // 5G-SA detection is supported from API 29.
  public void defaultInitialBitrateEstimate_for5gSa_isGreaterThanEstimateFor3g() {
    setActiveNetworkInfo(networkInfo3g);
    ExperimentalBandwidthMeter bandwidthMeter3g =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo5gSa);
    ExperimentalBandwidthMeter bandwidthMeter5gSa =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate5gSa = bandwidthMeter5gSa.getBitrateEstimate();

    assertThat(initialEstimate5gSa).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forOffline_isReasonable() {
    setActiveNetworkInfo(networkInfoOffline);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isGreaterThan(100_000L);
    assertThat(initialEstimate).isLessThan(50_000_000L);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_forWifi_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfoWifi);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_forEthernet_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfoEthernet);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for2G_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo2g);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for3G_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo3g);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for4g_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo4g);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  @Config(minSdk = 31) // 5G-NSA detection is supported from API 31.
  public void
      defaultInitialBitrateEstimate_for5gNsa_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo4g, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Ignore // 5G-SA isn't widespread enough yet to define a slow and fast country for testing.
  @Test
  @Config(minSdk = 29) // 5G-SA detection is supported from API 29.
  public void
      defaultInitialBitrateEstimate_for5gSa_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo5gSa);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFast =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void initialBitrateEstimateOverwrite_whileConnectedToNetwork_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_whileOffline_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoOffline);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forWifi_whileConnectedToWifi_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forWifi_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forEthernet_whileConnectedToEthernet_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoEthernet);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_ETHERNET, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forEthernet_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for2G_whileConnectedTo2G_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo2g);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for2G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for3G_whileConnectedTo3G_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo3g);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for3G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for4G_whileConnectedTo4G_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo4g);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for4G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  @Config(minSdk = 31) // 5G-NSA detection is supported from API 31.
  public void initialBitrateEstimateOverwrite_for5gNsa_whileConnectedTo5gNsa_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo4g, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_5G_NSA, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  @Config(minSdk = 31) // 5G-NSA detection is supported from API 31.
  public void
      initialBitrateEstimateOverwrite_for5gNsa_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfo4g);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_5G_NSA, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  @Config(minSdk = 29) // 5G-SA detection is supported from API 29.
  public void initialBitrateEstimateOverwrite_for5gSa_whileConnectedTo5gSa_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo5gSa);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_5G_SA, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  @Config(minSdk = 29) // 5G-SA detection is supported from API 29.
  public void
      initialBitrateEstimateOverwrite_for5gSa_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_5G_SA, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forOffline_whileOffline_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoOffline);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_OFFLINE, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forOffline_whileConnectedToNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_OFFLINE, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forCountry_usesDefaultValuesForCountry() {
    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterSlow =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    setNetworkCountryIso(FAST_COUNTRY_ISO);
    ExperimentalBandwidthMeter bandwidthMeterFastWithSlowOverwrite =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(SLOW_COUNTRY_ISO)
            .build();
    long initialEstimateFastWithSlowOverwrite =
        bandwidthMeterFastWithSlowOverwrite.getBitrateEstimate();

    assertThat(initialEstimateFastWithSlowOverwrite).isEqualTo(initialEstimateSlow);
  }

  @Test
  public void networkTypeOverride_updatesBitrateEstimate() {
    setActiveNetworkInfo(networkInfoEthernet);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateEthernet = bandwidthMeter.getBitrateEstimate();

    bandwidthMeter.setNetworkTypeOverride(C.NETWORK_TYPE_2G);
    long initialEstimate2g = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void networkTypeOverride_doesFullReset() {
    // Simulate transfers for an ethernet connection.
    setActiveNetworkInfo(networkInfoEthernet);
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long[] bitrateEstimatesWithNewInstance =
        simulateTransfers(bandwidthMeter, /* simulatedTransferCount= */ 100);

    // Create a new instance and seed with some transfers.
    setActiveNetworkInfo(networkInfo2g);
    bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    simulateTransfers(bandwidthMeter, /* simulatedTransferCount= */ 100);

    // Override the network type to ethernet and simulate transfers again.
    bandwidthMeter.setNetworkTypeOverride(C.NETWORK_TYPE_ETHERNET);
    long[] bitrateEstimatesAfterReset =
        simulateTransfers(bandwidthMeter, /* simulatedTransferCount= */ 100);

    // If overriding the network type fully reset the bandwidth meter, we expect the bitrate
    // estimates generated during simulation to be the same.
    assertThat(bitrateEstimatesAfterReset).isEqualTo(bitrateEstimatesWithNewInstance);
  }

  @Test
  public void getTimeToFirstByteEstimateUs_withSimultaneousTransferEvents_receivesUpdatedValues() {
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();

    Thread thread =
        new Thread("backgroundTransfers") {
          @Override
          public void run() {
            simulateTransfers(bandwidthMeter, /* simulatedTransferCount= */ 10000);
          }
        };
    thread.start();

    long currentTimeToFirstByteEstimateUs = bandwidthMeter.getTimeToFirstByteEstimateUs();
    boolean timeToFirstByteEstimateUpdated = false;
    while (thread.isAlive()) {
      long newTimeToFirstByteEstimateUs = bandwidthMeter.getTimeToFirstByteEstimateUs();
      if (newTimeToFirstByteEstimateUs != currentTimeToFirstByteEstimateUs) {
        currentTimeToFirstByteEstimateUs = newTimeToFirstByteEstimateUs;
        timeToFirstByteEstimateUpdated = true;
      }
    }

    assertThat(timeToFirstByteEstimateUpdated).isTrue();
  }

  @Test
  public void getBitrateEstimate_withSimultaneousTransferEvents_receivesUpdatedValues() {
    ExperimentalBandwidthMeter bandwidthMeter =
        new ExperimentalBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();

    Thread thread =
        new Thread("backgroundTransfers") {
          @Override
          public void run() {
            simulateTransfers(bandwidthMeter, /* simulatedTransferCount= */ 10000);
          }
        };
    thread.start();

    long currentBitrateEstimate = bandwidthMeter.getBitrateEstimate();
    boolean bitrateEstimateUpdated = false;
    while (thread.isAlive()) {
      long newBitrateEstimate = bandwidthMeter.getBitrateEstimate();
      if (newBitrateEstimate != currentBitrateEstimate) {
        currentBitrateEstimate = newBitrateEstimate;
        bitrateEstimateUpdated = true;
      }
    }

    assertThat(bitrateEstimateUpdated).isTrue();
  }

  private void setActiveNetworkInfo(NetworkInfo networkInfo) {
    setActiveNetworkInfo(networkInfo, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE);
  }

  @SuppressWarnings("StickyBroadcast")
  private void setActiveNetworkInfo(NetworkInfo networkInfo, int networkTypeOverride) {
    // Set network info in ConnectivityManager and TelephonyDisplayInfo in TelephonyManager.
    Shadows.shadowOf(connectivityManager).setActiveNetworkInfo(networkInfo);
    if (Util.SDK_INT >= 31) {
      Object displayInfo =
          ShadowTelephonyManager.createTelephonyDisplayInfo(
              networkInfo.getType(), networkTypeOverride);
      Shadows.shadowOf(telephonyManager).setTelephonyDisplayInfo(displayInfo);
    }
    // Create a sticky broadcast for the connectivity action because Robolectric isn't replying with
    // the current network state if a receiver for this intent is registered.
    ApplicationProvider.getApplicationContext()
        .sendStickyBroadcast(new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
    // Trigger initialization of static network type observer.
    NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    // Wait until all pending messages are handled and the network initialization is done.
    ShadowLooper.idleMainLooper();
  }

  private void setNetworkCountryIso(String countryIso) {
    Shadows.shadowOf(telephonyManager).setNetworkCountryIso(countryIso);
  }

  private static long[] simulateTransfers(
      ExperimentalBandwidthMeter bandwidthMeter, int simulatedTransferCount) {
    long[] bitrateEstimates = new long[simulatedTransferCount];
    Random random = new Random(/* seed= */ 0);
    DataSource dataSource = new FakeDataSource();
    DataSpec dataSpec = new DataSpec(Uri.parse("https://test.com"));
    for (int i = 0; i < simulatedTransferCount; i++) {
      bandwidthMeter.onTransferInitializing(dataSource, dataSpec, /* isNetwork= */ true);
      ShadowSystemClock.advanceBy(Duration.ofMillis(random.nextInt(50)));
      bandwidthMeter.onTransferStart(dataSource, dataSpec, /* isNetwork= */ true);
      ShadowSystemClock.advanceBy(Duration.ofMillis(random.nextInt(5000)));
      bandwidthMeter.onBytesTransferred(
          dataSource,
          dataSpec,
          /* isNetwork= */ true,
          /* bytesTransferred= */ random.nextInt(5 * 1024 * 1024));
      bandwidthMeter.onTransferEnd(dataSource, dataSpec, /* isNetwork= */ true);
      bitrateEstimates[i] = bandwidthMeter.getBitrateEstimate();
    }
    return bitrateEstimates;
  }
}
