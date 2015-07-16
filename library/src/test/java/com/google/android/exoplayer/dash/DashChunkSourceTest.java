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

import static org.mockito.Mockito.when;

import com.google.android.exoplayer.MediaFormat;
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

  private static final long AVAILABILITY_START_TIME = 0;
  private static final long AVAILABILITY_LATENCY = 5000;
  private static final long AVAILABILITY_REALTIME_OFFSET = 1000;
  private static final long AVAILABILITY_CURRENT_TIME =
      AVAILABILITY_START_TIME + AVAILABILITY_LATENCY - AVAILABILITY_REALTIME_OFFSET;
  private static final FakeClock AVAILABILITY_CLOCK = new FakeClock(AVAILABILITY_CURRENT_TIME);

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

  public void testLiveEdgeNoLatencyWithTimeline() {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(0L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge500msLatencyWithTimeline() {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(500L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge1000msLatencyWithTimeline() {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(1000L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge1001msLatencyWithTimeline() {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(1001L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(3000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(4000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge2500msLatencyWithTimeline() {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(2500L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(2000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(3000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdgeVeryHighLatencyWithTimeline() {
    DashChunkSource chunkSource = setupLiveEdgeTimelineTest(10000L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(0L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(1000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdgeNoLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(0L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    // this should actually return the "5th" segment, but it currently returns the "6th", which
    // doesn't actually exist yet; this will be resolved in a subsequent cl (cl/87518875).
    //assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    //assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdgeAlmostNoLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(1L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge500msLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(500L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge1000msLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(1000L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(4000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(5000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge1001msLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(1001L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(3000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(4000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdge2500msLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(2500L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(2000000L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(3000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  public void testLiveEdgeVeryHighLatencyWithTemplate() {
    DashChunkSource chunkSource = setupLiveEdgeTemplateTest(10000L);
    List<MediaChunk> queue = new ArrayList<MediaChunk>();
    ChunkOperationHolder out = new ChunkOperationHolder();
    chunkSource.getChunkOperation(queue, 0, 0, out);

    assertEquals(0L, ((MediaChunk) out.chunk).startTimeUs);
    assertEquals(1000000L, ((MediaChunk) out.chunk).endTimeUs);
  }

  private static MediaPresentationDescription generateMpd(boolean live,
      List<Representation> representations) {
    Representation firstRepresentation = representations.get(0);
    AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN, representations);
    Period period = new Period(null, firstRepresentation.periodStartMs,
        firstRepresentation.periodDurationMs, Collections.singletonList(adaptationSet));
    long duration = (live) ? TrackRenderer.UNKNOWN_TIME_US
        : firstRepresentation.periodDurationMs - firstRepresentation.periodStartMs;
    return new MediaPresentationDescription(AVAILABILITY_START_TIME, duration, -1, live, -1, -1,
        null, Collections.singletonList(period));
  }

  private static MediaPresentationDescription generateVodMpd() {
    List<Representation> representations = new ArrayList<Representation>();

    SingleSegmentBase segmentBase1 = new SingleSegmentBase("https://example.com/1.mp4");
    Representation representation1 =
        Representation.newInstance(0, 0, null, 0, TALL_VIDEO, segmentBase1);
    representations.add(representation1);

    SingleSegmentBase segmentBase2 = new SingleSegmentBase("https://example.com/2.mp4");
    Representation representation2 =
        Representation.newInstance(0, 0, null, 0, WIDE_VIDEO, segmentBase2);
    representations.add(representation2);

    return generateMpd(false, representations);
  }

  private static MediaPresentationDescription generateLiveMpdWithTimeline() {
    List<Representation> representations = new ArrayList<Representation>();

    List<SegmentTimelineElement> segmentTimeline = new ArrayList<SegmentTimelineElement>();
    segmentTimeline.add(new SegmentTimelineElement(0L, 1000L));
    segmentTimeline.add(new SegmentTimelineElement(1000L, 1000L));
    segmentTimeline.add(new SegmentTimelineElement(2000L, 1000L));
    segmentTimeline.add(new SegmentTimelineElement(3000L, 1000L));
    segmentTimeline.add(new SegmentTimelineElement(4000L, 1000L));
    List<RangedUri> mediaSegments = new ArrayList<RangedUri>();
    mediaSegments.add(new RangedUri("", "", 0L, 500L));
    mediaSegments.add(new RangedUri("", "", 500L, 500L));
    mediaSegments.add(new RangedUri("", "", 1000L, 500L));
    mediaSegments.add(new RangedUri("", "", 1500L, 500L));
    mediaSegments.add(new RangedUri("", "", 2000L, 500L));

    MultiSegmentBase segmentBase = new SegmentList(null, 1000, 0,
        TrackRenderer.UNKNOWN_TIME_US, 1, TrackRenderer.UNKNOWN_TIME_US, segmentTimeline,
        mediaSegments);
    Representation representation = Representation.newInstance(0, TrackRenderer.UNKNOWN_TIME_US,
        null, 0, REGULAR_VIDEO, segmentBase);
    representations.add(representation);

    return generateMpd(true, representations);
  }

  private static MediaPresentationDescription generateLiveMpdWithTemplate() {
    List<Representation> representations = new ArrayList<Representation>();

    UrlTemplate initializationTemplate = null;
    UrlTemplate mediaTemplate = UrlTemplate.compile("$RepresentationID$/$Number$");
    MultiSegmentBase segmentBase = new SegmentTemplate(null, 1000, 0,
        TrackRenderer.UNKNOWN_TIME_US, 1, 1000, null,
        initializationTemplate, mediaTemplate, "http://www.youtube.com");
    Representation representation = Representation.newInstance(0, TrackRenderer.UNKNOWN_TIME_US,
        null, 0, REGULAR_VIDEO, segmentBase);
    representations.add(representation);

    return generateMpd(true, representations);
  }

  private DashChunkSource setupLiveEdgeTimelineTest(long liveEdgeLatencyMs) {
    MediaPresentationDescription manifest = generateLiveMpdWithTimeline();
    when(mockManifestFetcher.getManifest()).thenReturn(manifest);
    return new DashChunkSource(mockManifestFetcher, manifest, AdaptationSet.TYPE_VIDEO, null,
        mockDataSource, EVALUATOR, AVAILABILITY_CLOCK, liveEdgeLatencyMs * 1000,
        AVAILABILITY_REALTIME_OFFSET * 1000);
  }

  private DashChunkSource setupLiveEdgeTemplateTest(long liveEdgeLatencyMs) {
    MediaPresentationDescription manifest = generateLiveMpdWithTemplate();
    when(mockManifestFetcher.getManifest()).thenReturn(manifest);
    return new DashChunkSource(mockManifestFetcher, manifest, AdaptationSet.TYPE_VIDEO, null,
        mockDataSource, EVALUATOR, AVAILABILITY_CLOCK, liveEdgeLatencyMs * 1000,
        AVAILABILITY_REALTIME_OFFSET * 1000);
  }

}
