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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.FilenameUtil.FILE_FORMAT_MATROSKA;
import static com.google.android.exoplayer2.util.FilenameUtil.FILE_FORMAT_MP3;
import static com.google.android.exoplayer2.util.FilenameUtil.FILE_FORMAT_UNKNOWN;
import static com.google.android.exoplayer2.util.FilenameUtil.getFormatFromExtension;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FilenameUtilTest}. */
@RunWith(AndroidJUnit4.class)
public class FilenameUtilTest {

  @Test
  public void getFormatFromExtension_withExtension_returnsExpectedFormat() {
    assertThat(getFormatFromExtension("filename.mp3")).isEqualTo(FILE_FORMAT_MP3);
  }

  @Test
  public void getFormatFromExtension_withExtensionPrefix_returnsExpectedFormat() {
    assertThat(getFormatFromExtension("filename.mka")).isEqualTo(FILE_FORMAT_MATROSKA);
  }

  @Test
  public void getFormatFromExtension_unknownExtension_returnsUnknownFormat() {
    assertThat(getFormatFromExtension("filename.unknown")).isEqualTo(FILE_FORMAT_UNKNOWN);
  }
}
