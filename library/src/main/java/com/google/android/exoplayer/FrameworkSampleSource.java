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
package com.google.android.exoplayer;

import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.drm.DrmInitData.SchemeInitData;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Extracts samples from a stream using Android's {@link MediaExtractor}.
 * <p>
 * Warning - This class is marked as deprecated because there are known device specific issues
 * associated with its use, including playbacks not starting, playbacks stuttering and other
 * miscellaneous failures. For mp4, m4a, mp3, webm, mkv, mpeg-ts, ogg, wav and aac playbacks it is
 * strongly recommended to use {@link ExtractorSampleSource} instead. Where this is not possible
 * this class can still be used, but please be aware of the associated risks. Playing container
 * formats for which an ExoPlayer extractor does not yet exist (e.g. avi) is a valid use case of
 * this class.
 * <p>
 * Over time we hope to enhance {@link ExtractorSampleSource} to support more formats, and hence
 * make use of this class unnecessary.
 */
// TODO: This implementation needs to be fixed so that its methods are non-blocking (either
// through use of a background thread, or through changes to the framework's MediaExtractor API).
@Deprecated
@TargetApi(16)
public final class FrameworkSampleSource implements SampleSource {

  private static final int TRACK_STATE_DISABLED = 0;
  private static final int TRACK_STATE_ENABLED = 1;
  private static final int TRACK_STATE_FORMAT_SENT = 2;

  // Parameters for a Uri data source.
  private final Context context;
  private final Uri uri;
  private final Map<String, String> headers;

  // Parameters for a FileDescriptor data source.
  private final FileDescriptor fileDescriptor;
  private final long fileDescriptorOffset;
  private final long fileDescriptorLength;

  private boolean prepared;
  private long durationUs;
  private MediaExtractor extractor;
  private TrackGroupArray tracks;
  private int[] trackStates;
  private boolean[] pendingResets;

  private int enabledTrackCount;
  private long lastSeekPositionUs;
  private long pendingSeekPositionUs;

  /**
   * Instantiates a new sample extractor reading from the specified {@code uri}.
   *
   * @param context Context for resolving {@code uri}.
   * @param uri The content URI from which to extract data.
   * @param headers Headers to send with requests for data.
   */
  public FrameworkSampleSource(Context context, Uri uri, Map<String, String> headers) {
    Assertions.checkState(Util.SDK_INT >= 16);
    this.context = Assertions.checkNotNull(context);
    this.uri = Assertions.checkNotNull(uri);
    this.headers = headers;
    fileDescriptor = null;
    fileDescriptorOffset = 0;
    fileDescriptorLength = 0;
  }

  /**
   * Instantiates a new sample extractor reading from the specified seekable {@code fileDescriptor}.
   * The caller is responsible for releasing the file descriptor.
   *
   * @param fileDescriptor File descriptor from which to read.
   * @param fileDescriptorOffset The offset in bytes where the data to be extracted starts.
   * @param fileDescriptorLength The length in bytes of the data to be extracted.
   */
  public FrameworkSampleSource(FileDescriptor fileDescriptor, long fileDescriptorOffset,
      long fileDescriptorLength) {
    Assertions.checkState(Util.SDK_INT >= 16);
    this.fileDescriptor = Assertions.checkNotNull(fileDescriptor);
    this.fileDescriptorOffset = fileDescriptorOffset;
    this.fileDescriptorLength = fileDescriptorLength;
    context = null;
    uri = null;
    headers = null;
  }

  // SampleSource implementation.

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    extractor = new MediaExtractor();
    if (context != null) {
      extractor.setDataSource(context, uri, headers);
    } else {
      extractor.setDataSource(fileDescriptor, fileDescriptorOffset, fileDescriptorLength);
    }
    durationUs = C.UNKNOWN_TIME_US;
    trackStates = new int[extractor.getTrackCount()];
    pendingResets = new boolean[trackStates.length];
    TrackGroup[] trackArray = new TrackGroup[trackStates.length];
    for (int i = 0; i < trackStates.length; i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      if (format.containsKey(MediaFormat.KEY_DURATION)) {
        durationUs = Math.max(durationUs, format.getLong(MediaFormat.KEY_DURATION));
      }
      trackArray[i] = new TrackGroup(createFormat(i, format));
    }
    tracks = new TrackGroupArray(trackArray);
    prepared = true;
    return true;
  }

  @Override
  public long getDurationUs() {
    return C.UNKNOWN_TIME_US;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);
    // Unselect old tracks.
    for (int i = 0; i < oldStreams.size(); i++) {
      int track = ((TrackStreamImpl) oldStreams.get(i)).track;
      Assertions.checkState(trackStates[track] != TRACK_STATE_DISABLED);
      enabledTrackCount--;
      trackStates[track] = TRACK_STATE_DISABLED;
      extractor.unselectTrack(track);
      pendingResets[track] = false;
    }
    // Select new tracks.
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    for (int i = 0; i < newStreams.length; i++) {
      TrackSelection selection = newSelections.get(i);
      Assertions.checkState(selection.length == 1);
      Assertions.checkState(selection.getTrack(0) == 0);
      int track = selection.group;
      Assertions.checkState(trackStates[track] == TRACK_STATE_DISABLED);
      enabledTrackCount++;
      trackStates[track] = TRACK_STATE_ENABLED;
      extractor.selectTrack(track);
      newStreams[i] = new TrackStreamImpl(track);
    }
    // Seek if necessary.
    if (enabledTrackCount > 0) {
      seekToUsInternal(positionUs, positionUs != 0);
    }
    return newStreams;
  }

  @Override
  public void continueBuffering(long positionUs) {
     // MediaExtractor takes care of buffering. Do nothing.
  }

  @Override
  public void seekToUs(long positionUs) {
    if (enabledTrackCount == 0) {
      return;
    }
    seekToUsInternal(positionUs, false);
  }

  @Override
  public long getBufferedPositionUs() {
    if (enabledTrackCount == 0) {
      return C.END_OF_SOURCE_US;
    }

    long bufferedDurationUs = extractor.getCachedDuration();
    if (bufferedDurationUs == -1) {
      return C.UNKNOWN_TIME_US;
    }

    long sampleTime = extractor.getSampleTime();
    return sampleTime == -1 ? C.END_OF_SOURCE_US : sampleTime + bufferedDurationUs;
  }

  @Override
  public void release() {
    if (extractor != null) {
      extractor.release();
      extractor = null;
    }
  }

  // TrackStream methods.

  /* package */ long readReset(int track) {
    if (pendingResets[track]) {
      pendingResets[track] = false;
      return lastSeekPositionUs;
    }
    return TrackStream.NO_RESET;
  }

  /* package */ int readData(int track, FormatHolder formatHolder, DecoderInputBuffer buffer) {
    Assertions.checkState(trackStates[track] != TRACK_STATE_DISABLED);
    if (pendingResets[track]) {
      return TrackStream.NOTHING_READ;
    }
    if (trackStates[track] != TRACK_STATE_FORMAT_SENT) {
      formatHolder.format = tracks.get(track).getFormat(0);
      formatHolder.drmInitData = Util.SDK_INT >= 18 ? getDrmInitDataV18() : null;
      trackStates[track] = TRACK_STATE_FORMAT_SENT;
      return TrackStream.FORMAT_READ;
    }
    int extractorTrackIndex = extractor.getSampleTrackIndex();
    if (extractorTrackIndex == track) {
      if (buffer.data != null) {
        int offset = buffer.data.position();
        buffer.size = extractor.readSampleData(buffer.data, offset);
        buffer.data.position(offset + buffer.size);
      } else {
        buffer.size = 0;
      }
      buffer.timeUs = extractor.getSampleTime();
      int flags = extractor.getSampleFlags();
      if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
        buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
      }
      if ((flags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
        buffer.addFlag(C.BUFFER_FLAG_ENCRYPTED);
        buffer.cryptoInfo.setFromExtractorV16(extractor);
      }
      pendingSeekPositionUs = C.UNKNOWN_TIME_US;
      extractor.advance();
      return TrackStream.BUFFER_READ;
    } else if (extractorTrackIndex < 0) {
      buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return TrackStream.BUFFER_READ;
    } else {
      return TrackStream.NOTHING_READ;
    }
  }

  // Internal methods.

  @TargetApi(18)
  private DrmInitData getDrmInitDataV18() {
    // MediaExtractor only supports psshInfo for MP4, so it's ok to hard code the mimeType here.
    Map<UUID, byte[]> psshInfo = extractor.getPsshInfo();
    if (psshInfo == null || psshInfo.isEmpty()) {
      return null;
    }
    DrmInitData.Mapped drmInitData = new DrmInitData.Mapped();
    for (UUID uuid : psshInfo.keySet()) {
      byte[] psshAtom = PsshAtomUtil.buildPsshAtom(uuid, psshInfo.get(uuid));
      drmInitData.put(uuid, new SchemeInitData(MimeTypes.VIDEO_MP4, psshAtom));
    }
    return drmInitData;
  }

  private void seekToUsInternal(long positionUs, boolean force) {
    // Unless forced, avoid duplicate calls to the underlying extractor's seek method in the case
    // that there have been no interleaving calls to readSample.
    if (force || pendingSeekPositionUs != positionUs) {
      lastSeekPositionUs = positionUs;
      pendingSeekPositionUs = positionUs;
      extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      for (int i = 0; i < trackStates.length; ++i) {
        if (trackStates[i] != TRACK_STATE_DISABLED) {
          pendingResets[i] = true;
        }
      }
    }
  }

  @SuppressLint("InlinedApi")
  private static Format createFormat(int index, MediaFormat mediaFormat) {
    String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
    String language = getOptionalStringV16(mediaFormat, MediaFormat.KEY_LANGUAGE);
    int maxInputSize = getOptionalIntegerV16(mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE);
    int width = getOptionalIntegerV16(mediaFormat, MediaFormat.KEY_WIDTH);
    int height = getOptionalIntegerV16(mediaFormat, MediaFormat.KEY_HEIGHT);
    float frameRate;
    try {
      frameRate = getOptionalIntegerV16(mediaFormat, MediaFormat.KEY_FRAME_RATE);
    } catch (ClassCastException e) {
      // There's an entry for KEY_FRAME_RATE but it's not a integer. It must be a float.
      frameRate = getOptionalFloatV16(mediaFormat, MediaFormat.KEY_FRAME_RATE);
    }
    int rotationDegrees = getOptionalIntegerV16(mediaFormat, "rotation-degrees");
    int channelCount = getOptionalIntegerV16(mediaFormat, MediaFormat.KEY_CHANNEL_COUNT);
    int sampleRate = getOptionalIntegerV16(mediaFormat, MediaFormat.KEY_SAMPLE_RATE);
    int encoderDelay = getOptionalIntegerV16(mediaFormat, "encoder-delay");
    int encoderPadding = getOptionalIntegerV16(mediaFormat, "encoder-padding");
    ArrayList<byte[]> initializationData = new ArrayList<>();
    for (int i = 0; mediaFormat.containsKey("csd-" + i); i++) {
      ByteBuffer buffer = mediaFormat.getByteBuffer("csd-" + i);
      byte[] data = new byte[buffer.limit()];
      buffer.get(data);
      initializationData.add(data);
      buffer.flip();
    }
    Format format = new Format(Integer.toString(index), null, mimeType, Format.NO_VALUE,
        maxInputSize, width, height, frameRate, rotationDegrees, Format.NO_VALUE, channelCount,
        sampleRate, encoderDelay, encoderPadding, language, Format.OFFSET_SAMPLE_RELATIVE,
        initializationData, false);
    format.setFrameworkMediaFormatV16(mediaFormat);
    return format;
  }

  @TargetApi(16)
  private static String getOptionalStringV16(MediaFormat format, String key) {
    return format.containsKey(key) ? format.getString(key) : null;
  }

  @TargetApi(16)
  private static int getOptionalIntegerV16(MediaFormat format, String key) {
    return format.containsKey(key) ? format.getInteger(key) : Format.NO_VALUE;
  }

  @TargetApi(16)
  private static float getOptionalFloatV16(MediaFormat format, String key) {
    return format.containsKey(key) ? format.getFloat(key) : Format.NO_VALUE;
  }

  private final class TrackStreamImpl implements TrackStream {

    private final int track;

    public TrackStreamImpl(int track) {
      this.track = track;
    }

    @Override
    public boolean isReady() {
      // MediaExtractor takes care of buffering and blocks until it has samples, so we can always
      // return true here. Although note that the blocking behavior is itself as bug, as per the
      // TODO further up this file. This method will need to return something else as part of fixing
      // the TODO.
      return true;
    }

    @Override
    public void maybeThrowError() throws IOException {
      // Do nothing.
    }

    @Override
    public long readReset() {
      return FrameworkSampleSource.this.readReset(track);
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
      return FrameworkSampleSource.this.readData(track, formatHolder, buffer);
    }

  }

}
