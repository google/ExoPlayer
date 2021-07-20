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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.PlaybackException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DataSourceException}. */
@RunWith(AndroidJUnit4.class)
public class DataSourceExceptionTest {

  @Test
  public void isCausedByPositionOutOfRange_reasonIsPositionOutOfRange_returnsTrue() {
    DataSourceException e =
        new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    assertThat(DataSourceException.isCausedByPositionOutOfRange(e)).isTrue();
  }

  @Test
  public void isCausedByPositionOutOfRange_reasonIsOther_returnsFalse() {
    DataSourceException e = new DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(DataSourceException.isCausedByPositionOutOfRange(e)).isFalse();
  }

  @Test
  public void isCausedByPositionOutOfRange_indirectCauseReasonIsPositionOutOfRange_returnsTrue() {
    DataSourceException cause =
        new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    IOException e = new IOException(new IOException(cause));
    assertThat(DataSourceException.isCausedByPositionOutOfRange(e)).isTrue();
  }

  @Test
  public void isCausedByPositionOutOfRange_causeReasonIsOther_returnsFalse() {
    DataSourceException cause =
        new DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    IOException e = new IOException(new IOException(cause));
    assertThat(DataSourceException.isCausedByPositionOutOfRange(e)).isFalse();
  }

  @Test
  public void constructor_withNestedCausesAndUnspecifiedErrorCodes_assignsCorrectErrorCodes() {
    DataSourceException exception =
        new DataSourceException(
            new UnknownHostException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason).isEqualTo(PlaybackException.ERROR_CODE_IO_DNS_FAILED);

    exception =
        new DataSourceException(
            new SocketTimeoutException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason)
        .isEqualTo(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);

    exception =
        new DataSourceException(
            new IOException(new SocketTimeoutException()),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason)
        .isEqualTo(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);

    exception =
        new DataSourceException(
            new DataSourceException(
                new SocketTimeoutException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason)
        .isEqualTo(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);

    exception =
        new DataSourceException(
            new DataSourceException(
                new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason)
        .isEqualTo(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);

    exception =
        new DataSourceException(
            new HttpDataSource.CleartextNotPermittedException(
                new IOException(), new DataSpec(Uri.parse("test"))),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason).isEqualTo(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED);

    exception =
        new DataSourceException(
            new HttpDataSource.HttpDataSourceException(
                new IOException(),
                new DataSpec(Uri.parse("test")),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason)
        .isEqualTo(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);

    exception =
        new DataSourceException(
            new DataSourceException(
                new DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(exception.reason).isEqualTo(PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
  }
}
