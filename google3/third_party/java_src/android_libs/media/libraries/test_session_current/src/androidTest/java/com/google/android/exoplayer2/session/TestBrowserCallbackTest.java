/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.session;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link TestBrowserCallback}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TestBrowserCallbackTest {

  /**
   * Test if the {@link TestBrowserCallback} wraps the callback proxy without missing any method.
   */
  @Test
  public void methods_overridden() {
    Method[] methods = TestBrowserCallback.class.getMethods();
    assertThat(methods).isNotNull();
    for (Method method : methods) {
      // For any methods in the controller callback, TestBrowserCallback should have
      // overridden the method and call matching API in the callback proxy.
      assertWithMessage(
              "TestBrowserCallback should override " + method + " and call callback proxy")
          .that(method.getDeclaringClass())
          .isNotEqualTo(MediaBrowser.BrowserCallback.class);
      assertWithMessage(
              "TestBrowserCallback should override " + method + " and call callback proxy")
          .that(method.getDeclaringClass())
          .isNotEqualTo(MediaController.ControllerCallback.class);
    }
  }
}
