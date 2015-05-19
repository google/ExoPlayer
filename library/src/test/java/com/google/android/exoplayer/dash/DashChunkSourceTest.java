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

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.FixedEvaluator;
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
import com.google.android.exoplayer.testutil.Util;
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

  private static final long VOD_DURATION = 30000;

  private static final long LIVE_SEGMENT_COUNT = 5;
  private static final long LIVE_SEGMENT_DURATION_MS = 1000;
  private static final long LIVE_TIMESHIFT_BUFFER_DEPTH_MS =
      LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS;

  private static final long AVAILABILITY_START_TIME_MS = 60000;
  private static final long AVAILABILITY_REALTIME_OFFSET_MS = 1000;
  private static final long AVAILABILITY_CURRENT_TIME_MS =
      AVAILABILITY_START_TIME_MS + LIVE_TIMESHIFT_BUFFER_DEPTH_MS - AVAILABILITY_REALTIME_OFFSET_MS;

  private static final long LIVE_SEEK_BEYOND_EDGE_MS = 60000;

  private static final int TALL_HEIGHT = 200;
  private static final int WIDE_WIDTH = 400;

  private static final Format REGULAR_VIDEO =
      new Format("1", "video/mp4", 480, 240, -1, -1, -1, 1000);
  private static final Format TALL_VIDEO =
      new Format("2", "video/mp4", 100, TALL_HEIGHT, -1, -1, -1, 1000);
  private static final Format WIDE_VIDEO =
      new Format("3", "video/mp4", WIDE_WIDTH, 50, -1, -1, -1, 1000);

  @Mock private DataSource mockDataSource;
  @Mock private ManifestFetcher<MediaPresentationDescription> mockManifestFetcher;

  @Override
  public void setUp() throws Exception {
    Util.setUpMockito(this);
  }

  public void testMaxVideoDimensions() {
    DashChunkSource chunkSource = new DashChunkSource(generateVodMpd(), AdaptationSet.TYPE_VIDEO,
        null, null, null);
    MediaFormat out = MediaFormat.createVideoFormat("video/h264", 1, 1, 1, 1, null);
    chunkSource.getMaxVideoDimensions(out);

    assertEquals(WIDE_WIDTH, out.getMaxVideoWidth());
    assertEquals(TALL_HEIGHT, out.getMaxVideoHeight());
  }

  public void testGetSeekRangeOnVod() {
    DashChunkSource chunkSource = new DashChunkSource(generateVodMpd(), AdaptationSet.TYPE_VIDEO,
        null, null, mock(FormatEvaluator.class));
    chunkSource.enable();
    TimeRange seekRange = chunkSource.getSeekRange();

    long[] seekRangeValuesUs = seekRange.getCurrentBoundsUs(null);
    assertEquals(0, seekRangeValuesUs[0]);
    assertEquals(VOD_DURATION * 1000, seekRangeValuesUs[1]);

    long[] seekRangeValuesMs = seekRange.getCurrentBoundsMs(null);
    assertEquals(0, seekRangeValuesMs[0]);
    assertEquals(VOD_DURATION, seekRangeValuesMs[1]);
  }

  public void testMaxVideoDimensionsLegacy() {
    SingleSegmentBase segmentBase1 = new SingleSegmentBase("https://example.com/1.mp4");
    Representation representation1 =
        Representation.newInstance(0, 0, null, 0, TALL_VIDEO, segmentBase1);

    SingleSegmentBase segmentBase2 = new SingleSegmentBase("https://example.com/2.mp4");
    Representation representation2 =
        Representation.newInstance(0, 0, null, 0, WIDE_VIDEO, segmentBase2);

    DashChunkSource chunkSource = new DashChunkSource(null, null, representation1, representation2);
    MediaFormat out = MediaFormat.createVideoFormat("video/h264", 1, 1, 1, 1, null);
    chunkSource.getMaxVideoDimensions(out);

    assertEquals(WIDE_WIDTH, out.getMaxVideoWidth());
    assertEquals(TALL_HEIGHT, out.getMaxVideoHeight());
  }

  public void testLiveEdgeNoLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 0;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeAlmostNoLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 1;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge500msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 500;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1000msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 1000;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 4000;
    long chunkEndTimeMs = 5000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1001msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 1001;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 3000;
    long chunkEndTimeMs = 4000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge2500msLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 2500;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 2000;
    long chunkEndTimeMs = 3000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeVeryHighLatency() {
    long startTimeMs = 0;
    long liveEdgeLatencyMs = 10000;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 0;
    long seekRangeEndMs = 0;
    long chunkStartTimeMs = 0;
    long chunkEndTimeMs = 1000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeNoLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 0;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000 + LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeAlmostNoLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 1;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000 + LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge500msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 500;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000 + LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1000msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 1000;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000 + LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 7000;
    long chunkEndTimeMs = 8000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge1001msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 1001;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000 + LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 6000;
    long chunkEndTimeMs = 7000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdge2500msLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 2500;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000 + LIVE_SEGMENT_COUNT * LIVE_SEGMENT_DURATION_MS - liveEdgeLatencyMs;
    long chunkStartTimeMs = 5000;
    long chunkEndTimeMs = 6000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
  }

  public void testLiveEdgeVeryHighLatencyInProgress() {
    long startTimeMs = 3000;
    long liveEdgeLatencyMs = 10000;
    long seekPositionMs = LIVE_SEEK_BEYOND_EDGE_MS;
    long seekRangeStartMs = 3000;
    long seekRangeEndMs = 3000;
    long chunkStartTimeMs = 3000;
    long chunkEndTimeMs = 4000;

    checkLiveEdgeLatencyWithTimeline(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
    checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, 0, 0, 1000);
    checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(startTimeMs, liveEdgeLatencyMs,
        seekPositionMs, seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs);
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
        (limitTimeshiftBuffer) ? LIVE_TIMESHIFT_BUFFER_DEPTH_MS : -1,
        null, Collections.singletonList(period));
  }

  private static MediaPresentationDescription generateVodMpd() {
    List<Representation> representations = new ArrayList<Representation>();

    SingleSegmentBase segmentBase1 = new SingleSegmentBase("https://example.com/1.mp4");
    Representation representation1 =
        Representation.newInstance(0, VOD_DURATION, null, 0, TALL_VIDEO, segmentBase1);
    representations.add(representation1);

    SingleSegmentBase segmentBase2 = new SingleSegmentBase("https://example.com/2.mp4");
    Representation representation2 =
        Representation.newInstance(0, VOD_DURATION, null, 0, WIDE_VIDEO, segmentBase2);
    representations.add(representation2);

    return generateMpd(false, representations, false);
  }

  private static MediaPresentationDescription generateLiveMpdWithTimeline(long startTime) {
    List<Representation> representations = new ArrayList<Representation>();

    List<SegmentTimelineElement> segmentTimeline = new ArrayList<SegmentTimelineElement>();
    List<RangedUri> mediaSegments = new ArrayList<RangedUri>();
    long byteStart = 0;
    for (int i = 0; i < LIVE_SEGMENT_COUNT; i++) {
      segmentTimeline.add(new SegmentTimelineElement(startTime, LIVE_SEGMENT_DURATION_MS));
      mediaSegments.add(new RangedUri("", "", byteStart, 500L));
      startTime += LIVE_SEGMENT_DURATION_MS;
      byteStart += 500;
    }

    MultiSegmentBase segmentBase = new SegmentList(null, 1000, 0,
        TrackRenderer.UNKNOWN_TIME_US, 0, TrackRenderer.UNKNOWN_TIME_US, segmentTimeline,
        mediaSegments);
    Representation representation = Representation.newInstance(startTime,
        TrackRenderer.UNKNOWN_TIME_US, null, 0, REGULAR_VIDEO, segmentBase);
    representations.add(representation);

    return generateMpd(true, representations, false);
  }

  private static MediaPresentationDescription generateLiveMpdWithTemplate(
      boolean limitTimeshiftBuffer) {
    List<Representation> representations = new ArrayList<Representation>();

    UrlTemplate initializationTemplate = null;
    UrlTemplate mediaTemplate = UrlTemplate.compile("$RepresentationID$/$Number$");
    MultiSegmentBase segmentBase = new SegmentTemplate(null, 1000, 0,
        TrackRenderer.UNKNOWN_TIME_US, 0, LIVE_SEGMENT_DURATION_MS, null,
        initializationTemplate, mediaTemplate, "http://www.youtube.com");
    Representation representation = Representation.newInstance(0, TrackRenderer.UNKNOWN_TIME_US,
        null, 0, REGULAR_VIDEO, segmentBase);
    representations.add(representation);

    return generateMpd(true, representations, limitTimeshiftBuffer);
  }

  private DashChunkSource setupLiveEdgeTimelineTest(long startTime, long liveEdgeLatencyMs) {
    MediaPresentationDescription manifest = generateLiveMpdWithTimeline(startTime);
    when(mockManifestFetcher.getManifest()).thenReturn(manifest);
    DashChunkSource chunkSource = new DashChunkSource(mockManifestFetcher, manifest,
        AdaptationSet.TYPE_VIDEO, null, mockDataSource, EVALUATOR,
        new FakeClock(AVAILABILITY_CURRENT_TIME_MS + startTime), liveEdgeLatencyMs * 1000,
        AVAILABILITY_REALTIME_OFFSET_MS * 1000, null, null);
    chunkSource.enable();
    return chunkSource;
  }

  private DashChunkSource setupLiveEdgeTemplateTest(long startTime, long liveEdgeLatencyMs,
      boolean limitTimeshiftBuffer) {
    MediaPresentationDescription manifest = generateLiveMpdWithTemplate(limitTimeshiftBuffer);
    when(mockManifestFetcher.getManifest()).thenReturn(manifest);
    DashChunkSource chunkSource = new DashChunkSource(mockManifestFetcher, manifest,
        AdaptationSet.TYPE_VIDEO, null, mockDataSource, EVALUATOR,
        new FakeClock(AVAILABILITY_CURRENT_TIME_MS + startTime), liveEdgeLatencyMs * 1000,
        AVAILABILITY_REALTIME_OFFSET_MS * 1000, null, null);
    chunkSource.enable();
    return chunkSource;
  }

  private void checkLiveEdgeLatencyWithTimeline(long startTimeMs, long liveEdgeLatencyMs,
      long seekPositionMs, long seekRangeStartMs, long seekRangeEndMs, long chunkStartTimeMs,
      long chunkEndTimeMs) {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(startTimeMs, liveEdgeLatencyMs);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, seekPositionMs * 1000, 0, out);
    TimeRange seekRange = chunkSource.getSeekRange();

    assertNotNull(out.chunk);
    long[] seekRangeValuesUs = seekRange.getCurrentBoundsUs(null);
    assertEquals(seekRangeStartMs * 1000, seekRangeValuesUs[0]);
    assertEquals(seekRangeEndMs * 1000, seekRangeValuesUs[1]);
    assertEquals(chunkStartTimeMs * 1000, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(chunkEndTimeMs * 1000, ((MediaChunk) out.chunk).endTimeUs);
  }

  private void checkLiveEdgeLatencyWithTemplate(long startTimeMs, long liveEdgeLatencyMs,
      long seekPositionMs, long seekRangeStartMs, long seekRangeEndMs, long chunkStartTimeMs,
      long chunkEndTimeMs, boolean limitTimeshiftBuffer) {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(startTimeMs, liveEdgeLatencyMs,
        limitTimeshiftBuffer);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, seekPositionMs * 1000, 0, out);
    TimeRange seekRange = chunkSource.getSeekRange();

    assertNotNull(out.chunk);
    long[] seekRangeValuesUs = seekRange.getCurrentBoundsUs(null);
    assertEquals(seekRangeStartMs * 1000, seekRangeValuesUs[0]);
    assertEquals(seekRangeEndMs * 1000, seekRangeValuesUs[1]);
    assertEquals(chunkStartTimeMs * 1000, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(chunkEndTimeMs * 1000, ((MediaChunk) out.chunk).endTimeUs);
  }

  private void checkLiveEdgeLatencyWithTemplateAndUnlimitedTimeshift(long startTimeMs,
      long liveEdgeLatencyMs, long seekPositionMs, long seekRangeEndMs,
      long chunkStartTimeMs, long chunkEndTimeMs) {
    checkLiveEdgeLatencyWithTemplate(startTimeMs, liveEdgeLatencyMs, seekPositionMs, 0,
        seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs, false);
  }

  private void checkLiveEdgeLatencyWithTemplateAndLimitedTimeshift(long startTimeMs,
      long liveEdgeLatencyMs, long seekPositionMs, long seekRangeStartMs, long seekRangeEndMs,
      long chunkStartTimeMs, long chunkEndTimeMs) {
    checkLiveEdgeLatencyWithTemplate(startTimeMs, liveEdgeLatencyMs, seekPositionMs,
        seekRangeStartMs, seekRangeEndMs, chunkStartTimeMs, chunkEndTimeMs, true);
  }

}
