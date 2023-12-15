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
package androidx.media3.effect;

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for frame rendering in {@link DefaultVideoFrameProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultVideoFrameProcessorVideoFrameRenderingTest {

  private static final int WIDTH = 200;
  private static final int HEIGHT = 100;

  /**
   * Time to wait between rendering frames to avoid frame drops between GL and the {@link
   * ImageReader}.
   */
  private static final long PER_FRAME_RENDERING_WAIT_TIME_MS = 1000L;

  /** Maximum time to wait for each rendered frame to be notified. */
  private static final long PER_FRAME_TIMEOUT_MS = 5000L;

  private static final long MICROS_TO_NANOS = 1000L;

  private final LinkedBlockingQueue<Long> outputRenderTimesNs = new LinkedBlockingQueue<>();

  private @MonotonicNonNull DefaultVideoFrameProcessor defaultVideoFrameProcessor;

  @After
  public void release() {
    if (defaultVideoFrameProcessor != null) {
      defaultVideoFrameProcessor.release();
    }
  }

  @Test
  public void automaticFrameRendering_withOneFrame_reusesInputTimestamp() throws Exception {
    long originalPresentationTimeUs = 1234;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ ImmutableList.of(originalPresentationTimeUs),
        /* onFrameAvailableListener= */ actualPresentationTimeUs::set,
        /* renderFramesAutomatically= */ true);

    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualRenderTimesNs).containsExactly(MICROS_TO_NANOS * originalPresentationTimeUs);
  }

  @Test
  public void automaticFrameRendering_withThreeFrames_reusesInputTimestamps() throws Exception {
    ImmutableList<Long> originalPresentationTimesUs = ImmutableList.of(1234L, 3456L, 4567L);
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    processFramesToEndOfStream(
        originalPresentationTimesUs,
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimesUs.add(presentationTimeUs);
          try {
            // TODO(b/264252759): Investigate output frames being dropped and remove sleep.
            // Frames can be dropped silently between EGL and the ImageReader. Sleep after each call
            // to swap buffers, to avoid this behavior.
            Thread.sleep(PER_FRAME_RENDERING_WAIT_TIME_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        },
        /* renderFramesAutomatically= */ true);

    assertThat(actualPresentationTimesUs)
        .containsExactlyElementsIn(originalPresentationTimesUs)
        .inOrder();
    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 3);
    assertThat(actualRenderTimesNs)
        .containsExactlyElementsIn(
            // Convert microseconds to nanoseconds.
            Iterables.transform(originalPresentationTimesUs, input -> MICROS_TO_NANOS * input))
        .inOrder();
  }

  @Test
  public void controlledFrameRendering_withOneFrame_usesGivenTimestamp() throws Exception {
    long originalPresentationTimeUs = 1234;
    long renderTimesNs = System.nanoTime() + 345678;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ ImmutableList.of(originalPresentationTimeUs),
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor).renderOutputFrame(renderTimesNs);
        },
        /* renderFramesAutomatically= */ false);

    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualRenderTimesNs).containsExactly(renderTimesNs);
  }

  @Test
  public void controlledFrameRendering_withOneFrameRequestImmediateRender_rendersframe()
      throws Exception {
    long originalPresentationTimeUs = 1234;
    long renderTimesNs = VideoFrameProcessor.RENDER_OUTPUT_FRAME_IMMEDIATELY;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ ImmutableList.of(originalPresentationTimeUs),
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor).renderOutputFrame(renderTimesNs);
        },
        /* renderFramesAutomatically= */ false);

    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    // The actual render time is determined by the VideoFrameProcessor when rendering the frame.
    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualRenderTimesNs).hasSize(1);
  }

  @Test
  public void controlledFrameRendering_withLateFrame_rendersframe() throws Exception {
    long originalPresentationTimeUs = 1234;
    long renderTimeBeforeCurrentTimeNs = System.nanoTime() - 345678;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ ImmutableList.of(originalPresentationTimeUs),
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor).renderOutputFrame(renderTimeBeforeCurrentTimeNs);
        },
        /* renderFramesAutomatically= */ false);

    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualRenderTimesNs).hasSize(1);
    // The actual render time is determined by the VideoFrameProcessor when rendering the frame.
    assertThat(actualRenderTimesNs.get(0)).isAtLeast(renderTimeBeforeCurrentTimeNs);
  }

  @Test
  public void controlledFrameRendering_requestsFrameDropping_dropsFrame() throws Exception {
    long originalPresentationTimeUs = 1234;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ ImmutableList.of(originalPresentationTimeUs),
        /* onFrameAvailableListener= */ presentationTimeNs -> {
          actualPresentationTimeUs.set(presentationTimeNs);
          checkNotNull(defaultVideoFrameProcessor)
              .renderOutputFrame(VideoFrameProcessor.DROP_OUTPUT_FRAME);
        },
        /* renderFramesAutomatically= */ false);

    waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 0);
  }

  @Test
  public void controlledFrameRendering_withThreeIndividualFrames_usesGivenTimestamps()
      throws Exception {
    ImmutableList<Long> originalPresentationTimesUs = ImmutableList.of(1234L, 3456L, 4567L);
    long offsetNs = System.nanoTime();
    ImmutableList<Long> renderTimesNs =
        ImmutableList.of(offsetNs + 123456, offsetNs + 234567, offsetNs + 345678);
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    AtomicInteger frameIndex = new AtomicInteger();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ originalPresentationTimesUs,
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimesUs.add(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor)
              .renderOutputFrame(renderTimesNs.get(frameIndex.getAndIncrement()));
          try {
            // TODO(b/264252759): Investigate output frames being dropped and remove sleep.
            // Frames can be dropped silently between EGL and the ImageReader. Sleep after each call
            // to swap buffers, to avoid this behavior.
            Thread.sleep(PER_FRAME_RENDERING_WAIT_TIME_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        },
        /* renderFramesAutomatically= */ false);

    assertThat(actualPresentationTimesUs)
        .containsExactlyElementsIn(originalPresentationTimesUs)
        .inOrder();
    int actualFrameCount = frameIndex.get();
    assertThat(actualFrameCount).isEqualTo(originalPresentationTimesUs.size());
    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(actualFrameCount);
    assertThat(actualRenderTimesNs).containsExactlyElementsIn(renderTimesNs).inOrder();
  }

  @Test
  public void controlledFrameRendering_withThreeFramesAtOnce_usesGivenTimestamps()
      throws Exception {
    ImmutableList<Long> originalPresentationTimesUs = ImmutableList.of(1234L, 3456L, 4567L);
    long offsetNs = System.nanoTime();
    ImmutableList<Long> renderTimesNs =
        ImmutableList.of(offsetNs + 123456, offsetNs + 234567, offsetNs + 345678);
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ originalPresentationTimesUs,
        /* onFrameAvailableListener= */ actualPresentationTimesUs::add,
        /* renderFramesAutomatically= */ false);

    // TODO(b/264252759): Investigate output frames being dropped and remove sleep.
    // Frames can be dropped silently between EGL and the ImageReader. Sleep after each call
    // to swap buffers, to avoid this behavior.
    defaultVideoFrameProcessor.renderOutputFrame(renderTimesNs.get(0));
    Thread.sleep(PER_FRAME_RENDERING_WAIT_TIME_MS);
    defaultVideoFrameProcessor.renderOutputFrame(renderTimesNs.get(1));
    Thread.sleep(PER_FRAME_RENDERING_WAIT_TIME_MS);
    defaultVideoFrameProcessor.renderOutputFrame(renderTimesNs.get(2));
    Thread.sleep(PER_FRAME_RENDERING_WAIT_TIME_MS);

    assertThat(actualPresentationTimesUs)
        .containsExactlyElementsIn(originalPresentationTimesUs)
        .inOrder();
    ImmutableList<Long> actualRenderTimesNs =
        waitForFrameRenderingAndGetRenderTimesNs(/* expectedFrameCount= */ 3);
    assertThat(actualRenderTimesNs).containsExactlyElementsIn(renderTimesNs).inOrder();
  }

  private interface OnOutputFrameAvailableForRenderingListener {
    void onFrameAvailableForRendering(long presentationTimeUs);
  }

  @EnsuresNonNull("defaultVideoFrameProcessor")
  private void processFramesToEndOfStream(
      List<Long> inputPresentationTimesUs,
      OnOutputFrameAvailableForRenderingListener onFrameAvailableListener,
      boolean renderFramesAutomatically)
      throws Exception {
    AtomicReference<@NullableType VideoFrameProcessingException>
        videoFrameProcessingExceptionReference = new AtomicReference<>();
    BlankFrameProducer blankFrameProducer = new BlankFrameProducer(WIDTH, HEIGHT);
    CountDownLatch videoFrameProcessorReadyCountDownLatch = new CountDownLatch(1);
    CountDownLatch videoFrameProcessingEndedCountDownLatch = new CountDownLatch(1);
    defaultVideoFrameProcessor =
        checkNotNull(
            new DefaultVideoFrameProcessor.Factory.Builder()
                .build()
                .create(
                    getApplicationContext(),
                    DebugViewProvider.NONE,
                    /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
                    renderFramesAutomatically,
                    MoreExecutors.directExecutor(),
                    new VideoFrameProcessor.Listener() {
                      @Override
                      public void onInputStreamRegistered(
                          @VideoFrameProcessor.InputType int inputType,
                          List<Effect> effects,
                          FrameInfo frameInfo) {
                        videoFrameProcessorReadyCountDownLatch.countDown();
                      }

                      @Override
                      public void onOutputSizeChanged(int width, int height) {
                        ImageReader outputImageReader =
                            ImageReader.newInstance(
                                width,
                                height,
                                PixelFormat.RGBA_8888,
                                /* maxImages= */ inputPresentationTimesUs.size());
                        checkNotNull(defaultVideoFrameProcessor)
                            .setOutputSurfaceInfo(
                                new SurfaceInfo(outputImageReader.getSurface(), width, height));
                        outputImageReader.setOnImageAvailableListener(
                            imageReader -> {
                              try (Image image = imageReader.acquireNextImage()) {
                                outputRenderTimesNs.add(image.getTimestamp());
                              }
                            },
                            Util.createHandlerForCurrentOrMainLooper());
                      }

                      @Override
                      public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                        onFrameAvailableListener.onFrameAvailableForRendering(presentationTimeUs);
                      }

                      @Override
                      public void onError(VideoFrameProcessingException exception) {
                        videoFrameProcessingExceptionReference.set(exception);
                        videoFrameProcessingEndedCountDownLatch.countDown();
                      }

                      @Override
                      public void onEnded() {
                        videoFrameProcessingEndedCountDownLatch.countDown();
                      }
                    }));

    defaultVideoFrameProcessor.getTaskExecutor().submit(blankFrameProducer::configureGlObjects);
    // A frame needs to be registered despite not queuing any external input to ensure
    // that the video frame processor knows about the stream offset.
    checkNotNull(defaultVideoFrameProcessor)
        .registerInputStream(
            INPUT_TYPE_SURFACE,
            /* effects= */ ImmutableList.of((GlEffect) (context, useHdr) -> blankFrameProducer),
            new FrameInfo.Builder(ColorInfo.SDR_BT709_LIMITED, WIDTH, HEIGHT).build());
    videoFrameProcessorReadyCountDownLatch.await();
    blankFrameProducer.produceBlankFrames(inputPresentationTimesUs);
    defaultVideoFrameProcessor.signalEndOfInput();
    videoFrameProcessingEndedCountDownLatch.await();
    @Nullable
    Exception videoFrameProcessingException = videoFrameProcessingExceptionReference.get();
    if (videoFrameProcessingException != null) {
      throw videoFrameProcessingException;
    }
  }

  private ImmutableList<Long> waitForFrameRenderingAndGetRenderTimesNs(int expectedFrameCount)
      throws Exception {
    ImmutableList.Builder<Long> listBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < expectedFrameCount; i++) {
      listBuilder.add(checkNotNull(outputRenderTimesNs.poll(PER_FRAME_TIMEOUT_MS, MILLISECONDS)));
    }
    // This is a best-effort check because there's no guarantee that frames aren't added to the
    // render times after this method has been called.
    assertThat(outputRenderTimesNs).isEmpty();
    return listBuilder.build();
  }
}
