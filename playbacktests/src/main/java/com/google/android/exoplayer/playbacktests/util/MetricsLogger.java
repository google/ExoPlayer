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

import android.app.Instrumentation;
import android.os.Bundle;

/**
 * Metric Logging interface for ExoPlayer playback tests.
 */
public interface MetricsLogger {

  String KEY_FRAMES_DROPPED_COUNT = "Frames Dropped (Count)";
  String KEY_FRAMES_RENDERED_COUNT = "Frames Rendered (Count)";
  String KEY_FRAMES_SKIPPED_COUNT = "Frames Skipped (Count)";
  String KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT = "Maximum Consecutive Frames Dropped";
  String KEY_TEST_NAME = "Test Name";

  /**
   * Logs the metrics provided from a test.
   *
   * @param metrics The {@link Bundle} of metrics to be logged.
   */
  void logMetrics(Bundle metrics);

  /**
   * A factory for instantiating MetricsLogger instances.
   */
  final class Factory {

    private Factory() {}

    /**
     * Obtains a new instance of MetricsLogger.
     *
     * @param instrumentation The test instrumentation.
     * @param tag The tag to be used for logcat logs.
     */
    public static MetricsLogger createDefault(Instrumentation instrumentation, String tag) {
      return new LogcatMetricsLogger(tag);
    }
  }

}
