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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link PlaybackException}. */
@RunWith(AndroidJUnit4.class)
public class PlaybackExceptionTest {

  @Test
  public void roundTripViaBundle_yieldsEqualInstance() {
    PlaybackException before =
        new PlaybackException(
            /* message= */ "test",
            /* cause= */ new IOException(/* message= */ "io"),
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
    PlaybackException after = PlaybackException.CREATOR.fromBundle(before.toBundle());
    assertPlaybackExceptionsAreEqual(before, after);
  }

  // TODO: Add test for backwards compatibility.

  private static void assertPlaybackExceptionsAreEqual(PlaybackException a, PlaybackException b) {
    assertThat(a).hasMessageThat().isEqualTo(b.getMessage());
    assertThat(a.errorCode).isEqualTo(b.errorCode);
    assertThat(a.timestampMs).isEqualTo(b.timestampMs);
    assertThat(a.getCause().getClass()).isSameInstanceAs(b.getCause().getClass());
    assertThat(a).hasCauseThat().hasMessageThat().isEqualTo(b.getCause().getMessage());
  }
}
