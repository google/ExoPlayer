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

import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ClippingMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public class ClippingMediaPeriodTest {

  @Test
  public void fastLoadingStreamAfterFirstRead_canBeReadFully() throws Exception {
    TrackGroup trackGroup = new TrackGroup(new Format.Builder().build());
    // Set up MediaPeriod with no samples and only add samples after the first SampleStream read.
    FakeMediaPeriod mediaPeriod =
        new FakeMediaPeriod(
            new TrackGroupArray(trackGroup),
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* trackDataFactory= */ (format, mediaPeriodId) -> ImmutableList.of(),
            new MediaSourceEventListener.EventDispatcher()
                .withParameters(
                    /* windowIndex= */ 0,
                    new MediaSource.MediaPeriodId(/* periodUid= */ new Object())),
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* deferOnPrepared= */ false) {
          @Override
          protected FakeSampleStream createSampleStream(
              Allocator allocator,
              @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              Format initialFormat,
              List<FakeSampleStream.FakeSampleStreamItem> fakeSampleStreamItems) {
            return new FakeSampleStream(
                allocator,
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                initialFormat,
                /* fakeSampleStreamItems= */ ImmutableList.of()) {
              private boolean addedSamples = false;

              @Override
              public int readData(
                  FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
                int result = super.readData(formatHolder, buffer, readFlags);
                if (!addedSamples) {
                  append(
                      ImmutableList.of(
                          oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                          oneByteSample(/* timeUs= */ 200, C.BUFFER_FLAG_KEY_FRAME),
                          oneByteSample(/* timeUs= */ 400, C.BUFFER_FLAG_KEY_FRAME),
                          oneByteSample(/* timeUs= */ 600, C.BUFFER_FLAG_KEY_FRAME),
                          oneByteSample(/* timeUs= */ 800, C.BUFFER_FLAG_KEY_FRAME),
                          END_OF_STREAM_ITEM));
                  writeData(/* startPositionUs= */ 0);
                  addedSamples = true;
                }
                return result;
              }
            };
          }
        };
    ClippingMediaPeriod clippingMediaPeriod =
        new ClippingMediaPeriod(
            mediaPeriod,
            /* enableInitialDiscontinuity= */ true,
            /* startUs= */ 0,
            /* endUs= */ 500);
    AtomicBoolean periodPrepared = new AtomicBoolean();
    clippingMediaPeriod.prepare(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            periodPrepared.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {
            clippingMediaPeriod.continueLoading(
                new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
          }
        },
        /* positionUs= */ 0);
    RobolectricUtil.runMainLooperUntil(periodPrepared::get);
    SampleStream[] sampleStreams = new SampleStream[1];
    clippingMediaPeriod.selectTracks(
        new ExoTrackSelection[] {new FixedTrackSelection(trackGroup, /* track= */ 0)},
        /* mayRetainStreamFlags= */ new boolean[] {false},
        sampleStreams,
        /* streamResetFlags= */ new boolean[] {true},
        /* positionUs= */ 0);
    FormatHolder formatHolder = new FormatHolder();
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    ArrayList<Long> readSamples = new ArrayList<>();

    int result;
    do {
      result = sampleStreams[0].readData(formatHolder, buffer, /* readFlags= */ 0);
      if (result == C.RESULT_BUFFER_READ && !buffer.isEndOfStream()) {
        readSamples.add(buffer.timeUs);
      }
    } while (result != C.RESULT_BUFFER_READ || !buffer.isEndOfStream());

    assertThat(readSamples).containsExactly(0L, 200L, 400L).inOrder();
  }
}
