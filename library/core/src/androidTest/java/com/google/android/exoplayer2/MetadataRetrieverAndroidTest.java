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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.testutil.MetadataRetrieverTestRunner.FrameCallbackData.frameCallbackData;
import static com.google.android.exoplayer2.testutil.MetadataRetrieverTestRunner.newTestRunner;
import static com.google.android.exoplayer2.testutil.TestUtil.readBitmapFromFile;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.reverse;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Pair;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.MetadataRetrieverTestRunner;
import com.google.android.exoplayer2.testutil.MetadataRetrieverTestRunner.FrameCallbackData;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumentation test for {@link MetadataRetriever}. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 24)
@ClosedSource(reason = "Not ready yet")
public class MetadataRetrieverAndroidTest {

  private static final String TEST_VIDEO_URI = "asset:///mp4/testvid_1022ms.mp4";

  private static final List<Integer> FRAMES_TO_CAPTURE = Arrays.asList(0, 14, 15, 16, 29);
  private static final List<Integer> CAPTURE_FRAMES_TIME_MS = Arrays.asList(0, 467, 501, 534, 969);

  // TODO: PSNR threshold of 20 is really low. This is partly due to a bug with Texture rendering.
  // To be updated when the internal bug has been resolved. See [Internal: b/80516628].
  private static final double PSNR_THRESHOLD = 20;
  private static final int ORIGINAL_FRAME_WIDTH = 480;
  private static final int ORIGINAL_FRAME_HEIGHT = 360;

  @Test
  public void testGetFrame_getAllFramesCorrectly_lengthUnset()
      throws InterruptedException, IOException, TimeoutException {
    testCaptureRequiredFramesFromVideo(
        /* outputWidth= */ C.LENGTH_UNSET,
        /* outputHeight= */ C.LENGTH_UNSET,
        FRAMES_TO_CAPTURE,
        CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_widthUnset()
      throws InterruptedException, IOException, TimeoutException {
    int outputHeight = 540;
    testCaptureRequiredFramesFromVideo(
        /* outputWidth= */ C.LENGTH_UNSET, outputHeight, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_heightUnset()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 300;
    testCaptureRequiredFramesFromVideo(
        outputWidth, /* outputHeight= */ C.LENGTH_UNSET, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_originalSize()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 480;
    int outputHeight = 360;
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_largerSize_sameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 720;
    int outputHeight = 540;
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_largerSize_notSameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 987;
    int outputHeight = 654;
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_smallerSize_sameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 320;
    int outputHeight = 240;
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_getAllFramesCorrectly_smallerSize_notSameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 432;
    int outputHeight = 321;
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, FRAMES_TO_CAPTURE, CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_lengthUnset()
      throws InterruptedException, IOException, TimeoutException {
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        /* outputWidth= */ C.LENGTH_UNSET,
        /* outputHeight= */ C.LENGTH_UNSET,
        framesToCapture,
        captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_widthUnset()
      throws InterruptedException, IOException, TimeoutException {
    int outputHeight = 540;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        /* outputWidth= */ C.LENGTH_UNSET, outputHeight, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_heightUnset()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 300;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        outputWidth, /* outputHeight= */ C.LENGTH_UNSET, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_originalSize()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 480;
    int outputHeight = 360;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_largerSize_sameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 720;
    int outputHeight = 540;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_largerSize_notSameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 987;
    int outputHeight = 654;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_smallerSize_sameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 320;
    int outputHeight = 240;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_inReverseOrder_getAllFramesCorrectly_smallerSize_notSameRatio()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 432;
    int outputHeight = 321;
    List<Integer> framesToCapture = new ArrayList<>(FRAMES_TO_CAPTURE);
    reverse(framesToCapture);
    List<Integer> captureFramesTimeMs = new ArrayList<>(CAPTURE_FRAMES_TIME_MS);
    reverse(captureFramesTimeMs);
    testCaptureRequiredFramesFromVideo(
        outputWidth, outputHeight, framesToCapture, captureFramesTimeMs);
  }

  @Test
  public void testGetFrame_multiWindowsMedia_getAllFramesCorrectly_lengthUnset()
      throws InterruptedException, IOException, TimeoutException {
    testCaptureRequiredFramesFromVideoMultiplePeriod(
        /* outputWidth= */ C.LENGTH_UNSET,
        /* outputHeight= */ C.LENGTH_UNSET,
        /* useMultiWindowMediaSource= */ true,
        FRAMES_TO_CAPTURE,
        Arrays.asList(1, 0, 1, 0, 1),
        CAPTURE_FRAMES_TIME_MS);
  }

  @Test
  public void testGetFrame_multiWindowsMedia_getAllFramesCorrectly_originalSize()
      throws InterruptedException, IOException, TimeoutException {
    int outputWidth = 480;
    int outputHeight = 360;
    testCaptureRequiredFramesFromVideoMultiplePeriod(
        outputWidth,
        outputHeight,
        /* useMultiWindowMediaSource= */ true,
        FRAMES_TO_CAPTURE,
        Arrays.asList(1, 0, 1, 0, 1),
        CAPTURE_FRAMES_TIME_MS);
  }

  private void testCaptureRequiredFramesFromVideo(
      int outputWidth,
      int outputHeight,
      List<Integer> framesToCapture,
      List<Integer> captureFramesTimeMs)
      throws InterruptedException, TimeoutException, IOException {
    List<Integer> windowIdsForFrame = new ArrayList<>();
    windowIdsForFrame.addAll(Collections.nCopies(framesToCapture.size(), 0));
    testCaptureRequiredFramesFromVideoMultiplePeriod(
        outputWidth,
        outputHeight,
        /* useMultiWindowMediaSource= */ false,
        framesToCapture,
        windowIdsForFrame,
        captureFramesTimeMs);
  }

  private void testCaptureRequiredFramesFromVideoMultiplePeriod(
      int outputWidth,
      int outputHeight,
      boolean useMultiWindowMediaSource,
      List<Integer> framesToCapture,
      List<Integer> windowIdsForFrame,
      List<Integer> captureFramesTimeMs)
      throws InterruptedException, TimeoutException, IOException {
    Context context = InstrumentationRegistry.getTargetContext();
    MetadataRetrieverTestRunner testRunner =
        newTestRunner(new MediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT));

    testRunner.prepareBlocking(
        useMultiWindowMediaSource
            ? getMultiWindowMediaSource(context, TEST_VIDEO_URI)
            : getMediaSource(context, TEST_VIDEO_URI));

    MetadataRetriever.Options options =
        (outputWidth == C.LENGTH_UNSET && outputHeight == C.LENGTH_UNSET)
            ? null
            : new MetadataRetriever.Options.Builder()
                .setOutputWidthHeight(outputWidth, outputHeight)
                .build();
    for (int i = 0; i < captureFramesTimeMs.size(); i++) {
      long framePosition = (long) captureFramesTimeMs.get(i);
      int windowIdForFrame = windowIdsForFrame.get(i);
      testRunner.setWindowIndexAsync(windowIdForFrame);
      if (i < captureFramesTimeMs.size() - 1) {
        testRunner.getFrameAtTimeAsync(framePosition, options);
      } else {
        // Blocking on the final call, so we can wait for the results.
        testRunner.getFrameAtTimeBlocking(framePosition, options);
      }
    }
    Pair<Integer, Integer> expectedWidthAndHeight =
        resolveExpectedWidthAndHeight(outputWidth, outputHeight);
    FrameCallbackData[] expectedCallbackData =
        getExpectedFrameCallbackData(
            expectedWidthAndHeight.first,
            expectedWidthAndHeight.second,
            testRunner.getMetadataRetriever().getCurrentTimeline(),
            framesToCapture,
            windowIdsForFrame,
            captureFramesTimeMs);

    testRunner.assertFrameCallbackDataMatches(PSNR_THRESHOLD, expectedCallbackData);
    assertThat(testRunner.getFailedQueryExceptions()).isEmpty();
    testRunner.release();
  }

  private Pair<Integer, Integer> resolveExpectedWidthAndHeight(int outputWidth, int outputHeight) {
    if (outputWidth == C.LENGTH_UNSET && outputHeight == C.LENGTH_UNSET) {
      outputWidth = ORIGINAL_FRAME_WIDTH;
      outputHeight = ORIGINAL_FRAME_HEIGHT;
    } else if (outputHeight == C.LENGTH_UNSET) {
      outputHeight = outputWidth * ORIGINAL_FRAME_HEIGHT / ORIGINAL_FRAME_WIDTH;
    } else if (outputWidth == C.LENGTH_UNSET) {
      outputWidth = outputHeight * ORIGINAL_FRAME_WIDTH / ORIGINAL_FRAME_HEIGHT;
    } else {
      double outputAspectRatio = 1.0 * outputWidth / outputHeight;
      double originalFrameAspectRatio = 1.0 * ORIGINAL_FRAME_WIDTH / ORIGINAL_FRAME_HEIGHT;
      if (outputAspectRatio < originalFrameAspectRatio) {
        outputHeight = outputWidth * ORIGINAL_FRAME_HEIGHT / ORIGINAL_FRAME_WIDTH;
      } else {
        outputWidth = outputHeight * ORIGINAL_FRAME_WIDTH / ORIGINAL_FRAME_HEIGHT;
      }
    }
    return new Pair<>(outputWidth, outputHeight);
  }

  private FrameCallbackData[] getExpectedFrameCallbackData(
      int expectedWidth,
      int expectedHeight,
      Timeline expectedTimeline,
      List<Integer> framesToCapture,
      List<Integer> windowIdsForFrame,
      List<Integer> captureFramesTimeMs)
      throws IOException {
    FrameCallbackData[] expectedCallbackData = new FrameCallbackData[framesToCapture.size()];
    for (int i = 0; i < framesToCapture.size(); i++) {
      int frameIndex = framesToCapture.get(i);
      int windowId = windowIdsForFrame.size() > i ? windowIdsForFrame.get(i) : 0;
      String expectedBitmapFileName = String.format("mp4/video%03d.png", frameIndex);
      Bitmap referenceFrame =
          readBitmapFromFile(InstrumentationRegistry.getTargetContext(), expectedBitmapFileName);
      Bitmap expectedBitmap =
          Bitmap.createScaledBitmap(
              referenceFrame, expectedWidth, expectedHeight, /* filter= */ true);
      expectedCallbackData[i] =
          frameCallbackData(
              expectedBitmap, expectedTimeline, windowId, (long) captureFramesTimeMs.get(i));
    }
    return expectedCallbackData;
  }

  private MediaSource getMultiWindowMediaSource(Context context, String testVideoUri) {
    return new ConcatenatingMediaSource(
        new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context, "ExoPlayerTest"))
            .createMediaSource(Uri.parse(testVideoUri)),
        new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context, "ExoPlayerTest"))
            .createMediaSource(Uri.parse(testVideoUri)));
  }

  private MediaSource getMediaSource(Context context, String testVideoUri) {
    return new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context, "ExoPlayerTest"))
        .createMediaSource(Uri.parse(testVideoUri));
  }
}
