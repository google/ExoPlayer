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

import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.transformer.MuxerWrapper.MUXER_MODE_MUX_PARTIAL_VIDEO;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link MuxerWrapper}. */
@RunWith(AndroidJUnit4.class)
public class MuxerWrapperTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
          .setSampleMimeType("audio/mp4a-latm")
          .setSampleRate(40000)
          .setChannelCount(2)
          .build();

  private static final ByteBuffer FAKE_SAMPLE = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});

  private String outputFilePath;

  @Before
  public void setUp() throws IOException {
    outputFilePath = temporaryFolder.newFile("output.mp4").getPath();
  }

  @Test
  public void changeToAppendVideoMode_afterDefaultMode_throws() {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MuxerWrapper.MUXER_MODE_DEFAULT);

    assertThrows(IllegalStateException.class, muxerWrapper::changeToAppendVideoMode);
  }

  @Test
  public void setTrackCount_toTwoInMuxPartialVideoMode_throws() {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);

    assertThrows(IllegalArgumentException.class, () -> muxerWrapper.setTrackCount(2));
  }

  @Test
  public void setTrackCount_toTwoInAppendVideoMode_throws() throws Exception {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.changeToAppendVideoMode();

    assertThrows(IllegalArgumentException.class, () -> muxerWrapper.setTrackCount(2));
  }

  @Test
  public void addTrackFormat_withAudioFormatInMuxPartialVideoMode_throws() {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);
    muxerWrapper.setTrackCount(1);

    assertThrows(
        IllegalArgumentException.class, () -> muxerWrapper.addTrackFormat(FAKE_AUDIO_TRACK_FORMAT));
  }

  @Test
  public void addTrackFormat_withSameVideoFormatInAppendVideoMode_doesNotThrow() throws Exception {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.changeToAppendVideoMode();
    muxerWrapper.setTrackCount(1);

    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
  }

  @Test
  public void addTrackFormat_withDifferentVideoFormatInAppendVideoMode_throws() throws Exception {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.changeToAppendVideoMode();
    muxerWrapper.setTrackCount(1);
    Format differentVideoFormat = FAKE_VIDEO_TRACK_FORMAT.buildUpon().setHeight(5000).build();

    assertThrows(
        IllegalArgumentException.class, () -> muxerWrapper.addTrackFormat(differentVideoFormat));
  }

  @Test
  public void isEnded_afterPartialVideoMuxed_returnsTrue() throws Exception {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);

    assertThat(muxerWrapper.isEnded()).isTrue();
  }

  @Test
  public void isEnded_afterStartingAppendVideo_returnsFalse() throws Exception {
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputFilePath,
            new DefaultMuxer.Factory(),
            new NoOpMuxerListenerImpl(),
            MUXER_MODE_MUX_PARTIAL_VIDEO);
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);
    muxerWrapper.writeSample(
        C.TRACK_TYPE_VIDEO, FAKE_SAMPLE, /* isKeyFrame= */ true, /* presentationTimeUs= */ 0);
    muxerWrapper.endTrack(C.TRACK_TYPE_VIDEO);
    muxerWrapper.changeToAppendVideoMode();
    muxerWrapper.setTrackCount(1);
    muxerWrapper.addTrackFormat(FAKE_VIDEO_TRACK_FORMAT);

    assertThat(muxerWrapper.isEnded()).isFalse();
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
