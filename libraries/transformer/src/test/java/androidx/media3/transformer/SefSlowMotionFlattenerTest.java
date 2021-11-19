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

package androidx.media3.transformer;

import static androidx.media3.transformer.SefSlowMotionFlattener.INPUT_FRAME_RATE;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import androidx.media3.extractor.metadata.mp4.SmtaMetadataEntry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SefSlowMotionFlattener}. */
@RunWith(AndroidJUnit4.class)
public class SefSlowMotionFlattenerTest {

  /**
   * Sequence of temporal SVC layers in an SEF slow motion video track with a maximum layer of 3.
   *
   * <p>Each value is attached to a frame and the sequence is repeated until there is no frame left.
   */
  private static final int[] LAYER_SEQUENCE_MAX_LAYER_THREE = new int[] {0, 3, 2, 3, 1, 3, 2, 3};

  @Test
  public void processCurrentFrame_240fps_keepsExpectedFrames() {
    int captureFrameRate = 240;
    int inputMaxLayer = 3;
    int frameCount = 46;
    SlowMotionData.Segment segment1 =
        createSegment(/* startFrameIndex= */ 11, /* endFrameIndex= */ 17, /* speedDivisor= */ 2);
    SlowMotionData.Segment segment2 =
        createSegment(/* startFrameIndex= */ 31, /* endFrameIndex= */ 38, /* speedDivisor= */ 8);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate, inputMaxLayer, Arrays.asList(segment1, segment2));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Integer> outputLayers =
        getKeptOutputLayers(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    List<Integer> expectedLayers = Arrays.asList(0, 0, 1, 0, 0, 1, 2, 3, 0, 3, 2, 3, 1, 3, 0);
    assertThat(outputLayers).isEqualTo(expectedLayers);
  }

  @Test
  public void processCurrentFrame_120fps_keepsExpectedFrames() {
    int captureFrameRate = 120;
    int inputMaxLayer = 3;
    int frameCount = 46;
    SlowMotionData.Segment segment1 =
        createSegment(/* startFrameIndex= */ 9, /* endFrameIndex= */ 17, /* speedDivisor= */ 4);
    SlowMotionData.Segment segment2 =
        createSegment(/* startFrameIndex= */ 31, /* endFrameIndex= */ 38, /* speedDivisor= */ 8);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate, inputMaxLayer, Arrays.asList(segment1, segment2));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Integer> outputLayers =
        getKeptOutputLayers(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    List<Integer> expectedLayers =
        Arrays.asList(0, 1, 0, 3, 2, 3, 1, 3, 2, 3, 0, 1, 0, 1, 2, 3, 0, 3, 2, 3, 1, 3, 0, 1);
    assertThat(outputLayers).isEqualTo(expectedLayers);
  }

  @Test
  public void processCurrentFrame_contiguousSegments_keepsExpectedFrames() {
    int captureFrameRate = 240;
    int inputMaxLayer = 3;
    int frameCount = 26;
    SlowMotionData.Segment segment1 =
        createSegment(/* startFrameIndex= */ 11, /* endFrameIndex= */ 19, /* speedDivisor= */ 2);
    SlowMotionData.Segment segment2 =
        createSegment(/* startFrameIndex= */ 19, /* endFrameIndex= */ 22, /* speedDivisor= */ 8);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate, inputMaxLayer, Arrays.asList(segment1, segment2));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Integer> outputLayers =
        getKeptOutputLayers(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    List<Integer> expectedLayers = Arrays.asList(0, 0, 1, 0, 2, 3, 1, 3, 0);
    assertThat(outputLayers).isEqualTo(expectedLayers);
  }

  @Test
  public void processCurrentFrame_skipsSegmentsWithNoFrame() {
    int captureFrameRate = 240;
    int inputMaxLayer = 3;
    int frameCount = 16;
    SlowMotionData.Segment segmentWithNoFrame1 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 120, /* endTimeMs= */ 130, /* speedDivisor= */ 2);
    SlowMotionData.Segment segmentWithNoFrame2 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 270, /* endTimeMs= */ 280, /* speedDivisor= */ 2);
    SlowMotionData.Segment segmentWithFrame =
        createSegment(/* startFrameIndex= */ 11, /* endFrameIndex= */ 16, /* speedDivisor= */ 2);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate,
            inputMaxLayer,
            Arrays.asList(segmentWithNoFrame1, segmentWithNoFrame2, segmentWithFrame));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Integer> outputLayers =
        getKeptOutputLayers(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    List<Integer> expectedLayers = Arrays.asList(0, 0, 1);
    assertThat(outputLayers).isEqualTo(expectedLayers);
  }

  @Test
  public void getCurrentFrameOutputTimeUs_240fps_outputsExpectedTimes() {
    int captureFrameRate = 240;
    int inputMaxLayer = 3;
    int frameCount = 16;
    SlowMotionData.Segment segment1 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 50, /* endTimeMs= */ 150, /* speedDivisor= */ 2);
    SlowMotionData.Segment segment2 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 210, /* endTimeMs= */ 360, /* speedDivisor= */ 8);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate, inputMaxLayer, Arrays.asList(segment1, segment2));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Long> outputTimesUs =
        getOutputTimesUs(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    // Test frame inside segment.
    assertThat(outputTimesUs.get(9))
        .isEqualTo(Math.round((300.0 + 100 + (300 - 210) * 7) * 1000 * 30 / 240));
    // Test frame outside segment.
    assertThat(outputTimesUs.get(13))
        .isEqualTo(Math.round((433 + 1 / 3.0 + 100 + 150 * 7) * 1000 * 30 / 240));
  }

  @Test
  public void getCurrentFrameOutputTimeUs_120fps_outputsExpectedTimes() {
    int captureFrameRate = 120;
    int inputMaxLayer = 3;
    int frameCount = 16;
    SlowMotionData.Segment segment1 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 50, /* endTimeMs= */ 150, /* speedDivisor= */ 2);
    SlowMotionData.Segment segment2 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 210, /* endTimeMs= */ 360, /* speedDivisor= */ 8);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate, inputMaxLayer, Arrays.asList(segment1, segment2));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Long> outputTimesUs =
        getOutputTimesUs(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    // Test frame inside segment.
    assertThat(outputTimesUs.get(9))
        .isEqualTo(Math.round((300.0 + 100 + (300 - 210) * 7) * 1000 * 30 / 120));
    // Test frame outside segment.
    assertThat(outputTimesUs.get(13))
        .isEqualTo(Math.round((433 + 1 / 3.0 + 100 + 150 * 7) * 1000 * 30 / 120));
  }

  @Test
  public void getCurrentFrameOutputTimeUs_contiguousSegments_outputsExpectedTimes() {
    int captureFrameRate = 240;
    int inputMaxLayer = 3;
    int frameCount = 16;
    SlowMotionData.Segment segment1 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 50, /* endTimeMs= */ 210, /* speedDivisor= */ 2);
    SlowMotionData.Segment segment2 =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 210, /* endTimeMs= */ 360, /* speedDivisor= */ 8);
    Format format =
        createSefSlowMotionFormat(
            captureFrameRate, inputMaxLayer, Arrays.asList(segment1, segment2));

    SefSlowMotionFlattener sefSlowMotionFlattener = new SefSlowMotionFlattener(format);
    List<Long> outputTimesUs =
        getOutputTimesUs(sefSlowMotionFlattener, LAYER_SEQUENCE_MAX_LAYER_THREE, frameCount);

    // Test frame inside second segment.
    assertThat(outputTimesUs.get(9)).isEqualTo(136_250);
  }

  /**
   * Creates a {@link SlowMotionData.Segment}.
   *
   * @param startFrameIndex The index of the first frame in the segment.
   * @param endFrameIndex The index of the first frame following the segment.
   * @param speedDivisor The factor by which the input is slowed down in the segment.
   * @return A {@link SlowMotionData.Segment}.
   */
  private static SlowMotionData.Segment createSegment(
      int startFrameIndex, int endFrameIndex, int speedDivisor) {
    return new SlowMotionData.Segment(
        /* startTimeMs= */ (int) (startFrameIndex * C.MILLIS_PER_SECOND / INPUT_FRAME_RATE),
        /* endTimeMs= */ (int) (endFrameIndex * C.MILLIS_PER_SECOND / INPUT_FRAME_RATE) - 1,
        speedDivisor);
  }

  /** Creates a {@link Format} for an SEF slow motion video track. */
  private static Format createSefSlowMotionFormat(
      int captureFrameRate, int inputMaxLayer, List<SlowMotionData.Segment> segments) {
    SmtaMetadataEntry smtaMetadataEntry =
        new SmtaMetadataEntry(captureFrameRate, /* svcTemporalLayerCount= */ inputMaxLayer + 1);
    SlowMotionData slowMotionData = new SlowMotionData(segments);
    Metadata metadata = new Metadata(smtaMetadataEntry, slowMotionData);
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setMetadata(metadata)
        .build();
  }

  /**
   * Returns a list containing the temporal SVC layers of the frames that should be kept according
   * to {@link SefSlowMotionFlattener#processCurrentFrame(int, long)}.
   *
   * @param sefSlowMotionFlattener The {@link SefSlowMotionFlattener}.
   * @param layerSequence The sequence of layer values in the input.
   * @param frameCount The number of video frames in the input.
   * @return The output layers.
   */
  private static List<Integer> getKeptOutputLayers(
      SefSlowMotionFlattener sefSlowMotionFlattener, int[] layerSequence, int frameCount) {
    List<Integer> outputLayers = new ArrayList<>();
    for (int i = 0; i < frameCount; i++) {
      int layer = layerSequence[i % layerSequence.length];
      long timeUs = i * C.MICROS_PER_SECOND / INPUT_FRAME_RATE;
      if (sefSlowMotionFlattener.processCurrentFrame(layer, timeUs)) {
        outputLayers.add(layer);
      }
    }
    return outputLayers;
  }

  /**
   * Returns a list containing the frame output times obtained using {@link
   * SefSlowMotionFlattener#getCurrentFrameOutputTimeUs(long)}.
   *
   * <p>The output contains the output times for all the input frames, regardless of whether they
   * should be kept or not.
   *
   * @param sefSlowMotionFlattener The {@link SefSlowMotionFlattener}.
   * @param layerSequence The sequence of layer values in the input.
   * @param frameCount The number of video frames in the input.
   * @return The frame output times, in microseconds.
   */
  private static List<Long> getOutputTimesUs(
      SefSlowMotionFlattener sefSlowMotionFlattener, int[] layerSequence, int frameCount) {
    List<Long> outputTimesUs = new ArrayList<>();
    for (int i = 0; i < frameCount; i++) {
      int layer = layerSequence[i % layerSequence.length];
      long inputTimeUs = i * C.MICROS_PER_SECOND / INPUT_FRAME_RATE;
      sefSlowMotionFlattener.processCurrentFrame(layer, inputTimeUs);
      outputTimesUs.add(sefSlowMotionFlattener.getCurrentFrameOutputTimeUs(inputTimeUs));
    }
    return outputTimesUs;
  }
}
