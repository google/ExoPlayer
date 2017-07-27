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
package com.google.android.exoplayer2.testutil;

import static junit.framework.Assert.fail;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * A host activity for performing playback tests.
 */
public final class HostActivity extends Activity implements SurfaceHolder.Callback {

  /**
   * Interface for tests that run inside of a {@link HostActivity}.
   */
  public interface HostedTest {

    /**
     * Called on the main thread when the test is started.
     * <p>
     * The test will not be started until the {@link HostActivity} has been resumed and its
     * {@link Surface} has been created.
     *
     * @param host The {@link HostActivity} in which the test is being run.
     * @param surface The {@link Surface}.
     */
    void onStart(HostActivity host, Surface surface);

    /**
     * Called on the main thread to block until the test has stopped or {@link #onStop()} is called.
     *
     * @param timeoutMs The maximum time to block in milliseconds.
     * @return Whether the test has stopped successful.
     */
    boolean blockUntilEnded(long timeoutMs);

    /**
     * Called on the main thread when the test is stopped.
     * <p>
     * The test will be stopped when {@link #blockUntilEnded(long)} returns, if the
     * {@link HostActivity} has been paused, or if the {@link HostActivity}'s {@link Surface} has
     * been destroyed.
     */
    void onStop();

    /**
     * Called on the test thread after the test has finished and been stopped.
     * <p>
     * Implementations may use this method to assert that test criteria were met.
     */
    void onFinished();

  }

  private static final String TAG = "HostActivity";

  private WakeLock wakeLock;
  private WifiLock wifiLock;
  private SurfaceView surfaceView;

  private HostedTest hostedTest;
  private boolean hostedTestStarted;
  private boolean forcedFinished;

  /**
   * Executes a {@link HostedTest} inside the host.
   *
   * @param hostedTest The test to execute.
   * @param timeoutMs The number of milliseconds to wait for the test to finish. If the timeout
   *     is exceeded then the test will fail.
   */
  public void runTest(HostedTest hostedTest, long timeoutMs) {
    runTest(hostedTest, timeoutMs, true);
  }

  /**
   * Executes a {@link HostedTest} inside the host.
   *
   * @param hostedTest The test to execute.
   * @param timeoutMs The number of milliseconds to wait for the test to finish.
   * @param failOnTimeout Whether the test fails when the timeout is exceeded.
   */
  public void runTest(HostedTest hostedTest, long timeoutMs, boolean failOnTimeout) {
    Assertions.checkArgument(timeoutMs > 0);
    Assertions.checkState(Thread.currentThread() != getMainLooper().getThread());

    Assertions.checkState(this.hostedTest == null);
    this.hostedTest = Assertions.checkNotNull(hostedTest);
    hostedTestStarted = false;
    forcedFinished = false;

    final ConditionVariable testStarted = new ConditionVariable();
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        maybeStartHostedTest();
        testStarted.open();
      }
    });
    testStarted.block();

    if (hostedTest.blockUntilEnded(timeoutMs)) {
      hostedTest.onStop();
      if (!forcedFinished) {
        Log.d(TAG, "Test finished. Checking pass conditions.");
        hostedTest.onFinished();
        this.hostedTest = null;
        Log.d(TAG, "Pass conditions checked.");
      } else {
        String message = "Test released before it finished. Activity may have been paused whilst "
            + "test was in progress.";
        Log.e(TAG, message);
        fail(message);
      }
    } else {
      String message = "Test timed out after " + timeoutMs + " ms.";
      Log.e(TAG, message);
      if (failOnTimeout) {
        fail(message);
      }
      maybeStopHostedTest();
    }
  }

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(getResources().getIdentifier("host_activity", "layout", getPackageName()));
    surfaceView = (SurfaceView) findViewById(
        getResources().getIdentifier("surface_view", "id", getPackageName()));
    surfaceView.getHolder().addCallback(this);
  }

  @Override
  public void onStart() {
    Context appContext = getApplicationContext();
    WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
    wifiLock = wifiManager.createWifiLock(getWifiLockMode(), TAG);
    wifiLock.acquire();
    PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    wakeLock.acquire();
    super.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    maybeStartHostedTest();
  }

  @Override
  public void onPause() {
    super.onPause();
    maybeStopHostedTest();
  }

  @Override
  public void onStop() {
    super.onStop();
    wakeLock.release();
    wakeLock = null;
    wifiLock.release();
    wifiLock = null;
  }

  // SurfaceHolder.Callback

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    maybeStartHostedTest();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    maybeStopHostedTest();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Do nothing.
  }

  // Internal logic

  private void maybeStartHostedTest() {
    if (hostedTest == null || hostedTestStarted) {
      return;
    }
    Surface surface = surfaceView.getHolder().getSurface();
    if (surface != null && surface.isValid()) {
      hostedTestStarted = true;
      Log.d(TAG, "Starting test.");
      hostedTest.onStart(this, surface);
    }
  }

  private void maybeStopHostedTest() {
    if (hostedTest != null && hostedTestStarted) {
      forcedFinished = true;
      hostedTest.onStop();
      hostedTest = null;
    }
  }

  @SuppressLint("InlinedApi")
  private static int getWifiLockMode() {
    return Util.SDK_INT < 12 ? WifiManager.WIFI_MODE_FULL : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
  }

}
