/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.dash;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.InitializationChunk;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.RangedUri;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.dash.mpd.SegmentBase.MultiSegmentBase;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentList;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentTemplate;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentTimelineElement;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer.dash.mpd.UrlTemplate;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.FakeClock;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.Util;

import android.test.InstrumentationTestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link DashChunkSource}.
 */
public class DashChunkSourceTest extends InstrumentationTestCase {

  private static final long VOD_DURATION_MS = 30000;

  private static final long LIVE_SEGMENT_COUNT = 5;
  private static final long LIVE_SEGMENT_DURATION_MS = 1000;
  private static final long LIVE_DURATION_MS = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS;
  private static final long LIVE_TIMESHIFT_BUFFER_DEPTH_MS = LIVE_DURATION_MS;

  private static final int MULTI_PERIOD_COUNT = 2;
  private static final long MULTI_PERIOD_VOD_DURATION_MS = VOD_DURATION_MS * MULTI_PERIOD_COUNT;
  private static final long MULTI_PERIOD_LIVE_DURATION_MS = LIVE_DURATION_MS * MULTI_PERIOD_COUNT;

  private static final long AVAILABILITY_START_TIME_MS = 60000;
  private static final long ELAPSED_REALTIME_OFFSET_MS = 1000;

  private static final int TALL_HEIGHT = 200;
  private static final int WIDE_WIDTH = 400;

  private static final Format REGULAR_VIDEO =
      new Format("1", "video/mp4", 480, 240, -1, -1, -1, 1000);
  private static final Format TALL_VIDEO =
      new Format("2", "video/mp4", 100, TALL_HEIGHT, -1, -1, -1, 1000);
  private static final Format WIDE_VIDEO =
      new Format("3", "video/mp4", WIDE_WIDTH, 50, -1, -1, -1, 1000);

  @Override
  public void setUp() throws Exception {
    TestUtil.setUpMockito(this);
  }

  public void testGetAvailableRangeOnVod() {
    DashChunkSource chunkSource = new DashChunkSource(buildVodMpd(),
        DefaultDashTrackSelector.newVideoInstance(null, false, false), null, null);
    chunkSource.prepare();
    chunkSource.enable(0);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, VOD_DURATION_MS * 1000);

    long[] seekRangeValuesMs = availableRange.getCurrentBoundsMs(null);
    assertEquals(0, seekRangeValuesMs[0]);
    assertEquals(VOD_DURATION_MS, seekRangeValuesMs[1]);
  }

  public void testGetAvailableRangeOnLiveWithTimeline() {
    MediaPresentationDescription mpd = buildLiveMpdWithTimeline(LIVE_DURATION_MS, 0);
    DashChunkSource chunkSource = buildDashChunkSource(mpd);
    TimeRange availableRange = chunkSource.getAvailableRange();
    checkAvailableRange(availableRange, 0, LIVE_DURATION_MS * 1000);
  }

  public void testGetAvailableRangeOnMultiPeriodVod() {
    DashChunkSource chunkSource = new DashChunkSource(buildMultiPeriodVodMpd(),
        DefaultDashTrackSelector.newVideoInstance(null, false, false), null, null);
    chunkSource.prepare();
    chunkSource.enable(0);
    TimeRange availableRange = chunkSource.getAvailableRange();
    checkAvailableRange(availableRange, 0, MULTI_PERIOD_VOD_DURATION_MS * 1000);
  }

  public void testGetSeekRangeOnMultiPeriodLiveWithTimeline() {
    MediaPresentationDescription mpd = buildMultiPeriodLiveMpdWithTimeline();
    DashChunkSource chunkSource = buildDashChunkSource(mpd);
    TimeRange availableRange = chunkSource.getAvailableRange();
    checkAvailableRange(availableRange, 0, MULTI_PERIOD_LIVE_DURATION_MS * 1000);
  }

  public void testSegmentIndexInitializationOnVod() {
    DashChunkSource chunkSource = new DashChunkSource(buildVodMpd(),
        DefaultDashTrackSelector.newVideoInstance(null, false, false), mock(DataSource.class),
        null);
    chunkSource.prepare();
    chunkSource.enable(0);

    List<MediaChunk> queue = new ArrayList<>();
    ChunkOperationHolder out = new ChunkOperationHolder();

    // request first chunk; should get back initialization chunk
    chunkSource.getChunkOperation(queue,  0, out);

    assertNotNull(out.chunk);
    assertNotNull(((InitializationChunk) out.chunk).dataSpec);
  }

  public void testSegmentRequestSequenceOnMultiPeriodLiveWithTimeline() {
    MediaPresentationDescription mpd = buildMultiPeriodLiveMpdWithTimeline();
    DashChunkSource chunkSource = buildDashChunkSource(mpd);
    checkSegmentRequestSequenceOnMultiPeriodLive(chunkSource);
  }

  public void testSegmentRequestSequenceOnMultiPeriodLiveWithTemplate() {
    MediaPresentationDescription mpd = buildMultiPeriodLiveMpdWithTemplate();
    DashChunkSource chunkSource = buildDashChunkSource(mpd);
    checkSegmentRequestSequenceOnMultiPeriodLive(chunkSource);
  }

  public void testLiveEdgeLatency() {
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long seekPositionMs = LIVE_DURATION_MS;

    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;
    // Test with 1-1000ms latency.
    long liveEdgeLatency = 1;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    liveEdgeLatency = 1000;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);

    chunkStartTimeMs = 3000;
    chunkEndTimeMs = 4000;
    // Test with 1001-2000ms latency.
    liveEdgeLatency = 1001;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    liveEdgeLatency = 2000;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);

    chunkStartTimeMs = 0;
    chunkEndTimeMs = 1000;
    // Test with 9001-10000 latency.
    liveEdgeLatency = 9001;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    liveEdgeLatency = 10000;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);

    // Test with 10001 latency. Seek position will be bounded to the first chunk.
    liveEdgeLatency = 10001;
    checkLiveEdgeConsistency(LIVE_DURATION_MS, 0, liveEdgeLatency, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  // Private methods.

  private static Representation buildVodRepresentation(Format format) {
    RangedUri rangedUri = new RangedUri("https://example.com/1.mp4", null, 0, 100);
    SingleSegmentBase segmentBase = new SingleSegmentBase(rangedUri, 1, 0,
        "https://example.com/1.mp4", 0, -1);
    return Representation.newInstance(null, 0, format, segmentBase);
  }

  private static Representation buildSegmentTimelineRepresentation(long timelineDurationMs,
      long timelineStartTimeMs) {
    List<SegmentTimelineElement> segmentTimeline = new ArrayList<>();
    List<RangedUri> mediaSegments = new ArrayList<>();
    long segmentStartTimeMs = timelineStartTimeMs;
    long byteStart = 0;
    // Create all but the last segment with LIVE_SEGMENT_DURATION_MS.
    int segmentCount = (int) Util.ceilDivide(timelineDurationMs, LIVE_SEGMENT_DURATION_MS);
    for (int i = 0; i < segmentCount - 1; i++) {
      segmentTimeline.add(new SegmentTimelineElement(segmentStartTimeMs, LIVE_SEGMENT_DURATION_MS));
      mediaSegments.add(new RangedUri("", "", byteStart, 500L));
      segmentStartTimeMs += LIVE_SEGMENT_DURATION_MS;
      byteStart += 500;
    }
    // The final segment duration is calculated so that the total duration is timelineDurationMs.
    long finalSegmentDurationMs = (timelineStartTimeMs + timelineDurationMs) - segmentStartTimeMs;
    segmentTimeline.add(new SegmentTimelineElement(segmentStartTimeMs, finalSegmentDurationMs));
    mediaSegments.add(new RangedUri("", "", byteStart, 500L));
    segmentStartTimeMs += finalSegmentDurationMs;
    byteStart += 500;
    // Construct the list.
    MultiSegmentBase segmentBase = new SegmentList(null, 1000, 0, 0, 0, segmentTimeline,
        mediaSegments);
    return Representation.newInstance(null, 0, REGULAR_VIDEO, segmentBase);
  }

  private static Representation buildSegmentTemplateRepresentation() {
    UrlTemplate initializationTemplate = null;
    UrlTemplate mediaTemplate = UrlTemplate.compile("$RepresentationID$/$Number$");
    MultiSegmentBase segmentBase = new SegmentTemplate(null, 1000, 0, 0, LIVE_SEGMENT_DURATION_MS,
        null, initializationTemplate, mediaTemplate, "http://www.youtube.com");
    return Representation.newInstance(null, 0, REGULAR_VIDEO, segmentBase);
  }

  private static MediaPresentationDescription buildMpd(long durationMs,
      List<Representation> representations, boolean live, boolean limitTimeshiftBuffer) {
    AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_VIDEO, representations);
    Period period = new Period(null, 0, Collections.singletonList(adaptationSet));
    return new MediaPresentationDescription(AVAILABILITY_START_TIME_MS, durationMs, -1, live, -1,
        (limitTimeshiftBuffer) ? LIVE_TIMESHIFT_BUFFER_DEPTH_MS : -1, null, null,
        Collections.singletonList(period));
  }

  private static MediaPresentationDescription buildMultiPeriodMpd(long durationMs,
      List<Period> periods, boolean live, boolean limitTimeshiftBuffer) {
    return new MediaPresentationDescription(AVAILABILITY_START_TIME_MS, durationMs, -1, live, -1,
        (limitTimeshiftBuffer) ? LIVE_TIMESHIFT_BUFFER_DEPTH_MS : -1,
        null, null, periods);
  }

  private static MediaPresentationDescription buildVodMpd() {
    List<Representation> representations = new ArrayList<>();
    representations.add(buildVodRepresentation(TALL_VIDEO));
    representations.add(buildVodRepresentation(WIDE_VIDEO));
    return buildMpd(VOD_DURATION_MS, representations, false, false);
  }

  private static MediaPresentationDescription buildMultiPeriodVodMpd() {
    List<Period> periods = new ArrayList<>();
    long timeMs = 0;
    long periodDurationMs = VOD_DURATION_MS;
    for (int i = 0; i < 2; i++) {
      Representation representation = buildVodRepresentation(REGULAR_VIDEO);
      AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_VIDEO,
          Collections.singletonList(representation));
      Period period = new Period(null, timeMs, Collections.singletonList(adaptationSet));
      periods.add(period);
      timeMs += periodDurationMs;
    }
    return buildMultiPeriodMpd(timeMs, periods, false, false);
  }

  private static MediaPresentationDescription buildLiveMpdWithTimeline(long durationMs,
      long timelineStartTimeMs) {
    Representation representation = buildSegmentTimelineRepresentation(
        durationMs - timelineStartTimeMs, timelineStartTimeMs);
    return buildMpd(durationMs, Collections.singletonList(representation), true, false);
  }

  private static MediaPresentationDescription buildLiveMpdWithTemplate(long durationMs,
      boolean limitTimeshiftBuffer) {
    Representation representation = buildSegmentTemplateRepresentation();
    return buildMpd(durationMs, Collections.singletonList(representation), true,
        limitTimeshiftBuffer);
  }

  private static MediaPresentationDescription buildMultiPeriodLiveMpdWithTimeline() {
    List<Period> periods = new ArrayList<>();
    long periodStartTimeMs = 0;
    long periodDurationMs = LIVE_DURATION_MS;
    for (int i = 0; i < MULTI_PERIOD_COUNT; i++) {
      Representation representation = buildSegmentTimelineRepresentation(LIVE_DURATION_MS, 0);
      AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_VIDEO,
          Collections.singletonList(representation));
      Period period = new Period(null, periodStartTimeMs, Collections.singletonList(adaptationSet));
      periods.add(period);
      periodStartTimeMs += periodDurationMs;
    }
    return buildMultiPeriodMpd(periodDurationMs, periods, true, false);
  }

  private static MediaPresentationDescription buildMultiPeriodLiveMpdWithTemplate() {
    List<Period> periods = new ArrayList<>();
    long periodStartTimeMs = 0;
    long periodDurationMs = LIVE_DURATION_MS;
    for (int i = 0; i < MULTI_PERIOD_COUNT; i++) {
      Representation representation = buildSegmentTemplateRepresentation();
      AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_VIDEO,
          Collections.singletonList(representation));
      Period period = new Period(null, periodStartTimeMs, Collections.singletonList(adaptationSet));
      periods.add(period);
      periodStartTimeMs += periodDurationMs;
    }
    return buildMultiPeriodMpd(MULTI_PERIOD_LIVE_DURATION_MS, periods, true, false);
  }

  private static DashChunkSource buildDashChunkSource(MediaPresentationDescription mpd) {
    return buildDashChunkSource(mpd, false, 0);
  }

  private static DashChunkSource buildDashChunkSource(MediaPresentationDescription mpd,
      boolean startAtLiveEdge, long liveEdgeLatencyMs) {
    @SuppressWarnings("unchecked")
    ManifestFetcher<MediaPresentationDescription> manifestFetcher = mock(ManifestFetcher.class);
    when(manifestFetcher.getManifest()).thenReturn(mpd);
    DashChunkSource chunkSource = new DashChunkSource(manifestFetcher, mpd,
        DefaultDashTrackSelector.newVideoInstance(null, false, false), mock(DataSource.class), null,
        new FakeClock(mpd.availabilityStartTime + mpd.duration - ELAPSED_REALTIME_OFFSET_MS),
        liveEdgeLatencyMs * 1000, ELAPSED_REALTIME_OFFSET_MS * 1000, startAtLiveEdge, null, null,
        0);
    chunkSource.prepare();
    chunkSource.enable(0);
    return chunkSource;
  }

  private static void checkAvailableRange(TimeRange seekRange, long startTimeUs, long endTimeUs) {
    long[] seekRangeValuesUs = seekRange.getCurrentBoundsUs(null);
    assertEquals(startTimeUs, seekRangeValuesUs[0]);
    assertEquals(endTimeUs, seekRangeValuesUs[1]);
  }

  private static void checkLiveEdgeConsistency(long durationMs, long timelineStartMs,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    checkLiveEdgeConsistencyWithTimeline(durationMs, timelineStartMs, liveEdgeLatencyMs,
        seekPositionMs, availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs,
        chunkEndTimeMs);
    checkLiveEdgeConsistencyWithTemplateAndUnlimitedTimeshift(durationMs, liveEdgeLatencyMs,
        seekPositionMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeConsistencyWithTemplateAndLimitedTimeshift(durationMs, liveEdgeLatencyMs,
        seekPositionMs, availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs,
        chunkEndTimeMs);
  }

  private static void checkLiveEdgeConsistencyWithTimeline(long durationMs, long timelineStartMs,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    MediaPresentationDescription mpd = buildLiveMpdWithTimeline(durationMs, timelineStartMs);
    checkLiveEdgeConsistency(mpd, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private static void checkLiveEdgeConsistencyWithTemplateAndUnlimitedTimeshift(long durationMs,
      long liveEdgeLatencyMs, long availablePositionMs, long availableRangeEndMs,
      long chunkStartTimeMs, long chunkEndTimeMs) {
    MediaPresentationDescription mpd = buildLiveMpdWithTemplate(durationMs, false);
    checkLiveEdgeConsistency(mpd, liveEdgeLatencyMs, availablePositionMs, 0,
        availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private static void checkLiveEdgeConsistencyWithTemplateAndLimitedTimeshift(long durationMs,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    MediaPresentationDescription mpd = buildLiveMpdWithTemplate(durationMs, true);
    checkLiveEdgeConsistency(mpd, liveEdgeLatencyMs, seekPositionMs, availableRangeStartMs,
        availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private static void checkLiveEdgeConsistency(MediaPresentationDescription mpd,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    DashChunkSource chunkSource = buildDashChunkSource(mpd, true, liveEdgeLatencyMs);
    List<MediaChunk> queue = new ArrayList<>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs, availableRangeStartMs,
        availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private static void checkLiveEdgeConsistency(DashChunkSource chunkSource, List<MediaChunk> queue,
      ChunkOperationHolder out, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    chunkSource.getChunkOperation(queue, seekPositionMs * 1000, out);
    TimeRange availableRange = chunkSource.getAvailableRange();
    checkAvailableRange(availableRange, availableRangeStartMs * 1000, availableRangeEndMs * 1000);
    if (chunkStartTimeMs < availableRangeEndMs) {
      assertNotNull(out.chunk);
      assertEquals(chunkStartTimeMs * 1000, ((MediaChunk) out.chunk).startTimeUs);
      assertEquals(chunkEndTimeMs * 1000, ((MediaChunk) out.chunk).endTimeUs);
    } else {
      assertNull(out.chunk);
    }
  }

  private static void checkSegmentRequestSequenceOnMultiPeriodLive(DashChunkSource chunkSource) {
    List<MediaChunk> queue = new ArrayList<>();
    ChunkOperationHolder out = new ChunkOperationHolder();

    long seekPositionMs = 0;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = MULTI_PERIOD_LIVE_DURATION_MS;
    long chunkStartTimeMs = 0;
    long chunkEndTimeMs = 1000;

    // request first chunk
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request second chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request third chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request fourth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request fifth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request sixth chunk; this is the first chunk in the 2nd period
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request seventh chunk;
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request eigth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request ninth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request tenth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeConsistency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request "eleventh" chunk; this chunk isn't available yet, so we should get null
    out.chunk = null;
    chunkSource.getChunkOperation(queue, seekPositionMs * 1000, out);
    assertNull(out.chunk);
  }

}
