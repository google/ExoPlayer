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
package androidx.media3.exoplayer.source;

import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TimeOffsetMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public final class TimeOffsetMediaPeriodTest {

  @Test
  public void selectTracks_createsSampleStreamCorrectingOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(fakeMediaPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 0);
    FormatHolder formatHolder = new FormatHolder();
    DecoderInputBuffer inputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    ImmutableList.Builder<Integer> readResults = ImmutableList.builder();

    SampleStream sampleStream = selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);
    readResults.add(sampleStream.readData(formatHolder, inputBuffer, FLAG_REQUIRE_FORMAT));
    readResults.add(sampleStream.readData(formatHolder, inputBuffer, /* readFlags= */ 0));
    long readBufferTimeUs = inputBuffer.timeUs;
    readResults.add(sampleStream.readData(formatHolder, inputBuffer, /* readFlags= */ 0));
    boolean readEndOfStreamBuffer = inputBuffer.isEndOfStream();

    assertThat(readResults.build())
        .containsExactly(C.RESULT_FORMAT_READ, C.RESULT_BUFFER_READ, C.RESULT_BUFFER_READ);
    assertThat(readBufferTimeUs).isEqualTo(5000);
    assertThat(readEndOfStreamBuffer).isTrue();
  }

  @Test
  public void getBufferedPositionUs_returnsPositionWithOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 12000, C.BUFFER_FLAG_KEY_FRAME)));
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(fakeMediaPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 0);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    assertThat(timeOffsetMediaPeriod.getBufferedPositionUs()).isEqualTo(9000);
  }

  @Test
  public void getNextLoadPositionUs_returnsPositionWithOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 12000, C.BUFFER_FLAG_KEY_FRAME)));
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(fakeMediaPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 0);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    assertThat(timeOffsetMediaPeriod.getNextLoadPositionUs()).isEqualTo(9000);
  }

  @Test
  public void prepare_isForwardedWithTimeOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(spyPeriod, /* timeOffsetUs= */ -3000);

    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 1000);

    verify(spyPeriod).prepare(any(), eq(4000L));
  }

  @Test
  public void discardBuffer_isForwardedWithTimeOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(spyPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 1000);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    timeOffsetMediaPeriod.discardBuffer(/* positionUs= */ 1000, /* toKeyframe= */ true);

    verify(spyPeriod).discardBuffer(4000, true);
  }

  @Test
  public void seekTo_isForwardedWithTimeOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(spyPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 1000);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    long seekResultTimeUs = timeOffsetMediaPeriod.seekToUs(/* positionUs= */ 1000);

    verify(spyPeriod).seekToUs(4000);
    assertThat(seekResultTimeUs).isEqualTo(1000);
  }

  @Test
  public void getAdjustedSeekPosition_isForwardedWithTimeOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeMediaPeriod.setSeekToUsOffset(2000);
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(spyPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 1000);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    long adjustedSeekPositionUs =
        timeOffsetMediaPeriod.getAdjustedSeekPositionUs(
            /* positionUs= */ 1000, SeekParameters.DEFAULT);

    verify(spyPeriod).getAdjustedSeekPositionUs(4000, SeekParameters.DEFAULT);
    assertThat(adjustedSeekPositionUs).isEqualTo(3000); // = 4000 + 2000 - 3000
  }

  @Test
  public void continueLoading_isForwardedWithTimeOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(spyPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 1000);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    timeOffsetMediaPeriod.continueLoading(
        new LoadingInfo.Builder().setPlaybackPositionUs(1000).build());

    verify(spyPeriod)
        .continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(4000).build());
  }

  @Test
  public void reevaluateBuffer_isForwardedWithTimeOffset() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    TimeOffsetMediaPeriod timeOffsetMediaPeriod =
        new TimeOffsetMediaPeriod(spyPeriod, /* timeOffsetUs= */ -3000);
    prepareMediaPeriodSync(timeOffsetMediaPeriod, /* positionUs= */ 1000);
    selectTracksOnMediaPeriodAndTriggerLoading(timeOffsetMediaPeriod);

    timeOffsetMediaPeriod.reevaluateBuffer(/* positionUs= */ 1000);

    verify(spyPeriod).reevaluateBuffer(4000);
  }

  private static FakeMediaPeriod createFakeMediaPeriod(
      ImmutableList<FakeSampleStream.FakeSampleStreamItem> sampleStreamItems) {
    EventDispatcher eventDispatcher =
        new EventDispatcher()
            .withParameters(/* windowIndex= */ 0, new MediaPeriodId(/* periodUid= */ new Object()));
    return new FakeMediaPeriod(
        new TrackGroupArray(new TrackGroup(new Format.Builder().build())),
        new DefaultAllocator(/* trimOnReset= */ false, /* individualAllocationSize= */ 1024),
        (unusedFormat, unusedMediaPeriodId) -> sampleStreamItems,
        eventDispatcher,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher(),
        /* deferOnPrepared= */ false);
  }

  private static void prepareMediaPeriodSync(MediaPeriod mediaPeriod, long positionUs)
      throws Exception {
    CountDownLatch prepareCountDown = new CountDownLatch(1);
    mediaPeriod.prepare(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            prepareCountDown.countDown();
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {
            mediaPeriod.continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
          }
        },
        positionUs);
    prepareCountDown.await();
  }

  private static SampleStream selectTracksOnMediaPeriodAndTriggerLoading(MediaPeriod mediaPeriod) {
    ExoTrackSelection selection =
        new FixedTrackSelection(mediaPeriod.getTrackGroups().get(0), /* track= */ 0);
    SampleStream[] streams = new SampleStream[1];
    mediaPeriod.selectTracks(
        /* selections= */ new ExoTrackSelection[] {selection},
        /* mayRetainStreamFlags= */ new boolean[] {false},
        streams,
        /* streamResetFlags= */ new boolean[] {false},
        /* positionUs= */ 0);
    mediaPeriod.continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
    return streams[0];
  }
}
