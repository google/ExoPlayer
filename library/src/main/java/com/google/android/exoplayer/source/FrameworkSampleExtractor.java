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
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaExtractor;
import android.net.Uri;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** {@link SampleExtractor} that extracts samples from a stream using {@link MediaExtractor}. */
// TODO: This implementation needs to be fixed so that its methods are non-blocking (either
// through use of a background thread, or through changes to the framework's MediaExtractor API).
@TargetApi(16)
public final class FrameworkSampleExtractor implements SampleExtractor {

  // Parameters for a Uri data source.
  private final Context context;
  private final Uri uri;
  private final Map<String, String> headers;

  // Parameters for a FileDescriptor data source.
  private final FileDescriptor fileDescriptor;
  private final long fileDescriptorOffset;
  private final long fileDescriptorLength;

  private final MediaExtractor mediaExtractor;

  /**
   * Instantiates a new sample extractor reading from the specified {@code uri}.
   *
   * @param context Context for resolving {@code uri}.
   * @param uri The content URI from which to extract data.
   * @param headers Headers to send with requests for data.
   */
  public FrameworkSampleExtractor(Context context, Uri uri, Map<String, String> headers) {
    Assertions.checkState(Util.SDK_INT >= 16);

    this.context = Assertions.checkNotNull(context);
    this.uri = Assertions.checkNotNull(uri);
    this.headers = headers;

    fileDescriptor = null;
    fileDescriptorOffset = 0;
    fileDescriptorLength = 0;

    mediaExtractor = new MediaExtractor();
  }

  /**
   * Instantiates a new sample extractor reading from the specified seekable {@code fileDescriptor}.
   * The caller is responsible for releasing the file descriptor.
   *
   * @param fileDescriptor File descriptor from which to read.
   * @param offset The offset in bytes into the file where the data to be extracted starts.
   * @param length The length in bytes of the data to be extracted.
   */
  public FrameworkSampleExtractor(FileDescriptor fileDescriptor, long offset, long length) {
    Assertions.checkState(Util.SDK_INT >= 16);

    context = null;
    uri = null;
    headers = null;

    this.fileDescriptor = Assertions.checkNotNull(fileDescriptor);
    fileDescriptorOffset = offset;
    fileDescriptorLength = length;

    mediaExtractor = new MediaExtractor();
  }

  @Override
  public boolean prepare() throws IOException {
    if (context != null) {
      mediaExtractor.setDataSource(context, uri, headers);
    } else {
      mediaExtractor.setDataSource(fileDescriptor, fileDescriptorOffset, fileDescriptorLength);
    }

    return true;
  }

  @Override
  public void selectTrack(int index) {
    mediaExtractor.selectTrack(index);
  }

  @Override
  public void deselectTrack(int index) {
    mediaExtractor.unselectTrack(index);
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedDurationUs = mediaExtractor.getCachedDuration();
    if (bufferedDurationUs == -1) {
      return TrackRenderer.UNKNOWN_TIME_US;
    } else {
      long sampleTime = mediaExtractor.getSampleTime();
      return sampleTime == -1 ? TrackRenderer.END_OF_TRACK_US : sampleTime + bufferedDurationUs;
    }
  }

  @Override
  public void seekTo(long positionUs) {
    mediaExtractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
  }

  @Override
  public int getTrackCount() {
    return mediaExtractor.getTrackCount();
  }

  @Override
  public MediaFormat getMediaFormat(int track) {
    return MediaFormat.createFromFrameworkMediaFormatV16(mediaExtractor.getTrackFormat(track));
  }

  @Override
  public DrmInitData getDrmInitData(int track) {
    return Util.SDK_INT >= 18 ? getDrmInitDataV18() : null;
  }

  @Override
  public int readSample(int track, SampleHolder sampleHolder) {
    int sampleTrack = mediaExtractor.getSampleTrackIndex();
    if (sampleTrack != track) {
      return sampleTrack < 0 ? SampleSource.END_OF_STREAM : SampleSource.NOTHING_READ;
    }

    if (sampleHolder.data != null) {
      int offset = sampleHolder.data.position();
      sampleHolder.size = mediaExtractor.readSampleData(sampleHolder.data, offset);
      sampleHolder.data.position(offset + sampleHolder.size);
    } else {
      sampleHolder.size = 0;
    }
    sampleHolder.timeUs = mediaExtractor.getSampleTime();
    sampleHolder.flags = mediaExtractor.getSampleFlags();
    if ((sampleHolder.flags & C.SAMPLE_FLAG_ENCRYPTED) != 0) {
      sampleHolder.cryptoInfo.setFromExtractorV16(mediaExtractor);
    }

    mediaExtractor.advance();

    return SampleSource.SAMPLE_READ;
  }

  @Override
  public void release() {
    mediaExtractor.release();
  }

  @TargetApi(18)
  private DrmInitData getDrmInitDataV18() {
    // MediaExtractor only supports psshInfo for MP4, so it's ok to hard code the mimeType here.
    Map<UUID, byte[]> psshInfo = mediaExtractor.getPsshInfo();
    if (psshInfo == null || psshInfo.isEmpty()) {
      return null;
    }
    DrmInitData.Mapped drmInitData = new DrmInitData.Mapped(MimeTypes.VIDEO_MP4);
    drmInitData.putAll(psshInfo);
    return drmInitData;
  }

}
