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
package androidx.media3.extractor.jpeg;

import androidx.media3.test.utils.ExtractorAsserts;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

/** Unit tests for {@link JpegExtractorTest}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class JpegExtractorTest {

  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
  public static ImmutableList<ExtractorAsserts.SimulationConfig> params() {
    return ExtractorAsserts.configs();
  }

  @ParameterizedRobolectricTestRunner.Parameter
  public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void sampleNonMotionPhotoShortened_extractImage() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new JpegExtractor(JpegExtractor.FLAG_READ_IMAGE),
        "media/jpeg/non-motion-photo-shortened.jpg",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix(
                "extractordumps/jpeg/non-motion-photo-shortened.jpg_JpegExtractor.FLAG_READ_IMAGE")
            .build(),
        simulationConfig);
  }

  @Test
  public void samplePixelMotionPhotoShortened_extractImage() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new JpegExtractor(JpegExtractor.FLAG_READ_IMAGE),
        "media/jpeg/pixel-motion-photo-shortened.jpg",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix(
                "extractordumps/jpeg/pixel-motion-photo-shortened.jpg_JpegExtractor.FLAG_READ_IMAGE")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleNonMotionPhotoShortened_extractMotionPhoto() throws Exception {
    ExtractorAsserts.assertBehavior(
        JpegExtractor::new, "media/jpeg/non-motion-photo-shortened.jpg", simulationConfig);
  }

  @Test
  public void samplePixelMotionPhotoShortened_extractMotionPhoto() throws Exception {
    ExtractorAsserts.assertBehavior(
        JpegExtractor::new, "media/jpeg/pixel-motion-photo-shortened.jpg", simulationConfig);
  }

  @Test
  public void samplePixelMotionPhotoJfifSegmentShortened_extractMotionPhoto() throws Exception {
    ExtractorAsserts.assertBehavior(
        JpegMotionPhotoExtractor::new,
        "media/jpeg/pixel-motion-photo-jfif-segment-shortened.jpg",
        simulationConfig);
  }

  @Test
  public void samplePixelMotionPhotoVideoRemovedShortened_extractMotionPhoto() throws Exception {
    ExtractorAsserts.assertBehavior(
        JpegMotionPhotoExtractor::new,
        "media/jpeg/pixel-motion-photo-video-removed-shortened.jpg",
        simulationConfig);
  }

  @Test
  public void sampleSsMotionPhotoShortened_extractMotionPhoto() throws Exception {
    ExtractorAsserts.assertBehavior(
        JpegMotionPhotoExtractor::new,
        "media/jpeg/ss-motion-photo-shortened.jpg",
        simulationConfig);
  }

  /** Regression test for [internal b/301025983]. */
  @Test
  public void samplePixelMotionPhotoWithTwoHevcTracks_extractMotionPhoto() throws Exception {
    ExtractorAsserts.assertBehavior(
        JpegMotionPhotoExtractor::new,
        "media/jpeg/pixel-motion-photo-2-hevc-tracks.jpg",
        simulationConfig);
  }
}
