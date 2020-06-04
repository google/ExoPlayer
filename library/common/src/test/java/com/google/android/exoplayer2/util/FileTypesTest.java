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

import static com.google.android.exoplayer2.util.FileTypes.getFormatFromExtension;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FileTypesTest}. */
@RunWith(AndroidJUnit4.class)
public class FileTypesTest {

  @Test
  public void getFormatFromExtension_withExtension_returnsExpectedFormat() {
    assertThat(getFormatFromExtension(Uri.parse("filename.mp3"))).isEqualTo(FileTypes.MP3);
  }

  @Test
  public void getFormatFromExtension_withExtensionPrefix_returnsExpectedFormat() {
    assertThat(getFormatFromExtension(Uri.parse("filename.mka"))).isEqualTo(FileTypes.MATROSKA);
  }

  @Test
  public void getFormatFromExtension_withUnknownExtension_returnsUnknownFormat() {
    assertThat(getFormatFromExtension(Uri.parse("filename.unknown"))).isEqualTo(FileTypes.UNKNOWN);
  }

  @Test
  public void getFormatFromExtension_withUriNotEndingWithFilename_returnsExpectedFormat() {
    assertThat(
            getFormatFromExtension(
                Uri.parse("http://www.example.com/filename.mp3?query=myquery#fragment")))
        .isEqualTo(FileTypes.MP3);
  }

  @Test
  public void getFormatFromExtension_withNullFilename_returnsUnknownFormat() {
    assertThat(getFormatFromExtension(Uri.EMPTY)).isEqualTo(FileTypes.UNKNOWN);
  }
}
