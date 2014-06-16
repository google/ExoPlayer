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

import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * An abstract {@link TrackRenderer} that uses {@link MediaCodec} to decode samples for rendering.
 */
@TargetApi(16)
public abstract class MediaCodecTrackRenderer extends TrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link MediaCodecTrackRenderer} events.
   */
  public interface EventListener {

    /**
     * Invoked when a decoder fails to initialize.
     *
     * @param e The corresponding exception.
     */
    void onDecoderInitializationError(DecoderInitializationException e);

    /**
     * Invoked when a decoder operation raises a {@link CryptoException}.
     *
     * @param e The corresponding exception.
     */
    void onCryptoError(CryptoException e);

  }

  /**
   * Thrown when a failure occurs instantiating a decoder.
   */
  public static class DecoderInitializationException extends Exception {

    /**
     * The name of the decoder that failed to initialize.
     */
    public final String decoderName;

    public DecoderInitializationException(String decoderName, MediaFormat mediaFormat,
        Exception cause) {
      super("Decoder init failed: " + decoderName + ", " + mediaFormat, cause);
      this.decoderName = decoderName;
    }

  }

  /**
   * If the {@link MediaCodec} is hotswapped (i.e. replaced during playback), this is the period of
   * time during which {@link #isReady()} will report true regardless of whether the new codec has
   * output frames that are ready to be rendered.
   * <p>
   * This allows codec hotswapping to be performed seamlessly, without interrupting the playback of
   * other renderers, provided the new codec is able to decode some frames within this time period.
   */
  private static final long MAX_CODEC_HOTSWAP_TIME_MS = 1000;

  /**
   * There is no pending adaptive reconfiguration work.
   */
  private static final int RECONFIGURATION_STATE_NONE = 0;
  /**
   * Codec configuration data needs to be written into the next buffer.
   */
  private static final int RECONFIGURATION_STATE_WRITE_PENDING = 1;
  /**
   * Codec configuration data has been written into the next buffer, but that buffer still needs to
   * be returned to the codec.
   */
  private static final int RECONFIGURATION_STATE_QUEUE_PENDING = 2;

  public final CodecCounters codecCounters;

  private final DrmSessionManager drmSessionManager;
  private final boolean playClearSamplesWithoutKeys;
  private final SampleSource source;
  private final SampleHolder sampleHolder;
  private final FormatHolder formatHolder;
  private final HashSet<Long> decodeOnlyPresentationTimestamps;
  private final MediaCodec.BufferInfo outputBufferInfo;
  private final EventListener eventListener;
  protected final Handler eventHandler;

  private MediaFormat format;
  private Map<UUID, byte[]> drmInitData;
  private MediaCodec codec;
  private boolean codecIsAdaptive;
  private ByteBuffer[] inputBuffers;
  private ByteBuffer[] outputBuffers;
  private long codecHotswapTimeMs;
  private int inputIndex;
  private int outputIndex;
  private boolean openedDrmSession;
  private boolean codecReconfigured;
  private int codecReconfigurationState;

  private int trackIndex;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeys;
  private boolean waitingForFirstSyncFrame;
  private long currentPositionUs;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler, EventListener eventListener) {
    Assertions.checkState(Util.SDK_INT >= 16);
    this.source = source;
    this.drmSessionManager = drmSessionManager;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    codecCounters = new CodecCounters();
    sampleHolder = new SampleHolder(false);
    formatHolder = new FormatHolder();
    decodeOnlyPresentationTimestamps = new HashSet<Long>();
    outputBufferInfo = new MediaCodec.BufferInfo();
  }

  @Override
  protected int doPrepare() throws ExoPlaybackException {
    try {
      boolean sourcePrepared = source.prepare();
      if (!sourcePrepared) {
        return TrackRenderer.STATE_UNPREPARED;
      }
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }

    for (int i = 0; i < source.getTrackCount(); i++) {
      // TODO: Right now this is getting the mime types of the container format
      // (e.g. audio/mp4 and video/mp4 for fragmented mp4). It needs to be getting the mime types
      // of the actual samples (e.g. audio/mp4a-latm and video/avc).
      if (handlesMimeType(source.getTrackInfo(i).mimeType)) {
        trackIndex = i;
        return TrackRenderer.STATE_PREPARED;
      }
    }

    return TrackRenderer.STATE_IGNORE;
  }

  @SuppressWarnings("unused")
  protected boolean handlesMimeType(String mimeType) {
    return true;
    // TODO: Uncomment once the TODO above is fixed.
    // DecoderInfoUtil.getDecoder(mimeType) != null;
  }

  @Override
  protected void onEnabled(long timeUs, boolean joining) {
    source.enable(trackIndex, timeUs);
    inputStreamEnded = false;
    outputStreamEnded = false;
    waitingForKeys = false;
    currentPositionUs = timeUs;
  }

  /**
   * Configures a newly created {@link MediaCodec}. Sub-classes should
   * override this method if they wish to configure the codec with a
   * non-null surface.
   **/
  protected void configureCodec(MediaCodec codec, android.media.MediaFormat x, MediaCrypto crypto) {
    codec.configure(x, null, crypto, 0);
  }

  protected final void maybeInitCodec() throws ExoPlaybackException {
    if (!shouldInitCodec()) {
      return;
    }

    String mimeType = format.mimeType;
    MediaCrypto mediaCrypto = null;
    boolean requiresSecureDecoder = false;
    if (drmInitData != null) {
      if (drmSessionManager == null) {
        throw new ExoPlaybackException("Media requires a DrmSessionManager");
      }
      if (!openedDrmSession) {
        drmSessionManager.open(drmInitData, mimeType);
        openedDrmSession = true;
      }
      int drmSessionState = drmSessionManager.getState();
      if (drmSessionState == DrmSessionManager.STATE_ERROR) {
        throw new ExoPlaybackException(drmSessionManager.getError());
      } else if (drmSessionState == DrmSessionManager.STATE_OPENED
          || drmSessionState == DrmSessionManager.STATE_OPENED_WITH_KEYS) {
        mediaCrypto = drmSessionManager.getMediaCrypto();
        requiresSecureDecoder = drmSessionManager.requiresSecureDecoderComponent(mimeType);
      } else {
        // The drm session isn't open yet.
        return;
      }
    }

    DecoderInfo selectedDecoderInfo = MediaCodecUtil.getDecoderInfo(mimeType);
    String selectedDecoderName = selectedDecoderInfo.name;
    if (requiresSecureDecoder) {
      selectedDecoderName = getSecureDecoderName(selectedDecoderName);
    }
    codecIsAdaptive = selectedDecoderInfo.adaptive;
    try {
      codec = MediaCodec.createByCodecName(selectedDecoderName);
      configureCodec(codec, format.getFrameworkMediaFormatV16(), mediaCrypto);
      codec.start();
      inputBuffers = codec.getInputBuffers();
      outputBuffers = codec.getOutputBuffers();
    } catch (Exception e) {
      DecoderInitializationException exception = new DecoderInitializationException(
          selectedDecoderName, format, e);
      notifyDecoderInitializationError(exception);
      throw new ExoPlaybackException(exception);
    }
    codecHotswapTimeMs = getState() == TrackRenderer.STATE_STARTED ?
        SystemClock.elapsedRealtime() : -1;
        inputIndex = -1;
        outputIndex = -1;
        waitingForFirstSyncFrame = true;
    codecCounters.codecInitCount++;
  }

  protected boolean shouldInitCodec() {
    return codec == null && format != null;
  }

  protected final boolean codecInitialized() {
    return codec != null;
  }

  protected final boolean haveFormat() {
    return format != null;
  }

  @Override
  protected void onDisabled() {
    releaseCodec();
    format = null;
    drmInitData = null;
    if (openedDrmSession) {
      drmSessionManager.close();
      openedDrmSession = false;
    }
    source.disable(trackIndex);
  }

  protected void releaseCodec() {
    if (codec != null) {
      codecHotswapTimeMs = -1;
      inputIndex = -1;
      outputIndex = -1;
      decodeOnlyPresentationTimestamps.clear();
      inputBuffers = null;
      outputBuffers = null;
      codecReconfigured = false;
      codecIsAdaptive = false;
      codecReconfigurationState = RECONFIGURATION_STATE_NONE;
      codecCounters.codecReleaseCount++;
      try {
        codec.stop();
      } finally {
        try {
          codec.release();
        } finally {
          codec = null;
        }
      }
    }
  }

  @Override
  protected void onReleased() {
    source.release();
  }

  @Override
  protected long getCurrentPositionUs() {
    return currentPositionUs;
  }

  @Override
  protected long getDurationUs() {
    return source.getTrackInfo(trackIndex).durationUs;
  }

  @Override
  protected long getBufferedPositionUs() {
    long sourceBufferedPosition = source.getBufferedPositionUs();
    return sourceBufferedPosition == UNKNOWN_TIME || sourceBufferedPosition == END_OF_TRACK
        ? sourceBufferedPosition : Math.max(sourceBufferedPosition, getCurrentPositionUs());
  }

  @Override
  protected void seekTo(long timeUs) throws ExoPlaybackException {
    currentPositionUs = timeUs;
    source.seekToUs(timeUs);
    inputStreamEnded = false;
    outputStreamEnded = false;
    waitingForKeys = false;
  }

  @Override
  protected void onStarted() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Override
  protected void onStopped() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Override
  protected void doSomeWork(long timeUs) throws ExoPlaybackException {
    try {
      source.continueBuffering(timeUs);
      checkForDiscontinuity();
      if (format == null) {
        readFormat();
      } else if (codec == null && !shouldInitCodec() && getState() == TrackRenderer.STATE_STARTED) {
        discardSamples(timeUs);
      } else {
        if (codec == null && shouldInitCodec()) {
          maybeInitCodec();
        }
        if (codec != null) {
          while (drainOutputBuffer(timeUs)) {}
          while (feedInputBuffer()) {}
        }
      }
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }
  }

  private void readFormat() throws IOException, ExoPlaybackException {
    int result = source.readData(trackIndex, currentPositionUs, formatHolder, sampleHolder, false);
    if (result == SampleSource.FORMAT_READ) {
      onInputFormatChanged(formatHolder);
    }
  }

  private void discardSamples(long timeUs) throws IOException, ExoPlaybackException {
    sampleHolder.data = null;
    int result = SampleSource.SAMPLE_READ;
    while (result == SampleSource.SAMPLE_READ && currentPositionUs <= timeUs) {
      result = source.readData(trackIndex, currentPositionUs, formatHolder, sampleHolder, false);
      if (result == SampleSource.SAMPLE_READ) {
        currentPositionUs = sampleHolder.timeUs;
        codecCounters.discardedSamplesCount++;
      } else if (result == SampleSource.FORMAT_READ) {
        onInputFormatChanged(formatHolder);
      }
    }
  }

  private void checkForDiscontinuity() throws IOException, ExoPlaybackException {
    if (codec == null) {
      return;
    }
    int result = source.readData(trackIndex, currentPositionUs, formatHolder, sampleHolder, true);
    if (result == SampleSource.DISCONTINUITY_READ) {
      flushCodec();
    }
  }

  private void flushCodec() throws ExoPlaybackException {
    codecHotswapTimeMs = -1;
    inputIndex = -1;
    outputIndex = -1;
    decodeOnlyPresentationTimestamps.clear();
    // Workaround for framework bugs.
    // See [redacted], [redacted], [redacted].
    if (Util.SDK_INT >= 18) {
      codec.flush();
    } else {
      releaseCodec();
      maybeInitCodec();
    }
    if (codecReconfigured && format != null) {
      // Any reconfiguration data that we send shortly before the flush may be discarded. We
      // avoid this issue by sending reconfiguration data following every flush.
      codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
    }
  }

  /**
   * @return True if it may be possible to feed more input data. False otherwise.
   * @throws IOException If an error occurs reading data from the upstream source.
   * @throws ExoPlaybackException If an error occurs feeding the input buffer.
   */
  private boolean feedInputBuffer() throws IOException, ExoPlaybackException {
    if (inputStreamEnded) {
      return false;
    }
    if (inputIndex < 0) {
      inputIndex = codec.dequeueInputBuffer(0);
      if (inputIndex < 0) {
        return false;
      }
      sampleHolder.data = inputBuffers[inputIndex];
      sampleHolder.data.clear();
    }

    int result;
    if (waitingForKeys) {
      // We've already read an encrypted sample into sampleHolder, and are waiting for keys.
      result = SampleSource.SAMPLE_READ;
    } else {
      // For adaptive reconfiguration OMX decoders expect all reconfiguration data to be supplied
      // at the start of the buffer that also contains the first frame in the new format.
      if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
        for (int i = 0; i < format.initializationData.size(); i++) {
          byte[] data = format.initializationData.get(i);
          sampleHolder.data.put(data);
        }
        codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
      }
      result = source.readData(trackIndex, currentPositionUs, formatHolder, sampleHolder, false);
    }

    if (result == SampleSource.NOTHING_READ) {
      codecCounters.inputBufferWaitingForSampleCount++;
      return false;
    }
    if (result == SampleSource.DISCONTINUITY_READ) {
      flushCodec();
      return true;
    }
    if (result == SampleSource.FORMAT_READ) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received two formats in a row. Clear the current buffer of any reconfiguration data
        // associated with the first format.
        sampleHolder.data.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      onInputFormatChanged(formatHolder);
      return true;
    }
    if (result == SampleSource.END_OF_STREAM) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received a new format immediately before the end of the stream. We need to clear
        // the corresponding reconfiguration data from the current buffer, but re-write it into
        // a subsequent buffer if there are any (e.g. if the user seeks backwards).
        sampleHolder.data.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      inputStreamEnded = true;
      try {
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        inputIndex = -1;
        codecCounters.queuedEndOfStreamCount++;
      } catch (CryptoException e) {
        notifyCryptoError(e);
        throw new ExoPlaybackException(e);
      }
      return false;
    }
    if (waitingForFirstSyncFrame) {
      // TODO: Find out if it's possible to supply samples prior to the first sync
      // frame for HE-AAC.
      if ((sampleHolder.flags & MediaExtractor.SAMPLE_FLAG_SYNC) == 0) {
        sampleHolder.data.clear();
        if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
          // The buffer we just cleared contained reconfiguration data. We need to re-write this
          // data into a subsequent buffer (if there is one).
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
        }
        return true;
      }
      waitingForFirstSyncFrame = false;
    }
    boolean sampleEncrypted = (sampleHolder.flags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0;
    waitingForKeys = shouldWaitForKeys(sampleEncrypted);
    if (waitingForKeys) {
      return false;
    }
    try {
      int bufferSize = sampleHolder.data.position();
      int adaptiveReconfigurationBytes = bufferSize - sampleHolder.size;
      long presentationTimeUs = sampleHolder.timeUs;
      if (sampleHolder.decodeOnly) {
        decodeOnlyPresentationTimestamps.add(presentationTimeUs);
      }
      if (sampleEncrypted) {
        MediaCodec.CryptoInfo cryptoInfo = getFrameworkCryptoInfo(sampleHolder,
            adaptiveReconfigurationBytes);
        codec.queueSecureInputBuffer(inputIndex, 0, cryptoInfo, presentationTimeUs, 0);
      } else {
        codec.queueInputBuffer(inputIndex, 0 , bufferSize, presentationTimeUs, 0);
      }
      codecCounters.queuedInputBufferCount++;
      if ((sampleHolder.flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
        codecCounters.keyframeCount++;
      }
      inputIndex = -1;
      codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    } catch (CryptoException e) {
      notifyCryptoError(e);
      throw new ExoPlaybackException(e);
    }
    return true;
  }

  private static MediaCodec.CryptoInfo getFrameworkCryptoInfo(SampleHolder sampleHolder,
      int adaptiveReconfigurationBytes) {
    MediaCodec.CryptoInfo cryptoInfo = sampleHolder.cryptoInfo.getFrameworkCryptoInfoV16();
    if (adaptiveReconfigurationBytes == 0) {
      return cryptoInfo;
    }
    // There must be at least one sub-sample, although numBytesOfClearData is permitted to be
    // null if it contains no clear data. Instantiate it if needed, and add the reconfiguration
    // bytes to the clear byte count of the first sub-sample.
    if (cryptoInfo.numBytesOfClearData == null) {
      cryptoInfo.numBytesOfClearData = new int[1];
    }
    cryptoInfo.numBytesOfClearData[0] += adaptiveReconfigurationBytes;
    return cryptoInfo;
  }

  private boolean shouldWaitForKeys(boolean sampleEncrypted) throws ExoPlaybackException {
    if (!openedDrmSession) {
      return false;
    }
    int drmManagerState = drmSessionManager.getState();
    if (drmManagerState == DrmSessionManager.STATE_ERROR) {
      throw new ExoPlaybackException(drmSessionManager.getError());
    }
    if (drmManagerState != DrmSessionManager.STATE_OPENED_WITH_KEYS &&
        (sampleEncrypted || !playClearSamplesWithoutKeys)) {
      return true;
    }
    return false;
  }

  /**
   * Invoked when a new format is read from the upstream {@link SampleSource}.
   *
   * @param formatHolder Holds the new format.
   * @throws ExoPlaybackException If an error occurs reinitializing the {@link MediaCodec}.
   */
  private void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    MediaFormat oldFormat = format;
    format = formatHolder.format;
    drmInitData = formatHolder.drmInitData;
    if (codec != null && canReconfigureCodec(codec, codecIsAdaptive, oldFormat, format)) {
      codecReconfigured = true;
      codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
    } else {
      releaseCodec();
      maybeInitCodec();
    }
  }

  /**
   * Invoked when the output format of the {@link MediaCodec} changes.
   * <p>
   * The default implementation is a no-op.
   *
   * @param format The new output format.
   */
  protected void onOutputFormatChanged(android.media.MediaFormat format) {
    // Do nothing.
  }

  /**
   * Determines whether the existing {@link MediaCodec} should be reconfigured for a new format by
   * sending codec specific initialization data at the start of the next input buffer. If true is
   * returned then the {@link MediaCodec} instance will be reconfigured in this way. If false is
   * returned then the instance will be released, and a new instance will be created for the new
   * format.
   * <p>
   * The default implementation returns false.
   *
   * @param codec The existing {@link MediaCodec} instance.
   * @param codecIsAdaptive Whether the codec is adaptive.
   * @param oldFormat The format for which the existing instance is configured.
   * @param newFormat The new format.
   * @return True if the existing instance can be reconfigured. False otherwise.
   */
  @SuppressWarnings("unused")
  protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
      MediaFormat oldFormat, MediaFormat newFormat) {
    return false;
  }

  @Override
  protected boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    return format != null && !waitingForKeys
        && ((codec == null && !shouldInitCodec()) // We don't want the codec
            || outputIndex >= 0 // Or we have an output buffer ready to release
            || inputIndex < 0 // Or we don't have any input buffers to write to
            || isWithinHotswapPeriod()); // Or the codec is being hotswapped
  }

  private boolean isWithinHotswapPeriod() {
    return SystemClock.elapsedRealtime() < codecHotswapTimeMs + MAX_CODEC_HOTSWAP_TIME_MS;
  }

  /**
   * @return True if it may be possible to drain more output data. False otherwise.
   * @throws ExoPlaybackException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long timeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return false;
    }

    if (outputIndex < 0) {
      outputIndex = codec.dequeueOutputBuffer(outputBufferInfo, 0);
    }

    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
      onOutputFormatChanged(codec.getOutputFormat());
      codecCounters.outputFormatChangedCount++;
      return true;
    } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
      outputBuffers = codec.getOutputBuffers();
      codecCounters.outputBuffersChangedCount++;
      return true;
    } else if (outputIndex < 0) {
      return false;
    }

    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      outputStreamEnded = true;
      return false;
    }

    if (decodeOnlyPresentationTimestamps.remove(outputBufferInfo.presentationTimeUs)) {
      codec.releaseOutputBuffer(outputIndex, false);
      outputIndex = -1;
      return true;
    }

    if (processOutputBuffer(timeUs, codec, outputBuffers[outputIndex], outputBufferInfo,
        outputIndex)) {
      currentPositionUs = outputBufferInfo.presentationTimeUs;
      outputIndex = -1;
      return true;
    }

    return false;
  }

  /**
   * Processes the provided output buffer.
   *
   * @return True if the output buffer was processed (e.g. rendered or discarded) and hence is no
   *     longer required. False otherwise.
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  protected abstract boolean processOutputBuffer(long timeUs, MediaCodec codec, ByteBuffer buffer,
      MediaCodec.BufferInfo bufferInfo, int bufferIndex) throws ExoPlaybackException;

  /**
   * Returns the name of the secure variant of a given decoder.
   */
  private static String getSecureDecoderName(String rawDecoderName) {
    return rawDecoderName + ".secure";
  }

  private void notifyDecoderInitializationError(final DecoderInitializationException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDecoderInitializationError(e);
        }
      });
    }
  }

  private void notifyCryptoError(final CryptoException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onCryptoError(e);
        }
      });
    }
  }

}
