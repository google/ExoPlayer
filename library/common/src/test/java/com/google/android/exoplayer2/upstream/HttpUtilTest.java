/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.upstream.HttpUtil.buildRangeRequestHeader;
import static com.google.android.exoplayer2.upstream.HttpUtil.getContentLength;
import static com.google.android.exoplayer2.upstream.HttpUtil.getDocumentSize;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultHttpDataSource}. */
@RunWith(AndroidJUnit4.class)
public class HttpUtilTest {

  @Test
  public void buildRangeRequestHeader_buildsHeader() {
    assertThat(buildRangeRequestHeader(0, C.LENGTH_UNSET)).isNull();
    assertThat(buildRangeRequestHeader(1, C.LENGTH_UNSET)).isEqualTo("bytes=1-");
    assertThat(buildRangeRequestHeader(0, 5)).isEqualTo("bytes=0-4");
    assertThat(buildRangeRequestHeader(5, 15)).isEqualTo("bytes=5-19");
  }

  @Test
  public void getContentLength_bothHeadersMissing_returnsUnset() {
    assertThat(getContentLength(null, null)).isEqualTo(C.LENGTH_UNSET);
    assertThat(getContentLength("", "")).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void getContentLength_onlyContentLengthHeaderSet_returnsCorrectValue() {
    assertThat(getContentLength("5", null)).isEqualTo(5);
    assertThat(getContentLength("5", "")).isEqualTo(5);
  }

  @Test
  public void getContentLength_onlyContentRangeHeaderSet_returnsCorrectValue() {
    assertThat(getContentLength(null, "bytes 5-9/100")).isEqualTo(5);
    assertThat(getContentLength("", "bytes 5-9/100")).isEqualTo(5);
    assertThat(getContentLength("", "bytes 5-9/*")).isEqualTo(5);
  }

  @Test
  public void getContentLength_bothHeadersSet_returnsCorrectValue() {
    assertThat(getContentLength("5", "bytes 5-9/100")).isEqualTo(5);
  }

  @Test
  public void getContentLength_headersInconsistent_returnsLargerValue() {
    assertThat(getContentLength("10", "bytes 0-4/100")).isEqualTo(10);
    assertThat(getContentLength("5", "bytes 0-9/100")).isEqualTo(10);
  }

  @Test
  public void getContentLength_ignoresInvalidValues() {
    assertThat(getContentLength("Invalid", "Invalid")).isEqualTo(C.LENGTH_UNSET);
    assertThat(getContentLength("Invalid", "bytes 5-9/100")).isEqualTo(5);
    assertThat(getContentLength("5", "Invalid")).isEqualTo(5);
  }

  @Test
  public void getContentLength_ignoresUnhandledRangeUnits() {
    assertThat(getContentLength(null, "unhandled 5-9/100")).isEqualTo(C.LENGTH_UNSET);
    assertThat(getContentLength("10", "unhandled 0-4/100")).isEqualTo(10);
  }

  @Test
  public void getDocumentSize_noHeader_returnsUnset() {
    assertThat(getDocumentSize(null)).isEqualTo(C.LENGTH_UNSET);
    assertThat(getDocumentSize("")).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void getDocumentSize_returnsSize() {
    assertThat(getDocumentSize("bytes */20")).isEqualTo(20);
    assertThat(getDocumentSize("bytes 0-4/20")).isEqualTo(20);
  }

  @Test
  public void getDocumentSize_ignoresUnhandledRangeUnits() {
    assertThat(getDocumentSize("unhandled */20")).isEqualTo(C.LENGTH_UNSET);
    assertThat(getDocumentSize("unhandled 0-4/20")).isEqualTo(C.LENGTH_UNSET);
  }
}
