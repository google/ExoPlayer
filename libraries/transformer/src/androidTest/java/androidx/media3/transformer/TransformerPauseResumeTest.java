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
package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.Util;
import androidx.media3.effect.RgbFilter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** End-to-end instrumentation tests for {@link Transformer} pause and resume scenarios. */
@RunWith(AndroidJUnit4.class)
public class TransformerPauseResumeTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final long DEFAULT_PRESENTATION_TIME_US_TO_BLOCK_FRAME = 5_000_000L;
  private static final int DEFAULT_TIMEOUT_SECONDS = 120;
  private static final int MP4_ASSET_FRAME_COUNT = 932;

  private final Context context = getApplicationContext();

  @Test
  public void resume_withSingleMediaItem_outputMatchesExpected() throws Exception {
    String testId = "resume_withSingleMediaItem_outputMatchesExpected";
    if (shouldSkipDevice(testId)) {
      return;
    }
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 1);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);
    TransformerAndroidTestRunner testRunner =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build();

    // Resume the export.
    ExportResult exportResult = testRunner.run(testId, composition, firstOutputPath).exportResult;

    assertThat(exportResult.processedInputs).hasSize(4);
    assertThat(exportResult.videoFrameCount)
        .isEqualTo(MP4_ASSET_FRAME_COUNT - getDeviceSpecificMissingFrameCount());
    // The first processed media item corresponds to remuxing previous output video.
    assertThat(exportResult.processedInputs.get(0).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(0).videoDecoderName).isNull();
    // The second processed media item corresponds to processing remaining video.
    assertThat(exportResult.processedInputs.get(1).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(1).videoDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(1).mediaItem.clippingConfiguration.startPositionMs)
        .isGreaterThan(0);
    // The third processed media item corresponds to processing audio.
    assertThat(exportResult.processedInputs.get(2).audioDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(2).videoDecoderName).isNull();
    // The fourth processed media item corresponds to transmuxing processed video.
    assertThat(exportResult.processedInputs.get(3).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(3).videoDecoderName).isNull();
  }

  @Test
  public void resume_withSingleMediaItemAfterImmediateCancellation_restartsExport()
      throws Exception {
    String testId = "resume_withSingleMediaItemAfterImmediateCancellation_restartsExport";
    if (shouldSkipDevice(testId)) {
      return;
    }
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 1);
    Transformer transformer = new Transformer.Builder(context).build();
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              transformer.start(composition, firstOutputPath);
              transformer.cancel();
            });

    // Resume the export.
    ExportResult exportResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition, firstOutputPath)
            .exportResult;

    // The first export did not progress because of the immediate cancellation hence resuming
    // actually restarts the export.
    assertThat(exportResult.processedInputs).hasSize(1);
  }

  @Test
  public void resume_withSingleMediaItem_outputMatchesWithoutResume() throws Exception {
    String testId = "resume_withSingleMediaItem_outputMatchesWithoutResume";
    if (shouldSkipDevice(testId)) {
      return;
    }
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 1);
    // Export without resume.
    ExportResult exportResultWithoutResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition)
            .exportResult;
    // Export with resume.
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    // Resume the export.
    ExportResult exportResultWithResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath)
            .exportResult;

    assertThat(exportResultWithResume.processedInputs).hasSize(4);
    assertThat(exportResultWithResume.audioEncoderName)
        .isEqualTo(exportResultWithoutResume.audioEncoderName);
    assertThat(exportResultWithResume.videoEncoderName)
        .isEqualTo(exportResultWithoutResume.videoEncoderName);
    assertThat(exportResultWithResume.videoFrameCount)
        .isEqualTo(
            exportResultWithoutResume.videoFrameCount - getDeviceSpecificMissingFrameCount());
    // TODO: b/306595508 - Remove this expected difference once inconsistent behaviour of audio
    //  encoder is fixed.
    int maxDiffExpectedInDurationMs = 2;
    assertThat(exportResultWithResume.durationMs - exportResultWithoutResume.durationMs)
        .isLessThan(maxDiffExpectedInDurationMs);
  }

  @Test
  public void resume_withSingleMediaItemHavingClippingConfig_outputMatchesWithoutResume()
      throws Exception {
    String testId = "resume_withSingleMediaItemHavingClippingConfig_outputMatchesWithoutResume";
    if (shouldSkipDevice(testId)) {
      return;
    }
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 2_000L,
            /* clippingEndPositionMs= */ 13_000L,
            /* mediaItemsInSequence= */ 1);
    // Export without resume.
    ExportResult exportResultWithoutResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition)
            .exportResult;
    // Export with resume.
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    // Resume the export.
    ExportResult exportResultWithResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath)
            .exportResult;

    assertThat(exportResultWithResume.processedInputs).hasSize(4);
    assertThat(exportResultWithResume.audioEncoderName)
        .isEqualTo(exportResultWithoutResume.audioEncoderName);
    assertThat(exportResultWithResume.videoEncoderName)
        .isEqualTo(exportResultWithoutResume.videoEncoderName);
    assertThat(exportResultWithResume.videoFrameCount)
        .isEqualTo(
            exportResultWithoutResume.videoFrameCount - getDeviceSpecificMissingFrameCount());
    int maxDiffExpectedInDurationMs = 2;
    assertThat(exportResultWithResume.durationMs - exportResultWithoutResume.durationMs)
        .isLessThan(maxDiffExpectedInDurationMs);
  }

  @Test
  public void resume_withTwoMediaItems_outputMatchesExpected() throws Exception {
    String testId = "resume_withTwoMediaItems_outputMatchesExpected";
    if (shouldSkipDevice(testId)) {
      return;
    }
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 2);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);
    TransformerAndroidTestRunner testRunner =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build();

    ExportResult exportResult = testRunner.run(testId, composition, firstOutputPath).exportResult;

    assertThat(exportResult.processedInputs).hasSize(6);
    int expectedVideoFrameCount = MP4_ASSET_FRAME_COUNT * 2 - getDeviceSpecificMissingFrameCount();
    assertThat(exportResult.videoFrameCount).isEqualTo(expectedVideoFrameCount);
    // The first processed media item corresponds to remuxing previous output video.
    assertThat(exportResult.processedInputs.get(0).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(0).videoDecoderName).isNull();
    // The next two processed media item corresponds to processing remaining video.
    assertThat(exportResult.processedInputs.get(1).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(1).videoDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(1).mediaItem.clippingConfiguration.startPositionMs)
        .isGreaterThan(0);
    assertThat(exportResult.processedInputs.get(2).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(2).videoDecoderName).isNotEmpty();
    // The next two processed media item corresponds to processing audio.
    assertThat(exportResult.processedInputs.get(3).audioDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(3).videoDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(4).audioDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(4).videoDecoderName).isNull();
    // The last processed media item corresponds to transmuxing processed video.
    assertThat(exportResult.processedInputs.get(5).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(5).videoDecoderName).isNull();
  }

  @Test
  public void resume_withTwoMediaItems_outputMatchesWithoutResume() throws Exception {
    String testId = "resume_withTwoMediaItems_outputMatchesWithoutResume";
    if (shouldSkipDevice(testId)) {
      return;
    }
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 2);
    // Export without resume.
    ExportResult exportResultWithoutResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition)
            .exportResult;
    // Export with resume.
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);
    TransformerAndroidTestRunner testRunner =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build();

    ExportResult exportResultWithResume =
        testRunner.run(testId, composition, firstOutputPath).exportResult;

    assertThat(exportResultWithResume.processedInputs).hasSize(6);
    assertThat(exportResultWithResume.audioEncoderName)
        .isEqualTo(exportResultWithoutResume.audioEncoderName);
    assertThat(exportResultWithResume.videoEncoderName)
        .isEqualTo(exportResultWithoutResume.videoEncoderName);
    assertThat(exportResultWithResume.videoFrameCount)
        .isEqualTo(
            exportResultWithoutResume.videoFrameCount - getDeviceSpecificMissingFrameCount());
    int maxDiffExpectedInDurationMs = 2;
    assertThat(exportResultWithResume.durationMs - exportResultWithoutResume.durationMs)
        .isLessThan(maxDiffExpectedInDurationMs);
  }

  private static Composition buildSingleSequenceComposition(
      long clippingStartPositionMs, long clippingEndPositionMs, int mediaItemsInSequence) {
    SonicAudioProcessor sonic = new SonicAudioProcessor();
    sonic.setPitch(/* pitch= */ 2f);
    ImmutableList<AudioProcessor> audioEffects = ImmutableList.of(sonic);

    ImmutableList<Effect> videoEffects = ImmutableList.of(RgbFilter.createInvertedFilter());

    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clippingStartPositionMs)
                            .setEndPositionMs(clippingEndPositionMs)
                            .build())
                    .build())
            .setEffects(new Effects(audioEffects, videoEffects))
            .build();

    List<EditedMediaItem> editedMediaItemList = new ArrayList<>();
    while (mediaItemsInSequence-- > 0) {
      editedMediaItemList.add(editedMediaItem);
    }

    return new Composition.Builder(new EditedMediaItemSequence(editedMediaItemList)).build();
  }

  private static Transformer buildBlockingTransformer(FrameBlockingMuxer.Listener listener) {
    return new Transformer.Builder(getApplicationContext())
        .setMuxerFactory(new FrameBlockingMuxerFactory(listener))
        .build();
  }

  private static boolean shouldSkipDevice(String testId) throws Exception {
    // v26 emulators are not producing I-frames, due to which resuming export does not work as
    // expected.
    return AndroidTestUtil.skipAndLogIfFormatsUnsupported(
            getApplicationContext(),
            testId,
            /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
            /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT)
        || (Util.SDK_INT == 26 && Util.isRunningOnEmulator());
  }

  private static int getDeviceSpecificMissingFrameCount() {
    // TODO: b/307700189 - Remove this after investigating pause/resume behaviour with B-frames.
    return (Util.SDK_INT == 27
            && (Ascii.equalsIgnoreCase(Util.MODEL, "asus_x00td")
                || Ascii.equalsIgnoreCase(Util.MODEL, "tc77")))
        ? 1
        : 0;
  }

  private static final class FrameBlockingMuxerFactory implements Muxer.Factory {
    private final Muxer.Factory wrappedMuxerFactory;
    private final FrameBlockingMuxer.Listener listener;

    public FrameBlockingMuxerFactory(FrameBlockingMuxer.Listener listener) {
      this.wrappedMuxerFactory = new DefaultMuxer.Factory();
      this.listener = listener;
    }

    @Override
    public Muxer create(String path) throws Muxer.MuxerException {
      return new FrameBlockingMuxer(wrappedMuxerFactory.create(path), listener);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return wrappedMuxerFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  private static final class FrameBlockingMuxer implements Muxer {
    interface Listener {
      void onFrameBlocked();
    }

    private final Muxer wrappedMuxer;
    private final FrameBlockingMuxer.Listener listener;

    private boolean notifiedListener;
    private int videoTrackIndex;

    private FrameBlockingMuxer(Muxer wrappedMuxer, FrameBlockingMuxer.Listener listener) {
      this.wrappedMuxer = wrappedMuxer;
      this.listener = listener;
      videoTrackIndex = C.INDEX_UNSET;
    }

    @Override
    public int addTrack(Format format) throws MuxerException {
      int trackIndex = wrappedMuxer.addTrack(format);
      if (MimeTypes.isVideo(format.sampleMimeType)) {
        videoTrackIndex = trackIndex;
      }
      return trackIndex;
    }

    @Override
    public void writeSampleData(
        int trackIndex, ByteBuffer data, long presentationTimeUs, @C.BufferFlags int flags)
        throws MuxerException {
      if (trackIndex == videoTrackIndex
          && presentationTimeUs >= DEFAULT_PRESENTATION_TIME_US_TO_BLOCK_FRAME) {
        if (!notifiedListener) {
          listener.onFrameBlocked();
          notifiedListener = true;
        }
        return;
      }
      wrappedMuxer.writeSampleData(trackIndex, data, presentationTimeUs, flags);
    }

    @Override
    public void addMetadata(Metadata metadata) {
      wrappedMuxer.addMetadata(metadata);
    }

    @Override
    public void release(boolean forCancellation) throws MuxerException {
      wrappedMuxer.release(forCancellation);
    }

    @Override
    public long getMaxDelayBetweenSamplesMs() {
      return wrappedMuxer.getMaxDelayBetweenSamplesMs();
    }
  }
}
