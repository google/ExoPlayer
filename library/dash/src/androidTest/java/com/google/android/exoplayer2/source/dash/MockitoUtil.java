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
package com.google.android.exoplayer2.source.dash;

import android.test.InstrumentationTestCase;
import org.mockito.MockitoAnnotations;

/**
 * Utility for setting up Mockito for instrumentation tests.
 */
public final class MockitoUtil {

  /**
   * Sets up Mockito for an instrumentation test.
   */
  public static void setUpMockito(InstrumentationTestCase instrumentationTestCase) {
    // Workaround for https://code.google.com/p/dexmaker/issues/detail?id=2.
    System.setProperty("dexmaker.dexcache",
        instrumentationTestCase.getInstrumentation().getTargetContext().getCacheDir().getPath());
    MockitoAnnotations.initMocks(instrumentationTestCase);
  }

  private MockitoUtil() {}

}
