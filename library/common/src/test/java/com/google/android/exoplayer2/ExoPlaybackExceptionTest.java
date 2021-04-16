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

import android.os.RemoteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ExoPlaybackException}. */
@RunWith(AndroidJUnit4.class)
public class ExoPlaybackExceptionTest {

  @Test
  public void roundTripViaBundle_ofExoPlaybackExceptionTypeRemote_yieldsEqualInstance() {
    ExoPlaybackException before = ExoPlaybackException.createForRemote(/* message= */ "test");
    ExoPlaybackException after = ExoPlaybackException.CREATOR.fromBundle(before.toBundle());
    assertThat(areEqual(before, after)).isTrue();
  }

  @Test
  public void roundTripViaBundle_ofExoPlaybackExceptionTypeRenderer_yieldsEqualInstance() {
    ExoPlaybackException before =
        ExoPlaybackException.createForRenderer(
            new IllegalStateException("ExoPlaybackExceptionTest"),
            /* rendererName= */ "rendererName",
            /* rendererIndex= */ 123,
            /* rendererFormat= */ new Format.Builder().setCodecs("anyCodec").build(),
            /* rendererFormatSupport= */ C.FORMAT_UNSUPPORTED_SUBTYPE,
            /* isRecoverable= */ true);

    ExoPlaybackException after = ExoPlaybackException.CREATOR.fromBundle(before.toBundle());
    assertThat(areEqual(before, after)).isTrue();
  }

  @Test
  public void
      roundTripViaBundle_ofExoPlaybackExceptionTypeRendererWithPrivateCause_yieldsRemoteExceptionWithSameMessage() {
    ExoPlaybackException before =
        ExoPlaybackException.createForRenderer(
            new Exception(/* message= */ "anonymous exception that class loader cannot know") {});
    ExoPlaybackException after = ExoPlaybackException.CREATOR.fromBundle(before.toBundle());

    assertThat(after.getCause()).isInstanceOf(RemoteException.class);
    assertThat(after.getCause()).hasMessageThat().isEqualTo(before.getCause().getMessage());
  }

  private static boolean areEqual(ExoPlaybackException a, ExoPlaybackException b) {
    if (a == null || b == null) {
      return a == b;
    }
    return Util.areEqual(a.getMessage(), b.getMessage())
        && a.type == b.type
        && Util.areEqual(a.rendererName, b.rendererName)
        && a.rendererIndex == b.rendererIndex
        && Util.areEqual(a.rendererFormat, b.rendererFormat)
        && a.rendererFormatSupport == b.rendererFormatSupport
        && a.timestampMs == b.timestampMs
        && a.isRecoverable == b.isRecoverable
        && areEqual(a.getCause(), b.getCause());
  }

  private static boolean areEqual(Throwable a, Throwable b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.getClass() == b.getClass() && Util.areEqual(a.getMessage(), b.getMessage());
  }
}
