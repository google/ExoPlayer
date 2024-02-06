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
package androidx.media3.exoplayer.image;

import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.sample;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.SystemClock;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ImageRenderer}. */
@RunWith(AndroidJUnit4.class)
public class ImageRendererTest {
  private static final long DEFAULT_LOOP_TIMEOUT_MS = 10 * C.MILLIS_PER_SECOND;
  private static final String IS_READY_TIMEOUT_MESSAGE =
      "Renderer not ready after " + DEFAULT_LOOP_TIMEOUT_MS + " milliseconds.";
  private static final String IS_ENDED_TIMEOUT_MESSAGE =
      "Renderer not ended after " + DEFAULT_LOOP_TIMEOUT_MS + " milliseconds.";
  private static final String HAS_READ_STREAM_TO_END_TIMEOUT_MESSAGE =
      "Renderer has not read stream to end after " + DEFAULT_LOOP_TIMEOUT_MS + " milliseconds.";
  private static final Format PNG_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.IMAGE_PNG)
          .setTileCountVertical(1)
          .setTileCountHorizontal(1)
          .build();
  private static final Format JPEG_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.IMAGE_JPEG)
          .setTileCountVertical(1)
          .setTileCountHorizontal(1)
          .build();
  private static final Format JPEG_FORMAT_WITH_FOUR_TILES =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.IMAGE_JPEG)
          .setTileCountVertical(2)
          .setTileCountHorizontal(2)
          .build();

  private final List<Pair<Long, Bitmap>> renderedBitmaps = new ArrayList<>();
  private final Bitmap fakeDecodedBitmap1 =
      Bitmap.createBitmap(/* width= */ 2, /* height= */ 2, Bitmap.Config.ARGB_8888);
  private final Bitmap fakeDecodedBitmap2 =
      Bitmap.createBitmap(/* width= */ 4, /* height= */ 4, Bitmap.Config.ARGB_8888);

  private ImageRenderer renderer;
  private int decodeCallCount;

  @Before
  public void setUp() throws Exception {
    decodeCallCount = 0;
    ImageDecoder.Factory fakeDecoderFactory =
        new BitmapFactoryImageDecoder.Factory(
            (data, length) -> ++decodeCallCount == 1 ? fakeDecodedBitmap1 : fakeDecodedBitmap2);
    ImageOutput queuingImageOutput =
        new ImageOutput() {
          @Override
          public void onImageAvailable(long presentationTimeUs, Bitmap bitmap) {
            renderedBitmaps.add(Pair.create(presentationTimeUs, bitmap));
          }

          @Override
          public void onDisabled() {
            // Do nothing.
          }
        };
    renderer = new ImageRenderer(fakeDecoderFactory, queuingImageOutput);
    renderer.init(/* index= */ 0, PlayerId.UNSET, Clock.DEFAULT);
  }

  @After
  public void tearDown() throws Exception {
    renderedBitmaps.clear();
    renderer.disable();
    renderer.release();
  }

  @Test
  public void renderOneStream_withMayRenderStartOfStream_rendersToImageOutput() throws Exception {
    FakeSampleStream fakeSampleStream = createSampleStream(/* timeUs= */ 0);
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    assertThat(renderedBitmaps).hasSize(1);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0L);
    assertThat(renderedBitmaps.get(0).second).isSameInstanceAs(fakeDecodedBitmap1);
  }

  @Test
  public void renderOneStream_withoutMayRenderStartOfStream_rendersToImageOutput()
      throws Exception {
    FakeSampleStream fakeSampleStream = createSampleStream(/* timeUs= */ 0);
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    assertThat(renderedBitmaps).isEmpty();
    renderer.start();
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    renderer.stop();

    assertThat(renderedBitmaps).hasSize(1);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0L);
    assertThat(renderedBitmaps.get(0).second).isSameInstanceAs(fakeDecodedBitmap1);
  }

  @Test
  public void renderTwoStreams_sameFormat_rendersToImageOutput() throws Exception {
    FakeSampleStream fakeSampleStream1 = createSampleStream(/* timeUs= */ 0);
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 = createSampleStream(/* timeUs= */ 10);
    fakeSampleStream2.writeData(/* startPositionUs= */ 10);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    renderer.start();
    StopWatch hasReadStreamToEndStopWatch = new StopWatch(HAS_READ_STREAM_TO_END_TIMEOUT_MESSAGE);
    while (!renderer.hasReadStreamToEnd() && hasReadStreamToEndStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    renderer.replaceStream(
        new Format[] {PNG_FORMAT},
        fakeSampleStream2,
        /* startPositionUs= */ 10,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 10, /* elapsedRealtimeUs= */ 0);
    }
    renderer.stop();

    assertThat(renderedBitmaps).hasSize(2);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0L);
    assertThat(renderedBitmaps.get(0).second).isSameInstanceAs(fakeDecodedBitmap1);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(10L);
    assertThat(renderedBitmaps.get(1).second).isSameInstanceAs(fakeDecodedBitmap2);
  }

  @Test
  public void
      renderTwoStreams_withReplaceStreamPriorToFinishingFirstStreamOutput_rendersWithCorrectPosition()
          throws Exception {
    FakeSampleStream fakeSampleStream1 =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0)));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 10L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 10L);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 100_000L,
        new MediaSource.MediaPeriodId(new Object()));
    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 100_000L, /* elapsedRealtimeUs= */ 0);
    }
    renderer.start();
    renderer.render(/* positionUs= */ 200_000L, /* elapsedRealtimeUs= */ 0);
    renderer.render(/* positionUs= */ 300_000L, /* elapsedRealtimeUs= */ 0);

    renderer.replaceStream(
        new Format[] {PNG_FORMAT},
        fakeSampleStream2,
        /* startPositionUs= */ 10,
        /* offsetUs= */ 450_000L,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();
    // Render last sample of first stream
    renderer.render(/* positionUs= */ 400_000L, /* elapsedRealtimeUs= */ 0);
    StopWatch hasReadStreamToEndStopWatch = new StopWatch(HAS_READ_STREAM_TO_END_TIMEOUT_MESSAGE);
    while (!renderer.hasReadStreamToEnd() && hasReadStreamToEndStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 450_010L, /* elapsedRealtimeUs= */ 0L);
    }
    renderer.stop();

    assertThat(renderedBitmaps).hasSize(5);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0);
    assertThat(renderedBitmaps.get(4).first).isEqualTo(10L);
  }

  @Test
  public void renderTwoStreams_withDisableandEnablePostReplaceStream_rendersWithCorrectPosition()
      throws Exception {
    FakeSampleStream fakeSampleStream1 =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0)));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 10L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 10L);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 100_000L,
        new MediaSource.MediaPeriodId(new Object()));
    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 100_000L, /* elapsedRealtimeUs= */ 0);
    }
    renderer.start();
    renderer.render(/* positionUs= */ 200_000L, /* elapsedRealtimeUs= */ 0);
    renderer.render(/* positionUs= */ 300_000L, /* elapsedRealtimeUs= */ 0);
    renderer.replaceStream(
        new Format[] {PNG_FORMAT},
        fakeSampleStream2,
        /* startPositionUs= */ 10,
        /* offsetUs= */ 400_000L,
        new MediaSource.MediaPeriodId(new Object()));

    // Reset and enable renderer as if application changed playlist to just the second stream.
    renderer.stop();
    renderer.disable();
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream2,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0L, /* elapsedRealtimeUs= */ 0);
    }
    renderer.start();
    StopWatch hasReadStreamToEndStopWatch = new StopWatch(HAS_READ_STREAM_TO_END_TIMEOUT_MESSAGE);
    while (!renderer.hasReadStreamToEnd() && hasReadStreamToEndStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0L, /* elapsedRealtimeUs= */ 0L);
    }
    renderer.stop();

    assertThat(renderedBitmaps).hasSize(4);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0);
    assertThat(renderedBitmaps.get(3).first).isEqualTo(10L);
  }

  @Test
  public void renderTwoStreams_differentFormat_rendersToImageOutput() throws Exception {
    FakeSampleStream fakeSampleStream1 = createSampleStream(/* timeUs= */ 0);
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 = createSampleStream(/* timeUs= */ 10);
    fakeSampleStream2.writeData(/* startPositionUs= */ 10);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {PNG_FORMAT},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    renderer.start();
    StopWatch hasReadStreamToEndStopWatch = new StopWatch(HAS_READ_STREAM_TO_END_TIMEOUT_MESSAGE);
    while (!renderer.hasReadStreamToEnd() && hasReadStreamToEndStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    renderer.replaceStream(
        new Format[] {JPEG_FORMAT},
        fakeSampleStream2,
        /* startPositionUs= */ 10,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(/* positionUs= */ 10, /* elapsedRealtimeUs= */ 0);
    }
    renderer.stop();

    assertThat(renderedBitmaps).hasSize(2);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0L);
    assertThat(renderedBitmaps.get(0).second).isSameInstanceAs(fakeDecodedBitmap1);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(10L);
    assertThat(renderedBitmaps.get(1).second).isSameInstanceAs(fakeDecodedBitmap2);
  }

  @Test
  public void render_tiledImage_cropsAndRendersToImageOutput() throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 0,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 0;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000;
    }

    assertThat(renderedBitmaps).hasSize(4);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(0L);
    assertThat(renderedBitmaps.get(0).second.getHeight()).isEqualTo(1);
    assertThat(renderedBitmaps.get(0).second.getWidth()).isEqualTo(1);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(100_000L);
    assertThat(renderedBitmaps.get(1).second.getHeight()).isEqualTo(1);
    assertThat(renderedBitmaps.get(1).second.getWidth()).isEqualTo(1);
    assertThat(renderedBitmaps.get(2).first).isEqualTo(200_000L);
    assertThat(renderedBitmaps.get(2).second.getHeight()).isEqualTo(1);
    assertThat(renderedBitmaps.get(2).second.getWidth()).isEqualTo(1);
    assertThat(renderedBitmaps.get(3).first).isEqualTo(300_000L);
    assertThat(renderedBitmaps.get(3).second.getHeight()).isEqualTo(1);
    assertThat(renderedBitmaps.get(3).second.getWidth()).isEqualTo(1);
  }

  @Test
  public void render_tiledImageWithNonZeroStartPosition_rendersToImageOutput() throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 200_000,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 200_000,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 200_000;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000;
    }

    assertThat(renderedBitmaps).hasSize(2);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(200_000L);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(300_000L);
  }

  @Test
  public void render_tiledImageStartPositionIsAfterLastTile_rendersToImageOutput()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 350_000,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 350_000;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000;
    }

    assertThat(renderedBitmaps).hasSize(1);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(300_000L);
  }

  @Test
  public void render_tiledImageStartPositionIsBeforeLastTileAndNotWithinThreshold_rendersPriorTile()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 250_000L,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 250_000L;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000L;
    }

    assertThat(renderedBitmaps).hasSize(2);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(200_000L);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(300_000L);
  }

  @Test
  public void
      render_tiledImageStartPositionBeforePresentationTimeAndWithinThreshold_rendersIncomingTile()
          throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 70_000,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 70_000;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000;
    }

    assertThat(renderedBitmaps).hasSize(3);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(100_000L);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(200_000L);
    assertThat(renderedBitmaps.get(2).first).isEqualTo(300_000L);
  }

  @Test
  public void
      render_tiledImageStartPositionAfterPresentationTimeAndWithinThreshold_rendersLastReadTile()
          throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 130_000,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 130_000;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000;
    }

    assertThat(renderedBitmaps).hasSize(3);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(100_000L);
    assertThat(renderedBitmaps.get(1).first).isEqualTo(200_000L);
    assertThat(renderedBitmaps.get(2).first).isEqualTo(300_000L);
  }

  @Test
  public void render_tiledImageStartPositionRightBeforeEOSAndWithinThreshold_rendersLastTileInGrid()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        createSampleStream(
            JPEG_FORMAT_WITH_FOUR_TILES,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0L, /* flags= */ C.BUFFER_FLAG_KEY_FRAME),
                emptySample(/* timeUs= */ 100_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 200_000L, /* flags= */ 0),
                emptySample(/* timeUs= */ 300_000L, /* flags= */ 0),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {JPEG_FORMAT_WITH_FOUR_TILES},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    StopWatch isReadyStopWatch = new StopWatch(IS_READY_TIMEOUT_MESSAGE);
    while (!renderer.isReady() && isReadyStopWatch.ensureNotExpired()) {
      renderer.render(
          /* positionUs= */ 330_000,
          /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
    }
    StopWatch isEndedStopWatch = new StopWatch(IS_ENDED_TIMEOUT_MESSAGE);
    long positionUs = 330_000;
    while (!renderer.isEnded() && isEndedStopWatch.ensureNotExpired()) {
      renderer.render(
          positionUs, /* elapsedRealtimeUs= */ SystemClock.DEFAULT.elapsedRealtime() * 1000);
      positionUs += 100_000;
    }

    assertThat(renderedBitmaps).hasSize(1);
    assertThat(renderedBitmaps.get(0).first).isEqualTo(300_000L);
  }

  private static FakeSampleStream.FakeSampleStreamItem emptySample(
      long timeUs, @C.BufferFlags int flags) {
    return sample(timeUs, flags, new byte[] {});
  }

  private static FakeSampleStream createSampleStream(long timeUs) {
    return new FakeSampleStream(
        new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
        /* mediaSourceEventDispatcher= */ null,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher(),
        PNG_FORMAT,
        ImmutableList.of(oneByteSample(timeUs, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
  }

  private static FakeSampleStream createSampleStream(
      Format format, List<FakeSampleStream.FakeSampleStreamItem> fakeSampleStreamItems) {
    return new FakeSampleStream(
        new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
        /* mediaSourceEventDispatcher= */ null,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher(),
        format,
        fakeSampleStreamItems);
  }

  private static final class StopWatch {
    private final long startTimeMs;
    private final long timeOutMs;
    private final String timeoutMessage;

    public StopWatch(String timeoutMessage) {
      startTimeMs = SystemClock.DEFAULT.currentTimeMillis();
      timeOutMs = DEFAULT_LOOP_TIMEOUT_MS;
      this.timeoutMessage = timeoutMessage;
    }

    public boolean ensureNotExpired() throws TimeoutException {
      if (startTimeMs + timeOutMs < SystemClock.DEFAULT.currentTimeMillis()) {
        throw new TimeoutException(timeoutMessage);
      }
      return true;
    }
  }
}
