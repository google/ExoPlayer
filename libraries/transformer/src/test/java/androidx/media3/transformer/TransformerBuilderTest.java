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

package androidx.media3.transformer;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.media3.common.MimeTypes;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Transformer.Builder}. */
@RunWith(AndroidJUnit4.class)
public class TransformerBuilderTest {

  @Test
  public void build_withUnsupportedAudioMimeType_throws() {
    Context context = ApplicationProvider.getApplicationContext();

    assertThrows(
        IllegalStateException.class,
        () -> new Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_UNKNOWN).build());
  }

  @Test
  public void build_withUnsupportedVideoMimeType_throws() {
    Context context = ApplicationProvider.getApplicationContext();

    assertThrows(
        IllegalStateException.class,
        () -> new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_UNKNOWN).build());
  }
}
