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
package com.google.android.exoplayer;

import com.google.android.exoplayer.TimeRange.StaticTimeRange;

import junit.framework.TestCase;

/**
 * Unit test for {@link TimeRange}.
 */
public class TimeRangeTest extends TestCase {

  public void testStaticEquals() {
    TimeRange timeRange1 = new StaticTimeRange(0, 30000000);
    assertTrue(timeRange1.equals(timeRange1));

    TimeRange timeRange2 = new StaticTimeRange(0, 30000000);
    assertTrue(timeRange1.equals(timeRange2));

    TimeRange timeRange3 = new StaticTimeRange(0, 60000000);
    assertFalse(timeRange1.equals(timeRange3));
  }

}
