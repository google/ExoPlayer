/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.transformer.mh;

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_AV1_VIDEO_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_AV1_VIDEO_URI_STRING;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.effect.RgbFilter;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.InAppMuxer;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** End-to-end instrumentation test for {@link Transformer} with {@link InAppMuxer}. */
@RunWith(AndroidJUnit4.class)
public class TransformerWithInAppMuxerEndToEndTest {
  @Test
  public void videoEditing_forAv1Video_completesSuccessfully() throws Exception {
    String testId = "videoEditing_forAv1Video_completesSuccessfully";
    Context context = ApplicationProvider.getApplicationContext();
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ MP4_ASSET_AV1_VIDEO_FORMAT, /* outputFormat= */ null)) {
      return;
    }
    Transformer transformer =
        new Transformer.Builder(context)
            .setMuxerFactory(new InAppMuxer.Factory.Builder().build())
            .build();
    ImmutableList<Effect> videoEffects = ImmutableList.of(RgbFilter.createGrayscaleFilter());
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_AV1_VIDEO_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.exportException).isNull();
  }
}
