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
package com.google.android.exoplayer2.scheduler;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Defines a set of device state requirements. */
public final class Requirements implements Parcelable {

  /**
   * Requirement flags. Possible flag values are {@link #NETWORK}, {@link #NETWORK_UNMETERED},
   * {@link #DEVICE_IDLE} and {@link #DEVICE_CHARGING}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {NETWORK, NETWORK_UNMETERED, DEVICE_IDLE, DEVICE_CHARGING, DEVICE_BATTERY_NOT_LOW, DEVICE_STORAGE_NOT_LOW})
  public @interface RequirementFlags {}

  /** Requirement that the device has network connectivity. */
  public static final int NETWORK = 1;
  /** Requirement that the device has a network connection that is unmetered. */
  public static final int NETWORK_UNMETERED = 1 << 1;
  /** Requirement that the device is idle. */
  public static final int DEVICE_IDLE = 1 << 2;
  /** Requirement that the device is charging. */
  public static final int DEVICE_CHARGING = 1 << 3;
  /** Requirement that the storage is not low. */
  public static final int DEVICE_STORAGE_NOT_LOW = 1 << 4;
  /** Requirement that the battery is not low. */
  public static final int DEVICE_BATTERY_NOT_LOW = 1 << 5;

  /** Constant indicating the battery is not plugged in a power source */
  private static final int BATTERY_PLUGGED_NONE = 0;
  /** Constant when the battery is considered low (in percentage) */
  private static final float BATTERY_LOW_PERCENTAGE = 0.15f;


  @RequirementFlags private final int requirements;

  /** @param requirements A combination of requirement flags. */
  public Requirements(@RequirementFlags int requirements) {
    if ((requirements & NETWORK_UNMETERED) != 0) {
      // Make sure network requirement flags are consistent.
      requirements |= NETWORK;
    }
    this.requirements = requirements;
  }

  /** Returns the requirements. */
  @RequirementFlags
  public int getRequirements() {
    return requirements;
  }

  /** Returns whether network connectivity is required. */
  public boolean isNetworkRequired() {
    return (requirements & NETWORK) != 0;
  }

  /** Returns whether un-metered network connectivity is required. */
  public boolean isUnmeteredNetworkRequired() {
    return (requirements & NETWORK_UNMETERED) != 0;
  }

  /** Returns whether the device is required to be charging. */
  public boolean isChargingRequired() {
    return (requirements & DEVICE_CHARGING) != 0;
  }

  /** Returns whether the device is required to be idle. */
  public boolean isIdleRequired() {
    return (requirements & DEVICE_IDLE) != 0;
  }

  public boolean isStorageNotLowRequired() {
    return (requirements & DEVICE_STORAGE_NOT_LOW) != 0;
  }

  public boolean isBatteryNotLowRequired() {
    return (requirements & DEVICE_BATTERY_NOT_LOW) != 0;
  }

  /**
   * Returns whether the requirements are met.
   *
   * @param context Any context.
   * @return Whether the requirements are met.
   */
  public boolean checkRequirements(Context context) {
    return getNotMetRequirements(context) == 0;
  }

  /**
   * Returns requirements that are not met, or 0.
   *
   * @param context Any context.
   * @return The requirements that are not met, or 0.
   */
  @RequirementFlags
  public int getNotMetRequirements(Context context) {
    @RequirementFlags int notMetRequirements = getNotMetNetworkRequirements(context);
    if (isChargingRequired() && !isDeviceCharging(context)) {
      notMetRequirements |= DEVICE_CHARGING;
    }
    if (isIdleRequired() && !isDeviceIdle(context)) {
      notMetRequirements |= DEVICE_IDLE;
    }
    if (isBatteryNotLowRequired() && !isBatteryNotLow(context)) {
      notMetRequirements |= DEVICE_BATTERY_NOT_LOW;
    }
    if (isStorageNotLowRequired() && !isStorageNotLow(context)) {
      notMetRequirements |= DEVICE_STORAGE_NOT_LOW;
    }
    return notMetRequirements;
  }

  @RequirementFlags
  private int getNotMetNetworkRequirements(Context context) {
    if (!isNetworkRequired()) {
      return 0;
    }

    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = Assertions.checkNotNull(connectivityManager).getActiveNetworkInfo();
    if (networkInfo == null
        || !networkInfo.isConnected()
        || !isInternetConnectivityValidated(connectivityManager)) {
      return requirements & (NETWORK | NETWORK_UNMETERED);
    }

    if (isUnmeteredNetworkRequired() && connectivityManager.isActiveNetworkMetered()) {
      return NETWORK_UNMETERED;
    }

    return 0;
  }

  private boolean isDeviceCharging(Context context) {
    Intent batteryStatus =
        context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (batteryStatus == null) {
      return false;
    }
    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    return status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL;
  }

  private boolean isDeviceIdle(Context context) {
    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    return Util.SDK_INT >= 23
        ? powerManager.isDeviceIdleMode()
        : Util.SDK_INT >= 20 ? !powerManager.isInteractive() : !powerManager.isScreenOn();
  }

  /**
   * Implementation taken from the the WorkManager source.
   * @see <a href="https://android.googlesource.com/platform/frameworks/support/+/androidx-master-dev/work/workmanager/src/main/java/androidx/work/impl/constraints/trackers/BatteryNotLowTracker.java">BatteryNotLowTracker</a>
   */
  private boolean isBatteryNotLow(Context context) {
    IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    Intent intent = context.registerReceiver(null, intentFilter);
    if (intent == null) {
      return true;
    }
    int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, BATTERY_PLUGGED_NONE);
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    float batteryPercentage = level / (float) scale;
    return (plugged != BATTERY_PLUGGED_NONE
        || status == BatteryManager.BATTERY_STATUS_UNKNOWN
        || batteryPercentage > BATTERY_LOW_PERCENTAGE);
  }

  /**
   * Implementation taken from the the WorkManager source.
   * @see <a href="https://android.googlesource.com/platform/frameworks/support/+/androidx-master-dev/work/workmanager/src/main/java/androidx/work/impl/constraints/trackers/StorageNotLowTracker.java">StorageNotLowTracker</a>
   */
  private boolean isStorageNotLow(Context context) {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
    intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
    Intent intent = context.registerReceiver(null, intentFilter);
    if (intent == null || intent.getAction() == null) {
      // ACTION_DEVICE_STORAGE_LOW is a sticky broadcast that is removed when sufficient
      // storage is available again.  ACTION_DEVICE_STORAGE_OK is not sticky.  So if we
      // don't receive anything here, we can assume that the storage state is okay.
      return true;
    } else {
      switch (intent.getAction()) {
        case Intent.ACTION_DEVICE_STORAGE_OK:
          return true;
        case Intent.ACTION_DEVICE_STORAGE_LOW:
          return false;
        default:
          // This should never happen because the intent filter is configured
          // correctly.
          return true;
      }
    }
  }

  private static boolean isInternetConnectivityValidated(ConnectivityManager connectivityManager) {
    // It's possible to query NetworkCapabilities from API level 23, but RequirementsWatcher only
    // fires an event to update its Requirements when NetworkCapabilities change from API level 24.
    // Since Requirements won't be updated, we assume connectivity is validated on API level 23.
    if (Util.SDK_INT < 24) {
      return true;
    }
    Network activeNetwork = connectivityManager.getActiveNetwork();
    if (activeNetwork == null) {
      return false;
    }
    NetworkCapabilities networkCapabilities =
        connectivityManager.getNetworkCapabilities(activeNetwork);
    return networkCapabilities != null
        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return requirements == ((Requirements) o).requirements;
  }

  @Override
  public int hashCode() {
    return requirements;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(requirements);
  }

  public static final Parcelable.Creator<Requirements> CREATOR =
      new Creator<Requirements>() {

        @Override
        public Requirements createFromParcel(Parcel in) {
          return new Requirements(in.readInt());
        }

        @Override
        public Requirements[] newArray(int size) {
          return new Requirements[size];
        }
      };
}
