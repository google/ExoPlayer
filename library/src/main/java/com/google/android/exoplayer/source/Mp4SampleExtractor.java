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
package com.google.android.exoplayer.source;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.mp4.Atom;
import com.google.android.exoplayer.mp4.Atom.ContainerAtom;
import com.google.android.exoplayer.mp4.CommonMp4AtomParsers;
import com.google.android.exoplayer.mp4.Mp4TrackSampleTable;
import com.google.android.exoplayer.mp4.Mp4Util;
import com.google.android.exoplayer.mp4.Track;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.BufferedNonBlockingInputStream;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceStream;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Extracts data from a {@link DataSpec} in unfragmented MP4 format (ISO 14496-12).
 */
public final class Mp4SampleExtractor implements SampleExtractor, Loader.Callback {

  private static final String TAG = "Mp4SampleExtractor";
  private static final String LOADER_THREAD_NAME = "Mp4SampleExtractor";

  // Reading results
  private static final int RESULT_NEED_MORE_DATA = 1;
  private static final int RESULT_END_OF_STREAM = 2;

  // Parser states
  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;

  /** Set of atom types that contain data to be parsed. */
  private static final Set<Integer> LEAF_ATOM_TYPES = getAtomTypeSet(
      Atom.TYPE_mdhd, Atom.TYPE_mvhd, Atom.TYPE_hdlr, Atom.TYPE_vmhd, Atom.TYPE_smhd,
      Atom.TYPE_stsd, Atom.TYPE_avc1, Atom.TYPE_avcC, Atom.TYPE_mp4a, Atom.TYPE_esds,
      Atom.TYPE_stts, Atom.TYPE_stss, Atom.TYPE_ctts, Atom.TYPE_stsc, Atom.TYPE_stsz,
      Atom.TYPE_stco, Atom.TYPE_co64, Atom.TYPE_tkhd);

  /** Set of atom types that contain other atoms that need to be parsed. */
  private static final Set<Integer> CONTAINER_TYPES = getAtomTypeSet(
      Atom.TYPE_moov, Atom.TYPE_trak, Atom.TYPE_mdia, Atom.TYPE_minf, Atom.TYPE_stbl);

  /** Default number of times to retry loading data prior to failing. */
  private static final int DEFAULT_LOADABLE_RETRY_COUNT = 3;

  private final DataSource dataSource;
  private final DataSpec dataSpec;

  private final int readAheadAllocationSize;
  private final int reloadMinimumSeekDistance;
  private final int maximumTrackSampleInterval;
  private final int loadRetryCount;

  private final BufferPool bufferPool;
  private final Loader loader;
  private final ParsableByteArray atomHeader;
  private final Stack<Atom.ContainerAtom> containerAtoms;

  private DataSourceStream dataSourceStream;
  private BufferedNonBlockingInputStream inputStream;
  private long inputStreamOffset;
  private long rootAtomBytesRead;
  private boolean loadCompleted;

  private int parserState;
  private int atomBytesRead;
  private int atomType;
  private long atomSize;
  private ParsableByteArray atomData;

  private boolean prepared;

  private int loadErrorCount;

  private Mp4Track[] tracks;

  /** An exception from {@link #inputStream}'s callbacks, or {@code null} if there was no error. */
  private IOException lastLoadError;
  private long loadErrorPosition;

  /** If handling a call to {@link #seekTo}, the new required stream offset, or -1 otherwise. */
  private long pendingSeekPosition;
  /** If the input stream is being reopened at a new position, the new offset, or -1 otherwise. */
  private long pendingLoadPosition;

  /**
   * Creates a new sample extractor for reading {@code dataSource} and {@code dataSpec} as an
   * unfragmented MP4 file with default settings.
   *
   * <p>The default settings read ahead by 5 MiB, handle maximum offsets between samples at the same
   * timestamp in different tracks of 3 MiB and restart loading when seeking forward by >= 256 KiB.
   *
   * @param dataSource Data source used to read from {@code dataSpec}.
   * @param dataSpec Data specification specifying what to read.
   */
  public Mp4SampleExtractor(DataSource dataSource, DataSpec dataSpec) {
    this(dataSource, dataSpec, 5 * 1024 * 1024, 3 * 1024 * 1024, 256 * 1024,
        DEFAULT_LOADABLE_RETRY_COUNT);
  }

  /**
   * Creates a new sample extractor for reading {@code dataSource} and {@code dataSpec} as an
   * unfragmented MP4 file.
   *
   * @param dataSource Data source used to read from {@code dataSpec}.
   * @param dataSpec Data specification specifying what to read.
   * @param readAheadAllocationSize Size of the allocation that buffers the stream, in bytes. The
   *     value must exceed the maximum sample size, so that a sample can be read in its entirety.
   * @param maximumTrackSampleInterval Size of the buffer that handles reading from any selected
   *     track. The value should be chosen so that the buffer is as big as the interval in bytes
   *     between the start of the earliest and the end of the latest sample required to render media
   *     from all selected tracks, at any timestamp in the data source.
   * @param reloadMinimumSeekDistance Determines when {@code dataSource} is reopened while seeking:
   *     if the number of bytes between the current position and the new position is greater than or
   *     equal to this value, or the new position is before the current position, loading will
   *     restart. The value should be set to the number of bytes that can be loaded/consumed from an
   *     existing connection in the time it takes to start a new connection.
   * @param loadableRetryCount The number of times to retry loading if an error occurs.
   */
  public Mp4SampleExtractor(DataSource dataSource, DataSpec dataSpec, int readAheadAllocationSize,
      int maximumTrackSampleInterval, int reloadMinimumSeekDistance, int loadableRetryCount) {
    // TODO: Handle minimumTrackSampleInterval specified in time not bytes.
    this.dataSource = Assertions.checkNotNull(dataSource);
    this.dataSpec = Assertions.checkNotNull(dataSpec);
    this.readAheadAllocationSize = readAheadAllocationSize;
    this.maximumTrackSampleInterval = maximumTrackSampleInterval;
    this.reloadMinimumSeekDistance = reloadMinimumSeekDistance;
    this.loadRetryCount = loadableRetryCount;

    // TODO: Implement Allocator here so it is possible to check there is only one buffer at a time.
    bufferPool = new BufferPool(readAheadAllocationSize);
    loader = new Loader(LOADER_THREAD_NAME);
    atomHeader = new ParsableByteArray(Mp4Util.LONG_ATOM_HEADER_SIZE);
    containerAtoms = new Stack<Atom.ContainerAtom>();

    parserState = STATE_READING_ATOM_HEADER;
    pendingLoadPosition = -1;
    pendingSeekPosition = -1;
    loadErrorPosition = -1;
  }

  @Override
  public boolean prepare() throws IOException {
    if (inputStream == null) {
      loadFromOffset(0L);
    }

    if (!prepared) {
      if (readHeaders() && !prepared) {
        throw new IOException("moov atom not found.");
      }

      if (!prepared) {
        maybeThrowLoadError();
      }
    }

    return prepared;
  }

  @Override
  public void selectTrack(int trackIndex) {
    Assertions.checkState(prepared);

    if (tracks[trackIndex].selected) {
      return;
    }
    tracks[trackIndex].selected = true;

    // Get the timestamp of the earliest currently-selected sample.
    int earliestSampleTrackIndex = getTrackIndexOfEarliestCurrentSample();
    if (earliestSampleTrackIndex == Mp4Util.NO_TRACK) {
      tracks[trackIndex].sampleIndex = 0;
      return;
    }
    if (earliestSampleTrackIndex == Mp4Util.NO_SAMPLE) {
      tracks[trackIndex].sampleIndex = Mp4Util.NO_SAMPLE;
      return;
    }
    long timestampUs =
        tracks[earliestSampleTrackIndex].sampleTable.timestampsUs[earliestSampleTrackIndex];

    // Find the latest sync sample in the new track that has an earlier or equal timestamp.
    tracks[trackIndex].sampleIndex =
        tracks[trackIndex].sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timestampUs);
  }

  @Override
  public void deselectTrack(int trackIndex) {
    Assertions.checkState(prepared);

    tracks[trackIndex].selected = false;
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(prepared);

    if (pendingLoadPosition != -1) {
      return TrackRenderer.UNKNOWN_TIME_US;
    }

    if (loadCompleted) {
      return TrackRenderer.END_OF_TRACK_US;
    }

    // Get the absolute position to which there is data buffered.
    long bufferedPosition =
        inputStreamOffset + inputStream.getReadPosition() + inputStream.getAvailableByteCount();

    // Find the timestamp of the latest sample that does not exceed the buffered position.
    long latestTimestampBeforeEnd = Long.MIN_VALUE;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      if (!tracks[trackIndex].selected) {
        continue;
      }

      Mp4TrackSampleTable sampleTable = tracks[trackIndex].sampleTable;
      int sampleIndex = Util.binarySearchFloor(sampleTable.offsets, bufferedPosition, false, true);
      if (sampleIndex > 0
          && sampleTable.offsets[sampleIndex] + sampleTable.sizes[sampleIndex] > bufferedPosition) {
        sampleIndex--;
      }

      // Update the latest timestamp if this is greater.
      long timestamp = sampleTable.timestampsUs[sampleIndex];
      if (timestamp > latestTimestampBeforeEnd) {
        latestTimestampBeforeEnd = timestamp;
      }
    }

    return latestTimestampBeforeEnd < 0L ? C.UNKNOWN_TIME_US : latestTimestampBeforeEnd;
  }

  @Override
  public void seekTo(long positionUs) {
    Assertions.checkState(prepared);

    long earliestSamplePosition = Long.MAX_VALUE;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      if (!tracks[trackIndex].selected) {
        continue;
      }

      Mp4TrackSampleTable sampleTable = tracks[trackIndex].sampleTable;
      int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(positionUs);
      if (sampleIndex == Mp4Util.NO_SAMPLE) {
        sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(positionUs);
      }
      tracks[trackIndex].sampleIndex = sampleIndex;

      long offset = sampleTable.offsets[tracks[trackIndex].sampleIndex];
      if (offset < earliestSamplePosition) {
        earliestSamplePosition = offset;
      }
    }

    pendingSeekPosition = earliestSamplePosition;
    if (pendingLoadPosition != -1) {
      loadFromOffset(earliestSamplePosition);
      return;
    }

    inputStream.returnToMark();
    long earliestOffset = inputStreamOffset + inputStream.getReadPosition();
    long latestOffset = earliestOffset + inputStream.getAvailableByteCount();
    if (earliestSamplePosition < earliestOffset
        || earliestSamplePosition >= latestOffset + reloadMinimumSeekDistance) {
      loadFromOffset(earliestSamplePosition);
    }
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return tracks.length;
  }

  @Override
  public MediaFormat getMediaFormat(int track) {
    Assertions.checkState(prepared);
    return tracks[track].track.mediaFormat;
  }

  @Override
  public DrmInitData getDrmInitData(int track) {
    return null;
  }

  @Override
  public int readSample(int trackIndex, SampleHolder sampleHolder) throws IOException {
    Assertions.checkState(prepared);

    Mp4Track track = tracks[trackIndex];
    Assertions.checkState(track.selected);
    int sampleIndex = track.sampleIndex;

    // Check for the end of the stream.
    if (sampleIndex == Mp4Util.NO_SAMPLE) {
      // TODO: Should END_OF_STREAM be returned as soon as this track has no more samples, or as
      // soon as no tracks have a sample (as implemented here)?
      return hasSampleInAnySelectedTrack() ? SampleSource.NOTHING_READ : SampleSource.END_OF_STREAM;
    }

    // Return if the input stream will be reopened at the requested position.
    if (pendingLoadPosition != -1) {
      return SampleSource.NOTHING_READ;
    }

    // If there was a seek request, try to skip forwards to the requested position.
    if (pendingSeekPosition != -1) {
      int bytesToSeekPosition =
          (int) (pendingSeekPosition - (inputStreamOffset + inputStream.getReadPosition()));
      int skippedByteCount = inputStream.skip(bytesToSeekPosition);
      if (skippedByteCount == -1) {
        throw new IOException("Unexpected end-of-stream while seeking to sample.");
      }
      bytesToSeekPosition -= skippedByteCount;
      inputStream.mark();
      if (bytesToSeekPosition == 0) {
        pendingSeekPosition = -1;
      } else {
        maybeThrowLoadError();
        return SampleSource.NOTHING_READ;
      }
    }

    // Return if the sample offset hasn't been loaded yet.
    inputStream.returnToMark();
    long sampleOffset = track.sampleTable.offsets[sampleIndex];
    long seekOffsetLong = (sampleOffset - inputStreamOffset) - inputStream.getReadPosition();
    Assertions.checkState(seekOffsetLong <= Integer.MAX_VALUE);
    int seekOffset = (int) seekOffsetLong;
    if (inputStream.skip(seekOffset) != seekOffset) {
      maybeThrowLoadError();
      return SampleSource.NOTHING_READ;
    }

    // Return if the sample has been loaded.
    int sampleSize = track.sampleTable.sizes[sampleIndex];
    if (inputStream.getAvailableByteCount() < sampleSize) {
      maybeThrowLoadError();
      return SampleSource.NOTHING_READ;
    }

    if (sampleHolder.data == null || sampleHolder.data.capacity() < sampleSize) {
      sampleHolder.replaceBuffer(sampleSize);
    }

    ByteBuffer data = sampleHolder.data;
    if (data == null) {
      inputStream.skip(sampleSize);
      sampleHolder.size = 0;
    } else {
      int bytesRead = inputStream.read(data, sampleSize);
      Assertions.checkState(bytesRead == sampleSize);

      if (MimeTypes.VIDEO_H264.equals(tracks[trackIndex].track.mediaFormat.mimeType)) {
        // The mp4 file contains length-prefixed access units, but the decoder wants start code
        // delimited content.
        Mp4Util.replaceLengthPrefixesWithAvcStartCodes(sampleHolder.data, sampleSize);
      }
      sampleHolder.size = sampleSize;
    }

    // Move the input stream mark forwards if the earliest current sample was just read.
    if (getTrackIndexOfEarliestCurrentSample() == trackIndex) {
      inputStream.mark();
    }

    // TODO: Read encryption data.
    sampleHolder.timeUs = track.sampleTable.timestampsUs[sampleIndex];
    sampleHolder.flags = track.sampleTable.flags[sampleIndex];

    // Advance to the next sample, checking if this was the last sample.
    track.sampleIndex =
        sampleIndex + 1 == track.sampleTable.getSampleCount() ? Mp4Util.NO_SAMPLE : sampleIndex + 1;

    // Reset the loading error counter if we read past the offset at which the error was thrown.
    if (dataSourceStream.getReadPosition() > loadErrorPosition) {
      loadErrorCount = 0;
      loadErrorPosition = -1;
    }

    return SampleSource.SAMPLE_READ;
  }

  @Override
  public void release() {
    pendingLoadPosition = -1;
    loader.release();

    if (inputStream != null) {
      inputStream.close();
    }
  }

  @Override
  public void onLoadError(Loadable loadable, IOException exception) {
    lastLoadError = exception;

    loadErrorCount++;
    if (loadErrorPosition == -1) {
      loadErrorPosition = dataSourceStream.getLoadPosition();
    }
    int delayMs = getRetryDelayMs(loadErrorCount);
    Log.w(TAG, "Retry loading (delay " + delayMs + " ms).");
    loader.startLoading(dataSourceStream, this, delayMs);
  }

  @Override
  public void onLoadCompleted(Loadable loadable) {
    loadCompleted = true;
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    if (pendingLoadPosition != -1) {
      loadFromOffset(pendingLoadPosition);
      pendingLoadPosition = -1;
    }
  }

  private void loadFromOffset(long offsetBytes) {
    inputStreamOffset = offsetBytes;
    rootAtomBytesRead = offsetBytes;

    if (loader.isLoading()) {
      // Wait for loading to be canceled before proceeding.
      pendingLoadPosition = offsetBytes;
      loader.cancelLoading();
      return;
    }

    if (inputStream != null) {
      inputStream.close();
    }

    DataSpec dataSpec = new DataSpec(
        this.dataSpec.uri, offsetBytes, C.LENGTH_UNBOUNDED, this.dataSpec.key);
    dataSourceStream =
        new DataSourceStream(dataSource, dataSpec, bufferPool, readAheadAllocationSize);
    loader.startLoading(dataSourceStream, this);

    // Wrap the input stream with a buffering stream so that it is possible to read from any track.
    inputStream =
        new BufferedNonBlockingInputStream(dataSourceStream, maximumTrackSampleInterval);
    loadCompleted = false;

    loadErrorCount = 0;
    loadErrorPosition = -1;
  }

  /**
   * Returns the index of the track that contains the earliest current sample, or
   * {@link Mp4Util#NO_TRACK} if no track is selected, or {@link Mp4Util#NO_SAMPLE} if no samples
   * remain in selected tracks.
   */
  private int getTrackIndexOfEarliestCurrentSample() {
    int earliestSampleTrackIndex = Mp4Util.NO_TRACK;
    long earliestSampleOffset = Long.MAX_VALUE;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      Mp4Track track = tracks[trackIndex];
      if (!track.selected) {
        continue;
      }

      int sampleIndex = track.sampleIndex;
      if (sampleIndex == Mp4Util.NO_SAMPLE) {
        if (earliestSampleTrackIndex == Mp4Util.NO_TRACK) {
          // A track is selected, but it has no more samples.
          earliestSampleTrackIndex = Mp4Util.NO_SAMPLE;
        }
        continue;
      }

      long trackSampleOffset = track.sampleTable.offsets[sampleIndex];
      if (trackSampleOffset < earliestSampleOffset) {
        earliestSampleOffset = trackSampleOffset;
        earliestSampleTrackIndex = trackIndex;
      }
    }

    return earliestSampleTrackIndex;
  }

  private boolean hasSampleInAnySelectedTrack() {
    boolean hasSample = false;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      if (tracks[trackIndex].selected && tracks[trackIndex].sampleIndex != Mp4Util.NO_SAMPLE) {
        hasSample = true;
        break;
      }
    }
    return hasSample;
  }

  /** Reads headers, returning whether the end of the stream was reached. */
  private boolean readHeaders() {
    int results = 0;
    while (!prepared && (results & (RESULT_NEED_MORE_DATA | RESULT_END_OF_STREAM)) == 0) {
      switch (parserState) {
        case STATE_READING_ATOM_HEADER:
          results |= readAtomHeader();
          break;
        case STATE_READING_ATOM_PAYLOAD:
          results |= readAtomPayload();
          break;
      }
    }

    return (results & RESULT_END_OF_STREAM) != 0;
  }

  private int readAtomHeader() {
    if (pendingLoadPosition != -1) {
      return RESULT_NEED_MORE_DATA;
    }

    // The size value is either 4 or 8 bytes long (in which case atomSize = Mp4Util.LONG_ATOM_SIZE).
    int remainingBytes;
    if (atomSize != Mp4Util.LONG_ATOM_SIZE) {
      remainingBytes = Mp4Util.ATOM_HEADER_SIZE - atomBytesRead;
    } else {
      remainingBytes = Mp4Util.LONG_ATOM_HEADER_SIZE - atomBytesRead;
    }

    int bytesRead = inputStream.read(atomHeader.data, atomBytesRead, remainingBytes);
    if (bytesRead == -1) {
      return RESULT_END_OF_STREAM;
    }
    rootAtomBytesRead += bytesRead;
    atomBytesRead += bytesRead;
    if (atomBytesRead < Mp4Util.ATOM_HEADER_SIZE
        || (atomSize == Mp4Util.LONG_ATOM_SIZE && atomBytesRead < Mp4Util.LONG_ATOM_HEADER_SIZE)) {
      return RESULT_NEED_MORE_DATA;
    }

    atomHeader.setPosition(0);
    atomSize = atomHeader.readUnsignedInt();
    atomType = atomHeader.readInt();
    if (atomSize == Mp4Util.LONG_ATOM_SIZE) {
      // The extended atom size is contained in the next 8 bytes, so try to read it now.
      if (atomBytesRead < Mp4Util.LONG_ATOM_HEADER_SIZE) {
        return readAtomHeader();
      }

      atomSize = atomHeader.readLong();
    }

    Integer atomTypeInteger = atomType; // Avoids boxing atomType twice.
    if (CONTAINER_TYPES.contains(atomTypeInteger)) {
      if (atomSize == Mp4Util.LONG_ATOM_SIZE) {
        containerAtoms.add(new ContainerAtom(
            atomType, rootAtomBytesRead + atomSize - Mp4Util.LONG_ATOM_HEADER_SIZE));
      } else {
        containerAtoms.add(new ContainerAtom(
            atomType, rootAtomBytesRead + atomSize - Mp4Util.ATOM_HEADER_SIZE));
      }
      enterState(STATE_READING_ATOM_HEADER);
    } else if (LEAF_ATOM_TYPES.contains(atomTypeInteger)) {
      Assertions.checkState(atomSize <= Integer.MAX_VALUE);
      atomData = new ParsableByteArray((int) atomSize);
      System.arraycopy(atomHeader.data, 0, atomData.data, 0, Mp4Util.ATOM_HEADER_SIZE);
      enterState(STATE_READING_ATOM_PAYLOAD);
    } else {
      atomData = null;
      enterState(STATE_READING_ATOM_PAYLOAD);
    }

    return 0;
  }

  private int readAtomPayload() {
    int bytesRead;
    if (atomData != null) {
      bytesRead = inputStream.read(atomData.data, atomBytesRead, (int) atomSize - atomBytesRead);
    } else {
      if (atomSize >= reloadMinimumSeekDistance || atomSize > Integer.MAX_VALUE) {
        loadFromOffset(rootAtomBytesRead + atomSize - atomBytesRead);
        onContainerAtomRead();
        enterState(STATE_READING_ATOM_HEADER);
        return 0;
      } else {
        bytesRead = inputStream.skip((int) atomSize - atomBytesRead);
      }
    }
    if (bytesRead == -1) {
      return RESULT_END_OF_STREAM;
    }
    rootAtomBytesRead += bytesRead;
    atomBytesRead += bytesRead;
    if (atomBytesRead != atomSize) {
      return RESULT_NEED_MORE_DATA;
    }

    if (atomData != null && !containerAtoms.isEmpty()) {
      containerAtoms.peek().add(new Atom.LeafAtom(atomType, atomData));
    }

    onContainerAtomRead();

    enterState(STATE_READING_ATOM_HEADER);
    return 0;
  }

  private void onContainerAtomRead() {
    while (!containerAtoms.isEmpty() && containerAtoms.peek().endByteOffset == rootAtomBytesRead) {
      Atom.ContainerAtom containerAtom = containerAtoms.pop();
      if (containerAtom.type == Atom.TYPE_moov) {
        processMoovAtom(containerAtom);
      } else if (!containerAtoms.isEmpty()) {
        containerAtoms.peek().add(containerAtom);
      }
    }
  }

  private void enterState(int state) {
    switch (state) {
      case STATE_READING_ATOM_HEADER:
        atomBytesRead = 0;
        atomSize = 0;
        break;
    }
    parserState = state;
    inputStream.mark();
  }

  /** Updates the stored track metadata to reflect the contents on the specified moov atom. */
  private void processMoovAtom(Atom.ContainerAtom moov) {
    List<Mp4Track> tracks = new ArrayList<Mp4Track>();
    long earliestSampleOffset = Long.MAX_VALUE;
    for (int i = 0; i < moov.containerChildren.size(); i++) {
      Atom.ContainerAtom atom = moov.containerChildren.get(i);
      if (atom.type != Atom.TYPE_trak) {
        continue;
      }

      Track track = CommonMp4AtomParsers.parseTrak(atom, moov.getLeafAtomOfType(Atom.TYPE_mvhd));
      if (track.type != Track.TYPE_AUDIO && track.type != Track.TYPE_VIDEO) {
        continue;
      }

      Atom.ContainerAtom stblAtom = atom.getContainerAtomOfType(Atom.TYPE_mdia)
          .getContainerAtomOfType(Atom.TYPE_minf).getContainerAtomOfType(Atom.TYPE_stbl);
      Mp4TrackSampleTable trackSampleTable = CommonMp4AtomParsers.parseStbl(track, stblAtom);

      if (trackSampleTable.getSampleCount() == 0) {
        continue;
      }

      tracks.add(new Mp4Track(track, trackSampleTable));

      // Keep track of the byte offset of the earliest sample.
      long firstSampleOffset = trackSampleTable.offsets[0];
      if (firstSampleOffset < earliestSampleOffset) {
        earliestSampleOffset = firstSampleOffset;
      }
    }
    this.tracks = tracks.toArray(new Mp4Track[0]);

    if (earliestSampleOffset < inputStream.getReadPosition()) {
      loadFromOffset(earliestSampleOffset);
    }

    prepared = true;
  }

  /** Returns an unmodifiable set of atom types. */
  private static Set<Integer> getAtomTypeSet(int... atomTypes) {
    Set<Integer> atomTypeSet = new HashSet<Integer>();
    for (int atomType : atomTypes) {
      atomTypeSet.add(atomType);
    }
    return Collections.unmodifiableSet(atomTypeSet);
  }

  private int getRetryDelayMs(int errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  private void maybeThrowLoadError() throws IOException {
    if (loadErrorCount > loadRetryCount) {
      throw lastLoadError;
    }
  }

  private static final class Mp4Track {

    public final Track track;
    public final Mp4TrackSampleTable sampleTable;

    public boolean selected;
    public int sampleIndex;

    public Mp4Track(Track track, Mp4TrackSampleTable sampleTable) {
      this.track = track;
      this.sampleTable = sampleTable;
    }

  }

}
