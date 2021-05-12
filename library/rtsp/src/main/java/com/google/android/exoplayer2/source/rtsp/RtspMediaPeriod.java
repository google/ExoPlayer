/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleQueue.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.source.SampleStream.ReadFlags;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.rtsp.RtspClient.PlaybackEventListener;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource.RtspPlaybackException;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.BindException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that loads an RTSP stream. */
/* package */ final class RtspMediaPeriod implements MediaPeriod {

  /** The maximum times to retry if the underlying data channel failed to bind. */
  private static final int PORT_BINDING_MAX_RETRY_COUNT = 3;

  private final Allocator allocator;
  private final Handler handler;

  private final InternalListener internalListener;
  private final RtspClient rtspClient;
  private final List<RtspLoaderWrapper> rtspLoaderWrappers;
  private final List<RtpLoadInfo> selectedLoadInfos;

  private @MonotonicNonNull Callback callback;
  private @MonotonicNonNull ImmutableList<TrackGroup> trackGroups;
  @Nullable private IOException preparationError;
  @Nullable private RtspPlaybackException playbackException;

  private long pendingSeekPositionUs;
  private boolean loadingFinished;
  private boolean released;
  private boolean prepared;
  private boolean trackSelected;
  private int portBindingRetryCount;
  private boolean hasRetriedWithRtpTcp;

  /**
   * Creates an RTSP media period.
   *
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param rtspTracks A list of tracks in an RTSP playback session.
   * @param rtspClient The {@link RtspClient} for the current RTSP playback.
   * @param rtpDataChannelFactory A {@link RtpDataChannel.Factory} for {@link RtpDataChannel}.
   */
  public RtspMediaPeriod(
      Allocator allocator,
      List<RtspMediaTrack> rtspTracks,
      RtspClient rtspClient,
      RtpDataChannel.Factory rtpDataChannelFactory) {
    this.allocator = allocator;
    handler = Util.createHandlerForCurrentLooper();

    internalListener = new InternalListener();
    rtspLoaderWrappers = new ArrayList<>(rtspTracks.size());
    this.rtspClient = rtspClient;
    this.rtspClient.setPlaybackEventListener(internalListener);

    for (int i = 0; i < rtspTracks.size(); i++) {
      RtspMediaTrack rtspMediaTrack = rtspTracks.get(i);
      rtspLoaderWrappers.add(
          new RtspLoaderWrapper(rtspMediaTrack, /* trackId= */ i, rtpDataChannelFactory));
    }
    selectedLoadInfos = new ArrayList<>(rtspTracks.size());
    pendingSeekPositionUs = C.TIME_UNSET;
  }

  /** Releases the {@link RtspMediaPeriod}. */
  public void release() {
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      rtspLoaderWrappers.get(i).release();
    }
    released = true;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;

    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      rtspLoaderWrappers.get(i).startLoading();
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    if (preparationError != null) {
      throw preparationError;
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    checkState(prepared);
    return new TrackGroupArray(checkNotNull(trackGroups).toArray(new TrackGroup[0]));
  }

  @Override
  public ImmutableList<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    return ImmutableList.of();
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {

    // Deselect old tracks.
    // Input array streams contains the streams selected in the previous track selection.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
    }

    // Select new tracks.
    selectedLoadInfos.clear();
    for (int i = 0; i < selections.length; i++) {
      TrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }

      TrackGroup trackGroup = selection.getTrackGroup();
      int trackGroupIndex = checkNotNull(trackGroups).indexOf(trackGroup);
      selectedLoadInfos.add(checkNotNull(rtspLoaderWrappers.get(trackGroupIndex)).loadInfo);

      // Find the sampleStreamWrapper that contains this track group.
      if (trackGroups.contains(trackGroup)) {
        if (streams[i] == null) {
          streams[i] = new SampleStreamImpl(trackGroupIndex);
          // Update flag for newly created SampleStream.
          streamResetFlags[i] = true;
        }
      }
    }

    // Cancel non-selected loadables.
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loadControl = rtspLoaderWrappers.get(i);
      if (!selectedLoadInfos.contains(loadControl.loadInfo)) {
        loadControl.cancelLoad();
      }
    }

    trackSelected = true;
    maybeSetupTracks();
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (isSeekPending()) {
      return;
    }

    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);
      if (!loaderWrapper.canceled) {
        loaderWrapper.sampleQueue.discardTo(positionUs, toKeyframe, /* stopAtReadPosition= */ true);
      }
    }
  }

  @Override
  public long readDiscontinuity() {
    return C.TIME_UNSET;
  }

  @Override
  public long seekToUs(long positionUs) {
    if (isSeekPending()) {
      // TODO(internal b/172331505) Allow seek when a seek is pending.
      // Does not allow another seek if a seek is pending.
      return pendingSeekPositionUs;
    }

    if (seekInsideBufferUs(positionUs)) {
      return positionUs;
    }

    pendingSeekPositionUs = positionUs;
    rtspClient.seekToUs(positionUs);
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      rtspLoaderWrappers.get(i).seekTo(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished || rtspLoaderWrappers.isEmpty()) {
      return C.TIME_END_OF_SOURCE;
    }

    if (isSeekPending()) {
      return pendingSeekPositionUs;
    }

    long bufferedPositionUs = rtspLoaderWrappers.get(0).sampleQueue.getLargestQueuedTimestampUs();
    for (int i = 1; i < rtspLoaderWrappers.size(); i++) {
      bufferedPositionUs =
          min(
              bufferedPositionUs,
              checkNotNull(rtspLoaderWrappers.get(i)).sampleQueue.getLargestQueuedTimestampUs());
    }
    return bufferedPositionUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    return getBufferedPositionUs();
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return isLoading();
  }

  @Override
  public boolean isLoading() {
    return !loadingFinished;
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  // SampleStream methods.

  /* package */ boolean isReady(int trackGroupIndex) {
    return rtspLoaderWrappers.get(trackGroupIndex).isSampleQueueReady();
  }

  @ReadDataResult
  /* package */ int readData(
      int sampleQueueIndex,
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      @ReadFlags int readFlags) {
    return rtspLoaderWrappers.get(sampleQueueIndex).read(formatHolder, buffer, readFlags);
  }

  // Internal methods.

  @Nullable
  private RtpDataLoadable getLoadableByTrackUri(Uri trackUri) {
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtpLoadInfo loadInfo = rtspLoaderWrappers.get(i).loadInfo;
      if (loadInfo.getTrackUri().equals(trackUri)) {
        return loadInfo.loadable;
      }
    }
    return null;
  }

  private boolean isSeekPending() {
    return pendingSeekPositionUs != C.TIME_UNSET;
  }

  private void maybeFinishPrepare() {
    if (released || prepared) {
      return;
    }

    // Make sure all sample queues have got format assigned.
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      if (rtspLoaderWrappers.get(i).sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }

    prepared = true;
    trackGroups = buildTrackGroups(ImmutableList.copyOf(rtspLoaderWrappers));
    checkNotNull(callback).onPrepared(/* mediaPeriod= */ this);
  }

  /**
   * Attempts to seek to the specified position within the sample queues.
   *
   * @param positionUs The seek position in microseconds.
   * @return Whether the in-buffer seek was successful for all loading RTSP tracks.
   */
  private boolean seekInsideBufferUs(long positionUs) {
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      SampleQueue sampleQueue = rtspLoaderWrappers.get(i).sampleQueue;
      if (!sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ false)) {
        return false;
      }
    }
    return true;
  }

  private void maybeSetupTracks() {
    boolean transportReady = true;
    for (int i = 0; i < selectedLoadInfos.size(); i++) {
      transportReady &= selectedLoadInfos.get(i).isTransportReady();
    }

    if (transportReady && trackSelected) {
      rtspClient.setupSelectedTracks(selectedLoadInfos);
    }
  }

  private void updateLoadingFinished() {
    loadingFinished = true;
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      loadingFinished &= rtspLoaderWrappers.get(i).canceled;
    }
  }

  private static ImmutableList<TrackGroup> buildTrackGroups(
      ImmutableList<RtspLoaderWrapper> rtspLoaderWrappers) {
    ImmutableList.Builder<TrackGroup> listBuilder = new ImmutableList.Builder<>();
    SampleQueue sampleQueue;
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      sampleQueue = rtspLoaderWrappers.get(i).sampleQueue;
      listBuilder.add(new TrackGroup(checkNotNull(sampleQueue.getUpstreamFormat())));
    }
    return listBuilder.build();
  }

  private final class InternalListener
      implements ExtractorOutput,
          Loader.Callback<RtpDataLoadable>,
          UpstreamFormatChangedListener,
          PlaybackEventListener {

    // ExtractorOutput implementation.

    @Override
    public TrackOutput track(int id, int type) {
      return checkNotNull(rtspLoaderWrappers.get(id)).sampleQueue;
    }

    @Override
    public void endTracks() {
      // TODO(b/172331505) Implement this method.
    }

    @Override
    public void seekMap(SeekMap seekMap) {
      // TODO(b/172331505) Implement this method.
    }

    // Loadable.Callback implementation.

    @Override
    public void onLoadCompleted(
        RtpDataLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {}

    @Override
    public void onLoadCanceled(
        RtpDataLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {}

    @Override
    public Loader.LoadErrorAction onLoadError(
        RtpDataLoadable loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      /* TODO(b/172331505) Sort out the retry policy.
      Three cases for IOException:
        - Socket open failure for RTP or RTCP.
          - RETRY for the RTCP open failure.
        - ExtractorInput read IOException (socket timeout, etc)
          - Keep retrying unless playback is stopped.
        - RtpPayloadReader consume ParserException (mal-formatted RTP packet)
          - Don't retry? (if a packet is distorted on the fly, the packet is likely discarded by the
           system, i.e. the server's sent a mal-formatted packet).
      */
      if (!prepared) {
        preparationError = error;
      } else {
        if (error.getCause() instanceof SocketTimeoutException) {
          return handleSocketTimeout(loadable);
        } else if (error.getCause() instanceof BindException) {
          // Allow for retry on RTP port open failure by catching BindException. Two ports are
          // opened for each RTP stream, the first port number is auto assigned by the system, while
          // the second is manually selected. It is thus possible that the second port fails to
          // bind. Failing is more likely when running in a server-side testing environment, it is
          // less likely on real devices.
          if (portBindingRetryCount++ < PORT_BINDING_MAX_RETRY_COUNT) {
            return Loader.RETRY;
          }
        } else {
          playbackException =
              new RtspPlaybackException(
                  /* message= */ loadable.rtspMediaTrack.uri.toString(), error);
        }
      }
      return Loader.DONT_RETRY;
    }

    // SampleQueue.UpstreamFormatChangedListener implementation.

    @Override
    public void onUpstreamFormatChanged(Format format) {
      handler.post(RtspMediaPeriod.this::maybeFinishPrepare);
    }

    // RtspClient.PlaybackEventListener implementation.

    @Override
    public void onRtspSetupCompleted() {
      rtspClient.startPlayback(/* offsetMs= */ 0);
    }

    @Override
    public void onPlaybackStarted(
        long startPositionUs, ImmutableList<RtspTrackTiming> trackTimingList) {
      // Validate that the trackTimingList contains timings for the selected tracks.
      ArrayList<Uri> trackUrisWithTiming = new ArrayList<>(trackTimingList.size());
      for (int i = 0; i < trackTimingList.size(); i++) {
        trackUrisWithTiming.add(trackTimingList.get(i).uri);
      }
      for (int i = 0; i < selectedLoadInfos.size(); i++) {
        RtpLoadInfo loadInfo = selectedLoadInfos.get(i);
        if (!trackUrisWithTiming.contains(loadInfo.getTrackUri())) {
          playbackException =
              new RtspPlaybackException(
                  "Server did not provide timing for track " + loadInfo.getTrackUri());
          return;
        }
      }

      for (int i = 0; i < trackTimingList.size(); i++) {
        RtspTrackTiming trackTiming = trackTimingList.get(i);
        @Nullable RtpDataLoadable dataLoadable = getLoadableByTrackUri(trackTiming.uri);
        if (dataLoadable == null) {
          continue;
        }

        dataLoadable.setTimestamp(trackTiming.rtpTimestamp);
        dataLoadable.setSequenceNumber(trackTiming.sequenceNumber);

        if (isSeekPending()) {
          dataLoadable.seekToUs(startPositionUs, trackTiming.rtpTimestamp);
        }
      }

      if (isSeekPending()) {
        pendingSeekPositionUs = C.TIME_UNSET;
      }
    }

    @Override
    public void onPlaybackError(RtspPlaybackException error) {
      playbackException = error;
    }

    /** Handles the {@link Loadable} whose {@link RtpDataChannel} timed out. */
    private LoadErrorAction handleSocketTimeout(RtpDataLoadable loadable) {
      // TODO(b/172331505) Allow for retry when loading is not ending.
      if (getBufferedPositionUs() == Long.MIN_VALUE) {
        // Retry playback with TCP if no sample has been received so far.
        if (!hasRetriedWithRtpTcp) {
          retryWithRtpTcp();
          hasRetriedWithRtpTcp = true;
        }
        // Don't retry with the current UDP backed loadables.
        return Loader.DONT_RETRY;
      }

      for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
        RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);
        if (loaderWrapper.loadInfo.loadable == loadable) {
          loaderWrapper.cancelLoad();
          break;
        }
      }
      playbackException = new RtspPlaybackException("Unknown loadable timed out.");
      return Loader.DONT_RETRY;
    }
  }

  private void retryWithRtpTcp() {
    rtspClient.retryWithRtpTcp();

    RtpDataChannel.Factory rtpDataChannelFactory = new TransferRtpDataChannelFactory();
    ArrayList<RtspLoaderWrapper> newLoaderWrappers = new ArrayList<>(rtspLoaderWrappers.size());
    ArrayList<RtpLoadInfo> newSelectedLoadInfos = new ArrayList<>(selectedLoadInfos.size());

    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);

      RtspLoaderWrapper newLoaderWrapper =
          new RtspLoaderWrapper(
              loaderWrapper.loadInfo.mediaTrack, /* trackId= */ i, rtpDataChannelFactory);
      newLoaderWrappers.add(newLoaderWrapper);
      newLoaderWrapper.startLoading();

      if (selectedLoadInfos.contains(loaderWrapper.loadInfo)) {
        newSelectedLoadInfos.add(newLoaderWrapper.loadInfo);
      }
    }

    // Switch to new LoaderWrappers.
    ImmutableList<RtspLoaderWrapper> oldRtspLoaderWrappers =
        ImmutableList.copyOf(rtspLoaderWrappers);
    rtspLoaderWrappers.clear();
    rtspLoaderWrappers.addAll(newLoaderWrappers);
    selectedLoadInfos.clear();
    selectedLoadInfos.addAll(newSelectedLoadInfos);

    // Cancel old loadable wrappers after switching, so that buffered position is always read from
    // active sample queues.
    for (int i = 0; i < oldRtspLoaderWrappers.size(); i++) {
      oldRtspLoaderWrappers.get(i).cancelLoad();
    }
  }

  private final class SampleStreamImpl implements SampleStream {
    private final int track;

    public SampleStreamImpl(int track) {
      this.track = track;
    }

    @Override
    public boolean isReady() {
      return RtspMediaPeriod.this.isReady(track);
    }

    @Override
    public void maybeThrowError() throws RtspPlaybackException {
      if (playbackException != null) {
        throw playbackException;
      }
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      return RtspMediaPeriod.this.readData(track, formatHolder, buffer, readFlags);
    }

    @Override
    public int skipData(long positionUs) {
      return 0;
    }
  }

  /** Manages the loading of an RTSP track. */
  private final class RtspLoaderWrapper {
    /** The {@link RtpLoadInfo} of the RTSP track to load. */
    public final RtpLoadInfo loadInfo;

    private final Loader loader;
    private final SampleQueue sampleQueue;
    private boolean canceled;
    private boolean released;

    /**
     * Creates a new instance.
     *
     * <p>Instances must be {@link #release() released} after loadings conclude.
     */
    public RtspLoaderWrapper(
        RtspMediaTrack mediaTrack, int trackId, RtpDataChannel.Factory rtpDataChannelFactory) {
      loadInfo = new RtpLoadInfo(mediaTrack, trackId, rtpDataChannelFactory);
      loader = new Loader("ExoPlayer:RtspMediaPeriod:RtspLoaderWrapper " + trackId);
      sampleQueue = SampleQueue.createWithoutDrm(allocator);
      sampleQueue.setUpstreamFormatChangeListener(internalListener);
    }

    /** Starts loading. */
    public void startLoading() {
      loader.startLoading(
          loadInfo.loadable, /* callback= */ internalListener, /* defaultMinRetryCount= */ 0);
    }

    public boolean isSampleQueueReady() {
      return sampleQueue.isReady(/* loadingFinished= */ canceled);
    }

    @ReadDataResult
    public int read(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      return sampleQueue.read(formatHolder, buffer, readFlags, /* loadingFinished= */ canceled);
    }

    /** Cancels loading. */
    public void cancelLoad() {
      if (canceled) {
        return;
      }
      loadInfo.loadable.cancelLoad();
      canceled = true;

      // Update loadingFinished every time loading is canceled.
      updateLoadingFinished();
    }

    /** Resets the {@link Loadable} and {@link SampleQueue} to prepare for an RTSP seek. */
    public void seekTo(long positionUs) {
      loadInfo.loadable.resetForSeek();
      sampleQueue.reset();
      sampleQueue.setStartTimeUs(positionUs);
    }

    /** Releases the instance. */
    public void release() {
      if (released) {
        return;
      }
      loader.release();
      sampleQueue.release();
      released = true;
    }
  }

  /** Groups the info needed for loading one RTSP track in RTP. */
  /* package */ final class RtpLoadInfo {
    /** The {@link RtspMediaTrack}. */
    public final RtspMediaTrack mediaTrack;

    private final RtpDataLoadable loadable;

    @Nullable private String transport;

    /** Creates a new instance. */
    public RtpLoadInfo(
        RtspMediaTrack mediaTrack, int trackId, RtpDataChannel.Factory rtpDataChannelFactory) {
      this.mediaTrack = mediaTrack;

      RtpDataLoadable.EventListener transportEventListener =
          (transport, rtpDataChannel) -> {
            RtpLoadInfo.this.transport = transport;

            if (rtpDataChannel.usesSidebandBinaryData()) {
              rtspClient.registerInterleavedDataChannel(rtpDataChannel);
            }

            maybeSetupTracks();
          };

      this.loadable =
          new RtpDataLoadable(
              trackId,
              mediaTrack,
              /* eventListener= */ transportEventListener,
              /* output= */ internalListener,
              rtpDataChannelFactory);
    }

    /**
     * Returns whether RTP transport is ready. Call {@link #getTransport()} only after transport is
     * ready.
     */
    public boolean isTransportReady() {
      return transport != null;
    }

    /**
     * Gets the transport string for RTP loading.
     *
     * @throws IllegalStateException When transport for this RTP stream is not set.
     */
    public String getTransport() {
      checkStateNotNull(transport);
      return transport;
    }

    /** Gets the {@link Uri} for the loading RTSP track. */
    public Uri getTrackUri() {
      return loadable.rtspMediaTrack.uri;
    }
  }
}
