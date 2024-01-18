/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.test.utils;

import static org.junit.Assert.assertThrows;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link TestUtil}. */
@RunWith(AndroidJUnit4.class)
public class TestUtilTest {

  @Test
  public void getThrowingBundle_throwsWhenUsed() {
    Bundle bundle = TestUtil.getThrowingBundle();

    assertThrows(RuntimeException.class, () -> bundle.getInt("0"));
  }
}
