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
package com.google.android.exoplayer2.drm;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit test for {@link ClearKeyUtil}.
 */
// TODO: When API level 27 is supported, add tests that check the adjust methods are no-ops.
@RunWith(RobolectricTestRunner.class)
public final class ClearKeyUtilTest {

  @Config(sdk = 26, manifest = Config.NONE)
  @Test
  public void testAdjustResponseDataV26() {
    byte[] data = ("{\"keys\":[{"
        + "\"k\":\"abc_def-\","
        + "\"kid\":\"ab_cde-f\"}],"
        + "\"type\":\"abc_def-"
        + "\"}").getBytes(Charset.forName(C.UTF8_NAME));
    // We expect "-" and "_" to be replaced with "+" and "\/" (forward slashes need to be escaped in
    // JSON respectively, for "k" and "kid" only.
    byte[] expected = ("{\"keys\":[{"
        + "\"k\":\"abc\\/def+\","
        + "\"kid\":\"ab\\/cde+f\"}],"
        + "\"type\":\"abc_def-"
        + "\"}").getBytes(Charset.forName(C.UTF8_NAME));
    assertThat(Arrays.equals(expected, ClearKeyUtil.adjustResponseData(data))).isTrue();
  }

  @Config(sdk = 26, manifest = Config.NONE)
  @Test
  public void testAdjustRequestDataV26() {
    byte[] data = "{\"kids\":[\"abc+def/\",\"ab+cde/f\"],\"type\":\"abc+def/\"}"
        .getBytes(Charset.forName(C.UTF8_NAME));
    // We expect "+" and "/" to be replaced with "-" and "_" respectively, for "kids".
    byte[] expected = "{\"kids\":[\"abc-def_\",\"ab-cde_f\"],\"type\":\"abc+def/\"}"
        .getBytes(Charset.forName(C.UTF8_NAME));
    assertThat(Arrays.equals(expected, ClearKeyUtil.adjustRequestData(data))).isTrue();
  }

}
