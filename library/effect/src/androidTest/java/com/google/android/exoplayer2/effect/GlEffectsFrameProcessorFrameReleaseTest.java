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
package com.google.android.exoplayer2.effect;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for frame release in {@link GlEffectsFrameProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class GlEffectsFrameProcessorFrameReleaseTest {

  private static final int WIDTH = 200;
  private static final int HEIGHT = 100;
  private static final long FRAME_PROCESSING_WAIT_MS = 5000L;
  private static final long MILLIS_TO_NANOS = 1_000_000L;
  private static final long MICROS_TO_NANOS = 1000L;

  private final AtomicReference<FrameProcessingException> frameProcessingException =
      new AtomicReference<>();
  private final Queue<Long> outputReleaseTimesNs = new ConcurrentLinkedQueue<>();

  private @MonotonicNonNull GlEffectsFrameProcessor glEffectsFrameProcessor;
  private volatile @MonotonicNonNull Runnable produceBlankFramesTask;

  @After
  public void release() {
    if (glEffectsFrameProcessor != null) {
      glEffectsFrameProcessor.release();
    }
  }

  @Test
  public void automaticFrameRelease_withOneFrame_reusesInputTimestamp() throws Exception {
    long originalPresentationTimeUs = 1234;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ actualPresentationTimeUs::set,
        /* releaseFramesAutomatically= */ true);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    assertThat(outputReleaseTimesNs).containsExactly(MICROS_TO_NANOS * originalPresentationTimeUs);
  }

  @Test
  public void automaticFrameRelease_withThreeFrames_reusesInputTimestamps() throws Exception {
    long[] originalPresentationTimesUs = new long[] {1234, 3456, 4567};
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        originalPresentationTimesUs,
        /* onFrameAvailableListener= */ actualPresentationTimesUs::add,
        /* releaseFramesAutomatically= */ true);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimesUs)
        .containsExactly(
            originalPresentationTimesUs[0],
            originalPresentationTimesUs[1],
            originalPresentationTimesUs[2])
        .inOrder();
    assertThat(outputReleaseTimesNs)
        .containsExactly(
            MICROS_TO_NANOS * originalPresentationTimesUs[0],
            MICROS_TO_NANOS * originalPresentationTimesUs[1],
            MICROS_TO_NANOS * originalPresentationTimesUs[2])
        .inOrder();
    ;
  }

  @Test
  public void controlledFrameRelease_withOneFrame_usesGivenTimestamp() throws Exception {
    long originalPresentationTimeUs = 1234;
    long releaseTimesNs = System.nanoTime() + MILLIS_TO_NANOS * FRAME_PROCESSING_WAIT_MS + 345678;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(glEffectsFrameProcessor).releaseOutputFrame(releaseTimesNs);
        },
        /* releaseFramesAutomatically= */ false);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    assertThat(outputReleaseTimesNs).containsExactly(releaseTimesNs);
  }

  @Test
  public void controlledFrameRelease_withOneFrameRequestImmediateRelease_releasesFrame()
      throws Exception {
    long originalPresentationTimeUs = 1234;
    long releaseTimesNs = FrameProcessor.RELEASE_OUTPUT_FRAME_IMMEDIATELY;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(glEffectsFrameProcessor).releaseOutputFrame(releaseTimesNs);
        },
        /* releaseFramesAutomatically= */ false);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    // The actual release time is determined by the FrameProcessor when releasing the frame.
    assertThat(outputReleaseTimesNs).hasSize(1);
  }

  @Test
  public void controlledFrameRelease_withLateFrame_releasesFrame() throws Exception {
    long originalPresentationTimeUs = 1234;
    long releaseTimeBeforeCurrentTimeNs = System.nanoTime() - 345678;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(glEffectsFrameProcessor).releaseOutputFrame(releaseTimeBeforeCurrentTimeNs);
        },
        /* releaseFramesAutomatically= */ false);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    assertThat(outputReleaseTimesNs).hasSize(1);
    // The actual release time is determined by the FrameProcessor when releasing the frame.
    assertThat(outputReleaseTimesNs.remove()).isAtLeast(releaseTimeBeforeCurrentTimeNs);
  }

  @Test
  public void controlledFrameRelease_requestsFrameDropping_dropsFrame() throws Exception {
    long originalPresentationTimeUs = 1234;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeNs -> {
          actualPresentationTimeUs.set(presentationTimeNs);
          checkNotNull(glEffectsFrameProcessor)
              .releaseOutputFrame(FrameProcessor.DROP_OUTPUT_FRAME);
        },
        /* releaseFramesAutomatically= */ false);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    assertThat(outputReleaseTimesNs).isEmpty();
  }

  @Test
  public void controlledFrameRelease_withThreeIndividualFrames_usesGivenTimestamps()
      throws Exception {
    long[] originalPresentationTimesUs = new long[] {1234, 3456, 4567};
    long offsetNs = System.nanoTime() + MILLIS_TO_NANOS * FRAME_PROCESSING_WAIT_MS;
    long[] releaseTimesNs = new long[] {offsetNs + 123456, offsetNs + 234567, offsetNs + 345678};
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    AtomicInteger frameIndex = new AtomicInteger();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ originalPresentationTimesUs,
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimesUs.add(presentationTimeUs);
          checkNotNull(glEffectsFrameProcessor)
              .releaseOutputFrame(releaseTimesNs[frameIndex.getAndIncrement()]);
        },
        /* releaseFramesAutomatically= */ false);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimesUs)
        .containsExactly(
            originalPresentationTimesUs[0],
            originalPresentationTimesUs[1],
            originalPresentationTimesUs[2])
        .inOrder();
    assertThat(frameIndex.get()).isEqualTo(originalPresentationTimesUs.length);
    assertThat(outputReleaseTimesNs)
        .containsExactly(releaseTimesNs[0], releaseTimesNs[1], releaseTimesNs[2])
        .inOrder();
  }

  @Test
  public void controlledFrameRelease_withThreeFramesAtOnce_usesGivenTimestamps() throws Exception {
    long[] originalPresentationTimesUs = new long[] {1234, 3456, 4567};
    long offsetNs = System.nanoTime() + MILLIS_TO_NANOS * 2 * FRAME_PROCESSING_WAIT_MS;
    long[] releaseTimesNs = new long[] {offsetNs + 123456, offsetNs + 234567, offsetNs + 345678};
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    setupGlEffectsFrameProcessorWithBlankFrameProducer(
        /* inputPresentationTimesUs= */ originalPresentationTimesUs,
        /* onFrameAvailableListener= */ actualPresentationTimesUs::add,
        /* releaseFramesAutomatically= */ false);

    checkNotNull(produceBlankFramesTask).run();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);
    glEffectsFrameProcessor.releaseOutputFrame(releaseTimesNs[0]);
    glEffectsFrameProcessor.releaseOutputFrame(releaseTimesNs[1]);
    glEffectsFrameProcessor.releaseOutputFrame(releaseTimesNs[2]);
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);

    assertThat(frameProcessingException.get()).isNull();
    assertThat(actualPresentationTimesUs)
        .containsExactly(
            originalPresentationTimesUs[0],
            originalPresentationTimesUs[1],
            originalPresentationTimesUs[2])
        .inOrder();
    assertThat(outputReleaseTimesNs)
        .containsExactly(releaseTimesNs[0], releaseTimesNs[1], releaseTimesNs[2])
        .inOrder();
  }

  private interface OnFrameAvailableListener {
    void onFrameAvailable(long presentationTimeUs);
  }

  @EnsuresNonNull("glEffectsFrameProcessor")
  private void setupGlEffectsFrameProcessorWithBlankFrameProducer(
      long[] inputPresentationTimesUs,
      OnFrameAvailableListener onFrameAvailableListener,
      boolean releaseFramesAutomatically)
      throws Exception {
    glEffectsFrameProcessor =
        checkNotNull(
            new GlEffectsFrameProcessor.Factory()
                .create(
                    getApplicationContext(),
                    new FrameProcessor.Listener() {
                      @Override
                      public void onOutputSizeChanged(int width, int height) {
                        ImageReader outputImageReader =
                            ImageReader.newInstance(
                                width,
                                height,
                                PixelFormat.RGBA_8888,
                                /* maxImages= */ inputPresentationTimesUs.length);
                        checkNotNull(glEffectsFrameProcessor)
                            .setOutputSurfaceInfo(
                                new SurfaceInfo(outputImageReader.getSurface(), width, height));
                        outputImageReader.setOnImageAvailableListener(
                            imageReader -> {
                              try (Image image = imageReader.acquireNextImage()) {
                                outputReleaseTimesNs.add(image.getTimestamp());
                              }
                            },
                            Util.createHandlerForCurrentOrMainLooper());
                      }

                      @Override
                      public void onOutputFrameAvailable(long presentationTimeUs) {
                        onFrameAvailableListener.onFrameAvailable(presentationTimeUs);
                      }

                      @Override
                      public void onFrameProcessingError(FrameProcessingException exception) {
                        frameProcessingException.set(exception);
                      }

                      @Override
                      public void onFrameProcessingEnded() {}
                    },
                    ImmutableList.of(
                        (GlEffect)
                            (context, useHdr) ->
                                new BlankFrameProducer(inputPresentationTimesUs, useHdr)),
                    DebugViewProvider.NONE,
                    ColorInfo.SDR_BT709_LIMITED,
                    releaseFramesAutomatically));

    glEffectsFrameProcessor.setInputFrameInfo(
        new FrameInfo(WIDTH, HEIGHT, /* pixelWidthHeightRatio= */ 1, /* streamOffsetUs= */ 0));
    // A frame needs to be registered despite not queuing any external input to ensure that the
    // frame processor knows about the stream offset.
    glEffectsFrameProcessor.registerInputFrame();
  }

  /** Produces blank frames with the given timestamps. */
  private final class BlankFrameProducer implements GlTextureProcessor {

    private final TextureInfo blankTexture;
    private final long[] presentationTimesUs;

    public BlankFrameProducer(long[] presentationTimesUs, boolean useHdr)
        throws FrameProcessingException {
      this.presentationTimesUs = presentationTimesUs;
      try {
        int texId = GlUtil.createTexture(WIDTH, HEIGHT, useHdr);
        int fboId = GlUtil.createFboForTexture(texId);
        blankTexture = new TextureInfo(texId, fboId, WIDTH, HEIGHT);
        GlUtil.focusFramebufferUsingCurrentContext(fboId, WIDTH, HEIGHT);
        GlUtil.clearOutputFrame();
      } catch (GlUtil.GlException e) {
        throw new FrameProcessingException(e);
      }
    }

    @Override
    public void setInputListener(InputListener inputListener) {}

    @Override
    public void setOutputListener(OutputListener outputListener) {
      produceBlankFramesTask =
          () -> {
            for (long presentationTimeUs : presentationTimesUs) {
              outputListener.onOutputFrameAvailable(blankTexture, presentationTimeUs);
            }
          };
    }

    @Override
    public void setErrorListener(ErrorListener errorListener) {}

    @Override
    public void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
      // No input is queued in these tests. The BlankFrameProducer is used to produce frames.
      throw new UnsupportedOperationException();
    }

    @Override
    public void releaseOutputFrame(TextureInfo outputTexture) {}

    @Override
    public void signalEndOfCurrentInputStream() {
      // The tests don't end the input stream.
      throw new UnsupportedOperationException();
    }

    @Override
    public void release() {
      // Do nothing as destroying the OpenGL context destroys the texture.
    }
  }
}
