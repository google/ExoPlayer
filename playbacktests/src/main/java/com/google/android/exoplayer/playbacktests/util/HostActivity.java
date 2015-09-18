/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.playbacktests.util;

import static junit.framework.Assert.fail;

import com.google.android.exoplayer.playbacktests.R;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

/**
 * A host activity for performing playback tests.
 */
public final class HostActivity extends Activity implements SurfaceHolder.Callback {

  /**
   * Interface for tests that run inside of a {@link HostActivity}.
   */
  public interface HostedTest {

    /**
     * Called once the activity has been resumed and its surface has been created.
     * <p>
     * Called on the main thread.
     *
     * @param host The host in which the test is being run.
     * @param surface The created surface.
     */
    void initialize(HostActivity host, Surface surface);

    /**
     * Called when the test has finished, or if the activity is paused or its surface is destroyed.
     * <p>
     * Called on the main thread.
     */
    void release();

    /**
     * Called periodically to check whether the test has finished.
     * <p>
     * Called on the main thread.
     *
     * @return True if the test has finished. False otherwise.
     */
    boolean isFinished();

    /**
     * Asserts that the test passed.
     * <p>
     * Called on the test thread once the test has reported that it's finished and after the test
     * has been released.
     */
    void assertPassed();

  }

  private static final String TAG = "HostActivity";

  private WakeLock wakeLock;
  private WifiLock wifiLock;

  private SurfaceView surfaceView;
  private Handler mainHandler;
  private CheckFinishedRunnable checkFinishedRunnable;

  private HostedTest hostedTest;
  private ConditionVariable hostedTestReleasedCondition;
  private boolean hostedTestInitialized;
  private boolean hostedTestFinished;

  /**
   * Executes a {@link HostedTest} inside the host.
   * <p>
   * Must only be called once on each instance. Must be called from the test thread.
   *
   * @param hostedTest The test to execute.
   * @param timeoutMs The number of milliseconds to wait for the test to finish. If the timeout
   *     is exceeded then the test will fail.
   */
  public void runTest(final HostedTest hostedTest, long timeoutMs) {
    Assertions.checkArgument(timeoutMs > 0);
    Assertions.checkState(Thread.currentThread() != getMainLooper().getThread());
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Assertions.checkState(HostActivity.this.hostedTest == null);
        HostActivity.this.hostedTest = Assertions.checkNotNull(hostedTest);
        maybeInitializeHostedTest();
      }
    });
    if (hostedTestReleasedCondition.block(timeoutMs)) {
      if (hostedTestFinished) {
        Log.d(TAG, "Test finished. Checking pass conditions.");
        hostedTest.assertPassed();
        Log.d(TAG, "Pass conditions checked.");
      } else {
        Log.e(TAG, "Test released before it finished. Activity may have been paused whilst test "
            + "was in progress.");
        fail();
      }
    } else {
      Log.e(TAG, "Test timed out after " + timeoutMs + " ms.");
      fail();
    }
  }

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.host_activity);
    surfaceView = (SurfaceView) findViewById(R.id.surface_view);
    surfaceView.getHolder().addCallback(this);
    mainHandler = new Handler();
    hostedTestReleasedCondition = new ConditionVariable();
    checkFinishedRunnable = new CheckFinishedRunnable();
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
    maybeInitializeHostedTest();
  }

  @Override
  public void onPause() {
    super.onPause();
    maybeReleaseHostedTest();
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
    maybeInitializeHostedTest();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    maybeReleaseHostedTest();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Do nothing.
  }

  // Internal logic

  private void maybeInitializeHostedTest() {
    if (hostedTest == null || hostedTestInitialized) {
      return;
    }
    Surface surface = surfaceView.getHolder().getSurface();
    if (surface != null && surface.isValid()) {
      hostedTestInitialized = true;
      Log.d(TAG, "Initializing test.");
      hostedTest.initialize(this, surface);
      checkFinishedRunnable.startChecking();
    }
  }

  private void maybeReleaseHostedTest() {
    if (hostedTest != null && hostedTestInitialized) {
      hostedTest.release();
      hostedTest = null;
      mainHandler.removeCallbacks(checkFinishedRunnable);
      hostedTestReleasedCondition.open();
    }
  }

  @SuppressLint("InlinedApi")
  private static final int getWifiLockMode() {
    return Util.SDK_INT < 12 ? WifiManager.WIFI_MODE_FULL : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
  }

  private final class CheckFinishedRunnable implements Runnable {

    private static final long CHECK_INTERVAL_MS = 1000;

    private void startChecking() {
      mainHandler.post(this);
    }

    @Override
    public void run() {
      if (hostedTest.isFinished()) {
        hostedTestFinished = true;
        finish();
      } else {
        mainHandler.postDelayed(this, CHECK_INTERVAL_MS);
      }
    }

  }

}
