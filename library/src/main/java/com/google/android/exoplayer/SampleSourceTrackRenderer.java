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

import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer.SampleSource.SampleSourceReader;

import java.io.IOException;
import java.util.Arrays;

/**
 * Base class for {@link TrackRenderer} implementations that render samples obtained from a
 * {@link SampleSource}.
 */
public abstract class SampleSourceTrackRenderer extends TrackRenderer {

  private final SampleSourceReader[] sources;

  private int[] handledSourceIndices;
  private int[] handledSourceTrackIndices;

  private SampleSourceReader enabledSource;
  private int enabledSourceTrackIndex;

  private long durationUs;

  /**
   * @param sources One or more upstream sources from which the renderer can obtain samples.
   */
  public SampleSourceTrackRenderer(SampleSource... sources) {
    this.sources = new SampleSourceReader[sources.length];
    for (int i = 0; i < sources.length; i++) {
      this.sources[i] = sources[i].register();
    }
  }

  @Override
  protected boolean doPrepare(long positionUs) throws ExoPlaybackException {
    boolean allSourcesPrepared = true;
    for (int i = 0; i < sources.length; i++) {
      allSourcesPrepared &= sources[i].prepare(positionUs);
    }
    if (!allSourcesPrepared) {
      return false;
    }
    // The sources are all prepared.
    int totalSourceTrackCount = 0;
    for (int i = 0; i < sources.length; i++) {
      totalSourceTrackCount += sources[i].getTrackCount();
    }
    long durationUs = 0;
    int handledTrackCount = 0;
    int[] handledSourceIndices = new int[totalSourceTrackCount];
    int[] handledTrackIndices = new int[totalSourceTrackCount];
    int sourceCount = sources.length;
    for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
      SampleSourceReader source = sources[sourceIndex];
      int sourceTrackCount = source.getTrackCount();
      for (int trackIndex = 0; trackIndex < sourceTrackCount; trackIndex++) {
        MediaFormat format = source.getFormat(trackIndex);
        boolean handlesTrack;
        try {
          handlesTrack = handlesTrack(format);
        } catch (DecoderQueryException e) {
          throw new ExoPlaybackException(e);
        }
        if (handlesTrack) {
          handledSourceIndices[handledTrackCount] = sourceIndex;
          handledTrackIndices[handledTrackCount] = trackIndex;
          handledTrackCount++;
          if (durationUs == TrackRenderer.UNKNOWN_TIME_US) {
            // We've already encountered a track for which the duration is unknown, so the media
            // duration is unknown regardless of the duration of this track.
          } else {
            long trackDurationUs = format.durationUs;
            if (trackDurationUs == TrackRenderer.UNKNOWN_TIME_US) {
              durationUs = TrackRenderer.UNKNOWN_TIME_US;
            } else if (trackDurationUs == TrackRenderer.MATCH_LONGEST_US) {
              // Do nothing.
            } else {
              durationUs = Math.max(durationUs, trackDurationUs);
            }
          }
        }
      }
    }
    this.durationUs = durationUs;
    this.handledSourceIndices = Arrays.copyOf(handledSourceIndices, handledTrackCount);
    this.handledSourceTrackIndices = Arrays.copyOf(handledTrackIndices, handledTrackCount);
    return true;
  }

  /**
   * Returns whether this renderer is capable of handling the provided track.
   *
   * @param mediaFormat The format of the track.
   * @return True if the renderer can handle the track, false otherwise.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected abstract boolean handlesTrack(MediaFormat mediaFormat) throws DecoderQueryException;

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    enabledSource = sources[handledSourceIndices[track]];
    enabledSourceTrackIndex = handledSourceTrackIndices[track];
    enabledSource.enable(enabledSourceTrackIndex, positionUs);
  }

  @Override
  protected void seekTo(long positionUs) throws ExoPlaybackException {
    enabledSource.seekToUs(positionUs);
  }

  @Override
  protected long getBufferedPositionUs() {
    return enabledSource.getBufferedPositionUs();
  }

  @Override
  protected long getDurationUs() {
    return durationUs;
  }

  @Override
  protected void maybeThrowError() throws ExoPlaybackException {
    if (enabledSource != null) {
      maybeThrowError(enabledSource);
    } else {
      int sourceCount = sources.length;
      for (int i = 0; i < sourceCount; i++) {
        maybeThrowError(sources[i]);
      }
    }
  }

  private void maybeThrowError(SampleSourceReader source) throws ExoPlaybackException {
    try {
      source.maybeThrowError();
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    enabledSource.disable(enabledSourceTrackIndex);
    enabledSource = null;
  }

  @Override
  protected void onReleased() throws ExoPlaybackException {
    int sourceCount = sources.length;
    for (int i = 0; i < sourceCount; i++) {
      sources[i].release();
    }
  }

  protected final boolean continueBufferingSource(long positionUs) {
    return enabledSource.continueBuffering(enabledSourceTrackIndex, positionUs);
  }

  protected final int readSource(long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) {
    return enabledSource.readData(enabledSourceTrackIndex, positionUs, formatHolder, sampleHolder,
        onlyReadDiscontinuity);
  }

  @Override
  protected final int getTrackCount() {
    return handledSourceTrackIndices.length;
  }

  @Override
  protected final MediaFormat getFormat(int track) {
    SampleSourceReader source = sources[handledSourceIndices[track]];
    return source.getFormat(handledSourceTrackIndices[track]);
  }

}
