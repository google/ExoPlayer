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
package com.google.android.exoplayer.smoothstreaming;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.drm.DrmInitData.SchemeInitData;
import com.google.android.exoplayer.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.ProtectionElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Pair;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link SampleSource} for SmoothStreaming media.
 */
public final class SmoothStreamingSampleSource implements SampleSource {

  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  private static final int INITIALIZATION_VECTOR_SIZE = 8;

  private final DataSourceFactory dataSourceFactory;
  private final BandwidthMeter bandwidthMeter;
  private final Handler eventHandler;
  private final ChunkTrackStreamEventListener eventListener;
  private final LoadControl loadControl;
  private final ManifestFetcher<SmoothStreamingManifest> manifestFetcher;

  private boolean prepared;
  private long durationUs;
  private SmoothStreamingManifest currentManifest;
  private TrackEncryptionBox[] trackEncryptionBoxes;
  private DrmInitData.Mapped drmInitData;
  private TrackGroupArray trackGroups;
  private int[] trackGroupElementIndices;
  private boolean pendingReset;
  private long lastSeekPositionUs;

  private SmoothStreamingChunkSource[] chunkSources;
  private ChunkTrackStream[] trackStreams;

  public SmoothStreamingSampleSource(Uri uri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, Handler eventHandler,
      ChunkTrackStreamEventListener eventListener) {
    this.dataSourceFactory = dataSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;

    loadControl = new DefaultLoadControl(new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
    chunkSources = new SmoothStreamingChunkSource[0];
    trackStreams = new ChunkTrackStream[0];

    if (!Util.toLowerInvariant(uri.getLastPathSegment()).equals("manifest")) {
      uri = Uri.withAppendedPath(uri, "Manifest");
    }
    SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    manifestFetcher = new ManifestFetcher<>(uri, manifestDataSource, parser);
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }

    if (currentManifest == null) {
      currentManifest = manifestFetcher.getManifest();
      if (currentManifest == null) {
        manifestFetcher.maybeThrowError();
        manifestFetcher.requestRefresh();
        return false;
      }
    }

    durationUs = currentManifest.durationUs;
    buildTrackGroups(currentManifest);

    ProtectionElement protectionElement = currentManifest.protectionElement;
    if (protectionElement != null) {
      byte[] keyId = getProtectionElementKeyId(protectionElement.data);
      trackEncryptionBoxes = new TrackEncryptionBox[1];
      trackEncryptionBoxes[0] = new TrackEncryptionBox(true, INITIALIZATION_VECTOR_SIZE, keyId);
      drmInitData = new DrmInitData.Mapped();
      drmInitData.put(protectionElement.uuid,
          new SchemeInitData(MimeTypes.VIDEO_MP4, protectionElement.data));
    }

    prepared = true;
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);

    int newEnabledSourceCount = trackStreams.length + newSelections.size() - oldStreams.size();
    SmoothStreamingChunkSource[] newChunkSources =
        new SmoothStreamingChunkSource[newEnabledSourceCount];
    ChunkTrackStream[] newTrackStreams = new ChunkTrackStream[newEnabledSourceCount];
    int newEnabledSourceIndex = 0;

    // Iterate over currently enabled streams, either releasing them or adding them to the new list.
    for (int i = 0; i < trackStreams.length; i++) {
      ChunkTrackStream trackStream = trackStreams[i];
      if (oldStreams.contains(trackStream)) {
        chunkSources[i].release();
        trackStream.release();
      } else {
        newChunkSources[newEnabledSourceIndex] = chunkSources[i];
        newTrackStreams[newEnabledSourceIndex++] = trackStream;
      }
    }

    // Instantiate and return new streams.
    TrackStream[] streamsToReturn = new TrackStream[newSelections.size()];
    for (int i = 0; i < newSelections.size(); i++) {
      Pair<SmoothStreamingChunkSource, ChunkTrackStream> trackComponents =
          buildTrackStream(newSelections.get(i), positionUs);
      newChunkSources[newEnabledSourceIndex] = trackComponents.first;
      newTrackStreams[newEnabledSourceIndex++] = trackComponents.second;
      streamsToReturn[i] = trackComponents.second;
    }

    chunkSources = newChunkSources;
    trackStreams = newTrackStreams;
    return streamsToReturn;
  }

  @Override
  public void continueBuffering(long positionUs) {
    if (currentManifest.isLive) {
      SmoothStreamingManifest newManifest = manifestFetcher.getManifest();
      if (newManifest != currentManifest) {
        currentManifest = newManifest;
        for (SmoothStreamingChunkSource chunkSource : chunkSources) {
          chunkSource.updateManifest(newManifest);
        }
      }

      if (SystemClock.elapsedRealtime()
          > manifestFetcher.getManifestLoadStartTimestamp() + MINIMUM_MANIFEST_REFRESH_PERIOD_MS) {
        for (SmoothStreamingChunkSource chunkSource : chunkSources) {
          if (chunkSource.needManifestRefresh()) {
            manifestFetcher.requestRefresh();
            break;
          }
        }
      }
    }

    for (ChunkTrackStream trackStream : trackStreams) {
      trackStream.continueBuffering(positionUs);
    }
  }

  @Override
  public long readReset() {
    if (pendingReset) {
      pendingReset = false;
      for (ChunkTrackStream trackStream : trackStreams) {
        trackStream.setReadingEnabled(true);
      }
      return lastSeekPositionUs;
    }
    return C.UNSET_TIME_US;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (ChunkTrackStream trackStream : trackStreams) {
      long rendererBufferedPositionUs = trackStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    pendingReset = true;
    for (ChunkTrackStream trackStream : trackStreams) {
      trackStream.setReadingEnabled(false);
      trackStream.seekToUs(positionUs);
    }
  }

  @Override
  public void release() {
    manifestFetcher.release();
    for (SmoothStreamingChunkSource chunkSource : chunkSources) {
      chunkSource.release();
    }
    for (ChunkTrackStream trackStream : trackStreams) {
      trackStream.release();
    }
  }

  // Internal methods.

  private void buildTrackGroups(SmoothStreamingManifest manifest) {
    int trackGroupCount = 0;
    trackGroupElementIndices = new int[manifest.streamElements.length];
    TrackGroup[] trackGroupArray = new TrackGroup[manifest.streamElements.length];
    for (int i = 0; i < manifest.streamElements.length; i++) {
      StreamElement streamElement = manifest.streamElements[i];
      int streamElementType = streamElement.type;
      Format[] formats = streamElement.formats;
      if (formats.length > 0 && (streamElementType == C.TRACK_TYPE_AUDIO
          || streamElementType == C.TRACK_TYPE_VIDEO || streamElementType == C.TRACK_TYPE_TEXT)) {
        trackGroupElementIndices[trackGroupCount] = i;
        boolean adaptive = streamElementType == C.TRACK_TYPE_VIDEO;
        trackGroupArray[trackGroupCount++] = new TrackGroup(adaptive, formats);
      }
    }
    if (trackGroupCount < trackGroupArray.length) {
      trackGroupElementIndices = Arrays.copyOf(trackGroupElementIndices, trackGroupCount);
      trackGroupArray = Arrays.copyOf(trackGroupArray, trackGroupCount);
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
  }

  private Pair<SmoothStreamingChunkSource, ChunkTrackStream> buildTrackStream(
      TrackSelection selection, long positionUs) {
    int[] selectedTracks = selection.getTracks();
    FormatEvaluator adaptiveEvaluator = selectedTracks.length > 1
        ? new AdaptiveEvaluator(bandwidthMeter) : null;
    int streamElementIndex = trackGroupElementIndices[selection.group];
    StreamElement streamElement = currentManifest.streamElements[streamElementIndex];
    int streamElementType = streamElement.type;
    int bufferSize = Util.getDefaultBufferSize(streamElementType);
    DataSource dataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    SmoothStreamingChunkSource chunkSource = new SmoothStreamingChunkSource(currentManifest,
        streamElementIndex, trackGroups.get(selection.group), selectedTracks, dataSource,
        adaptiveEvaluator, trackEncryptionBoxes, drmInitData);
    ChunkTrackStream trackStream = new ChunkTrackStream(chunkSource, loadControl, bufferSize,
        positionUs, eventHandler, eventListener, streamElementType);
    return Pair.create(chunkSource, trackStream);
  }

  private static byte[] getProtectionElementKeyId(byte[] initData) {
    StringBuilder initDataStringBuilder = new StringBuilder();
    for (int i = 0; i < initData.length; i += 2) {
      initDataStringBuilder.append((char) initData[i]);
    }
    String initDataString = initDataStringBuilder.toString();
    String keyIdString = initDataString.substring(
        initDataString.indexOf("<KID>") + 5, initDataString.indexOf("</KID>"));
    byte[] keyId = Base64.decode(keyIdString, Base64.DEFAULT);
    swap(keyId, 0, 3);
    swap(keyId, 1, 2);
    swap(keyId, 4, 5);
    swap(keyId, 6, 7);
    return keyId;
  }

  private static void swap(byte[] data, int firstPosition, int secondPosition) {
    byte temp = data[firstPosition];
    data[firstPosition] = data[secondPosition];
    data[secondPosition] = temp;
  }

}
