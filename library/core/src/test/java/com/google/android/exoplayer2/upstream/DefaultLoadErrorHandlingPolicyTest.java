/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.chunk.ChunkedTrackBlacklistUtil;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link LoadErrorHandlingPolicy#DEFAULT}. */
@RunWith(RobolectricTestRunner.class)
public final class DefaultLoadErrorHandlingPolicyTest {

  private static final Loadable DUMMY_LOADABLE =
      new Loadable() {
        @Override
        public void cancelLoad() {
          // Do nothing.
        }

        @Override
        public void load() throws IOException, InterruptedException {
          // Do nothing.
        }
      };

  @Test
  public void getBlacklistDurationMsFor_blacklist404() throws Exception {
    InvalidResponseCodeException exception =
        new InvalidResponseCodeException(404, Collections.emptyMap(), new DataSpec(Uri.EMPTY));
    assertThat(getDefaultPolicyBlacklistOutputFor(exception))
        .isEqualTo(ChunkedTrackBlacklistUtil.DEFAULT_TRACK_BLACKLIST_MS);
  }

  @Test
  public void getBlacklistDurationMsFor_blacklist410() throws Exception {
    InvalidResponseCodeException exception =
        new InvalidResponseCodeException(410, Collections.emptyMap(), new DataSpec(Uri.EMPTY));
    assertThat(getDefaultPolicyBlacklistOutputFor(exception))
        .isEqualTo(ChunkedTrackBlacklistUtil.DEFAULT_TRACK_BLACKLIST_MS);
  }

  @Test
  public void getBlacklistDurationMsFor_dontBlacklistUnexpectedHttpCodes() throws Exception {
    InvalidResponseCodeException exception =
        new InvalidResponseCodeException(500, Collections.emptyMap(), new DataSpec(Uri.EMPTY));
    assertThat(getDefaultPolicyBlacklistOutputFor(exception)).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getBlacklistDurationMsFor_dontBlacklistUnexpectedExceptions() throws Exception {
    FileNotFoundException exception = new FileNotFoundException();
    assertThat(getDefaultPolicyBlacklistOutputFor(exception)).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getRetryDelayMsFor_dontRetryParserException() throws Exception {
    assertThat(getDefaultPolicyRetryDelayOutputFor(new ParserException(), 1))
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getRetryDelayMsFor_successiveRetryDelays() throws Exception {
    assertThat(getDefaultPolicyRetryDelayOutputFor(new FileNotFoundException(), 3)).isEqualTo(2000);
    assertThat(getDefaultPolicyRetryDelayOutputFor(new FileNotFoundException(), 5)).isEqualTo(4000);
    assertThat(getDefaultPolicyRetryDelayOutputFor(new FileNotFoundException(), 9)).isEqualTo(5000);
  }

  private static long getDefaultPolicyBlacklistOutputFor(IOException exception) {
    return LoadErrorHandlingPolicy.DEFAULT.getBlacklistDurationMsFor(
        DUMMY_LOADABLE, /* loadDurationMs= */ 1000, exception, /* errorCount= */ 1);
  }

  private static long getDefaultPolicyRetryDelayOutputFor(IOException exception, int errorCount) {
    return LoadErrorHandlingPolicy.DEFAULT.getRetryDelayMsFor(
        DUMMY_LOADABLE, /* loadDurationMs= */ 1000, exception, errorCount);
  }
}
