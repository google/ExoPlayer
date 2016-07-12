/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DecoderInputBuffer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.TrackSelection;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.DefaultTrackOutput;
import com.google.android.exoplayer2.extractor.DefaultTrackOutput.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceFactory;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;

import android.net.Uri;
import android.os.Handler;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Provides a single {@link MediaPeriod} whose data is loaded from a {@link Uri} and extracted using
 * an {@link Extractor}.
 * <p>
 * If the possible input stream container formats are known, pass a factory that instantiates
 * extractors for them to the constructor. Otherwise, pass a {@link DefaultExtractorsFactory} to
 * use the default extractors. When reading a new stream, the first {@link Extractor} in the array
 * of extractors created by the factory that returns {@code true} from
 * {@link Extractor#sniff(ExtractorInput)} will be used to extract samples from the input stream.
 *
 * <p>Note that the built-in extractors for AAC, MPEG TS and FLV streams do not support seeking.
 */
public final class ExtractorMediaSource implements MediaPeriod, MediaSource,
    ExtractorOutput, Loader.Callback<ExtractorMediaSource.ExtractingLoadable>,
    UpstreamFormatChangedListener {

  /**
   * Interface definition for a callback to be notified of {@link ExtractorMediaSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when an error occurs loading media data.
     *
     * @param error The load error.
     */
    void onLoadError(IOException error);

  }

  /**
   * Thrown if the input format could not recognized.
   */
  public static final class UnrecognizedInputFormatException extends ParserException {

    public UnrecognizedInputFormatException(Extractor[] extractors) {
      super("None of the available extractors ("
          + Util.getCommaDelimitedSimpleClassNames(extractors) + ") could read the stream.");
    }

  }

  /**
   * The default minimum number of times to retry loading prior to failing for on-demand streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND = 3;

  /**
   * The default minimum number of times to retry loading prior to failing for live streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE = 6;

  private static final int MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA = -1;

  /**
   * When the source's duration is unknown, it is calculated by adding this value to the largest
   * sample timestamp seen when buffering completes.
   */
  private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10000;

  private final Uri uri;
  private final DataSourceFactory dataSourceFactory;
  private final BandwidthMeter bandwidthMeter;
  private final ExtractorsFactory extractorsFactory;
  private final int minLoadableRetryCount;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private DataSource dataSource;
  private ExtractorHolder extractorHolder;
  private Loader loader;
  private ConditionVariable loadCondition;

  private Callback callback;
  private Allocator allocator;
  private SeekMap seekMap;
  private boolean tracksBuilt;
  private boolean prepared;

  private boolean seenFirstTrackSelection;
  private boolean notifyReset;
  private int enabledTrackCount;
  private DefaultTrackOutput[] sampleQueues;
  private TrackGroupArray tracks;
  private long durationUs;
  private boolean[] trackEnabledStates;
  private long length;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private int extractedSamplesCountAtStartOfLoad;
  private boolean loadingFinished;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
   * @param bandwidthMeter A {@link BandwidthMeter} to notify of loads performed by the source.
   * @param extractorsFactory Factory for {@link Extractor}s to process the media stream. If the
   *     possible formats are known, pass a factory that instantiates extractors for those formats.
   *     Otherwise, pass a {@link DefaultExtractorsFactory} to use default extractors.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public ExtractorMediaSource(Uri uri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, ExtractorsFactory extractorsFactory, Handler eventHandler,
      EventListener eventListener) {
    this(uri, dataSourceFactory, bandwidthMeter, extractorsFactory,
        MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA, eventHandler, eventListener);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
   * @param bandwidthMeter A {@link BandwidthMeter} to notify of loads performed by the source.
   * @param extractorsFactory Factory for {@link Extractor}s to process the media stream. If the
   *     possible formats are known, pass a factory that instantiates extractors for those formats.
   *     Otherwise, pass a {@link DefaultExtractorsFactory} to use default extractors.
   * @param minLoadableRetryCount The minimum number of times that the sample source will retry
   *     if a loading error occurs.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public ExtractorMediaSource(Uri uri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, ExtractorsFactory extractorsFactory, int minLoadableRetryCount,
      Handler eventHandler, EventListener eventListener) {
    this.uri = uri;
    this.dataSourceFactory = dataSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.extractorsFactory = extractorsFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
  }

  // MediaSource implementation.

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public MediaPeriod createPeriod(int index) {
    Assertions.checkArgument(index == 0);
    return this;
  }

  // MediaPeriod implementation.

  @Override
  public void prepare(Callback callback, Allocator allocator, long positionUs) {
    this.callback = callback;
    this.allocator = allocator;

    dataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    extractorHolder = new ExtractorHolder(extractorsFactory.createExtractors(), this);
    loader = new Loader("Loader:ExtractorMediaSource", extractorHolder);
    loadCondition = new ConditionVariable();
    pendingResetPositionUs = C.UNSET_TIME_US;
    sampleQueues = new DefaultTrackOutput[0];
    length = C.LENGTH_UNBOUNDED;

    loadCondition.open();
    startLoading();
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    maybeThrowError();
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public SampleStream[] selectTracks(List<SampleStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);
    // Unselect old tracks.
    for (int i = 0; i < oldStreams.size(); i++) {
      int track = ((SampleStreamImpl) oldStreams.get(i)).track;
      Assertions.checkState(trackEnabledStates[track]);
      enabledTrackCount--;
      trackEnabledStates[track] = false;
      sampleQueues[track].disable();
    }
    // Select new tracks.
    SampleStream[] newStreams = new SampleStream[newSelections.size()];
    for (int i = 0; i < newStreams.length; i++) {
      TrackSelection selection = newSelections.get(i);
      Assertions.checkState(selection.length == 1);
      Assertions.checkState(selection.getTrack(0) == 0);
      int track = selection.group;
      Assertions.checkState(!trackEnabledStates[track]);
      enabledTrackCount++;
      trackEnabledStates[track] = true;
      newStreams[i] = new SampleStreamImpl(track);
    }
    // At the time of the first track selection all queues will be enabled, so we need to disable
    // any that are no longer required.
    if (!seenFirstTrackSelection) {
      for (int i = 0; i < sampleQueues.length; i++) {
        if (!trackEnabledStates[i]) {
          sampleQueues[i].disable();
        }
      }
    }
    if (enabledTrackCount == 0) {
      notifyReset = false;
      if (loader.isLoading()) {
        loader.cancelLoading();
      }
    } else if (seenFirstTrackSelection ? newStreams.length > 0 : positionUs != 0) {
      seekToUs(positionUs);
    }
    seenFirstTrackSelection = true;
    return newStreams;
  }

  @Override
  public boolean continueLoading(long playbackPositionUs) {
    if (loadingFinished) {
      return false;
    }
    boolean continuedLoading = loadCondition.open();
    if (!loader.isLoading()) {
      startLoading();
      continuedLoading = true;
    }
    return continuedLoading;
  }

  @Override
  public long getNextLoadPositionUs() {
    return getBufferedPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    if (notifyReset) {
      notifyReset = false;
      return lastSeekPositionUs;
    }
    return C.UNSET_TIME_US;
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.END_OF_SOURCE_US;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long largestQueuedTimestampUs = getLargestQueuedTimestampUs();
      return largestQueuedTimestampUs == Long.MIN_VALUE ? lastSeekPositionUs
          : largestQueuedTimestampUs;
    }
  }

  @Override
  public long seekToUs(long positionUs) {
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = seekMap.isSeekable() ? positionUs : 0;
    lastSeekPositionUs = positionUs;
    // If we're not pending a reset, see if we can seek within the sample queues.
    boolean seekInsideBuffer = !isPendingReset();
    for (int i = 0; seekInsideBuffer && i < sampleQueues.length; i++) {
      if (trackEnabledStates[i]) {
        seekInsideBuffer = sampleQueues[i].skipToKeyframeBefore(positionUs);
      }
    }
    // If we failed to seek within the sample queues, we need to restart.
    if (!seekInsideBuffer) {
      pendingResetPositionUs = positionUs;
      loadingFinished = false;
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        for (int i = 0; i < sampleQueues.length; i++) {
          sampleQueues[i].reset(trackEnabledStates[i]);
        }
      }
    }
    notifyReset = false;
    return positionUs;
  }

  @Override
  public void release() {
    dataSource = null;
    extractorHolder = null;
    if (loader != null) {
      loader.release(); // Releases extractorHolder via its own reference on the loader's thread.
      loader = null;
    }
    loadCondition = null;
    callback = null;
    allocator = null;
    seekMap = null;
    tracksBuilt = false;
    prepared = false;
    seenFirstTrackSelection = false;
    notifyReset = false;
    enabledTrackCount = 0;
    if (sampleQueues != null) {
      for (DefaultTrackOutput sampleQueue : sampleQueues) {
        sampleQueue.disable();
      }
      sampleQueues = null;
    }
    tracks = null;
    durationUs = 0;
    trackEnabledStates = null;
    length = 0;
    lastSeekPositionUs = 0;
    pendingResetPositionUs = 0;
    extractedSamplesCountAtStartOfLoad = 0;
    loadingFinished = false;
  }

  // SampleStream methods.

  /* package */ boolean isReady(int track) {
    return loadingFinished || (!isPendingReset() && !sampleQueues[track].isEmpty());
  }

  /* package */ void maybeThrowError() throws IOException {
    loader.maybeThrowError();
  }

  /* package */ int readData(int track, FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (notifyReset || isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }

    return sampleQueues[track].readData(formatHolder, buffer, loadingFinished, lastSeekPositionUs);
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    copyLengthFromLoader(loadable);
    loadingFinished = true;
    if (durationUs == C.UNSET_TIME_US) {
      long largestQueuedTimestampUs = getLargestQueuedTimestampUs();
      durationUs = largestQueuedTimestampUs == Long.MIN_VALUE ? 0
          : largestQueuedTimestampUs + DEFAULT_LAST_SAMPLE_DURATION_US;
    }
  }

  @Override
  public void onLoadCanceled(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    copyLengthFromLoader(loadable);
    if (!released && enabledTrackCount > 0) {
      for (int i = 0; i < sampleQueues.length; i++) {
        sampleQueues[i].reset(trackEnabledStates[i]);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public int onLoadError(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    copyLengthFromLoader(loadable);
    notifyLoadError(error);
    if (isLoadableExceptionFatal(error)) {
      return Loader.DONT_RETRY_FATAL;
    }
    int extractedSamplesCount = getExtractedSamplesCount();
    boolean madeProgress = extractedSamplesCount > extractedSamplesCountAtStartOfLoad;
    configureRetry(loadable); // May reset the sample queues.
    extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();
    return madeProgress ? Loader.RETRY_RESET_ERROR_COUNT : Loader.RETRY;
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id) {
    sampleQueues = Arrays.copyOf(sampleQueues, sampleQueues.length + 1);
    DefaultTrackOutput sampleQueue = new DefaultTrackOutput(allocator);
    sampleQueue.setUpstreamFormatChangeListener(this);
    sampleQueues[sampleQueues.length - 1] = sampleQueue;
    return sampleQueue;
  }

  @Override
  public void endTracks() {
    tracksBuilt = true;
    maybeFinishPrepare();
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
    maybeFinishPrepare();
  }

  // UpstreamFormatChangedListener implementation

  @Override
  public void onUpstreamFormatChanged(Format format) {
    maybeFinishPrepare();
  }

  // Internal methods.

  private void maybeFinishPrepare() {
    if (prepared || seekMap == null || !tracksBuilt) {
      return;
    }
    for (DefaultTrackOutput sampleQueue : sampleQueues) {
      if (sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }
    loadCondition.close();
    int trackCount = sampleQueues.length;
    TrackGroup[] trackArray = new TrackGroup[trackCount];
    trackEnabledStates = new boolean[trackCount];
    durationUs = seekMap.getDurationUs();
    for (int i = 0; i < trackCount; i++) {
      trackArray[i] = new TrackGroup(sampleQueues[i].getUpstreamFormat());
    }
    tracks = new TrackGroupArray(trackArray);
    prepared = true;
    callback.onPeriodPrepared(this);
  }

  private void copyLengthFromLoader(ExtractingLoadable loadable) {
    if (length == C.LENGTH_UNBOUNDED) {
      length = loadable.length;
    }
  }

  private void startLoading() {
    ExtractingLoadable loadable = new ExtractingLoadable(uri, dataSource, extractorHolder,
        loadCondition);
    if (prepared) {
      Assertions.checkState(isPendingReset());
      if (durationUs != C.UNSET_TIME_US && pendingResetPositionUs >= durationUs) {
        loadingFinished = true;
        pendingResetPositionUs = C.UNSET_TIME_US;
        return;
      }
      loadable.setLoadPosition(seekMap.getPosition(pendingResetPositionUs));
      pendingResetPositionUs = C.UNSET_TIME_US;
    }
    extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();

    int minRetryCount = minLoadableRetryCount;
    if (minRetryCount == MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA) {
      // We assume on-demand before we're prepared.
      minRetryCount = !prepared || length != C.LENGTH_UNBOUNDED
          || (seekMap != null && seekMap.getDurationUs() != C.UNSET_TIME_US)
          ? DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND
          : DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE;
    }
    loader.startLoading(loadable, this, minRetryCount);
  }

  private void configureRetry(ExtractingLoadable loadable) {
    if (length != C.LENGTH_UNBOUNDED
        || (seekMap != null && seekMap.getDurationUs() != C.UNSET_TIME_US)) {
      // We're playing an on-demand stream. Resume the current loadable, which will
      // request data starting from the point it left off.
    } else {
      // We're playing a stream of unknown length and duration. Assume it's live, and
      // therefore that the data at the uri is a continuously shifting window of the latest
      // available media. For this case there's no way to continue loading from where a
      // previous load finished, so it's necessary to load from the start whenever commencing
      // a new load.
      lastSeekPositionUs = 0;
      notifyReset = prepared;
      for (int i = 0; i < sampleQueues.length; i++) {
        sampleQueues[i].reset(trackEnabledStates[i]);
      }
      loadable.setLoadPosition(0);
    }
  }

  private int getExtractedSamplesCount() {
    int extractedSamplesCount = 0;
    for (DefaultTrackOutput sampleQueue : sampleQueues) {
      extractedSamplesCount += sampleQueue.getWriteIndex();
    }
    return extractedSamplesCount;
  }

  private long getLargestQueuedTimestampUs() {
    long largestQueuedTimestampUs = Long.MIN_VALUE;
    for (DefaultTrackOutput sampleQueue : sampleQueues) {
      largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs,
          sampleQueue.getLargestQueuedTimestampUs());
    }
    return largestQueuedTimestampUs;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.UNSET_TIME_US;
  }

  private boolean isLoadableExceptionFatal(IOException e) {
    return e instanceof UnrecognizedInputFormatException;
  }

  private void notifyLoadError(final IOException error) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadError(error);
        }
      });
    }
  }

  private final class SampleStreamImpl implements SampleStream {

    private final int track;

    public SampleStreamImpl(int track) {
      this.track = track;
    }

    @Override
    public boolean isReady() {
      return ExtractorMediaSource.this.isReady(track);
    }

    @Override
    public void maybeThrowError() throws IOException {
      ExtractorMediaSource.this.maybeThrowError();
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
      return ExtractorMediaSource.this.readData(track, formatHolder, buffer);
    }

  }

  /**
   * Loads the media stream and extracts sample data from it.
   */
  /* package */ final class ExtractingLoadable implements Loadable {

    /**
     * The number of bytes that should be loaded between each each invocation of
     * {@link Callback#onContinueLoadingRequested(SequenceableLoader)}.
     */
    private static final int CONTINUE_LOADING_CHECK_INTERVAL_BYTES = 1024 * 1024;

    private final Uri uri;
    private final DataSource dataSource;
    private final ExtractorHolder extractorHolder;
    private final ConditionVariable loadCondition;
    private final PositionHolder positionHolder;

    private volatile boolean loadCanceled;

    private boolean pendingExtractorSeek;
    private long length;

    public ExtractingLoadable(Uri uri, DataSource dataSource, ExtractorHolder extractorHolder,
        ConditionVariable loadCondition) {
      this.uri = Assertions.checkNotNull(uri);
      this.dataSource = Assertions.checkNotNull(dataSource);
      this.extractorHolder = Assertions.checkNotNull(extractorHolder);
      this.loadCondition = loadCondition;
      this.positionHolder = new PositionHolder();
      this.pendingExtractorSeek = true;
      this.length = C.LENGTH_UNBOUNDED;
    }

    public void setLoadPosition(long position) {
      positionHolder.position = position;
      pendingExtractorSeek = true;
    }

    @Override
    public void cancelLoad() {
      loadCanceled = true;
    }

    @Override
    public boolean isLoadCanceled() {
      return loadCanceled;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      int result = Extractor.RESULT_CONTINUE;
      while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
        ExtractorInput input = null;
        try {
          long position = positionHolder.position;
          length = dataSource.open(new DataSpec(uri, position, C.LENGTH_UNBOUNDED, null));
          if (length != C.LENGTH_UNBOUNDED) {
            length += position;
          }
          input = new DefaultExtractorInput(dataSource, position, length);
          Extractor extractor = extractorHolder.selectExtractor(input);
          if (pendingExtractorSeek) {
            extractor.seek(position);
            pendingExtractorSeek = false;
          }
          while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
            loadCondition.block();
            result = extractor.read(input, positionHolder);
            if (input.getPosition() > position + CONTINUE_LOADING_CHECK_INTERVAL_BYTES) {
              position = input.getPosition();
              loadCondition.close();
              callback.onContinueLoadingRequested(ExtractorMediaSource.this);
            }
          }
        } finally {
          if (result == Extractor.RESULT_SEEK) {
            result = Extractor.RESULT_CONTINUE;
          } else if (input != null) {
            positionHolder.position = input.getPosition();
          }
          dataSource.close();
        }
      }
    }

  }

  /**
   * Stores a list of extractors and a selected extractor when the format has been detected.
   */
  private static final class ExtractorHolder implements Loader.Releasable {

    private final Extractor[] extractors;
    private final ExtractorOutput extractorOutput;
    private Extractor extractor;

    /**
     * Creates a holder that will select an extractor and initialize it using the specified output.
     *
     * @param extractors One or more extractors to choose from.
     * @param extractorOutput The output that will be used to initialize the selected extractor.
     */
    public ExtractorHolder(Extractor[] extractors, ExtractorOutput extractorOutput) {
      this.extractors = extractors;
      this.extractorOutput = extractorOutput;
    }

    /**
     * Returns an initialized extractor for reading {@code input}, and returns the same extractor on
     * later calls.
     *
     * @param input The {@link ExtractorInput} from which data should be read.
     * @return An initialized extractor for reading {@code input}.
     * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
     * @throws IOException Thrown if the input could not be read.
     * @throws InterruptedException Thrown if the thread was interrupted.
     */
    public Extractor selectExtractor(ExtractorInput input)
        throws IOException, InterruptedException {
      if (extractor != null) {
        return extractor;
      }
      for (Extractor extractor : extractors) {
        try {
          if (extractor.sniff(input)) {
            this.extractor = extractor;
            break;
          }
        } catch (EOFException e) {
          // Do nothing.
        } finally {
          input.resetPeekPosition();
        }
      }
      if (extractor == null) {
        throw new UnrecognizedInputFormatException(extractors);
      }
      extractor.init(extractorOutput);
      return extractor;
    }

    @Override
    public void release() {
      if (extractor != null) {
        extractor.release();
        extractor = null;
      }
    }

  }

}
