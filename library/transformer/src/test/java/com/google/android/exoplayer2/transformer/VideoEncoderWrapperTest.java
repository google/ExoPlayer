/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link VideoTranscodingSamplePipeline.EncoderWrapper}. */
@RunWith(AndroidJUnit4.class)
public final class VideoEncoderWrapperTest {
  private final TransformationRequest emptyTransformationRequest =
      new TransformationRequest.Builder().build();
  private final FakeVideoEncoderFactory fakeEncoderFactory = new FakeVideoEncoderFactory();
  private final FallbackListener fallbackListener =
      new FallbackListener(
          MediaItem.fromUri(Uri.EMPTY),
          new ListenerSet<>(Looper.myLooper(), Clock.DEFAULT, (listener, flags) -> {}),
          Clock.DEFAULT.createHandler(Looper.myLooper(), /* callback= */ null),
          emptyTransformationRequest);
  private final VideoTranscodingSamplePipeline.EncoderWrapper encoderWrapper =
      new VideoTranscodingSamplePipeline.EncoderWrapper(
          fakeEncoderFactory,
          /* inputFormat= */ new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build(),
          /* allowedOutputMimeTypes= */ ImmutableList.of(),
          emptyTransformationRequest,
          fallbackListener);

  @Before
  public void registerTrack() {
    fallbackListener.registerTrack();
  }

  @Test
  public void getSurfaceInfo_landscape_leavesOrientationUnchanged() throws Exception {
    int inputWidth = 200;
    int inputHeight = 150;

    SurfaceInfo surfaceInfo = encoderWrapper.getSurfaceInfo(inputWidth, inputHeight);

    assertThat(surfaceInfo.orientationDegrees).isEqualTo(0);
    assertThat(surfaceInfo.width).isEqualTo(inputWidth);
    assertThat(surfaceInfo.height).isEqualTo(inputHeight);
  }

  @Test
  public void getSurfaceInfo_square_leavesOrientationUnchanged() throws Exception {
    int inputWidth = 150;
    int inputHeight = 150;

    SurfaceInfo surfaceInfo = encoderWrapper.getSurfaceInfo(inputWidth, inputHeight);

    assertThat(surfaceInfo.orientationDegrees).isEqualTo(0);
    assertThat(surfaceInfo.width).isEqualTo(inputWidth);
    assertThat(surfaceInfo.height).isEqualTo(inputHeight);
  }

  @Test
  public void getSurfaceInfo_portrait_flipsOrientation() throws Exception {
    int inputWidth = 150;
    int inputHeight = 200;

    SurfaceInfo surfaceInfo = encoderWrapper.getSurfaceInfo(inputWidth, inputHeight);

    assertThat(surfaceInfo.orientationDegrees).isEqualTo(90);
    assertThat(surfaceInfo.width).isEqualTo(inputHeight);
    assertThat(surfaceInfo.height).isEqualTo(inputWidth);
  }

  @Test
  public void getSurfaceInfo_withEncoderFallback_usesFallbackResolution()
      throws TransformationException {
    int inputWidth = 200;
    int inputHeight = 150;
    int fallbackWidth = 100;
    int fallbackHeight = 75;
    fakeEncoderFactory.setFallbackResolution(fallbackWidth, fallbackHeight);

    SurfaceInfo surfaceInfo = encoderWrapper.getSurfaceInfo(inputWidth, inputHeight);

    assertThat(surfaceInfo.orientationDegrees).isEqualTo(0);
    assertThat(surfaceInfo.width).isEqualTo(fallbackWidth);
    assertThat(surfaceInfo.height).isEqualTo(fallbackHeight);
  }

  private static class FakeVideoEncoderFactory implements Codec.EncoderFactory {

    private int fallbackWidth;
    private int fallbackHeight;

    public FakeVideoEncoderFactory() {
      fallbackWidth = C.LENGTH_UNSET;
      fallbackHeight = C.LENGTH_UNSET;
    }

    public void setFallbackResolution(int fallbackWidth, int fallbackHeight) {
      this.fallbackWidth = fallbackWidth;
      this.fallbackHeight = fallbackHeight;
    }

    @Override
    public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes) {
      Codec mockEncoder = mock(Codec.class);
      if (fallbackWidth != C.LENGTH_UNSET) {
        format = format.buildUpon().setWidth(fallbackWidth).build();
      }
      if (fallbackHeight != C.LENGTH_UNSET) {
        format = format.buildUpon().setHeight(fallbackHeight).build();
      }
      when(mockEncoder.getConfigurationFormat()).thenReturn(format);
      return mockEncoder;
    }
  }
}
