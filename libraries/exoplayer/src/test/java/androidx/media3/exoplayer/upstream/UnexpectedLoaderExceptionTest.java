/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.upstream;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.exoplayer.upstream.Loader.UnexpectedLoaderException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link UnexpectedLoaderException}. */
@RunWith(JUnit4.class)
public class UnexpectedLoaderExceptionTest {

  @Test
  public void causeWithMessage_messageAppended() {
    UnexpectedLoaderException unexpectedLoaderException =
        new UnexpectedLoaderException(new IllegalStateException("test message"));

    assertThat(unexpectedLoaderException)
        .hasMessageThat()
        .isEqualTo("Unexpected IllegalStateException: test message");
  }

  @Test
  public void causeWithoutMessage_noMessageAppended() {
    UnexpectedLoaderException unexpectedLoaderException =
        new UnexpectedLoaderException(new IllegalStateException());

    assertThat(unexpectedLoaderException)
        .hasMessageThat()
        .isEqualTo("Unexpected IllegalStateException");
  }
}
