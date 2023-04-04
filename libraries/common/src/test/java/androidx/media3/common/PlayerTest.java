/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.common;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Player}. */
@RunWith(AndroidJUnit4.class)
public class PlayerTest {

  /**
   * This test picks a method on the {@link Player} interface that is known will never be
   * stabilised, and asserts that it is required to be implemented (therefore enforcing that {@link
   * Player} is unstable-for-implementors). If this test fails because the {@link Player#next()}
   * method is removed, it should be replaced with an equivalent unstable, unimplemented method.
   */
  @Test
  public void testAtLeastOneUnstableUnimplementedMethodExists() throws Exception {
    Method nextMethod = Player.class.getMethod("next");
    assertThat(nextMethod.isDefault()).isFalse();
  }
}
