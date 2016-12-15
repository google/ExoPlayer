/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.Handler;
import android.os.HandlerThread;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.TestCase;

/**
 * Unit test for {@link ExoPlayer}.
 */
public final class ExoPlayerTest extends TestCase {

  /**
   * For tests that rely on the player transitioning to the ended state, the duration in
   * milliseconds after starting the player before the test will time out. This is to catch cases
   * where the player under test is not making progress, in which case the test should fail.
   */
  private static final int TIMEOUT_MS = 10000;

  public void testPlayToEnd() throws Exception {
    PlayerWrapper playerWrapper = new PlayerWrapper();
    Format format = Format.createVideoSampleFormat(null, MimeTypes.VIDEO_H264, null,
        Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE, null, null);
    playerWrapper.setup(new SinglePeriodTimeline(0, false), new Object(), format);
    playerWrapper.blockUntilEndedOrError(TIMEOUT_MS);
  }

  /**
   * Wraps a player with its own handler thread.
   */
  private static final class PlayerWrapper implements ExoPlayer.EventListener {

    private final CountDownLatch endedCountDownLatch;
    private final HandlerThread playerThread;
    private final Handler handler;

    private Timeline expectedTimeline;
    private Object expectedManifest;
    private Format expectedFormat;
    private ExoPlayer player;
    private Exception exception;
    private boolean seenPositionDiscontinuity;

    public PlayerWrapper() {
      endedCountDownLatch = new CountDownLatch(1);
      playerThread = new HandlerThread("ExoPlayerTest thread");
      playerThread.start();
      handler = new Handler(playerThread.getLooper());
    }

    // Called on the test thread.

    public void blockUntilEndedOrError(long timeoutMs) throws Exception {
      if (!endedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        exception = new TimeoutException("Test playback timed out.");
      }
      release();

      // Throw any pending exception (from playback, timing out or releasing).
      if (exception != null) {
        throw exception;
      }
    }

    public void setup(final Timeline timeline, final Object manifest, final Format format) {
      expectedTimeline = timeline;
      expectedManifest = manifest;
      expectedFormat = format;
      handler.post(new Runnable() {
        @Override
        public void run() {
          try {
            Renderer fakeRenderer = new FakeVideoRenderer(expectedFormat);
            player = ExoPlayerFactory.newInstance(new Renderer[] {fakeRenderer},
                new DefaultTrackSelector());
            player.addListener(PlayerWrapper.this);
            player.setPlayWhenReady(true);
            player.prepare(new FakeMediaSource(timeline, manifest, format));
          } catch (Exception e) {
            handlePlayerException(e);
          }
        }
      });
    }

    public void release() throws InterruptedException {
      handler.post(new Runnable() {
        @Override
        public void run() {
          try {
            if (player != null) {
              player.release();
            }
          } catch (Exception e) {
            handlePlayerException(e);
          } finally {
            playerThread.quit();
          }
        }
      });
      playerThread.join();
    }

    private void handlePlayerException(Exception exception) {
      if (this.exception == null) {
        this.exception = exception;
      }
      endedCountDownLatch.countDown();
    }

    // ExoPlayer.EventListener implementation.

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == ExoPlayer.STATE_ENDED) {
        endedCountDownLatch.countDown();
      }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      assertEquals(expectedTimeline, timeline);
      assertEquals(expectedManifest, manifest);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups,
        TrackSelectionArray trackSelections) {
      assertEquals(new TrackGroupArray(new TrackGroup(expectedFormat)), trackGroups);
    }

    @Override
    public void onPlayerError(ExoPlaybackException exception) {
      this.exception = exception;
      endedCountDownLatch.countDown();
    }

    @Override
    public void onPositionDiscontinuity() {
      assertFalse(seenPositionDiscontinuity);
      assertEquals(0, player.getCurrentWindowIndex());
      assertEquals(0, player.getCurrentPeriodIndex());
      assertEquals(0, player.getCurrentPosition());
      assertEquals(0, player.getBufferedPosition());
      assertEquals(expectedTimeline, player.getCurrentTimeline());
      assertEquals(expectedManifest, player.getCurrentManifest());
      seenPositionDiscontinuity = true;
    }

  }

  /**
   * Fake {@link MediaSource} that provides a given timeline (which must have one period). Creating
   * the period will return a {@link FakeMediaPeriod}.
   */
  private static final class FakeMediaSource implements MediaSource {

    private final Timeline timeline;
    private final Object manifest;
    private final Format format;

    private FakeMediaPeriod mediaPeriod;
    private boolean preparedSource;
    private boolean releasedPeriod;
    private boolean releasedSource;

    public FakeMediaSource(Timeline timeline, Object manifest, Format format) {
      Assertions.checkArgument(timeline.getPeriodCount() == 1);
      this.timeline = timeline;
      this.manifest = manifest;
      this.format = format;
    }

    @Override
    public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
      assertFalse(preparedSource);
      preparedSource = true;
      listener.onSourceInfoRefreshed(timeline, manifest);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
      assertTrue(preparedSource);
    }

    @Override
    public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
      assertTrue(preparedSource);
      assertNull(mediaPeriod);
      assertFalse(releasedPeriod);
      assertFalse(releasedSource);
      assertEquals(0, index);
      assertEquals(0, positionUs);
      mediaPeriod = new FakeMediaPeriod(format);
      return mediaPeriod;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
      assertTrue(preparedSource);
      assertNotNull(this.mediaPeriod);
      assertFalse(releasedPeriod);
      assertFalse(releasedSource);
      assertEquals(this.mediaPeriod, mediaPeriod);
      this.mediaPeriod.release();
      releasedPeriod = true;
    }

    @Override
    public void releaseSource() {
      assertTrue(preparedSource);
      assertNotNull(this.mediaPeriod);
      assertTrue(releasedPeriod);
      assertFalse(releasedSource);
      releasedSource = true;
    }

  }

  /**
   * Fake {@link MediaPeriod} that provides one track with a given {@link Format}. Selecting that
   * track will give the player a {@link FakeSampleStream}.
   */
  private static final class FakeMediaPeriod implements MediaPeriod {

    private final TrackGroup trackGroup;

    private boolean preparedPeriod;

    public FakeMediaPeriod(Format format) {
      trackGroup = new TrackGroup(format);
    }

    public void release() {
      preparedPeriod = false;
    }

    @Override
    public void prepare(Callback callback) {
      assertFalse(preparedPeriod);
      preparedPeriod = true;
      callback.onPrepared(this);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
      assertTrue(preparedPeriod);
    }

    @Override
    public TrackGroupArray getTrackGroups() {
      assertTrue(preparedPeriod);
      return new TrackGroupArray(trackGroup);
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
        SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
      assertTrue(preparedPeriod);
      assertEquals(1, selections.length);
      assertEquals(1, mayRetainStreamFlags.length);
      assertEquals(1, streams.length);
      assertEquals(1, streamResetFlags.length);
      assertEquals(0, positionUs);
      if (streams[0] != null && (selections[0] == null || !mayRetainStreamFlags[0])) {
        streams[0] = null;
      }
      if (streams[0] == null && selections[0] != null) {
        FakeSampleStream stream = new FakeSampleStream(trackGroup.getFormat(0));
        assertEquals(trackGroup, selections[0].getTrackGroup());
        streams[0] = stream;
        streamResetFlags[0] = true;
      }
      return 0;
    }

    @Override
    public long readDiscontinuity() {
      assertTrue(preparedPeriod);
      return C.TIME_UNSET;
    }

    @Override
    public long getBufferedPositionUs() {
      assertTrue(preparedPeriod);
      return C.TIME_END_OF_SOURCE;
    }

    @Override
    public long seekToUs(long positionUs) {
      assertTrue(preparedPeriod);
      assertEquals(0, positionUs);
      return positionUs;
    }

    @Override
    public long getNextLoadPositionUs() {
      assertTrue(preparedPeriod);
      return 0;
    }

    @Override
    public boolean continueLoading(long positionUs) {
      assertTrue(preparedPeriod);
      return false;
    }

  }

  /**
   * Fake {@link SampleStream} that outputs a given {@link Format} then sets the end of stream flag
   * on its input buffer.
   */
  private static final class FakeSampleStream implements SampleStream {

    private final Format format;

    private boolean readFormat;
    private boolean readEndOfStream;

    public FakeSampleStream(Format format) {
      this.format = format;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
      Assertions.checkState(!readEndOfStream);
      if (readFormat) {
        buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
        readEndOfStream = true;
        return C.RESULT_BUFFER_READ;
      }
      formatHolder.format = format;
      readFormat = true;
      return C.RESULT_FORMAT_READ;
    }

    @Override
    public void maybeThrowError() throws IOException {
      // Do nothing.
    }

    @Override
    public void skipToKeyframeBefore(long timeUs) {
      // Do nothing.
    }

  }

  /**
   * Fake {@link Renderer} that supports any video format. The renderer verifies that it reads a
   * given {@link Format} then a buffer with the end of stream flag set.
   */
  private static final class FakeVideoRenderer extends BaseRenderer {

    private final Format expectedFormat;

    private boolean isEnded;

    public FakeVideoRenderer(Format expectedFormat) {
      super(C.TRACK_TYPE_VIDEO);
      Assertions.checkArgument(MimeTypes.isVideo(expectedFormat.sampleMimeType));
      this.expectedFormat = expectedFormat;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
      if (isEnded) {
        return;
      }

      // Verify the format matches the expected format.
      FormatHolder formatHolder = new FormatHolder();
      readSource(formatHolder, null);
      assertEquals(expectedFormat, formatHolder.format);

      // Verify that we get an end-of-stream buffer.
      DecoderInputBuffer buffer =
          new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
      readSource(null, buffer);
      assertTrue(buffer.isEndOfStream());
      isEnded = true;
    }

    @Override
    public boolean isReady() {
      return isEnded;
    }

    @Override
    public boolean isEnded() {
      return isEnded;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.isVideo(format.sampleMimeType) ? FORMAT_HANDLED : FORMAT_UNSUPPORTED_TYPE;
    }

  }

}
