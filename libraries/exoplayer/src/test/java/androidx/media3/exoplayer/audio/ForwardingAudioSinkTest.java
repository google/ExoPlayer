/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.exoplayer.audio;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ForwardingAudioSink}. */
@RunWith(AndroidJUnit4.class)
public final class ForwardingAudioSinkTest {
  @Test
  public void forwardingAudioSink_overridesAllAudioSinkMethods() throws NoSuchMethodException {
    // Check with reflection that ForwardingAudioSink overrides all AudioSink methods.
    List<Method> methods = TestUtil.getPublicMethods(AudioSink.class);
    for (Method method : methods) {
      assertThat(
              ForwardingAudioSink.class
                  .getDeclaredMethod(method.getName(), method.getParameterTypes())
                  .getDeclaringClass())
          .isEqualTo(ForwardingAudioSink.class);
    }
  }
}
