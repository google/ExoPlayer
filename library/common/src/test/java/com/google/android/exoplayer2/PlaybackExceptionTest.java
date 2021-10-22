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

import android.os.Bundle;
import android.os.RemoteException;
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
    assertPlaybackExceptionsAreEquivalent(before, after);
  }

  // Backward compatibility tests.
  // The following tests prevent accidental modifications which break communication with older
  // ExoPlayer versions hosted in other processes.

  @Test
  public void bundle_producesExpectedException() {
    IOException expectedCause = new IOException("cause message");
    PlaybackException expectedException =
        new PlaybackException(
            "message",
            expectedCause,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            /* timestampMs= */ 1000);

    Bundle bundle = new Bundle();
    bundle.putInt("0", 5001); // Error code
    bundle.putLong("1", 1000); // Timestamp.
    bundle.putString("2", "message");
    bundle.putString("3", expectedCause.getClass().getName());
    bundle.putString("4", "cause message");

    assertPlaybackExceptionsAreEquivalent(
        expectedException, PlaybackException.CREATOR.fromBundle(bundle));
  }

  @Test
  public void exception_producesExpectedBundle() {
    IllegalStateException cause = new IllegalStateException("cause message");
    PlaybackException exception =
        new PlaybackException(
            "message",
            cause,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            /* timestampMs= */ 2000);

    Bundle bundle = exception.toBundle();
    assertThat(bundle.getInt("0")).isEqualTo(4003); // Error code.
    assertThat(bundle.getLong("1")).isEqualTo(2000); // Timestamp.
    assertThat(bundle.getString("2")).isEqualTo("message");
    assertThat(bundle.getString("3")).isEqualTo(cause.getClass().getName());
    assertThat(bundle.getString("4")).isEqualTo("cause message");
  }

  @Test
  public void bundleWithUnexpectedCause_producesRemoteExceptionCause() {
    RemoteException expectedCause = new RemoteException("cause message");
    PlaybackException expectedException =
        new PlaybackException(
            "message",
            expectedCause,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            /* timestampMs= */ 1000);

    Bundle bundle = new Bundle();
    bundle.putInt("0", 5001); // Error code
    bundle.putLong("1", 1000); // Timestamp.
    bundle.putString("2", "message");
    bundle.putString("3", "invalid cause class name");
    bundle.putString("4", "cause message");

    assertPlaybackExceptionsAreEquivalent(
        expectedException, PlaybackException.CREATOR.fromBundle(bundle));
  }

  // Internal methods.

  private static void assertPlaybackExceptionsAreEquivalent(
      PlaybackException a, PlaybackException b) {
    assertThat(a).hasMessageThat().isEqualTo(b.getMessage());
    assertThat(a.errorCode).isEqualTo(b.errorCode);
    assertThat(a.timestampMs).isEqualTo(b.timestampMs);
    assertThat(a.getCause().getClass()).isSameInstanceAs(b.getCause().getClass());
    assertThat(a).hasCauseThat().hasMessageThat().isEqualTo(b.getCause().getMessage());
  }
}
