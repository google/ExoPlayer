/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.PowerManager.WakeLock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowPowerManager;

/** Unit tests for {@link WakeLockManager} */
@RunWith(AndroidJUnit4.class)
public class WakeLockManagerTest {

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void stayAwakeFalse_wakeLockIsNeverHeld() {
    WakeLockManager wakeLockManager = new WakeLockManager(context);
    wakeLockManager.setEnabled(true);
    wakeLockManager.setStayAwake(false);

    WakeLock wakeLock = ShadowPowerManager.getLatestWakeLock();
    assertThat(wakeLock.isHeld()).isFalse();

    wakeLockManager.setEnabled(false);

    assertThat(wakeLock.isHeld()).isFalse();
  }

  @Test
  public void stayAwakeTrue_wakeLockIsOnlyHeldWhenEnabled() {
    WakeLockManager wakeLockManager = new WakeLockManager(context);
    wakeLockManager.setEnabled(true);
    wakeLockManager.setStayAwake(true);

    WakeLock wakeLock = ShadowPowerManager.getLatestWakeLock();

    assertThat(wakeLock.isHeld()).isTrue();

    wakeLockManager.setEnabled(false);

    assertThat(wakeLock.isHeld()).isFalse();
  }
}
