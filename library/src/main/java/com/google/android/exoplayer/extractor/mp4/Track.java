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
package com.google.android.exoplayer.extractor.mp4;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;

/**
 * Encapsulates information describing an MP4 track.
 */
public final class Track {

  /**
   * Type of a video track.
   */
  public static final int TYPE_VIDEO = 0x76696465;
  /**
   * Type of an audio track.
   */
  public static final int TYPE_AUDIO = 0x736F756E;
  /**
   * Type of a text track.
   */
  public static final int TYPE_TEXT = 0x74657874;
  /**
   * Type of a hint track.
   */
  public static final int TYPE_HINT = 0x68696E74;
  /**
   * Type of a meta track.
   */
  public static final int TYPE_META = 0x6D657461;
  /**
   * Type of a time-code track.
   */
  public static final int TYPE_TIME_CODE = 0x746D6364;

  /**
   * The track identifier.
   */
  public final int id;

  /**
   * One of {@link #TYPE_VIDEO}, {@link #TYPE_AUDIO}, {@link #TYPE_HINT}, {@link #TYPE_META} and
   * {@link #TYPE_TIME_CODE}.
   */
  public final int type;

  /**
   * The track timescale, defined as the number of time units that pass in one second.
   */
  public final long timescale;

  /**
   * The duration of the track in microseconds, or {@link C#UNKNOWN_TIME_US} if unknown.
   */
  public final long durationUs;

  /**
   * The format if {@link #type} is {@link #TYPE_VIDEO} or {@link #TYPE_AUDIO}. Null otherwise.
   */
  public final MediaFormat mediaFormat;

  /**
   * Track encryption boxes for the different track sample descriptions. Entries may be null.
   */
  public final TrackEncryptionBox[] sampleDescriptionEncryptionBoxes;

  /**
   * For H264 video tracks, the length in bytes of the NALUnitLength field in each sample. -1 for
   * other track types.
   */
  public final int nalUnitLengthFieldLength;

  public Track(int id, int type, long timescale, long durationUs, MediaFormat mediaFormat,
      TrackEncryptionBox[] sampleDescriptionEncryptionBoxes, int nalUnitLengthFieldLength) {
    this.id = id;
    this.type = type;
    this.timescale = timescale;
    this.durationUs = durationUs;
    this.mediaFormat = mediaFormat;
    this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
  }

}
