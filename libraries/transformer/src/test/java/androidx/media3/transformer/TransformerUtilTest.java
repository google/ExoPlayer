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
package androidx.media3.transformer;

import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.transformer.MuxerWrapper.MUXER_MODE_DEFAULT;
import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TransformerUtil.shouldTranscodeVideo;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link TransformerUtil}. */
@RunWith(AndroidJUnit4.class)
public final class TransformerUtilTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  public static final Format FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1080)
          .setHeight(720)
          .setFrameRate(29.97f)
          .setCodecs("avc1.64001F")
          .build();

  @Test
  public void shouldTranscodeVideo_regularRotationAndTranscodingPresentation_returnsTrue()
      throws Exception {
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    GlEffect regularRotation =
        new ScaleAndRotateTransformation.Builder().setRotationDegrees(90).build();
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(regularRotation, Presentation.createForHeight(FORMAT.height));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem)).build();
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ false);

    assertThat(
            shouldTranscodeVideo(
                FORMAT,
                composition,
                /* sequenceIndex= */ 0,
                new TransformationRequest.Builder().build(),
                new DefaultEncoderFactory.Builder(getApplicationContext()).build(),
                muxerWrapper))
        .isTrue();
  }

  @Test
  public void shouldTranscodeVideo_irregularRotationAndPresentation_returnsTrue() throws Exception {
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    GlEffect irregularRotation =
        new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build();
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            irregularRotation, Presentation.createForHeight(FORMAT.height), irregularRotation);
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem)).build();
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ false);

    assertThat(
            shouldTranscodeVideo(
                FORMAT,
                composition,
                /* sequenceIndex= */ 0,
                new TransformationRequest.Builder().build(),
                new DefaultEncoderFactory.Builder(getApplicationContext()).build(),
                muxerWrapper))
        .isTrue();
  }

  private static final class NoOpMuxerListenerImpl implements MuxerWrapper.Listener {

    @Override
    public void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount) {}

    @Override
    public void onEnded(long durationMs, long fileSizeBytes) {}

    @Override
    public void onError(ExportException exportException) {}
  }
}
