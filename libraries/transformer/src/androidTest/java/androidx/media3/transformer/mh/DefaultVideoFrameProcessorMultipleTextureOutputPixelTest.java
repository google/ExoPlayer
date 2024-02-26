/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.createTimestampIterator;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.util.Pair;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for {@link DefaultVideoFrameProcessor} texture output.
 *
 * <p>Confirms that the output timestamps are correct for each frame, and that the output pixels are
 * correct for the first frame of each bitmap.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultVideoFrameProcessorMultipleTextureOutputPixelTest {

  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String MEDIA3_TEST_PNG_ASSET_PATH = "media/png/media3test.png";
  private static final String SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/srgb_to_electrical_original.png";
  private static final String SRGB_TO_ELECTRICAL_MEDIA3_TEST_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/srgb_to_electrical_media3test.png";

  @Rule public final TestName testName = new TestName();

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void release() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  @Test
  public void textureOutput_queueBitmap_matchesGoldenFile() throws Exception {
    videoFrameProcessorTestRunner = getFrameProcessorTestRunnerBuilder(testId).build();
    ImmutableList<Long> inputTimestamps = ImmutableList.of(1_000_000L, 2_000_000L, 3_000_000L);

    queueBitmaps(videoFrameProcessorTestRunner, ORIGINAL_PNG_ASSET_PATH, inputTimestamps);
    videoFrameProcessorTestRunner.endFrameProcessing();

    TextureBitmapReader textureBitmapReader = checkNotNull(this.textureBitmapReader);
    Set<Long> outputTimestamps = textureBitmapReader.getOutputTimestamps();
    assertThat(outputTimestamps).containsExactlyElementsIn(inputTimestamps).inOrder();
    Bitmap actualBitmap = textureBitmapReader.getBitmapAtPresentationTimeUs(1_000_000L);
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            readBitmap(SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH), actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void textureOutput_queueTwoBitmaps_matchesGoldenFiles() throws Exception {
    videoFrameProcessorTestRunner = getFrameProcessorTestRunnerBuilder(testId).build();
    ImmutableList<Long> inputTimestamps1 = ImmutableList.of(1_000_000L, 1_500_000L);
    ImmutableList<Long> inputTimestamps2 = ImmutableList.of(2_000_000L, 3_000_000L, 4_000_000L);
    ImmutableList<Long> outputTimestamps =
        ImmutableList.of(1_000_000L, 1_500_000L, 2_000_000L, 3_000_000L, 4_000_000L);

    queueBitmaps(videoFrameProcessorTestRunner, ORIGINAL_PNG_ASSET_PATH, inputTimestamps1);
    queueBitmaps(videoFrameProcessorTestRunner, MEDIA3_TEST_PNG_ASSET_PATH, inputTimestamps2);
    videoFrameProcessorTestRunner.endFrameProcessing();

    TextureBitmapReader textureBitmapReader = checkNotNull(this.textureBitmapReader);
    Set<Long> actualOutputTimestamps = textureBitmapReader.getOutputTimestamps();
    assertThat(actualOutputTimestamps).containsExactlyElementsIn(outputTimestamps).inOrder();
    Bitmap actualBitmap1 = textureBitmapReader.getBitmapAtPresentationTimeUs(1_000_000L);
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual1", actualBitmap1, /* path= */ null);
    Bitmap actualBitmap2 = textureBitmapReader.getBitmapAtPresentationTimeUs(2_000_000L);
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual2", actualBitmap2, /* path= */ null);
    float averagePixelAbsoluteDifference1 =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            readBitmap(SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH), actualBitmap1, testId);
    assertThat(averagePixelAbsoluteDifference1)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
    float averagePixelAbsoluteDifference2 =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            readBitmap(SRGB_TO_ELECTRICAL_MEDIA3_TEST_PNG_ASSET_PATH), actualBitmap2, testId);
    assertThat(averagePixelAbsoluteDifference2)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  // This tests a condition that is difficult to synchronize, and is subject to a race
  // condition. It may flake/fail if any queued frames are processed in the VideoFrameProcessor
  // thread, before flush begins and cancels these pending frames. However, this is better than not
  // testing this behavior at all, and in practice has succeeded every time on a 1000-time run.
  // TODO: b/302695659 - Make this test more deterministic.
  @Test
  public void textureOutput_queueFiveBitmapsAndFlush_outputsOnlyAfterFlush() throws Exception {
    videoFrameProcessorTestRunner = getFrameProcessorTestRunnerBuilder(testId).build();
    ImmutableList<Long> inputTimestamps1 = ImmutableList.of(1_000_000L, 2_000_000L, 3_000_000L);
    ImmutableList<Long> inputTimestamps2 = ImmutableList.of(4_000_000L, 5_000_000L, 6_000_000L);

    queueBitmaps(videoFrameProcessorTestRunner, ORIGINAL_PNG_ASSET_PATH, inputTimestamps1);
    videoFrameProcessorTestRunner.flush();
    queueBitmaps(videoFrameProcessorTestRunner, MEDIA3_TEST_PNG_ASSET_PATH, inputTimestamps2);
    videoFrameProcessorTestRunner.endFrameProcessing();

    TextureBitmapReader textureBitmapReader = checkNotNull(this.textureBitmapReader);
    Set<Long> actualOutputTimestamps = textureBitmapReader.getOutputTimestamps();
    assertThat(actualOutputTimestamps).containsAtLeastElementsIn(inputTimestamps2).inOrder();
    // This assertion is subject to flaking, per test comments. If it flakes, consider increasing
    // the number of elements in inputTimestamps2.
    assertThat(actualOutputTimestamps.size())
        .isLessThan(inputTimestamps1.size() + inputTimestamps2.size());
  }

  private void queueBitmaps(
      VideoFrameProcessorTestRunner videoFrameProcessorTestRunner,
      String bitmapAssetPath,
      List<Long> timestamps)
      throws IOException, VideoFrameProcessingException {
    Bitmap bitmap = readBitmap(bitmapAssetPath);
    videoFrameProcessorTestRunner.queueInputBitmaps(
        bitmap.getWidth(),
        bitmap.getHeight(),
        Pair.create(bitmap, createTimestampIterator(timestamps)));
  }

  private VideoFrameProcessorTestRunner.Builder getFrameProcessorTestRunnerBuilder(String testId) {
    textureBitmapReader = new TextureBitmapReader();
    VideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs, unusedSyncObject) -> {
                  checkNotNull(textureBitmapReader).readBitmap(outputTexture, presentationTimeUs);
                  outputTextureProducer.releaseOutputTexture(presentationTimeUs);
                },
                /* textureOutputCapacity= */ 1)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setBitmapReader(textureBitmapReader);
  }
}
