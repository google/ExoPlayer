/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.contains;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DebugTraceUtil;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A wrapper around a media muxer.
 *
 * <p>This wrapper can contain at most one video track and one audio track.
 */
/* package */ final class MuxerWrapper {
  /** Different modes for muxing. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({MUXER_MODE_DEFAULT, MUXER_MODE_MUX_PARTIAL_VIDEO, MUXER_MODE_APPEND_VIDEO})
  public @interface MuxerMode {}

  /** The default muxer mode. */
  public static final int MUXER_MODE_DEFAULT = 0;

  /**
   * Used for muxing a partial video. The video {@link TrackInfo} is kept the same when {@linkplain
   * #changeToAppendVideoMode() transitioning} to {@link #MUXER_MODE_APPEND_VIDEO} after finishing
   * muxing partial video. Only one video track can be {@link #addTrackFormat(Format) added} in this
   * mode.
   */
  public static final int MUXER_MODE_MUX_PARTIAL_VIDEO = 1;

  /** Used for appending the remaining video samples with the previously muxed partial video. */
  public static final int MUXER_MODE_APPEND_VIDEO = 2;

  private static final String TIMER_THREAD_NAME = "Muxer:Timer";
  private static final String MUXER_TIMEOUT_ERROR_FORMAT_STRING =
      "Abort: no output sample written in the last %d milliseconds. DebugTrace: %s";

  public interface Listener {
    void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount);

    void onEnded(long durationMs, long fileSizeBytes);

    void onError(ExportException exportException);
  }

  /**
   * The maximum difference between the track positions, in microseconds.
   *
   * <p>The value of this constant has been chosen based on the interleaving observed in a few media
   * files, where continuous chunks of the same track were about 0.5 seconds long.
   */
  private static final long MAX_TRACK_WRITE_AHEAD_US = Util.msToUs(500);

  private final String outputPath;
  private final Muxer.Factory muxerFactory;
  private final Listener listener;
  private final SparseArray<TrackInfo> trackTypeToInfo;
  private final ScheduledExecutorService abortScheduledExecutorService;

  private boolean isReady;
  private boolean isEnded;
  private @C.TrackType int previousTrackType;
  private long minTrackTimeUs;
  private long maxEndedTrackTimeUs;
  private @MonotonicNonNull ScheduledFuture<?> abortScheduledFuture;
  private boolean isAborted;
  private @MonotonicNonNull Muxer muxer;
  private @MuxerMode int muxerMode;

  private volatile boolean muxedPartialVideo;
  private volatile int additionalRotationDegrees;
  private volatile int trackCount;

  /**
   * Creates an instance.
   *
   * @param outputPath The output file path to write the media data to.
   * @param muxerFactory A {@link Muxer.Factory} to create a {@link Muxer}.
   * @param listener A {@link MuxerWrapper.Listener}.
   * @param muxerMode The {@link MuxerMode}. The initial mode must be {@link #MUXER_MODE_DEFAULT} or
   *     {@link #MUXER_MODE_MUX_PARTIAL_VIDEO}.
   */
  public MuxerWrapper(
      String outputPath, Muxer.Factory muxerFactory, Listener listener, @MuxerMode int muxerMode) {
    this.outputPath = outputPath;
    this.muxerFactory = muxerFactory;
    this.listener = listener;
    checkArgument(muxerMode == MUXER_MODE_DEFAULT || muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO);
    this.muxerMode = muxerMode;
    trackTypeToInfo = new SparseArray<>();
    previousTrackType = C.TRACK_TYPE_NONE;
    abortScheduledExecutorService = Util.newSingleThreadScheduledExecutor(TIMER_THREAD_NAME);
  }

  /**
   * Changes {@link MuxerMode} to {@link #MUXER_MODE_APPEND_VIDEO}.
   *
   * <p>This method must be called only after partial video is muxed using {@link
   * #MUXER_MODE_MUX_PARTIAL_VIDEO}.
   */
  public void changeToAppendVideoMode() {
    checkState(muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO);

    muxerMode = MUXER_MODE_APPEND_VIDEO;
  }

  /**
   * Sets the clockwise rotation to add to the {@linkplain #addTrackFormat(Format) video track's}
   * rotation, in degrees.
   *
   * <p>This value must be set before any track format is {@linkplain #addTrackFormat(Format)
   * added}.
   *
   * <p>Can be called from any thread.
   *
   * @throws IllegalStateException If a track format was {@linkplain #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void setAdditionalRotationDegrees(int additionalRotationDegrees) {
    checkState(
        trackTypeToInfo.size() == 0,
        "The additional rotation cannot be changed after adding track formats.");
    this.additionalRotationDegrees = additionalRotationDegrees;
  }

  /**
   * Sets the number of output tracks.
   *
   * <p>The track count must be set before any track format is {@linkplain #addTrackFormat(Format)
   * added}.
   *
   * <p>When using muxer mode other than {@link #MUXER_MODE_DEFAULT}, the track count must be 1.
   *
   * <p>Can be called from any thread.
   *
   * @throws IllegalStateException If a track format was {@linkplain #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void setTrackCount(@IntRange(from = 1) int trackCount) {
    if (muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO || muxerMode == MUXER_MODE_APPEND_VIDEO) {
      checkArgument(
          trackCount == 1,
          "Only one video track can be added in MUXER_MODE_MUX_PARTIAL_VIDEO and"
              + " MUXER_MODE_APPEND_VIDEO");
      if (muxerMode == MUXER_MODE_APPEND_VIDEO) {
        return;
      }
    }
    checkState(
        trackTypeToInfo.size() == 0,
        "The track count cannot be changed after adding track formats.");
    this.trackCount = trackCount;
  }

  /** Returns whether the sample {@linkplain MimeTypes MIME type} is supported. */
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    @C.TrackType int trackType = MimeTypes.getTrackType(mimeType);
    return getSupportedSampleMimeTypes(trackType).contains(mimeType);
  }

  /**
   * Returns the supported {@linkplain MimeTypes MIME types} for the given {@linkplain C.TrackType
   * track type}.
   */
  public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
    return muxerFactory.getSupportedSampleMimeTypes(trackType);
  }

  /**
   * Adds a track format to the muxer.
   *
   * <p>The number of tracks must be {@linkplain #setTrackCount(int) set} before any format is added
   * and all the formats must be added before any samples can be {@linkplain #writeSample(int,
   * ByteBuffer, boolean, long) written}.
   *
   * <p>{@link Muxer#addMetadata(Metadata)} is called if the {@link Format#metadata} is present.
   *
   * @param format The {@link Format} to be added. In {@link #MUXER_MODE_APPEND_VIDEO} mode, the
   *     added {@link Format} must match the existing {@link Format} set when the muxer was in
   *     {@link #MUXER_MODE_MUX_PARTIAL_VIDEO} mode.
   * @throws IllegalArgumentException If the format is unsupported or if it does not match the
   *     existing format in {@link #MUXER_MODE_APPEND_VIDEO} mode.
   * @throws IllegalStateException If the number of formats added exceeds the {@linkplain
   *     #setTrackCount track count}, if {@link #setTrackCount(int)} has not been called or if there
   *     is already a track of that {@link C.TrackType}.
   * @throws Muxer.MuxerException If the underlying {@link Muxer} encounters a problem while adding
   *     the track.
   */
  public void addTrackFormat(Format format) throws Muxer.MuxerException {
    if (muxerMode == MUXER_MODE_APPEND_VIDEO) {
      checkState(contains(trackTypeToInfo, C.TRACK_TYPE_VIDEO));
      TrackInfo videoTrackInfo = trackTypeToInfo.get(C.TRACK_TYPE_VIDEO);

      // Ensure that video formats are the same. Some fields like codecs, averageBitrate, framerate,
      // etc, don't match exactly in the Extractor output format and the Encoder output
      // format but these fields can be ignored.
      Format existingFormat = videoTrackInfo.format;
      checkArgument(Util.areEqual(existingFormat.sampleMimeType, format.sampleMimeType));
      checkArgument(existingFormat.width == format.width);
      checkArgument(existingFormat.height == format.height);
      checkArgument(existingFormat.initializationDataEquals(format));
      checkArgument(Util.areEqual(existingFormat.colorInfo, format.colorInfo));

      checkNotNull(muxer);
      resetAbortTimer();
      return;
    } else if (muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO) {
      checkArgument(MimeTypes.isVideo(format.sampleMimeType));
    }

    int trackCount = this.trackCount;
    checkState(trackCount > 0, "The track count should be set before the formats are added.");
    checkState(trackTypeToInfo.size() < trackCount, "All track formats have already been added.");
    @Nullable String sampleMimeType = format.sampleMimeType;
    @C.TrackType int trackType = MimeTypes.getTrackType(sampleMimeType);
    checkArgument(
        trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO,
        "Unsupported track format: " + sampleMimeType);

    checkState(
        !contains(trackTypeToInfo, trackType), "There is already a track of type " + trackType);

    ensureMuxerInitialized();

    if (trackType == C.TRACK_TYPE_VIDEO) {
      format =
          format
              .buildUpon()
              .setRotationDegrees((format.rotationDegrees + additionalRotationDegrees) % 360)
              .build();
    }
    TrackInfo trackInfo = new TrackInfo(format, muxer.addTrack(format));
    trackTypeToInfo.put(trackType, trackInfo);

    if (format.metadata != null) {
      muxer.addMetadata(format.metadata);
    }

    if (trackTypeToInfo.size() == trackCount) {
      isReady = true;
      resetAbortTimer();
    }
  }

  /**
   * Attempts to write a sample to the muxer.
   *
   * @param trackType The {@link C.TrackType} of the sample.
   * @param data The sample to write.
   * @param isKeyFrame Whether the sample is a key frame.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @return Whether the sample was successfully written. {@code false} if samples of other
   *     {@linkplain C.TrackType track types} should be written first to ensure the files track
   *     interleaving is balanced, or if the muxer hasn't {@linkplain #addTrackFormat(Format)
   *     received a format} for every {@linkplain #setTrackCount(int) track}.
   * @throws IllegalArgumentException If the muxer doesn't have a {@linkplain #endTrack(int)
   *     non-ended} track of the given {@link C.TrackType}.
   * @throws Muxer.MuxerException If the underlying {@link Muxer} fails to write the sample.
   */
  public boolean writeSample(
      @C.TrackType int trackType, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws Muxer.MuxerException {
    checkArgument(contains(trackTypeToInfo, trackType));
    TrackInfo trackInfo = trackTypeToInfo.get(trackType);
    boolean canWriteSample = canWriteSample(trackType, presentationTimeUs);
    if (trackType == C.TRACK_TYPE_VIDEO) {
      DebugTraceUtil.logEvent(
          DebugTraceUtil.EVENT_MUXER_CAN_WRITE_SAMPLE_VIDEO,
          presentationTimeUs,
          String.valueOf(canWriteSample));
    } else if (trackType == C.TRACK_TYPE_AUDIO) {
      DebugTraceUtil.logEvent(
          DebugTraceUtil.EVENT_MUXER_CAN_WRITE_SAMPLE_AUDIO,
          presentationTimeUs,
          String.valueOf(canWriteSample));
    }
    if (!canWriteSample) {
      return false;
    }

    trackInfo.sampleCount++;
    trackInfo.bytesWritten += data.remaining();
    trackInfo.timeUs = max(trackInfo.timeUs, presentationTimeUs);

    checkNotNull(muxer);
    resetAbortTimer();
    muxer.writeSampleData(
        trackInfo.index, data, presentationTimeUs, isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0);
    if (trackType == C.TRACK_TYPE_VIDEO) {
      DebugTraceUtil.logEvent(DebugTraceUtil.EVENT_MUXER_WRITE_SAMPLE_VIDEO, presentationTimeUs);
    } else if (trackType == C.TRACK_TYPE_AUDIO) {
      DebugTraceUtil.logEvent(DebugTraceUtil.EVENT_MUXER_WRITE_SAMPLE_AUDIO, presentationTimeUs);
    }
    previousTrackType = trackType;
    return true;
  }

  /**
   * Notifies the muxer that all the samples have been {@linkplain #writeSample(int, ByteBuffer,
   * boolean, long) written} for a given track.
   *
   * @param trackType The {@link C.TrackType}.
   */
  public void endTrack(@C.TrackType int trackType) {
    if (!contains(trackTypeToInfo, trackType)) {
      return;
    }

    TrackInfo trackInfo = trackTypeToInfo.get(trackType);
    maxEndedTrackTimeUs = max(maxEndedTrackTimeUs, trackInfo.timeUs);
    listener.onTrackEnded(
        trackType, trackInfo.format, trackInfo.getAverageBitrate(), trackInfo.sampleCount);
    if (trackType == C.TRACK_TYPE_VIDEO) {
      DebugTraceUtil.logEvent(DebugTraceUtil.EVENT_MUXER_TRACK_ENDED_VIDEO, trackInfo.timeUs);
    } else if (trackType == C.TRACK_TYPE_AUDIO) {
      DebugTraceUtil.logEvent(DebugTraceUtil.EVENT_MUXER_TRACK_ENDED_AUDIO, trackInfo.timeUs);
    }

    if (muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO) {
      muxedPartialVideo = true;
    } else {
      trackTypeToInfo.delete(trackType);
      if (trackTypeToInfo.size() == 0) {
        isEnded = true;
      }
    }

    if (muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO && muxedPartialVideo) {
      listener.onEnded(Util.usToMs(maxEndedTrackTimeUs), getCurrentOutputSizeBytes());
      if (abortScheduledFuture != null) {
        abortScheduledFuture.cancel(/* mayInterruptIfRunning= */ false);
      }
      return;
    }

    if (isEnded) {
      listener.onEnded(Util.usToMs(maxEndedTrackTimeUs), getCurrentOutputSizeBytes());
      abortScheduledExecutorService.shutdownNow();
    }
  }

  /**
   * Returns whether all the tracks are {@linkplain #endTrack(int) ended} or a partial video is
   * completely muxed using {@link #MUXER_MODE_MUX_PARTIAL_VIDEO}.
   */
  public boolean isEnded() {
    return isEnded || (muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO && muxedPartialVideo);
  }

  /**
   * Finishes writing the output and releases any resources associated with muxing.
   *
   * <p>When this method is called in {@link #MUXER_MODE_MUX_PARTIAL_VIDEO} mode, the resources are
   * not released and the {@link MuxerWrapper} can be reused after {@link #changeToAppendVideoMode()
   * changing mode} to {@link #MUXER_MODE_APPEND_VIDEO} mode. In all other modes the {@link
   * MuxerWrapper} cannot be used anymore once this method has been called.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   * @throws Muxer.MuxerException If the underlying {@link Muxer} fails to finish writing the output
   *     and {@code forCancellation} is false.
   */
  public void release(boolean forCancellation) throws Muxer.MuxerException {
    if (muxerMode == MUXER_MODE_MUX_PARTIAL_VIDEO && !forCancellation) {
      return;
    }
    isReady = false;
    abortScheduledExecutorService.shutdownNow();
    if (muxer != null) {
      muxer.release(forCancellation);
    }
  }

  private boolean canWriteSample(@C.TrackType int trackType, long presentationTimeUs) {
    if (!isReady) {
      return false;
    }
    if (trackTypeToInfo.size() == 1) {
      return true;
    }
    if (presentationTimeUs - trackTypeToInfo.get(trackType).timeUs > MAX_TRACK_WRITE_AHEAD_US) {
      TrackInfo trackInfoWithMinTimeUs = checkNotNull(getTrackInfoWithMinTimeUs(trackTypeToInfo));
      if (MimeTypes.getTrackType(trackInfoWithMinTimeUs.format.sampleMimeType) == trackType) {
        // Unstuck the muxer if consecutive timestamps from the same track are more than
        // MAX_TRACK_WRITE_AHEAD_US apart.
        return true;
      }
    }
    if (trackType != previousTrackType) {
      minTrackTimeUs = checkNotNull(getTrackInfoWithMinTimeUs(trackTypeToInfo)).timeUs;
    }
    return presentationTimeUs - minTrackTimeUs <= MAX_TRACK_WRITE_AHEAD_US;
  }

  @RequiresNonNull("muxer")
  private void resetAbortTimer() {
    long maxDelayBetweenSamplesMs = muxer.getMaxDelayBetweenSamplesMs();
    if (maxDelayBetweenSamplesMs == C.TIME_UNSET) {
      return;
    }
    if (abortScheduledFuture != null) {
      abortScheduledFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
    abortScheduledFuture =
        abortScheduledExecutorService.schedule(
            () -> {
              if (isAborted) {
                return;
              }
              isAborted = true;
              listener.onError(
                  ExportException.createForMuxer(
                      new IllegalStateException(
                          Util.formatInvariant(
                              MUXER_TIMEOUT_ERROR_FORMAT_STRING,
                              maxDelayBetweenSamplesMs,
                              DebugTraceUtil.generateTraceSummary())),
                      ExportException.ERROR_CODE_MUXING_TIMEOUT));
            },
            maxDelayBetweenSamplesMs,
            MILLISECONDS);
  }

  @EnsuresNonNull("muxer")
  private void ensureMuxerInitialized() throws Muxer.MuxerException {
    if (muxer == null) {
      muxer = muxerFactory.create(outputPath);
    }
  }

  /** Returns the current size in bytes of the output, or {@link C#LENGTH_UNSET} if unavailable. */
  private long getCurrentOutputSizeBytes() {
    long fileSize = new File(outputPath).length();
    return fileSize > 0 ? fileSize : C.LENGTH_UNSET;
  }

  @Nullable
  private static TrackInfo getTrackInfoWithMinTimeUs(SparseArray<TrackInfo> trackTypeToInfo) {
    if (trackTypeToInfo.size() == 0) {
      return null;
    }

    TrackInfo trackInfoWithMinTimeUs = trackTypeToInfo.valueAt(0);
    for (int i = 1; i < trackTypeToInfo.size(); i++) {
      TrackInfo trackInfo = trackTypeToInfo.valueAt(i);
      if (trackInfo.timeUs < trackInfoWithMinTimeUs.timeUs) {
        trackInfoWithMinTimeUs = trackInfo;
      }
    }
    return trackInfoWithMinTimeUs;
  }

  private static final class TrackInfo {
    public final Format format;
    public final int index;

    public long bytesWritten;
    public int sampleCount;
    public long timeUs;

    public TrackInfo(Format format, int index) {
      this.format = format;
      this.index = index;
    }

    /**
     * Returns the average bitrate of data written to the track, or {@link C#RATE_UNSET_INT} if
     * there is no track data.
     */
    public int getAverageBitrate() {
      if (timeUs <= 0 || bytesWritten <= 0) {
        return C.RATE_UNSET_INT;
      }

      // The number of bytes written is not a timestamp, however this utility method provides
      // overflow-safe multiplication & division.
      return (int)
          Util.scaleLargeTimestamp(
              /* timestamp= */ bytesWritten,
              /* multiplier= */ C.BITS_PER_BYTE * C.MICROS_PER_SECOND,
              /* divisor= */ timeUs);
    }
  }
}
