/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.video.surfacecapturer;

import static com.google.android.exoplayer2.testutil.TestUtil.assertBitmapsAreSimilar;
import static com.google.android.exoplayer2.testutil.TestUtil.getBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link VideoRendererOutputCapturer}. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 24)
public final class VideoRendererOutputCapturerTest {

  private static final String TEST_VIDEO_URI = "asset:///media/mp4/testvid_1022ms.mp4";
  private static final List<Integer> FRAMES_TO_CAPTURE =
      Collections.unmodifiableList(Arrays.asList(0, 14, 15, 16, 29));
  private static final List<Integer> CAPTURE_FRAMES_TIME_MS =
      Collections.unmodifiableList(Arrays.asList(0, 467, 501, 534, 969));

  // TODO: PSNR threshold of 20 is really low. This is partly due to a bug with Texture rendering.
  // To be updated when the internal bug has been resolved. See [Internal: b/80516628].
  private static final double PSNR_THRESHOLD = 20;

  private DummyMainThread testThread;

  private List<Pair<Integer, Integer>> resultOutputSizes;
  private List<Bitmap> resultBitmaps;
  private AtomicReference<Exception> testException;
  private ExoPlayer exoPlayer;
  private VideoRendererOutputCapturer videoRendererOutputCapturer;
  private SingleFrameMediaCodecVideoRenderer mediaCodecVideoRenderer;
  private TestRunner testRunner;

  @Before
  public void setUp() {
    testThread = new DummyMainThread();
    resultOutputSizes = new ArrayList<>();
    resultBitmaps = new ArrayList<>();
    testException = new AtomicReference<>();
    testRunner = new TestRunner();
    testThread.runOnMainThread(
        () -> {
          Context context = ApplicationProvider.getApplicationContext();
          mediaCodecVideoRenderer =
              new SingleFrameMediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT);
          exoPlayer = new ExoPlayer.Builder(context, mediaCodecVideoRenderer).build();
          exoPlayer.setMediaSource(getMediaSource(context, TEST_VIDEO_URI));
          exoPlayer.prepare();

          videoRendererOutputCapturer =
              new VideoRendererOutputCapturer(
                  testRunner, new Handler(Looper.myLooper()), mediaCodecVideoRenderer, exoPlayer);
        });
  }

  @After
  public void tearDown() {
    testThread.runOnMainThread(
        () -> {
          if (exoPlayer != null) {
            exoPlayer.release();
          }
          if (videoRendererOutputCapturer != null) {
            videoRendererOutputCapturer.release();
          }
        });
    testThread.release();
  }

  @Test
  public void setOutputSize() throws InterruptedException {
    testThread.runOnMainThread(
        () -> {
          videoRendererOutputCapturer =
              new VideoRendererOutputCapturer(
                  testRunner, new Handler(Looper.myLooper()), mediaCodecVideoRenderer, exoPlayer);
          videoRendererOutputCapturer.setOutputSize(800, 600);
        });

    testRunner.outputSetCondition.block();
    assertNoExceptionOccurred();
    assertThat(resultOutputSizes).containsExactly(new Pair<>(800, 600));
  }

  @Test
  public void getFrame_getAllFramesCorrectly_originalSize() throws Exception {
    int outputWidth = 480;
    int outputHeight = 360;
    startFramesCaptureProcess(outputWidth, outputHeight, CAPTURE_FRAMES_TIME_MS);

    assertNoExceptionOccurred();
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, outputWidth, outputHeight, resultBitmaps);
  }

  @Test
  public void getFrame_getAllFramesCorrectly_largerSize_SameRatio() throws Exception {
    int outputWidth = 720;
    int outputHeight = 540;
    startFramesCaptureProcess(outputWidth, outputHeight, CAPTURE_FRAMES_TIME_MS);

    assertNoExceptionOccurred();
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, outputWidth, outputHeight, resultBitmaps);
  }

  @Test
  public void getFrame_getAllFramesCorrectly_largerSize_NotSameRatio() throws Exception {
    int outputWidth = 987;
    int outputHeight = 654;
    startFramesCaptureProcess(outputWidth, outputHeight, CAPTURE_FRAMES_TIME_MS);

    assertNoExceptionOccurred();
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, outputWidth, outputHeight, resultBitmaps);
  }

  @Test
  public void getFrame_getAllFramesCorrectly_smallerSize_SameRatio() throws Exception {
    int outputWidth = 320;
    int outputHeight = 240;
    startFramesCaptureProcess(outputWidth, outputHeight, CAPTURE_FRAMES_TIME_MS);

    assertNoExceptionOccurred();
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, outputWidth, outputHeight, resultBitmaps);
  }

  @Test
  public void getFrame_getAllFramesCorrectly_smallerSize_NotSameRatio() throws Exception {
    int outputWidth = 432;
    int outputHeight = 321;
    startFramesCaptureProcess(outputWidth, outputHeight, CAPTURE_FRAMES_TIME_MS);

    assertNoExceptionOccurred();
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, outputWidth, outputHeight, resultBitmaps);
  }

  @Ignore // [Internal ref: b/111542655]
  @Test
  public void getFrame_getAllFramesCorrectly_setSurfaceMultipleTimes() throws Exception {
    int firstOutputWidth = 480;
    int firstOutputHeight = 360;

    startFramesCaptureProcess(firstOutputWidth, firstOutputHeight, CAPTURE_FRAMES_TIME_MS);
    int secondOutputWidth = 432;
    int secondOutputHeight = 321;

    startFramesCaptureProcess(secondOutputWidth, secondOutputHeight, CAPTURE_FRAMES_TIME_MS);

    List<Bitmap> firstHalfResult =
        new ArrayList<>(resultBitmaps.subList(0, FRAMES_TO_CAPTURE.size()));
    List<Bitmap> secondHalfResult =
        new ArrayList<>(
            resultBitmaps.subList(FRAMES_TO_CAPTURE.size(), 2 * FRAMES_TO_CAPTURE.size()));
    assertThat(resultBitmaps).hasSize(FRAMES_TO_CAPTURE.size() * 2);

    assertNoExceptionOccurred();
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, firstOutputWidth, firstOutputHeight, firstHalfResult);
    assertExtractedFramesMatchExpectation(
        FRAMES_TO_CAPTURE, secondOutputWidth, secondOutputHeight, secondHalfResult);
  }

  private void startFramesCaptureProcess(
      int outputWidth, int outputHeight, List<Integer> listFrameToCaptureMs)
      throws InterruptedException {

    testRunner.captureFinishedCondition.close();
    testThread.runOnMainThread(
        () -> {
          videoRendererOutputCapturer.setOutputSize(outputWidth, outputHeight);
          testRunner.setListFramesToCapture(listFrameToCaptureMs);
          testRunner.startCapturingProcess();
        });
    testRunner.captureFinishedCondition.block();
  }

  private MediaSource getMediaSource(Context context, String testVideoUri) {
    return new ProgressiveMediaSource.Factory(new DefaultDataSourceFactory(context))
        .createMediaSource(MediaItem.fromUri(testVideoUri));
  }

  private void assertNoExceptionOccurred() {
    if (testException.get() != null) {
      throw new AssertionError("Unexpected exception", testException.get());
    }
  }

  private void assertExtractedFramesMatchExpectation(
      List<Integer> framesToExtract, int outputWidth, int outputHeight, List<Bitmap> resultBitmaps)
      throws IOException {
    assertThat(resultBitmaps).hasSize(framesToExtract.size());
    for (int i = 0; i < framesToExtract.size(); i++) {
      int frameIndex = framesToExtract.get(i);
      String expectedBitmapFileName =
          String.format("media/mp4/testvid_1022ms_%03d.png", frameIndex);
      Bitmap referenceFrame =
          getBitmap(
              InstrumentationRegistry.getInstrumentation().getTargetContext(),
              expectedBitmapFileName);
      Bitmap expectedBitmap =
          Bitmap.createScaledBitmap(referenceFrame, outputWidth, outputHeight, /* filter= */ true);
      assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(i), PSNR_THRESHOLD);
    }
  }

  /** A {@link VideoRendererOutputCapturer.Callback} implementation that facilities testing. */
  private final class TestRunner implements VideoRendererOutputCapturer.Callback {

    private final ConditionVariable outputSetCondition;
    private final ConditionVariable captureFinishedCondition;
    private final List<Integer> captureFrameTimeMs;
    private final AtomicInteger currentFrameIndex;

    TestRunner() {
      captureFrameTimeMs = new ArrayList<>();
      currentFrameIndex = new AtomicInteger();
      outputSetCondition = new ConditionVariable();
      captureFinishedCondition = new ConditionVariable();
    }

    public void setListFramesToCapture(List<Integer> listFrameToCaptureMs) {
      captureFrameTimeMs.clear();
      captureFrameTimeMs.addAll(listFrameToCaptureMs);
    }

    public void startCapturingProcess() {
      currentFrameIndex.set(0);
      exoPlayer.seekTo(captureFrameTimeMs.get(currentFrameIndex.get()));
    }

    @Override
    public void onOutputSizeSet(int width, int height) {
      resultOutputSizes.add(new Pair<>(width, height));
      outputSetCondition.open();
    }

    @Override
    public void onSurfaceCaptured(Bitmap bitmap) {
      resultBitmaps.add(bitmap);
      int frameIndex = currentFrameIndex.incrementAndGet();
      if (frameIndex == captureFrameTimeMs.size()) {
        captureFinishedCondition.open();
      } else {
        exoPlayer.seekTo(captureFrameTimeMs.get(frameIndex));
      }
    }

    @Override
    public void onSurfaceCaptureError(Exception exception) {
      testException.set(exception);
      // By default, if there is any thrown exception, we will finish the test.
      captureFinishedCondition.open();
    }
  }
}
