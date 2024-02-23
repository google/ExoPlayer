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
package androidx.media3.transformer;

import static androidx.media3.common.MimeTypes.AUDIO_AAC;
import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.common.MimeTypes.VIDEO_H265;
import static androidx.media3.transformer.MuxerWrapper.MUXER_MODE_DEFAULT;
import static androidx.media3.transformer.MuxerWrapper.MUXER_MODE_MUX_PARTIAL;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Unit tests for {@link MuxerWrapper}. */
@RunWith(AndroidJUnit4.class)
public class MuxerWrapperTest {
  private static final Format FAKE_VIDEO_TRACK_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1080)
          .setHeight(720)
          .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 4}))
          .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
          .build();
  private static final Format FAKE_AUDIO_TRACK_FORMAT =
      new Format.Builder()
          .setSampleMimeType(AUDIO_AAC)
          .setSampleRate(40000)
          .setChannelCount(2)
          .build();

  private static final ByteBuffer FAKE_SAMPLE = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public final TestName testName = new TestName();

  @Nullable private MuxerWrapper muxerWrapper;

  @After
  public void tearDown() throws Muxer.MuxerException {
    if (muxerWrapper != null) {
      // Release with reason cancellation so that underlying resources are always released.
      muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_CANCELLED);
    }
  }

  @Test
  public void setAdditionalRotationDegrees_sameRotationSetAfterTracksAdded_doesNotThrow()
      throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setAdditionalRotationDegrees(90);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.setAdditionalRotationDegrees(180);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
    muxerWrapper.setAdditionalRotationDegrees(180);
  }

  @Test
  public void setAdditionalRotationDegrees_differentRotationSetAfterTracksAdded_throws()
      throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setAdditionalRotationDegrees(90);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.setAdditionalRotationDegrees(180);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);

    assertThrows(IllegalStateException.class, () -> muxerWrapper.setAdditionalRotationDegrees(90));
  }

  @Test
  public void changeToAppendMode_afterDefaultMode_throws() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ false);

    assertThrows(IllegalStateException.class, muxerWrapper::changeToAppendMode);
  }

  @Test
  public void addTrackFormat_withSameVideoFormatInAppendMode_doesNotThrow() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);

    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);
    muxerWrapper.changeToAppendMode();
    muxerWrapper.setTrackCount(1);

    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
  }

  @Test
  public void addTrackFormat_withSameAudioFormatInAppendMode_doesNotThrow() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_AUDIO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);
    muxerWrapper.changeToAppendMode();
    muxerWrapper.setTrackCount(1);

    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
  }

  @Test
  public void addTrackFormat_withDifferentVideoFormatInAppendMode_throws() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);
    muxerWrapper.changeToAppendMode();
    muxerWrapper.setTrackCount(1);
    Format differentVideoFormat = FAKE_VIDEO_TRACK_FORMAT.buildUpon().setHeight(5000).build();

    assertThrows(
        IllegalArgumentException.class, () -> muxerWrapper.addTrackFormat(differentVideoFormat));
  }

  @Test
  public void addTrackFormat_withDifferentAudioFormatInAppendMode_throws() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_AUDIO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);
    muxerWrapper.changeToAppendMode();
    muxerWrapper.setTrackCount(1);
    Format differentAudioFormat = FAKE_AUDIO_TRACK_FORMAT.buildUpon().setSampleRate(48000).build();

    assertThrows(
        IllegalArgumentException.class, () -> muxerWrapper.addTrackFormat(differentAudioFormat));
  }

  @Test
  public void
      writeSample_dropSamplesBeforeFirstVideoSampleEnabled_rejectsAudioSamplesReceivedBeforeFirstVideoSample()
          throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ true);
    muxerWrapper.setTrackCount(2);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);

    assertThat(
            muxerWrapper.writeSample(
                C.TRACK_TYPE_AUDIO,
                FAKE_SAMPLE,
                /* isKeyFrame= */ true,
                /* presentationTimeUs= */ 0))
        .isFalse();
  }

  @Test
  public void
      writeSample_dropSamplesBeforeFirstVideoSampleEnabled_dropsAudioSamplesTimedBeforeFirstVideoSample()
          throws Exception {
    String testId = testName.getMethodName();
    Context context = ApplicationProvider.getApplicationContext();
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory();
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            muxerFactory,
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_DEFAULT,
            /* dropSamplesBeforeFirstVideoSample= */ true);
    muxerWrapper.setTrackCount(2);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);

    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 10);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 5);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 10);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 12);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 15);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 17);
    muxerWrapper.endTrack(C.TRACK_TYPE_AUDIO);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);
    muxerWrapper = null;

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(/* originalFileName= */ "testspecificdumps/" + testId));
  }

  @Test
  public void isEnded_afterPartialVideoMuxed_returnsTrue() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);

    assertThat(muxerWrapper.isEnded()).isTrue();
    muxerWrapper = null;
  }

  @Test
  public void isEnded_afterPartialAudioAndVideoMuxed_returnsTrue() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);

    muxerWrapper.setTrackCount(2);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);

    assertThat(muxerWrapper.isEnded()).isFalse();

    muxerWrapper.writeSample(
        C.TRACK_TYPE_AUDIO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_AUDIO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);

    assertThat(muxerWrapper.isEnded()).isTrue();
    muxerWrapper = null;
  }

  @Test
  public void isEnded_afterEnteringAppendMode_returnsFalse() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);
    muxerWrapper.changeToAppendMode();
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);

    assertThat(muxerWrapper.isEnded()).isFalse();
  }

  @Test
  public void isInitializationDataCompatible_h265_differentCSD_returnsFalse() {
    Format existingFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H265)
            .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 0}))
            .build();
    Format otherFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H265)
            .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 4}))
            .build();

    assertThat(MuxerWrapper.isInitializationDataCompatible(existingFormat, otherFormat)).isFalse();
  }

  @Test
  public void isInitializationDataCompatible_h265_matchingCSD_returnsTrue() {
    Format existingFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H265)
            .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 4}))
            .build();
    Format otherFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H265)
            .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 4}))
            .build();

    assertThat(MuxerWrapper.isInitializationDataCompatible(existingFormat, otherFormat)).isTrue();
  }

  @Test
  public void isInitializationDataCompatible_h264_matchingCSD_returnsTrue() {
    Format existingFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 4}))
            .build();
    Format otherFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(ImmutableList.of(new byte[] {1, 2, 3, 4}))
            .build();

    assertThat(MuxerWrapper.isInitializationDataCompatible(existingFormat, otherFormat)).isTrue();
  }

  @Test
  public void isInitializationDataCompatible_h264_ignoresLevel_returnsTrue() {
    Format existingFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(
                ImmutableList.of(new byte[] {0, 0, 0, 1, 103, 100, 0, 40}, new byte[] {0, 0, 0, 1}))
            .build();
    Format otherFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(
                ImmutableList.of(new byte[] {0, 0, 0, 1, 103, 100, 0, 41}, new byte[] {0, 0, 0, 1}))
            .build();

    assertThat(MuxerWrapper.isInitializationDataCompatible(existingFormat, otherFormat)).isTrue();
  }

  @Test
  public void isInitializationDataCompatible_h264_mismatchProfile_returnsFalse() {
    Format existingFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(
                ImmutableList.of(new byte[] {0, 0, 0, 1, 103, 110, 0, 41}, new byte[] {0, 0, 0, 1}))
            .build();
    Format otherFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(
                ImmutableList.of(new byte[] {0, 0, 0, 1, 103, 100, 0, 41}, new byte[] {0, 0, 0, 1}))
            .build();

    assertThat(MuxerWrapper.isInitializationDataCompatible(existingFormat, otherFormat)).isFalse();
  }

  @Test
  public void isInitializationDataCompatible_h264_missingMimeType_returnsFalse() {
    Format existingFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setInitializationData(
                ImmutableList.of(new byte[] {0, 0, 0, 1, 103, 100, 0, 40}, new byte[] {0, 0, 0, 1}))
            .build();
    Format otherFormat =
        new Format.Builder()
            .setInitializationData(
                ImmutableList.of(new byte[] {0, 0, 0, 1, 103, 100, 0, 41}, new byte[] {0, 0, 0, 1}))
            .build();

    assertThat(MuxerWrapper.isInitializationDataCompatible(existingFormat, otherFormat)).isFalse();
  }

  @Test
  public void release_withReasonCompletedInMuxPartialMode_doesNotReleaseResources()
      throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);

    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_COMPLETED);

    // Resources are not released and samples can be written in the append mode.
    muxerWrapper.changeToAppendMode();
    boolean sampleWritten =
        muxerWrapper.writeSample(
            C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 100);
    assertThat(sampleWritten).isTrue();
  }

  @Test
  public void release_withReleaseReasonCancelledInMuxPartialMode_releasesResources()
      throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);

    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_CANCELLED);

    // Resources are released and samples can not be written in the append mode.
    muxerWrapper.changeToAppendMode();
    boolean sampleWritten =
        muxerWrapper.writeSample(
            C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 100);
    assertThat(sampleWritten).isFalse();
  }

  @Test
  public void release_withReleaseReasonErrorInMuxPartialMode_releasesResources() throws Exception {
    muxerWrapper =
        new MuxerWrapper(
            temporaryFolder.newFile().getPath(),
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL,
            /* dropSamplesBeforeFirstVideoSample= */ false);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);

    muxerWrapper.release(MuxerWrapper.MUXER_RELEASE_REASON_ERROR);

    // Resources are released and samples can not be written in the append mode.
    muxerWrapper.changeToAppendMode();
    boolean sampleWritten =
        muxerWrapper.writeSample(
            C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 100);
    assertThat(sampleWritten).isFalse();
  }

  private static final class NoOpMuxerListenerImpl implements MuxerWrapper.Listener {

    @Override
    public void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount) {}

    @Override
    public void onEnded(long durationMs, long fileSizeBytes) {}

    @Override
    public void onError(ExportException exportException) {}
  }
}
