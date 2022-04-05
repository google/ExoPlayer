/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link AdvancedFrameProcessor}.
 *
 * <p>See {@code AdvancedFrameProcessorPixelTest} for pixel tests testing {@link
 * AdvancedFrameProcessor} given a transformation matrix.
 */
@RunWith(AndroidJUnit4.class)
public final class AdvancedFrameProcessorTest {

  @Test
  public void construct_withInvalidMatrixSize_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AdvancedFrameProcessor(getApplicationContext(), new float[4]));
  }

  @Test
  public void construct_withValidMatrixSize_completesSuccessfully() {
    new AdvancedFrameProcessor(getApplicationContext(), new float[16]);
  }
}
