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
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.FixedEvaluator;
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

import android.test.InstrumentationTestCase;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link DashChunkSource}.
 */
public class DashChunkSourceTest extends InstrumentationTestCase {

  private static final FormatEvaluator EVALUATOR = new FixedEvaluator();

  private static final long VOD_DURATION_MS = 30000;

  private static final long LIVE_SEGMENT_COUNT = 5;
  private static final long LIVE_SEGMENT_DURATION_MS = 1000;
  private static final long LIVE_DURATION_MS = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS;
  private static final long LIVE_TIMESHIFT_BUFFER_DEPTH_MS = LIVE_DURATION_MS;

  private static final int MULTI_PERIOD_COUNT = 2;

  private static final long MULTI_PERIOD_VOD_DURATION_MS = VOD_DURATION_MS * MULTI_PERIOD_COUNT;
  private static final long MULTI_PERIOD_LIVE_DURATION_MS = LIVE_DURATION_MS * MULTI_PERIOD_COUNT;

  private static final long AVAILABILITY_START_TIME_MS = 60000;
  private static final long AVAILABILITY_REALTIME_OFFSET_MS = 1000;
  private static final long AVAILABILITY_CURRENT_TIME_MS =
      AVAILABILITY_START_TIME_MS + LIVE_TIMESHIFT_BUFFER_DEPTH_MS - AVAILABILITY_REALTIME_OFFSET_MS;

  private static final int TALL_HEIGHT = 200;
  private static final int WIDE_WIDTH = 400;

  private static final Format REGULAR_VIDEO =
      new Format("1", "video/mp4", 480, 240, -1, -1, -1, 1000);
  private static final Format TALL_VIDEO =
      new Format("2", "video/mp4", 100, TALL_HEIGHT, -1, -1, -1, 1000);
  private static final Format WIDE_VIDEO =
      new Format("3", "video/mp4", WIDE_WIDTH, 50, -1, -1, -1, 1000);

  @Mock
  private DataSource mockDataSource;

  @Override
  public void setUp() throws Exception {
    TestUtil.setUpMockito(this);
  }

  public void testGetAvailableRangeOnVod() {
    DashChunkSource chunkSource = new DashChunkSource(generateVodMpd(), AdaptationSet.TYPE_VIDEO,
        null, null, mock(FormatEvaluator.class));
    chunkSource.enable(0);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, VOD_DURATION_MS * 1000);

    long[] seekRangeValuesMs = availableRange.getCurrentBoundsMs(null);
    assertEquals(0, seekRangeValuesMs[0]);
    assertEquals(VOD_DURATION_MS, seekRangeValuesMs[1]);
  }

  public void testGetAvailableRangeOnLiveWithTimelineNoEdgeLatency() {
    long liveEdgeLatency = 0;
    MediaPresentationDescription mpd = generateLiveMpdWithTimeline(0, 0, LIVE_DURATION_MS);
    DashChunkSource chunkSource = setupDashChunkSource(mpd, 0, liveEdgeLatency);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, LIVE_DURATION_MS * 1000);
  }

  public void testGetAvailableRangeOnLiveWithTimeline500msEdgeLatency() {
    long liveEdgeLatency = 500;
    MediaPresentationDescription mpd = generateLiveMpdWithTimeline(0, 0, LIVE_DURATION_MS);
    DashChunkSource chunkSource = setupDashChunkSource(mpd, 0, liveEdgeLatency);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, LIVE_DURATION_MS * 1000);
  }

  public void testGetAvailableRangeOnMultiPeriodVod() {
    DashChunkSource chunkSource = new DashChunkSource(generateMultiPeriodVodMpd(),
        AdaptationSet.TYPE_VIDEO, null, null, EVALUATOR);
    chunkSource.enable(0);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, MULTI_PERIOD_VOD_DURATION_MS * 1000);
  }

  public void testGetSeekRangeOnMultiPeriodLiveWithTimelineNoEdgeLatency() {
    long liveEdgeLatency = 0;
    MediaPresentationDescription mpd = generateMultiPeriodLiveMpdWithTimeline(0);
    DashChunkSource chunkSource = setupDashChunkSource(mpd, 0, liveEdgeLatency);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, MULTI_PERIOD_LIVE_DURATION_MS * 1000);
  }

  public void testGetSeekRangeOnMultiPeriodLiveWithTimeline500msEdgeLatency() {
    long liveEdgeLatency = 500;
    MediaPresentationDescription mpd = generateMultiPeriodLiveMpdWithTimeline(0);
    DashChunkSource chunkSource = setupDashChunkSource(mpd, 0, liveEdgeLatency);
    TimeRange availableRange = chunkSource.getAvailableRange();

    checkAvailableRange(availableRange, 0, MULTI_PERIOD_LIVE_DURATION_MS * 1000);
  }

  public void testSegmentIndexInitializationOnVod() {
    DashChunkSource chunkSource = new DashChunkSource(generateVodMpd(),
        AdaptationSet.TYPE_VIDEO, null, mockDataSource, EVALUATOR);
    chunkSource.enable(0);

    List<MediaChunk> queue = new ArrayList<>();
    ChunkOperationHolder out = new ChunkOperationHolder();

    // request first chunk; should get back initialization chunk
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertNotNull(out.chunk);
    assertNotNull(((InitializationChunk) out.chunk).dataSpec);
  }

  public void testSegmentRequestSequenceOnMultiPeriodLiveWithTimeline() {
    long liveEdgeLatency = 0;
    MediaPresentationDescription mpd = generateMultiPeriodLiveMpdWithTimeline(0);
    DashChunkSource chunkSource = setupDashChunkSource(mpd, 0, liveEdgeLatency);

    checkSegmentRequestSequenceOnMultiPeriodLive(chunkSource);
  }

  public void testSegmentRequestSequenceOnMultiPeriodLiveWithTemplate() {
    long liveEdgeLatency = 0;
    MediaPresentationDescription mpd = generateMultiPeriodLiveMpdWithTemplate(0);
    DashChunkSource chunkSource = setupDashChunkSource(mpd, 0, liveEdgeLatency,
        AVAILABILITY_CURRENT_TIME_MS + LIVE_DURATION_MS);

    checkSegmentRequestSequenceOnMultiPeriodLive(chunkSource);
  }


  public void testLiveEdgeNoLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 0;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeAlmostNoLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 1;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge500msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 500;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1000msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 1000;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1001msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 1001;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 3000;
    long chunkEndTimeMs = 4000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge2500msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 2500;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 2000;
    long chunkEndTimeMs = 3000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeVeryHighLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 10000;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = LIVE_DURATION_MS;
    long chunkStartTimeMs = 0;
    long chunkEndTimeMs = 1000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeNoLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 0;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeAlmostNoLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 1;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge500msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 500;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1000msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 1000;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1001msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 1001;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 6000;
    long chunkEndTimeMs = 7000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge2500msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 2500;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 5000;
    long chunkEndTimeMs = 6000;

    checkLiveTimelineConsistency(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeVeryHighLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 10000;
    long seekPositionMs = startTimeMs + LIVE_DURATION_MS - liveEdgeLatencyMs;
    long availableRangeStartMs = 3000;
    long availableRangeEndMs = 3000 + LIVE_DURATION_MS;
    long chunkStartTimeMs = 3000;
    long chunkEndTimeMs = 4000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, 0, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTimeline(0, startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        0, availableRangeEndMs, 0, 1000);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs,
        chunkEndTimeMs);
  }

  private static Representation generateVodRepresentation(long startTimeMs, long duration,
      Format format) {
    RangedUri rangedUri = new RangedUri("https://example.com/1.mp4", null, 0, 100);
    SingleSegmentBase segmentBase = new SingleSegmentBase(rangedUri, 1, 0,
        "https://example.com/1.mp4", 0, -1);
    return Representation.newInstance(startTimeMs, duration, null, 0, format, segmentBase);
  }

  private static Representation generateSegmentTimelineRepresentation(long segmentStartMs,
      long periodStartMs, long duration) {
    List<SegmentTimelineElement> segmentTimeline = new ArrayList<>();
    List<RangedUri> mediaSegments = new ArrayList<>();
    long segmentStartTimeMs = segmentStartMs;
    long byteStart = 0;
    for (int i = 0; i < (duration / LIVE_SEGMENT_DURATION_MS); i++) {
      segmentTimeline.add(new SegmentTimelineElement(segmentStartTimeMs, LIVE_SEGMENT_DURATION_MS));
      mediaSegments.add(new RangedUri("", "", byteStart, 500L));
      segmentStartTimeMs += LIVE_SEGMENT_DURATION_MS;
      byteStart += 500;
    }

    int startNumber = (int) ((periodStartMs + segmentStartMs) / LIVE_SEGMENT_DURATION_MS);
    MultiSegmentBase segmentBase = new SegmentList(null, 1000, 0,
        TrackRenderer.UNKNOWN_TIME_US, startNumber, TrackRenderer.UNKNOWN_TIME_US, segmentTimeline,
        mediaSegments);
    return Representation.newInstance(periodStartMs, TrackRenderer.UNKNOWN_TIME_US, null, 0,
        REGULAR_VIDEO, segmentBase);
  }

  private static Representation generateSegmentTemplateRepresentation(long periodStartMs,
      long periodDurationMs) {
    UrlTemplate initializationTemplate = null;
    UrlTemplate mediaTemplate = UrlTemplate.compile("$RepresentationID$/$Number$");
    int startNumber = (int) (periodStartMs / LIVE_SEGMENT_DURATION_MS);
    MultiSegmentBase segmentBase = new SegmentTemplate(null, 1000, 0,
        periodDurationMs, startNumber, LIVE_SEGMENT_DURATION_MS, null,
        initializationTemplate, mediaTemplate, "http://www.youtube.com");
    return Representation.newInstance(periodStartMs, periodDurationMs, null, 0, REGULAR_VIDEO,
        segmentBase);
  }

  private static MediaPresentationDescription generateMpd(boolean live,
      List<Representation> representations, boolean limitTimeshiftBuffer) {
    Representation firstRepresentation = representations.get(0);
    AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN, representations);
    Period period = new Period(null, firstRepresentation.periodStartMs,
        firstRepresentation.periodDurationMs, Collections.singletonList(adaptationSet));
    long duration = (live) ? TrackRenderer.UNKNOWN_TIME_US
        : firstRepresentation.periodDurationMs - firstRepresentation.periodStartMs;
    return new MediaPresentationDescription(AVAILABILITY_START_TIME_MS, duration, -1, live, -1,
        (limitTimeshiftBuffer) ? LIVE_TIMESHIFT_BUFFER_DEPTH_MS : -1, null, null,
        Collections.singletonList(period));
  }

  private static MediaPresentationDescription generateMultiPeriodMpd(boolean live,
      List<Period> periods, boolean limitTimeshiftBuffer) {
    Period firstPeriod = periods.get(0);
    Period lastPeriod = periods.get(periods.size() - 1);
    long duration = (live) ? TrackRenderer.UNKNOWN_TIME_US
        : (lastPeriod.startMs + lastPeriod.durationMs - firstPeriod.startMs);
    return new MediaPresentationDescription(AVAILABILITY_START_TIME_MS, duration, -1, live, -1,
        (limitTimeshiftBuffer) ? LIVE_TIMESHIFT_BUFFER_DEPTH_MS : -1,
        null, null, periods);
  }

  private static MediaPresentationDescription generateVodMpd() {
    List<Representation> representations = new ArrayList<>();

    representations.add(generateVodRepresentation(0, VOD_DURATION_MS, TALL_VIDEO));
    representations.add(generateVodRepresentation(0, VOD_DURATION_MS, WIDE_VIDEO));

    return generateMpd(false, representations, false);
  }

  private MediaPresentationDescription generateMultiPeriodVodMpd() {
    List<Period> periods = new ArrayList<>();
    long startTimeMs = 0;

    long duration = VOD_DURATION_MS;
    for (int i = 0; i < 2; i++) {
      Representation representation = generateVodRepresentation(startTimeMs, duration,
          REGULAR_VIDEO);
      AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN,
          Collections.singletonList(representation));
      Period period = new Period(null, startTimeMs, duration,
          Collections.singletonList(adaptationSet));
      periods.add(period);
      startTimeMs += duration;
    }

    return generateMultiPeriodMpd(false, periods, false);
  }

  private static MediaPresentationDescription generateLiveMpdWithTimeline(long segmentStartMs,
      long periodStartMs, long durationMs) {
    return generateMpd(true, Collections.singletonList(generateSegmentTimelineRepresentation(
        segmentStartMs, periodStartMs, durationMs)), false);
  }

  private static MediaPresentationDescription generateLiveMpdWithTemplate(long periodStartMs,
      long periodDurationMs, boolean limitTimeshiftBuffer) {
    return generateMpd(true, Collections.singletonList(generateSegmentTemplateRepresentation(
        periodStartMs, periodDurationMs)), limitTimeshiftBuffer);
  }

  private static MediaPresentationDescription generateMultiPeriodLiveMpdWithTimeline(
      long startTimeMs) {
    List<Period> periods = new ArrayList<>();

    for (int i = 0; i < MULTI_PERIOD_COUNT; i++) {
      Representation representation = generateSegmentTimelineRepresentation(0, startTimeMs,
          LIVE_DURATION_MS);
      AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN,
          Collections.singletonList(representation));
      long duration = (i < MULTI_PERIOD_COUNT - 1) ? MULTI_PERIOD_COUNT
          : TrackRenderer.END_OF_TRACK_US;
      Period period = new Period(null, startTimeMs, duration,
          Collections.singletonList(adaptationSet));
      periods.add(period);
      startTimeMs += LIVE_DURATION_MS;
    }

    return generateMultiPeriodMpd(true, periods, false);
  }

  private static MediaPresentationDescription generateMultiPeriodLiveMpdWithTemplate(
      long periodStartTimeMs) {
    List<Period> periods = new ArrayList<>();

    Representation representation1 = generateSegmentTemplateRepresentation(periodStartTimeMs,
        LIVE_DURATION_MS);
    AdaptationSet adaptationSet1 = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN,
        Collections.singletonList(representation1));
    Period period1 = new Period(null, periodStartTimeMs, LIVE_DURATION_MS,
        Collections.singletonList(adaptationSet1));
    periods.add(period1);

    periodStartTimeMs += LIVE_DURATION_MS;

    Representation representation2 = generateSegmentTemplateRepresentation(periodStartTimeMs,
        TrackRenderer.UNKNOWN_TIME_US);
    AdaptationSet adaptationSet2 = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN,
        Collections.singletonList(representation2));
    Period period2 = new Period(null, periodStartTimeMs, TrackRenderer.UNKNOWN_TIME_US,
        Collections.singletonList(adaptationSet2));
    periods.add(period2);

    return generateMultiPeriodMpd(true, periods, false);
  }

  private DashChunkSource setupDashChunkSource(MediaPresentationDescription mpd, long periodStartMs,
      long liveEdgeLatencyMs) {
    return setupDashChunkSource(mpd, periodStartMs, liveEdgeLatencyMs,
        AVAILABILITY_CURRENT_TIME_MS + periodStartMs);
  }

  @SuppressWarnings("unused")
  private DashChunkSource setupDashChunkSource(MediaPresentationDescription mpd, long periodStartMs,
      long liveEdgeLatencyMs, long nowUs) {
    @SuppressWarnings("unchecked")
    ManifestFetcher<MediaPresentationDescription> manifestFetcher = mock(ManifestFetcher.class);
    when(manifestFetcher.getManifest()).thenReturn(mpd);
    DashChunkSource chunkSource = new DashChunkSource(manifestFetcher, mpd,
        AdaptationSet.TYPE_VIDEO, null, mockDataSource, EVALUATOR,
        new FakeClock(nowUs), liveEdgeLatencyMs * 1000, AVAILABILITY_REALTIME_OFFSET_MS * 1000,
        false, null, null);
    chunkSource.enable(0);
    return chunkSource;
  }

  private void checkAvailableRange(TimeRange seekRange, long startTimeUs, long endTimeUs) {
    long[] seekRangeValuesUs = seekRange.getCurrentBoundsUs(null);
    assertEquals(startTimeUs, seekRangeValuesUs[0]);
    assertEquals(endTimeUs, seekRangeValuesUs[1]);
  }

  private void checkLiveEdgeLatency(DashChunkSource chunkSource, List<MediaChunk> queue,
      ChunkOperationHolder out, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    chunkSource.getChunkOperation(queue, seekPositionMs * 1000, 0, out);
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

  private void checkLiveEdgeLatency(MediaPresentationDescription mpd, long periodStartMs,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    DashChunkSource chunkSource = setupDashChunkSource(mpd, periodStartMs, liveEdgeLatencyMs);
    List<MediaChunk> queue = new ArrayList<>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs, availableRangeStartMs,
        availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private void checkLiveEdgeLatencyWithTimeline(long segmentStartMs, long periodStartMs,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    MediaPresentationDescription mpd = generateLiveMpdWithTimeline(segmentStartMs, periodStartMs,
        LIVE_DURATION_MS);
    checkLiveEdgeLatency(mpd, periodStartMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private void checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(long startTimeMs,
      long liveEdgeLatencyMs, long availablePositionMs, long availableRangeEndMs,
      long chunkStartTimeMs, long chunkEndTimeMs) {
    MediaPresentationDescription mpd = generateLiveMpdWithTemplate(0,
        TrackRenderer.UNKNOWN_TIME_US, false);
    checkLiveEdgeLatency(mpd, startTimeMs, liveEdgeLatencyMs, availablePositionMs, 0,
        availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private void checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(long startTimeMs,
      long liveEdgeLatencyMs, long seekPositionMs, long availableRangeStartMs,
      long availableRangeEndMs, long chunkStartTimeMs, long chunkEndTimeMs) {
    MediaPresentationDescription mpd = generateLiveMpdWithTemplate(0,
        TrackRenderer.UNKNOWN_TIME_US, true);
    checkLiveEdgeLatency(mpd, startTimeMs, liveEdgeLatencyMs, seekPositionMs, availableRangeStartMs,
        availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  private void checkLiveTimelineConsistency(long startTimeMs, long liveEdgeLatencyMs,
      long seekPositionMs, long availableRangeStartMs, long availableRangeEndMs,
      long chunkStartTimeMs, long chunkEndTimeMs) {
    // check the standard live-MPD style in which the period starts at time 0 and the segments
    // start at startTimeMs
    checkLiveEdgeLatencyWithTimeline(startTimeMs, 0, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    // check the other live-MPD style in which the segments start at time 0 and the period starts
    // at startTimeMs
    checkLiveEdgeLatencyWithTimeline(0, startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs,
        chunkEndTimeMs);
  }

  private void checkSegmentRequestSequenceOnMultiPeriodLive(DashChunkSource chunkSource) {
    List<MediaChunk> queue = new ArrayList<>();
    ChunkOperationHolder out = new ChunkOperationHolder();

    long seekPositionMs = 0;
    long availableRangeStartMs = 0;
    long availableRangeEndMs = MULTI_PERIOD_LIVE_DURATION_MS;
    long chunkStartTimeMs = 0;
    long chunkEndTimeMs = 1000;

    // request first chunk
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request second chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request third chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request fourth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request fifth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request sixth chunk; this is the first chunk in the 2nd period
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request seventh chunk;
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request eigth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request ninth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request tenth chunk
    chunkStartTimeMs += 1000;
    chunkEndTimeMs += 1000;
    out.chunk = null;
    checkLiveEdgeLatency(chunkSource, queue, out, seekPositionMs,
        availableRangeStartMs, availableRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    queue.add((MediaChunk) out.chunk);

    // request "eleventh" chunk; this chunk isn't available yet, so we should get null
    out.chunk = null;
    chunkSource.getChunkOperation(queue, seekPositionMs * 1000, 0, out);
    assertNull(out.chunk);
  }

}
