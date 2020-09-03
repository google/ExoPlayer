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
package com.google.android.exoplayer2.mediacodec;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.CallSuper;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.TimedValueQueue;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract renderer that uses {@link MediaCodec} to decode samples for rendering.
 */
public abstract class MediaCodecRenderer extends BaseRenderer {

  /**
   * The modes to operate the {@link MediaCodec}.
   *
   * <p>Allowed values:
   *
   * <ul>
   *   <li>{@link #OPERATION_MODE_SYNCHRONOUS}
   *   <li>{@link #OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD}
   *   <li>{@link #OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING}
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
  @IntDef({
    OPERATION_MODE_SYNCHRONOUS,
    OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD,
    OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING,
  })
  public @interface MediaCodecOperationMode {}

  // TODO: Refactor these constants once internal evaluation completed.
  // Do not assign values 1, 3 and 5 to a new operation mode until the evaluation is completed,
  // otherwise existing clients may operate one of the dropped modes.
  // [Internal ref: b/132684114]
  /** Operates the {@link MediaCodec} in synchronous mode. */
  public static final int OPERATION_MODE_SYNCHRONOUS = 0;
  /**
   * Operates the {@link MediaCodec} in asynchronous mode and routes {@link MediaCodec.Callback}
   * callbacks to a dedicated thread.
   */
  public static final int OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD = 2;
  /**
   * Same as {@link #OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD}, and offloads queueing to another
   * thread.
   */
  public static final int OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING = 4;

  /** Thrown when a failure occurs instantiating a decoder. */
  public static class DecoderInitializationException extends Exception {

    private static final int CUSTOM_ERROR_CODE_BASE = -50000;
    private static final int NO_SUITABLE_DECODER_ERROR = CUSTOM_ERROR_CODE_BASE + 1;
    private static final int DECODER_QUERY_ERROR = CUSTOM_ERROR_CODE_BASE + 2;

    /**
     * The mime type for which a decoder was being initialized.
     */
    public final String mimeType;

    /**
     * Whether it was required that the decoder support a secure output path.
     */
    public final boolean secureDecoderRequired;

    /**
     * The {@link MediaCodecInfo} of the decoder that failed to initialize. Null if no suitable
     * decoder was found.
     */
    @Nullable public final MediaCodecInfo codecInfo;

    /** An optional developer-readable diagnostic information string. May be null. */
    @Nullable public final String diagnosticInfo;

    /**
     * If the decoder failed to initialize and another decoder being used as a fallback also failed
     * to initialize, the {@link DecoderInitializationException} for the fallback decoder. Null if
     * there was no fallback decoder or no suitable decoders were found.
     */
    @Nullable public final DecoderInitializationException fallbackDecoderInitializationException;

    public DecoderInitializationException(
        Format format, @Nullable Throwable cause, boolean secureDecoderRequired, int errorCode) {
      this(
          "Decoder init failed: [" + errorCode + "], " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          /* mediaCodecInfo= */ null,
          buildCustomDiagnosticInfo(errorCode),
          /* fallbackDecoderInitializationException= */ null);
    }

    public DecoderInitializationException(
        Format format,
        @Nullable Throwable cause,
        boolean secureDecoderRequired,
        MediaCodecInfo mediaCodecInfo) {
      this(
          "Decoder init failed: " + mediaCodecInfo.name + ", " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          mediaCodecInfo,
          Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null,
          /* fallbackDecoderInitializationException= */ null);
    }

    private DecoderInitializationException(
        String message,
        @Nullable Throwable cause,
        String mimeType,
        boolean secureDecoderRequired,
        @Nullable MediaCodecInfo mediaCodecInfo,
        @Nullable String diagnosticInfo,
        @Nullable DecoderInitializationException fallbackDecoderInitializationException) {
      super(message, cause);
      this.mimeType = mimeType;
      this.secureDecoderRequired = secureDecoderRequired;
      this.codecInfo = mediaCodecInfo;
      this.diagnosticInfo = diagnosticInfo;
      this.fallbackDecoderInitializationException = fallbackDecoderInitializationException;
    }

    @CheckResult
    private DecoderInitializationException copyWithFallbackException(
        DecoderInitializationException fallbackException) {
      return new DecoderInitializationException(
          getMessage(),
          getCause(),
          mimeType,
          secureDecoderRequired,
          codecInfo,
          diagnosticInfo,
          fallbackException);
    }

    @RequiresApi(21)
    @Nullable
    private static String getDiagnosticInfoV21(@Nullable Throwable cause) {
      if (cause instanceof CodecException) {
        return ((CodecException) cause).getDiagnosticInfo();
      }
      return null;
    }

    private static String buildCustomDiagnosticInfo(int errorCode) {
      String sign = errorCode < 0 ? "neg_" : "";
      return "com.google.android.exoplayer2.mediacodec.MediaCodecRenderer_"
          + sign
          + Math.abs(errorCode);
    }
  }

  /** Indicates no codec operating rate should be set. */
  protected static final float CODEC_OPERATING_RATE_UNSET = -1;

  private static final String TAG = "MediaCodecRenderer";

  /**
   * If the {@link MediaCodec} is hotswapped (i.e. replaced during playback), this is the period of
   * time during which {@link #isReady()} will report true regardless of whether the new codec has
   * output frames that are ready to be rendered.
   * <p>
   * This allows codec hotswapping to be performed seamlessly, without interrupting the playback of
   * other renderers, provided the new codec is able to decode some frames within this time period.
   */
  private static final long MAX_CODEC_HOTSWAP_TIME_MS = 1000;

  // Generally there is zero or one pending output stream offset. We track more offsets to allow for
  // pending output streams that have fewer frames than the codec latency.
  private static final int MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT = 10;

  /**
   * The possible return values for {@link #canKeepCodec(MediaCodec, MediaCodecInfo, Format,
   * Format)}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    KEEP_CODEC_RESULT_NO,
    KEEP_CODEC_RESULT_YES_WITH_FLUSH,
    KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION,
    KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION
  })
  protected @interface KeepCodecResult {}
  /** The codec cannot be kept. */
  protected static final int KEEP_CODEC_RESULT_NO = 0;
  /** The codec can be kept, but must be flushed. */
  protected static final int KEEP_CODEC_RESULT_YES_WITH_FLUSH = 1;
  /**
   * The codec can be kept. It does not need to be flushed, but must be reconfigured by prefixing
   * the next input buffer with the new format's configuration data.
   */
  protected static final int KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION = 2;
  /** The codec can be kept. It does not need to be flushed and no reconfiguration is required. */
  protected static final int KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RECONFIGURATION_STATE_NONE,
    RECONFIGURATION_STATE_WRITE_PENDING,
    RECONFIGURATION_STATE_QUEUE_PENDING
  })
  private @interface ReconfigurationState {}
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

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({DRAIN_STATE_NONE, DRAIN_STATE_SIGNAL_END_OF_STREAM, DRAIN_STATE_WAIT_END_OF_STREAM})
  private @interface DrainState {}
  /** The codec is not being drained. */
  private static final int DRAIN_STATE_NONE = 0;
  /** The codec needs to be drained, but we haven't signaled an end of stream to it yet. */
  private static final int DRAIN_STATE_SIGNAL_END_OF_STREAM = 1;
  /** The codec needs to be drained, and we're waiting for it to output an end of stream. */
  private static final int DRAIN_STATE_WAIT_END_OF_STREAM = 2;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DRAIN_ACTION_NONE,
    DRAIN_ACTION_FLUSH,
    DRAIN_ACTION_UPDATE_DRM_SESSION,
    DRAIN_ACTION_REINITIALIZE
  })
  private @interface DrainAction {}
  /** No special action should be taken. */
  private static final int DRAIN_ACTION_NONE = 0;
  /** The codec should be flushed. */
  private static final int DRAIN_ACTION_FLUSH = 1;
  /** The codec should be flushed and updated to use the pending DRM session. */
  private static final int DRAIN_ACTION_UPDATE_DRM_SESSION = 2;
  /** The codec should be reinitialized. */
  private static final int DRAIN_ACTION_REINITIALIZE = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ADAPTATION_WORKAROUND_MODE_NEVER,
    ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION,
    ADAPTATION_WORKAROUND_MODE_ALWAYS
  })
  private @interface AdaptationWorkaroundMode {}

  /**
   * The adaptation workaround is never used.
   */
  private static final int ADAPTATION_WORKAROUND_MODE_NEVER = 0;
  /**
   * The adaptation workaround is used when adapting between formats of the same resolution only.
   */
  private static final int ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION = 1;
  /**
   * The adaptation workaround is always used when adapting between formats.
   */
  private static final int ADAPTATION_WORKAROUND_MODE_ALWAYS = 2;

  /**
   * H.264/AVC buffer to queue when using the adaptation workaround (see {@link
   * #codecAdaptationWorkaroundMode(String)}. Consists of three NAL units with start codes: Baseline
   * sequence/picture parameter sets and a 32 * 32 pixel IDR slice. This stream can be queued to
   * force a resolution change when adapting to a new format.
   */
  private static final byte[] ADAPTATION_WORKAROUND_BUFFER =
      new byte[] {
        0, 0, 1, 103, 66, -64, 11, -38, 37, -112, 0, 0, 1, 104, -50, 15, 19, 32, 0, 0, 1, 101, -120,
        -124, 13, -50, 113, 24, -96, 0, 47, -65, 28, 49, -61, 39, 93, 120
      };

  private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;

  private final MediaCodecSelector mediaCodecSelector;
  private final boolean enableDecoderFallback;
  private final float assumedMinimumCodecOperatingRate;
  private final DecoderInputBuffer buffer;
  private final DecoderInputBuffer flagsOnlyBuffer;
  private final BatchBuffer bypassBatchBuffer;
  private final TimedValueQueue<Format> formatQueue;
  private final ArrayList<Long> decodeOnlyPresentationTimestamps;
  private final MediaCodec.BufferInfo outputBufferInfo;
  private final long[] pendingOutputStreamStartPositionsUs;
  private final long[] pendingOutputStreamOffsetsUs;
  private final long[] pendingOutputStreamSwitchTimesUs;

  @Nullable private Format inputFormat;
  @Nullable private Format outputFormat;
  @Nullable private DrmSession codecDrmSession;
  @Nullable private DrmSession sourceDrmSession;
  @Nullable private MediaCrypto mediaCrypto;
  private boolean mediaCryptoRequiresSecureDecoder;
  private float operatingRate;
  @Nullable private MediaCodec codec;
  @Nullable private MediaCodecAdapter codecAdapter;
  @Nullable private Format codecInputFormat;
  @Nullable private MediaFormat codecOutputMediaFormat;
  private boolean codecOutputMediaFormatChanged;
  private float codecOperatingRate;
  @Nullable private ArrayDeque<MediaCodecInfo> availableCodecInfos;
  @Nullable private DecoderInitializationException preferredDecoderInitializationException;
  @Nullable private MediaCodecInfo codecInfo;
  @AdaptationWorkaroundMode private int codecAdaptationWorkaroundMode;
  private boolean codecNeedsReconfigureWorkaround;
  private boolean codecNeedsDiscardToSpsWorkaround;
  private boolean codecNeedsFlushWorkaround;
  private boolean codecNeedsSosFlushWorkaround;
  private boolean codecNeedsEosFlushWorkaround;
  private boolean codecNeedsEosOutputExceptionWorkaround;
  private boolean codecNeedsMonoChannelCountWorkaround;
  private boolean codecNeedsAdaptationWorkaroundBuffer;
  private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
  private boolean codecNeedsEosPropagation;
  @Nullable private C2Mp3TimestampTracker c2Mp3TimestampTracker;
  private ByteBuffer[] inputBuffers;
  private ByteBuffer[] outputBuffers;
  private long codecHotswapDeadlineMs;
  private int inputIndex;
  private int outputIndex;
  @Nullable private ByteBuffer outputBuffer;
  private boolean isDecodeOnlyOutputBuffer;
  private boolean isLastOutputBuffer;
  private boolean bypassEnabled;
  private boolean bypassDrainAndReinitialize;
  private boolean codecReconfigured;
  @ReconfigurationState private int codecReconfigurationState;
  @DrainState private int codecDrainState;
  @DrainAction private int codecDrainAction;
  private boolean codecReceivedBuffers;
  private boolean codecReceivedEos;
  private boolean codecHasOutputMediaFormat;
  private long largestQueuedPresentationTimeUs;
  private long lastBufferInStreamPresentationTimeUs;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForFirstSampleInFormat;
  private boolean pendingOutputEndOfStream;
  @MediaCodecOperationMode private int mediaCodecOperationMode;
  @Nullable private ExoPlaybackException pendingPlaybackException;
  protected DecoderCounters decoderCounters;
  private long outputStreamStartPositionUs;
  private long outputStreamOffsetUs;
  private int pendingOutputStreamOffsetCount;

  /**
   * @param trackType The track type that the renderer handles. One of the {@code C.TRACK_TYPE_*}
   *     constants defined in {@link C}.
   * @param mediaCodecSelector A decoder selector.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is less efficient or slower
   *     than the primary decoder.
   * @param assumedMinimumCodecOperatingRate A codec operating rate that all codecs instantiated by
   *     this renderer are assumed to meet implicitly (i.e. without the operating rate being set
   *     explicitly using {@link MediaFormat#KEY_OPERATING_RATE}).
   */
  public MediaCodecRenderer(
      int trackType,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      float assumedMinimumCodecOperatingRate) {
    super(trackType);
    this.mediaCodecSelector = Assertions.checkNotNull(mediaCodecSelector);
    this.enableDecoderFallback = enableDecoderFallback;
    this.assumedMinimumCodecOperatingRate = assumedMinimumCodecOperatingRate;
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
    formatQueue = new TimedValueQueue<>();
    decodeOnlyPresentationTimestamps = new ArrayList<>();
    outputBufferInfo = new MediaCodec.BufferInfo();
    operatingRate = 1f;
    mediaCodecOperationMode = OPERATION_MODE_SYNCHRONOUS;
    pendingOutputStreamStartPositionsUs = new long[MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT];
    pendingOutputStreamOffsetsUs = new long[MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT];
    pendingOutputStreamSwitchTimesUs = new long[MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT];
    outputStreamStartPositionUs = C.TIME_UNSET;
    outputStreamOffsetUs = C.TIME_UNSET;
    bypassBatchBuffer = new BatchBuffer();
    resetCodecStateForRelease();
  }

  /**
   * Set the mode of operation of the underlying {@link MediaCodec}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release. It should
   * only be called before the renderer is used.
   *
   * @param mode The mode of the MediaCodec. The supported modes are:
   *     <ul>
   *       <li>{@link #OPERATION_MODE_SYNCHRONOUS}: The {@link MediaCodec} will operate in
   *           synchronous mode.
   *       <li>{@link #OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD}: The {@link MediaCodec} will
   *           operate in asynchronous mode and {@link MediaCodec.Callback} callbacks will be routed
   *           to a dedicated thread. This mode requires API level &ge; 23; if the API level is &le;
   *           22, the operation mode will be set to {@link #OPERATION_MODE_SYNCHRONOUS}.
   *       <li>{@link #OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING}: Same as
   *           {@link #OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD} and, in addition, input buffers
   *           will be submitted to the {@link MediaCodec} in a separate thread.
   *     </ul>
   *     By default, the operation mode is set to {@link
   *     MediaCodecRenderer#OPERATION_MODE_SYNCHRONOUS}.
   */
  public void experimentalSetMediaCodecOperationMode(@MediaCodecOperationMode int mode) {
    mediaCodecOperationMode = mode;
  }

  @Override
  @AdaptiveSupport
  public final int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  @Capabilities
  public final int supportsFormat(Format format) throws ExoPlaybackException {
    try {
      return supportsFormat(mediaCodecSelector, format);
    } catch (DecoderQueryException e) {
      throw createRendererException(e, format);
    }
  }

  /**
   * Returns the {@link Capabilities} for the given {@link Format}.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format}.
   * @return The {@link Capabilities} for this {@link Format}.
   * @throws DecoderQueryException If there was an error querying decoders.
   */
  @Capabilities
  protected abstract int supportsFormat(
      MediaCodecSelector mediaCodecSelector,
      Format format)
      throws DecoderQueryException;

  /**
   * Returns a list of decoders that can decode media in the specified format, in priority order.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected abstract List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException;

  /**
   * Configures a newly created {@link MediaCodec}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param codecAdapter The {@link MediaCodecAdapter} to configure.
   * @param format The {@link Format} for which the codec is being configured.
   * @param crypto For drm protected playbacks, a {@link MediaCrypto} to use for decryption.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   */
  protected abstract void configureCodec(
      MediaCodecInfo codecInfo,
      MediaCodecAdapter codecAdapter,
      Format format,
      @Nullable MediaCrypto crypto,
      float codecOperatingRate);

  protected final void maybeInitCodecOrBypass() throws ExoPlaybackException {
    if (codec != null || bypassEnabled || inputFormat == null) {
      // We have a codec, are bypassing it, or don't have a format to decide how to render.
      return;
    }

    if (sourceDrmSession == null && shouldUseBypass(inputFormat)) {
      initBypass(inputFormat);
      return;
    }

    setCodecDrmSession(sourceDrmSession);

    String mimeType = inputFormat.sampleMimeType;
    if (codecDrmSession != null) {
      if (mediaCrypto == null) {
        @Nullable
        FrameworkMediaCrypto sessionMediaCrypto = getFrameworkMediaCrypto(codecDrmSession);
        if (sessionMediaCrypto == null) {
          @Nullable DrmSessionException drmError = codecDrmSession.getError();
          if (drmError != null) {
            // Continue for now. We may be able to avoid failure if the session recovers, or if a
            // new input format causes the session to be replaced before it's used.
          } else {
            // The drm session isn't open yet.
            return;
          }
        } else {
          try {
            mediaCrypto = new MediaCrypto(sessionMediaCrypto.uuid, sessionMediaCrypto.sessionId);
          } catch (MediaCryptoException e) {
            throw createRendererException(e, inputFormat);
          }
          mediaCryptoRequiresSecureDecoder =
              !sessionMediaCrypto.forceAllowInsecureDecoderComponents
                  && mediaCrypto.requiresSecureDecoderComponent(mimeType);
        }
      }
      if (FrameworkMediaCrypto.WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC) {
        @DrmSession.State int drmSessionState = codecDrmSession.getState();
        if (drmSessionState == DrmSession.STATE_ERROR) {
          throw createRendererException(codecDrmSession.getError(), inputFormat);
        } else if (drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS) {
          // Wait for keys.
          return;
        }
      }
    }

    try {
      maybeInitCodecWithFallback(mediaCrypto, mediaCryptoRequiresSecureDecoder);
    } catch (DecoderInitializationException e) {
      throw createRendererException(e, inputFormat);
    }
  }

  /**
   * Returns whether buffers in the input format can be processed without a codec.
   *
   * <p>This method is only called if the content is not DRM protected, because if the content is
   * DRM protected use of bypass is never possible.
   *
   * @param format The input {@link Format}.
   * @return Whether playback bypassing {@link MediaCodec} is supported.
   */
  protected boolean shouldUseBypass(Format format) {
    return false;
  }

  protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
    return true;
  }

  /**
   * Returns whether the codec needs the renderer to propagate the end-of-stream signal directly,
   * rather than by using an end-of-stream buffer queued to the codec.
   */
  protected boolean getCodecNeedsEosPropagation() {
    return false;
  }

  /**
   * Sets an exception to be re-thrown by render.
   *
   * @param exception The exception.
   */
  protected final void setPendingPlaybackException(ExoPlaybackException exception) {
    pendingPlaybackException = exception;
  }

  /**
   * Updates the output formats for the specified output buffer timestamp, calling {@link
   * #onOutputFormatChanged} if a change has occurred.
   *
   * <p>Subclasses should only call this method if operating in a mode where buffers are not
   * dequeued from the decoder, for example when using video tunneling).
   *
   * @throws ExoPlaybackException Thrown if an error occurs as a result of the output format change.
   */
  protected final void updateOutputFormatForTime(long presentationTimeUs)
      throws ExoPlaybackException {
    boolean outputFormatChanged = false;
    @Nullable Format format = formatQueue.pollFloor(presentationTimeUs);
    if (format == null && codecOutputMediaFormatChanged) {
      // If the codec's output MediaFormat has changed then there should be a corresponding Format
      // change, which we've not found. Check the Format queue in case the corresponding
      // presentation timestamp is greater than presentationTimeUs, which can happen for some codecs
      // [Internal ref: b/162719047].
      format = formatQueue.pollFirst();
    }
    if (format != null) {
      outputFormat = format;
      outputFormatChanged = true;
    }
    if (outputFormatChanged || (codecOutputMediaFormatChanged && outputFormat != null)) {
      onOutputFormatChanged(outputFormat, codecOutputMediaFormat);
      codecOutputMediaFormatChanged = false;
    }
  }

  @Nullable
  protected Format getInputFormat() {
    return inputFormat;
  }

  @Nullable
  protected final Format getOutputFormat() {
    return outputFormat;
  }

  @Nullable
  protected final MediaCodec getCodec() {
    return codec;
  }

  @Nullable
  protected final MediaFormat getCodecOutputMediaFormat() {
    return codecOutputMediaFormat;
  }

  @Nullable
  protected final MediaCodecInfo getCodecInfo() {
    return codecInfo;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    if (this.outputStreamOffsetUs == C.TIME_UNSET) {
      checkState(this.outputStreamStartPositionUs == C.TIME_UNSET);
      this.outputStreamStartPositionUs = startPositionUs;
      this.outputStreamOffsetUs = offsetUs;
    } else {
      if (pendingOutputStreamOffsetCount == pendingOutputStreamOffsetsUs.length) {
        Log.w(
            TAG,
            "Too many stream changes, so dropping offset: "
                + pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1]);
      } else {
        pendingOutputStreamOffsetCount++;
      }
      pendingOutputStreamStartPositionsUs[pendingOutputStreamOffsetCount - 1] = startPositionUs;
      pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1] = offsetUs;
      pendingOutputStreamSwitchTimesUs[pendingOutputStreamOffsetCount - 1] =
          largestQueuedPresentationTimeUs;
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false;
    outputStreamEnded = false;
    pendingOutputEndOfStream = false;
    if (bypassEnabled) {
      bypassBatchBuffer.flush();
    } else {
      flushOrReinitializeCodec();
    }
    // If there is a format change on the input side still pending propagation to the output, we
    // need to queue a format next time a buffer is read. This is because we may not read a new
    // input format after the position reset.
    if (formatQueue.size() > 0) {
      waitingForFirstSampleInFormat = true;
    }
    formatQueue.clear();
    if (pendingOutputStreamOffsetCount != 0) {
      outputStreamOffsetUs = pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1];
      outputStreamStartPositionUs =
          pendingOutputStreamStartPositionsUs[pendingOutputStreamOffsetCount - 1];
      pendingOutputStreamOffsetCount = 0;
    }
  }

  @Override
  public void setOperatingRate(float operatingRate) throws ExoPlaybackException {
    this.operatingRate = operatingRate;
    if (codec != null
        && codecDrainAction != DRAIN_ACTION_REINITIALIZE
        && getState() != STATE_DISABLED) {
      updateCodecOperatingRate();
    }
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    outputStreamStartPositionUs = C.TIME_UNSET;
    outputStreamOffsetUs = C.TIME_UNSET;
    pendingOutputStreamOffsetCount = 0;
    if (sourceDrmSession != null || codecDrmSession != null) {
      // TODO: Do something better with this case.
      onReset();
    } else {
      flushOrReleaseCodec();
    }
  }

  @Override
  protected void onReset() {
    try {
      disableBypass();
      releaseCodec();
    } finally {
      setSourceDrmSession(null);
    }
  }

  private void disableBypass() {
    bypassDrainAndReinitialize = false;
    bypassBatchBuffer.clear();
    bypassEnabled = false;
  }

  protected void releaseCodec() {
    try {
      if (codecAdapter != null) {
        codecAdapter.shutdown();
      }
      if (codec != null) {
        decoderCounters.decoderReleaseCount++;
        codec.release();
      }
    } finally {
      codec = null;
      codecAdapter = null;
      try {
        if (mediaCrypto != null) {
          mediaCrypto.release();
        }
      } finally {
        mediaCrypto = null;
        setCodecDrmSession(null);
        resetCodecStateForRelease();
      }
    }
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
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (pendingOutputEndOfStream) {
      pendingOutputEndOfStream = false;
      processEndOfStream();
    }
    if (pendingPlaybackException != null) {
      ExoPlaybackException playbackException = pendingPlaybackException;
      pendingPlaybackException = null;
      throw playbackException;
    }

    try {
      if (outputStreamEnded) {
        renderToEndOfStream();
        return;
      }
      if (inputFormat == null && !readToFlagsOnlyBuffer(/* requireFormat= */ true)) {
        // We still don't have a format and can't make progress without one.
        return;
      }
      // We have a format.
      maybeInitCodecOrBypass();
      if (bypassEnabled) {
        TraceUtil.beginSection("bypassRender");
        while (bypassRender(positionUs, elapsedRealtimeUs)) {}
        TraceUtil.endSection();
      } else if (codec != null) {
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection();
      } else {
        decoderCounters.skippedInputBufferCount += skipSource(positionUs);
        // We need to read any format changes despite not having a codec so that drmSession can be
        // updated, and so that we have the most recent format should the codec be initialized. We
        // may also reach the end of the stream. Note that readSource will not read a sample into a
        // flags-only buffer.
        readToFlagsOnlyBuffer(/* requireFormat= */ false);
      }
      decoderCounters.ensureUpdated();
    } catch (IllegalStateException e) {
      if (isMediaCodecException(e)) {
        throw createRendererException(createDecoderException(e, getCodecInfo()), inputFormat);
      }
      throw e;
    }
  }

  /**
   * Flushes the codec. If flushing is not possible, the codec will be released and re-instantiated.
   * This method is a no-op if the codec is {@code null}.
   *
   * <p>The implementation of this method calls {@link #flushOrReleaseCodec()}, and {@link
   * #maybeInitCodecOrBypass()} if the codec needs to be re-instantiated.
   *
   * @return Whether the codec was released and reinitialized, rather than being flushed.
   * @throws ExoPlaybackException If an error occurs re-instantiating the codec.
   */
  protected final boolean flushOrReinitializeCodec() throws ExoPlaybackException {
    boolean released = flushOrReleaseCodec();
    if (released) {
      maybeInitCodecOrBypass();
    }
    return released;
  }

  /**
   * Flushes the codec. If flushing is not possible, the codec will be released. This method is a
   * no-op if the codec is {@code null}.
   *
   * @return Whether the codec was released.
   */
  protected boolean flushOrReleaseCodec() {
    if (codec == null) {
      return false;
    }
    if (codecDrainAction == DRAIN_ACTION_REINITIALIZE
        || codecNeedsFlushWorkaround
        || (codecNeedsSosFlushWorkaround && !codecHasOutputMediaFormat)
        || (codecNeedsEosFlushWorkaround && codecReceivedEos)) {
      releaseCodec();
      return true;
    }
    try {
      codecAdapter.flush();
    } finally {
      resetCodecStateForFlush();
    }
    return false;
  }

  /** Resets the renderer internal state after a codec flush. */
  @CallSuper
  protected void resetCodecStateForFlush() {
    resetInputBuffer();
    resetOutputBuffer();
    codecHotswapDeadlineMs = C.TIME_UNSET;
    codecReceivedEos = false;
    codecReceivedBuffers = false;
    codecNeedsAdaptationWorkaroundBuffer = false;
    shouldSkipAdaptationWorkaroundOutputBuffer = false;
    isDecodeOnlyOutputBuffer = false;
    isLastOutputBuffer = false;
    decodeOnlyPresentationTimestamps.clear();
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET;
    if (c2Mp3TimestampTracker != null) {
      c2Mp3TimestampTracker.reset();
    }
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
    // Reconfiguration data sent shortly before the flush may not have been processed by the
    // decoder. If the codec has been reconfigured we always send reconfiguration data again to
    // guarantee that it's processed.
    codecReconfigurationState =
        codecReconfigured ? RECONFIGURATION_STATE_WRITE_PENDING : RECONFIGURATION_STATE_NONE;
  }

  /**
   * Resets the renderer internal state after a codec release.
   *
   * <p>Note that this only needs to reset state variables that are changed in addition to those
   * already changed in {@link #resetCodecStateForFlush()}.
   */
  @CallSuper
  protected void resetCodecStateForRelease() {
    resetCodecStateForFlush();

    pendingPlaybackException = null;
    c2Mp3TimestampTracker = null;
    availableCodecInfos = null;
    codecInfo = null;
    codecInputFormat = null;
    codecOutputMediaFormat = null;
    codecOutputMediaFormatChanged = false;
    codecHasOutputMediaFormat = false;
    codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    codecAdaptationWorkaroundMode = ADAPTATION_WORKAROUND_MODE_NEVER;
    codecNeedsReconfigureWorkaround = false;
    codecNeedsDiscardToSpsWorkaround = false;
    codecNeedsFlushWorkaround = false;
    codecNeedsSosFlushWorkaround = false;
    codecNeedsEosFlushWorkaround = false;
    codecNeedsEosOutputExceptionWorkaround = false;
    codecNeedsMonoChannelCountWorkaround = false;
    codecNeedsEosPropagation = false;
    codecReconfigured = false;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    resetCodecBuffers();
    mediaCryptoRequiresSecureDecoder = false;
  }

  protected MediaCodecDecoderException createDecoderException(
      Throwable cause, @Nullable MediaCodecInfo codecInfo) {
    return new MediaCodecDecoderException(cause, codecInfo);
  }

  /** Reads into {@link #flagsOnlyBuffer} and returns whether a {@link Format} was read. */
  private boolean readToFlagsOnlyBuffer(boolean requireFormat) throws ExoPlaybackException {
    FormatHolder formatHolder = getFormatHolder();
    flagsOnlyBuffer.clear();
    @SampleStream.ReadDataResult
    int result = readSource(formatHolder, flagsOnlyBuffer, requireFormat);
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder);
      return true;
    } else if (result == C.RESULT_BUFFER_READ && flagsOnlyBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      processEndOfStream();
    }
    return false;
  }

  private void maybeInitCodecWithFallback(
      MediaCrypto crypto, boolean mediaCryptoRequiresSecureDecoder)
      throws DecoderInitializationException {
    if (availableCodecInfos == null) {
      try {
        List<MediaCodecInfo> allAvailableCodecInfos =
            getAvailableCodecInfos(mediaCryptoRequiresSecureDecoder);
        availableCodecInfos = new ArrayDeque<>();
        if (enableDecoderFallback) {
          availableCodecInfos.addAll(allAvailableCodecInfos);
        } else if (!allAvailableCodecInfos.isEmpty()) {
          availableCodecInfos.add(allAvailableCodecInfos.get(0));
        }
        preferredDecoderInitializationException = null;
      } catch (DecoderQueryException e) {
        throw new DecoderInitializationException(
            inputFormat,
            e,
            mediaCryptoRequiresSecureDecoder,
            DecoderInitializationException.DECODER_QUERY_ERROR);
      }
    }

    if (availableCodecInfos.isEmpty()) {
      throw new DecoderInitializationException(
          inputFormat,
          /* cause= */ null,
          mediaCryptoRequiresSecureDecoder,
          DecoderInitializationException.NO_SUITABLE_DECODER_ERROR);
    }

    while (codec == null) {
      MediaCodecInfo codecInfo = availableCodecInfos.peekFirst();
      if (!shouldInitCodec(codecInfo)) {
        return;
      }
      try {
        initCodec(codecInfo, crypto);
      } catch (Exception e) {
        Log.w(TAG, "Failed to initialize decoder: " + codecInfo, e);
        // This codec failed to initialize, so fall back to the next codec in the list (if any). We
        // won't try to use this codec again unless there's a format change or the renderer is
        // disabled and re-enabled.
        availableCodecInfos.removeFirst();
        DecoderInitializationException exception =
            new DecoderInitializationException(
                inputFormat, e, mediaCryptoRequiresSecureDecoder, codecInfo);
        if (preferredDecoderInitializationException == null) {
          preferredDecoderInitializationException = exception;
        } else {
          preferredDecoderInitializationException =
              preferredDecoderInitializationException.copyWithFallbackException(exception);
        }
        if (availableCodecInfos.isEmpty()) {
          throw preferredDecoderInitializationException;
        }
      }
    }

    availableCodecInfos = null;
  }

  private List<MediaCodecInfo> getAvailableCodecInfos(boolean mediaCryptoRequiresSecureDecoder)
      throws DecoderQueryException {
    List<MediaCodecInfo> codecInfos =
        getDecoderInfos(mediaCodecSelector, inputFormat, mediaCryptoRequiresSecureDecoder);
    if (codecInfos.isEmpty() && mediaCryptoRequiresSecureDecoder) {
      // The drm session indicates that a secure decoder is required, but the device does not
      // have one. Assuming that supportsFormat indicated support for the media being played, we
      // know that it does not require a secure output path. Most CDM implementations allow
      // playback to proceed with a non-secure decoder in this case, so we try our luck.
      codecInfos =
          getDecoderInfos(mediaCodecSelector, inputFormat, /* requiresSecureDecoder= */ false);
      if (!codecInfos.isEmpty()) {
        Log.w(
            TAG,
            "Drm session requires secure decoder for "
                + inputFormat.sampleMimeType
                + ", but no secure decoder available. Trying to proceed with "
                + codecInfos
                + ".");
      }
    }
    return codecInfos;
  }

  /**
   * Configures rendering where no codec is used. Called instead of {@link
   * #configureCodec(MediaCodecInfo, MediaCodecAdapter, Format, MediaCrypto, float)} when no codec
   * is used to render.
   */
  private void initBypass(Format format) {
    disableBypass(); // In case of transition between 2 bypass formats.

    String mimeType = format.sampleMimeType;
    if (!MimeTypes.AUDIO_AAC.equals(mimeType)
        && !MimeTypes.AUDIO_MPEG.equals(mimeType)
        && !MimeTypes.AUDIO_OPUS.equals(mimeType)) {
      // TODO(b/154746451): Batching provokes frame drops in non offload.
      bypassBatchBuffer.setMaxAccessUnitCount(1);
    } else {
      bypassBatchBuffer.setMaxAccessUnitCount(BatchBuffer.DEFAULT_BATCH_SIZE_ACCESS_UNITS);
    }
    bypassEnabled = true;
  }

  private void initCodec(MediaCodecInfo codecInfo, MediaCrypto crypto) throws Exception {
    long codecInitializingTimestamp;
    long codecInitializedTimestamp;
    MediaCodec codec = null;
    String codecName = codecInfo.name;

    float codecOperatingRate =
        Util.SDK_INT < 23
            ? CODEC_OPERATING_RATE_UNSET
            : getCodecOperatingRateV23(operatingRate, inputFormat, getStreamFormats());
    if (codecOperatingRate <= assumedMinimumCodecOperatingRate) {
      codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    }

    MediaCodecAdapter codecAdapter = null;
    try {
      codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createCodec:" + codecName);
      codec = MediaCodec.createByCodecName(codecName);
      if (mediaCodecOperationMode == OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD
          && Util.SDK_INT >= 23) {
        codecAdapter = new AsynchronousMediaCodecAdapter(codec, getTrackType());
      } else if (mediaCodecOperationMode
              == OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING
          && Util.SDK_INT >= 23) {
        codecAdapter =
            new AsynchronousMediaCodecAdapter(
                codec, /* enableAsynchronousQueueing= */ true, getTrackType());
      } else {
        codecAdapter = new SynchronousMediaCodecAdapter(codec);
      }

      TraceUtil.endSection();
      TraceUtil.beginSection("configureCodec");
      configureCodec(codecInfo, codecAdapter, inputFormat, crypto, codecOperatingRate);
      TraceUtil.endSection();
      TraceUtil.beginSection("startCodec");
      codecAdapter.start();
      TraceUtil.endSection();
      codecInitializedTimestamp = SystemClock.elapsedRealtime();
      getCodecBuffers(codec);
    } catch (Exception e) {
      if (codecAdapter != null) {
        codecAdapter.shutdown();
      }
      if (codec != null) {
        resetCodecBuffers();
        codec.release();
      }
      throw e;
    }

    this.codec = codec;
    this.codecAdapter = codecAdapter;
    this.codecInfo = codecInfo;
    this.codecOperatingRate = codecOperatingRate;
    codecInputFormat = inputFormat;
    codecAdaptationWorkaroundMode = codecAdaptationWorkaroundMode(codecName);
    codecNeedsReconfigureWorkaround = codecNeedsReconfigureWorkaround(codecName);
    codecNeedsDiscardToSpsWorkaround =
        codecNeedsDiscardToSpsWorkaround(codecName, codecInputFormat);
    codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName);
    codecNeedsSosFlushWorkaround = codecNeedsSosFlushWorkaround(codecName);
    codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
    codecNeedsEosOutputExceptionWorkaround = codecNeedsEosOutputExceptionWorkaround(codecName);
    codecNeedsMonoChannelCountWorkaround =
        codecNeedsMonoChannelCountWorkaround(codecName, codecInputFormat);
    codecNeedsEosPropagation =
        codecNeedsEosPropagationWorkaround(codecInfo) || getCodecNeedsEosPropagation();
    if ("c2.android.mp3.decoder".equals(codecInfo.name)) {
      c2Mp3TimestampTracker = new C2Mp3TimestampTracker();
    }

    if (getState() == STATE_STARTED) {
      codecHotswapDeadlineMs = SystemClock.elapsedRealtime() + MAX_CODEC_HOTSWAP_TIME_MS;
    }

    decoderCounters.decoderInitCount++;
    long elapsed = codecInitializedTimestamp - codecInitializingTimestamp;
    onCodecInitialized(codecName, codecInitializedTimestamp, elapsed);
  }

  private void getCodecBuffers(MediaCodec codec) {
    if (Util.SDK_INT < 21) {
      inputBuffers = codec.getInputBuffers();
      outputBuffers = codec.getOutputBuffers();
    }
  }

  private void resetCodecBuffers() {
    if (Util.SDK_INT < 21) {
      inputBuffers = null;
      outputBuffers = null;
    }
  }

  private ByteBuffer getInputBuffer(int inputIndex) {
    if (Util.SDK_INT >= 21) {
      return codec.getInputBuffer(inputIndex);
    } else {
      return inputBuffers[inputIndex];
    }
  }

  @Nullable
  private ByteBuffer getOutputBuffer(int outputIndex) {
    if (Util.SDK_INT >= 21) {
      return codec.getOutputBuffer(outputIndex);
    } else {
      return outputBuffers[outputIndex];
    }
  }

  private boolean hasOutputBuffer() {
    return outputIndex >= 0;
  }

  private void resetInputBuffer() {
    inputIndex = C.INDEX_UNSET;
    buffer.data = null;
  }

  private void resetOutputBuffer() {
    outputIndex = C.INDEX_UNSET;
    outputBuffer = null;
  }

  private void setSourceDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setCodecDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(codecDrmSession, session);
    codecDrmSession = session;
  }

  /**
   * @return Whether it may be possible to feed more input data.
   * @throws ExoPlaybackException If an error occurs feeding the input buffer.
   */
  private boolean feedInputBuffer() throws ExoPlaybackException {
    if (codec == null || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM || inputStreamEnded) {
      return false;
    }

    if (inputIndex < 0) {
      inputIndex = codecAdapter.dequeueInputBufferIndex();
      if (inputIndex < 0) {
        return false;
      }
      buffer.data = getInputBuffer(inputIndex);
      buffer.clear();
    }

    if (codecDrainState == DRAIN_STATE_SIGNAL_END_OF_STREAM) {
      // We need to re-initialize the codec. Send an end of stream signal to the existing codec so
      // that it outputs any remaining buffers before we release it.
      if (codecNeedsEosPropagation) {
        // Do nothing.
      } else {
        codecReceivedEos = true;
        codecAdapter.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        resetInputBuffer();
      }
      codecDrainState = DRAIN_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    if (codecNeedsAdaptationWorkaroundBuffer) {
      codecNeedsAdaptationWorkaroundBuffer = false;
      buffer.data.put(ADAPTATION_WORKAROUND_BUFFER);
      codecAdapter.queueInputBuffer(inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.length, 0, 0);
      resetInputBuffer();
      codecReceivedBuffers = true;
      return true;
    }

    // For adaptive reconfiguration, decoders expect all reconfiguration data to be supplied at
    // the start of the buffer that also contains the first frame in the new format.
    if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
      for (int i = 0; i < codecInputFormat.initializationData.size(); i++) {
        byte[] data = codecInputFormat.initializationData.get(i);
        buffer.data.put(data);
      }
      codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
    }
    int adaptiveReconfigurationBytes = buffer.data.position();

    FormatHolder formatHolder = getFormatHolder();
    @SampleStream.ReadDataResult
    int result = readSource(formatHolder, buffer, /* formatRequired= */ false);

    if (hasReadStreamToEnd()) {
      // Notify output queue of the last buffer's timestamp.
      lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
    }

    if (result == C.RESULT_NOTHING_READ) {
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received two formats in a row. Clear the current buffer of any reconfiguration data
        // associated with the first format.
        buffer.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      onInputFormatChanged(formatHolder);
      return true;
    }

    // We've read a buffer.
    if (buffer.isEndOfStream()) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received a new format immediately before the end of the stream. We need to clear
        // the corresponding reconfiguration data from the current buffer, but re-write it into
        // a subsequent buffer if there are any (for example, if the user seeks backwards).
        buffer.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      inputStreamEnded = true;
      if (!codecReceivedBuffers) {
        processEndOfStream();
        return false;
      }
      try {
        if (codecNeedsEosPropagation) {
          // Do nothing.
        } else {
          codecReceivedEos = true;
          codecAdapter.queueInputBuffer(
              inputIndex,
              /* offset= */ 0,
              /* size= */ 0,
              /* presentationTimeUs= */ 0,
              MediaCodec.BUFFER_FLAG_END_OF_STREAM);
          resetInputBuffer();
        }
      } catch (CryptoException e) {
        throw createRendererException(e, inputFormat);
      }
      return false;
    }

    // This logic is required for cases where the decoder needs to be flushed or re-instantiated
    // during normal consumption of samples from the source (i.e., without a corresponding
    // Renderer.enable or Renderer.resetPosition call). This is necessary for certain legacy and
    // workaround behaviors, for example when switching the output Surface on API levels prior to
    // the introduction of MediaCodec.setOutputSurface.
    if (!codecReceivedBuffers && !buffer.isKeyFrame()) {
      buffer.clear();
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // The buffer we just cleared contained reconfiguration data. We need to re-write this data
        // into a subsequent buffer (if there is one).
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      return true;
    }

    boolean bufferEncrypted = buffer.isEncrypted();
    if (bufferEncrypted) {
      buffer.cryptoInfo.increaseClearDataFirstSubSampleBy(adaptiveReconfigurationBytes);
    }
    if (codecNeedsDiscardToSpsWorkaround && !bufferEncrypted) {
      NalUnitUtil.discardToSps(buffer.data);
      if (buffer.data.position() == 0) {
        return true;
      }
      codecNeedsDiscardToSpsWorkaround = false;
    }

    long presentationTimeUs = buffer.timeUs;

    if (c2Mp3TimestampTracker != null) {
      presentationTimeUs =
          c2Mp3TimestampTracker.updateAndGetPresentationTimeUs(inputFormat, buffer);
    }

    if (buffer.isDecodeOnly()) {
      decodeOnlyPresentationTimestamps.add(presentationTimeUs);
    }
    if (waitingForFirstSampleInFormat) {
      formatQueue.add(presentationTimeUs, inputFormat);
      waitingForFirstSampleInFormat = false;
    }

    // TODO(b/158483277): Find the root cause of why a gap is introduced in MP3 playback when using
    // presentationTimeUs from the c2Mp3TimestampTracker.
    if (c2Mp3TimestampTracker != null) {
      largestQueuedPresentationTimeUs = max(largestQueuedPresentationTimeUs, buffer.timeUs);
    } else {
      largestQueuedPresentationTimeUs = max(largestQueuedPresentationTimeUs, presentationTimeUs);
    }
    buffer.flip();
    if (buffer.hasSupplementalData()) {
      handleInputBufferSupplementalData(buffer);
    }

    onQueueInputBuffer(buffer);
    try {
      if (bufferEncrypted) {
        codecAdapter.queueSecureInputBuffer(
            inputIndex, /* offset= */ 0, buffer.cryptoInfo, presentationTimeUs, /* flags= */ 0);
      } else {
        codecAdapter.queueInputBuffer(
            inputIndex, /* offset= */ 0, buffer.data.limit(), presentationTimeUs, /* flags= */ 0);
      }
    } catch (CryptoException e) {
      throw createRendererException(e, inputFormat);
    }

    resetInputBuffer();
    codecReceivedBuffers = true;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    decoderCounters.inputBufferCount++;
    return true;
  }

  /**
   * Called when a {@link MediaCodec} has been created and configured.
   * <p>
   * The default implementation is a no-op.
   *
   * @param name The name of the codec that was initialized.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the codec in milliseconds.
   */
  protected void onCodecInitialized(String name, long initializedTimestampMs,
      long initializationDurationMs) {
    // Do nothing.
  }

  /**
   * Called when a new {@link Format} is read from the upstream {@link MediaPeriod}.
   *
   * @param formatHolder A {@link FormatHolder} that holds the new {@link Format}.
   * @throws ExoPlaybackException If an error occurs re-initializing the {@link MediaCodec}.
   */
  @CallSuper
  protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    waitingForFirstSampleInFormat = true;
    Format newFormat = Assertions.checkNotNull(formatHolder.format);
    setSourceDrmSession(formatHolder.drmSession);
    inputFormat = newFormat;

    if (bypassEnabled) {
      bypassDrainAndReinitialize = true;
      return; // Need to drain batch buffer first.
    }

    if (codec == null) {
      if (!legacyKeepAvailableCodecInfosWithoutCodec()) {
        availableCodecInfos = null;
      }
      maybeInitCodecOrBypass();
      return;
    }

    // We have an existing codec that we may need to reconfigure or re-initialize or release it to
    // switch to bypass. If the existing codec instance is being kept then its operating rate
    // may need to be updated.

    if ((sourceDrmSession == null && codecDrmSession != null)
        || (sourceDrmSession != null && codecDrmSession == null)
        || (sourceDrmSession != codecDrmSession
            && !codecInfo.secure
            && maybeRequiresSecureDecoder(sourceDrmSession, newFormat))
        || (Util.SDK_INT < 23 && sourceDrmSession != codecDrmSession)) {
      // We might need to switch between the clear and protected output paths, or we're using DRM
      // prior to API level 23 where the codec needs to be re-initialized to switch to the new DRM
      // session.
      drainAndReinitializeCodec();
      return;
    }

    switch (canKeepCodec(codec, codecInfo, codecInputFormat, newFormat)) {
      case KEEP_CODEC_RESULT_NO:
        drainAndReinitializeCodec();
        break;
      case KEEP_CODEC_RESULT_YES_WITH_FLUSH:
        codecInputFormat = newFormat;
        updateCodecOperatingRate();
        if (sourceDrmSession != codecDrmSession) {
          drainAndUpdateCodecDrmSession();
        } else {
          drainAndFlushCodec();
        }
        break;
      case KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION:
        if (codecNeedsReconfigureWorkaround) {
          drainAndReinitializeCodec();
        } else {
          codecReconfigured = true;
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
          codecNeedsAdaptationWorkaroundBuffer =
              codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_ALWAYS
                  || (codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION
                      && newFormat.width == codecInputFormat.width
                      && newFormat.height == codecInputFormat.height);
          codecInputFormat = newFormat;
          updateCodecOperatingRate();
          if (sourceDrmSession != codecDrmSession) {
            drainAndUpdateCodecDrmSession();
          }
        }
        break;
      case KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION:
        codecInputFormat = newFormat;
        updateCodecOperatingRate();
        if (sourceDrmSession != codecDrmSession) {
          drainAndUpdateCodecDrmSession();
        }
        break;
      default:
        throw new IllegalStateException(); // Never happens.
    }
  }

  /**
   * Returns whether to keep available codec infos when the codec hasn't been initialized, which is
   * the behavior before a bug fix. See also [Internal: b/162837741].
   */
  protected boolean legacyKeepAvailableCodecInfosWithoutCodec() {
    return false;
  }

  /**
   * Called when one of the output formats changes.
   *
   * <p>The default implementation is a no-op.
   *
   * @param format The input {@link Format} to which future output now corresponds. If the renderer
   *     is in bypass mode, this is also the output format.
   * @param mediaFormat The codec output {@link MediaFormat}, or {@code null} if the renderer is in
   *     bypass mode.
   * @throws ExoPlaybackException Thrown if an error occurs configuring the output.
   */
  protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Handles supplemental data associated with an input buffer.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The input buffer that is about to be queued.
   * @throws ExoPlaybackException Thrown if an error occurs handling supplemental data.
   */
  protected void handleInputBufferSupplementalData(DecoderInputBuffer buffer)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called immediately before an input buffer is queued into the codec.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The buffer to be queued.
   * @throws ExoPlaybackException Thrown if an error occurs handling the input buffer.
   */
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when an output buffer is successfully processed.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  @CallSuper
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    while (pendingOutputStreamOffsetCount != 0
        && presentationTimeUs >= pendingOutputStreamSwitchTimesUs[0]) {
      outputStreamStartPositionUs = pendingOutputStreamStartPositionsUs[0];
      outputStreamOffsetUs = pendingOutputStreamOffsetsUs[0];
      pendingOutputStreamOffsetCount--;
      System.arraycopy(
          pendingOutputStreamStartPositionsUs,
          /* srcPos= */ 1,
          pendingOutputStreamStartPositionsUs,
          /* destPos= */ 0,
          pendingOutputStreamOffsetCount);
      System.arraycopy(
          pendingOutputStreamOffsetsUs,
          /* srcPos= */ 1,
          pendingOutputStreamOffsetsUs,
          /* destPos= */ 0,
          pendingOutputStreamOffsetCount);
      System.arraycopy(
          pendingOutputStreamSwitchTimesUs,
          /* srcPos= */ 1,
          pendingOutputStreamSwitchTimesUs,
          /* destPos= */ 0,
          pendingOutputStreamOffsetCount);
      onProcessedStreamChange();
    }
  }

  /** Called after the last output buffer before a stream change has been processed. */
  protected void onProcessedStreamChange() {
    // Do nothing.
  }

  /**
   * Determines whether the existing {@link MediaCodec} can be kept for a new {@link Format}, and if
   * it can whether it requires reconfiguration.
   *
   * <p>The default implementation returns {@link #KEEP_CODEC_RESULT_NO}.
   *
   * @param codec The existing {@link MediaCodec} instance.
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param oldFormat The {@link Format} for which the existing instance is configured.
   * @param newFormat The new {@link Format}.
   * @return Whether the instance can be kept, and if it can whether it requires reconfiguration.
   */
  protected @KeepCodecResult int canKeepCodec(
      MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    return KEEP_CODEC_RESULT_NO;
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    return inputFormat != null
        && (isSourceReady()
            || hasOutputBuffer()
            || (codecHotswapDeadlineMs != C.TIME_UNSET
                && SystemClock.elapsedRealtime() < codecHotswapDeadlineMs));
  }

  /** Returns the renderer operating rate, as set by {@link #setOperatingRate}. */
  protected float getOperatingRate() {
    return operatingRate;
  }

  /** Returns the operating rate used by the current codec */
  protected float getCodecOperatingRate() {
    return codecOperatingRate;
  }

  /**
   * Returns the {@link MediaFormat#KEY_OPERATING_RATE} value for a given renderer operating rate,
   * current {@link Format} and set of possible stream formats.
   *
   * <p>The default implementation returns {@link #CODEC_OPERATING_RATE_UNSET}.
   *
   * @param operatingRate The renderer operating rate.
   * @param format The {@link Format} for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if no codec operating
   *     rate should be set.
   */
  protected float getCodecOperatingRateV23(
      float operatingRate, Format format, Format[] streamFormats) {
    return CODEC_OPERATING_RATE_UNSET;
  }

  /**
   * Updates the codec operating rate.
   *
   * @throws ExoPlaybackException If an error occurs releasing or initializing a codec.
   */
  private void updateCodecOperatingRate() throws ExoPlaybackException {
    if (Util.SDK_INT < 23) {
      return;
    }

    float newCodecOperatingRate =
        getCodecOperatingRateV23(operatingRate, codecInputFormat, getStreamFormats());
    if (codecOperatingRate == newCodecOperatingRate) {
      // No change.
    } else if (newCodecOperatingRate == CODEC_OPERATING_RATE_UNSET) {
      // The only way to clear the operating rate is to instantiate a new codec instance. See
      // [Internal ref: b/111543954].
      drainAndReinitializeCodec();
    } else if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET
        || newCodecOperatingRate > assumedMinimumCodecOperatingRate) {
      // We need to set the operating rate, either because we've set it previously or because it's
      // above the assumed minimum rate.
      Bundle codecParameters = new Bundle();
      codecParameters.putFloat(MediaFormat.KEY_OPERATING_RATE, newCodecOperatingRate);
      codec.setParameters(codecParameters);
      codecOperatingRate = newCodecOperatingRate;
    }
  }

  /** Starts draining the codec for flush. */
  private void drainAndFlushCodec() {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM;
      codecDrainAction = DRAIN_ACTION_FLUSH;
    }
  }

  /**
   * Starts draining the codec to update its DRM session. The update may occur immediately if no
   * buffers have been queued to the codec.
   *
   * @throws ExoPlaybackException If an error occurs updating the codec's DRM session.
   */
  private void drainAndUpdateCodecDrmSession() throws ExoPlaybackException {
    if (Util.SDK_INT < 23) {
      // The codec needs to be re-initialized to switch to the source DRM session.
      drainAndReinitializeCodec();
      return;
    }
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM;
      codecDrainAction = DRAIN_ACTION_UPDATE_DRM_SESSION;
    } else {
      // Nothing has been queued to the decoder, so we can do the update immediately.
      updateDrmSessionOrReinitializeCodecV23();
    }
  }

  /**
   * Starts draining the codec for re-initialization. Re-initialization may occur immediately if no
   * buffers have been queued to the codec.
   *
   * @throws ExoPlaybackException If an error occurs re-initializing a codec.
   */
  private void drainAndReinitializeCodec() throws ExoPlaybackException {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM;
      codecDrainAction = DRAIN_ACTION_REINITIALIZE;
    } else {
      // Nothing has been queued to the decoder, so we can re-initialize immediately.
      reinitializeCodec();
    }
  }

  /**
   * @return Whether it may be possible to drain more output data.
   * @throws ExoPlaybackException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    if (!hasOutputBuffer()) {
      int outputIndex;
      if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) {
        try {
          outputIndex = codecAdapter.dequeueOutputBufferIndex(outputBufferInfo);
        } catch (IllegalStateException e) {
          processEndOfStream();
          if (outputStreamEnded) {
            // Release the codec, as it's in an error state.
            releaseCodec();
          }
          return false;
        }
      } else {
        outputIndex = codecAdapter.dequeueOutputBufferIndex(outputBufferInfo);
      }

      if (outputIndex < 0) {
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED /* (-2) */) {
          processOutputMediaFormatChanged();
          return true;
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED /* (-3) */) {
          processOutputBuffersChanged();
          return true;
        }
        /* MediaCodec.INFO_TRY_AGAIN_LATER (-1) or unknown negative return value */
        if (codecNeedsEosPropagation
            && (inputStreamEnded || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM)) {
          processEndOfStream();
        }
        return false;
      }

      // We've dequeued a buffer.
      if (shouldSkipAdaptationWorkaroundOutputBuffer) {
        shouldSkipAdaptationWorkaroundOutputBuffer = false;
        codec.releaseOutputBuffer(outputIndex, false);
        return true;
      } else if (outputBufferInfo.size == 0
          && (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        // The dequeued buffer indicates the end of the stream. Process it immediately.
        processEndOfStream();
        return false;
      }

      this.outputIndex = outputIndex;
      outputBuffer = getOutputBuffer(outputIndex);

      // The dequeued buffer is a media buffer. Do some initial setup.
      // It will be processed by calling processOutputBuffer (possibly multiple times).
      if (outputBuffer != null) {
        outputBuffer.position(outputBufferInfo.offset);
        outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
      }
      isDecodeOnlyOutputBuffer = isDecodeOnlyBuffer(outputBufferInfo.presentationTimeUs);
      isLastOutputBuffer =
          lastBufferInStreamPresentationTimeUs == outputBufferInfo.presentationTimeUs;
      updateOutputFormatForTime(outputBufferInfo.presentationTimeUs);
    }

    boolean processedOutputBuffer;
    if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) {
      try {
        processedOutputBuffer =
            processOutputBuffer(
                positionUs,
                elapsedRealtimeUs,
                codec,
                outputBuffer,
                outputIndex,
                outputBufferInfo.flags,
                /* sampleCount= */ 1,
                outputBufferInfo.presentationTimeUs,
                isDecodeOnlyOutputBuffer,
                isLastOutputBuffer,
                outputFormat);
      } catch (IllegalStateException e) {
        processEndOfStream();
        if (outputStreamEnded) {
          // Release the codec, as it's in an error state.
          releaseCodec();
        }
        return false;
      }
    } else {
      processedOutputBuffer =
          processOutputBuffer(
              positionUs,
              elapsedRealtimeUs,
              codec,
              outputBuffer,
              outputIndex,
              outputBufferInfo.flags,
              /* sampleCount= */ 1,
              outputBufferInfo.presentationTimeUs,
              isDecodeOnlyOutputBuffer,
              isLastOutputBuffer,
              outputFormat);
    }

    if (processedOutputBuffer) {
      onProcessedOutputBuffer(outputBufferInfo.presentationTimeUs);
      boolean isEndOfStream = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
      resetOutputBuffer();
      if (!isEndOfStream) {
        return true;
      }
      processEndOfStream();
    }

    return false;
  }

  /** Processes a change in the decoder output {@link MediaFormat}. */
  private void processOutputMediaFormatChanged() {
    codecHasOutputMediaFormat = true;
    MediaFormat mediaFormat = codecAdapter.getOutputFormat();
    if (codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER
        && mediaFormat.getInteger(MediaFormat.KEY_WIDTH) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
        && mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
            == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT) {
      // We assume this format changed event was caused by the adaptation workaround.
      shouldSkipAdaptationWorkaroundOutputBuffer = true;
      return;
    }
    if (codecNeedsMonoChannelCountWorkaround) {
      mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
    }
    codecOutputMediaFormat = mediaFormat;
    codecOutputMediaFormatChanged = true;
  }

  /**
   * Processes a change in the output buffers.
   */
  private void processOutputBuffersChanged() {
    if (Util.SDK_INT < 21) {
      outputBuffers = codec.getOutputBuffers();
    }
  }

  /**
   * Processes an output media buffer.
   *
   * <p>When a new {@link ByteBuffer} is passed to this method its position and limit delineate the
   * data to be processed. The return value indicates whether the buffer was processed in full. If
   * true is returned then the next call to this method will receive a new buffer to be processed.
   * If false is returned then the same buffer will be passed to the next call. An implementation of
   * this method is free to modify the buffer and can assume that the buffer will not be externally
   * modified between successive calls. Hence an implementation can, for example, modify the
   * buffer's position to keep track of how much of the data it has processed.
   *
   * <p>Note that the first call to this method following a call to {@link #onPositionReset(long,
   * boolean)} will always receive a new {@link ByteBuffer} to be processed.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the current
   *     iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} in microseconds, measured at the
   *     start of the current iteration of the rendering loop.
   * @param codec The {@link MediaCodec} instance, or null in bypass mode were no codec is used.
   * @param buffer The output buffer to process, or null if the buffer data is not made available to
   *     the application layer (see {@link MediaCodec#getOutputBuffer(int)}). This {@code buffer}
   *     can only be null for video data. Note that the buffer data can still be rendered in this
   *     case by using the {@code bufferIndex}.
   * @param bufferIndex The index of the output buffer.
   * @param bufferFlags The flags attached to the output buffer.
   * @param sampleCount The number of samples extracted from the sample queue in the buffer. This
   *     allows handling multiple samples as a batch for efficiency.
   * @param bufferPresentationTimeUs The presentation time of the output buffer in microseconds.
   * @param isDecodeOnlyBuffer Whether the buffer was marked with {@link C#BUFFER_FLAG_DECODE_ONLY}
   *     by the source.
   * @param isLastBuffer Whether the buffer is the last sample of the current stream.
   * @param format The {@link Format} associated with the buffer.
   * @return Whether the output buffer was fully processed (for example, rendered or skipped).
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  protected abstract boolean processOutputBuffer(
      long positionUs,
      long elapsedRealtimeUs,
      @Nullable MediaCodec codec,
      @Nullable ByteBuffer buffer,
      int bufferIndex,
      int bufferFlags,
      int sampleCount,
      long bufferPresentationTimeUs,
      boolean isDecodeOnlyBuffer,
      boolean isLastBuffer,
      Format format)
      throws ExoPlaybackException;

  /**
   * Incrementally renders any remaining output.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException Thrown if an error occurs rendering remaining output.
   */
  protected void renderToEndOfStream() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Processes an end of stream signal.
   *
   * @throws ExoPlaybackException If an error occurs processing the signal.
   */
  @TargetApi(23) // codecDrainAction == DRAIN_ACTION_UPDATE_DRM_SESSION implies SDK_INT >= 23.
  private void processEndOfStream() throws ExoPlaybackException {
    switch (codecDrainAction) {
      case DRAIN_ACTION_REINITIALIZE:
        reinitializeCodec();
        break;
      case DRAIN_ACTION_UPDATE_DRM_SESSION:
        updateDrmSessionOrReinitializeCodecV23();
        break;
      case DRAIN_ACTION_FLUSH:
        flushOrReinitializeCodec();
        break;
      case DRAIN_ACTION_NONE:
      default:
        outputStreamEnded = true;
        renderToEndOfStream();
        break;
    }
  }

  /**
   * Notifies the renderer that output end of stream is pending and should be handled on the next
   * render.
   */
  protected final void setPendingOutputEndOfStream() {
    pendingOutputEndOfStream = true;
  }

  /** Returns the largest queued input presentation time, in microseconds. */
  protected final long getLargestQueuedPresentationTimeUs() {
    return largestQueuedPresentationTimeUs;
  }

  /**
   * Returns the start position of the output {@link SampleStream}, in renderer time microseconds.
   */
  protected final long getOutputStreamStartPositionUs() {
    return outputStreamStartPositionUs;
  }

  /**
   * Returns the offset that should be subtracted from {@code bufferPresentationTimeUs} in {@link
   * #processOutputBuffer(long, long, MediaCodec, ByteBuffer, int, int, int, long, boolean, boolean,
   * Format)} to get the playback position with respect to the media.
   */
  protected final long getOutputStreamOffsetUs() {
    return outputStreamOffsetUs;
  }

  /** Returns whether this renderer supports the given {@link Format Format's} DRM scheme. */
  protected static boolean supportsFormatDrm(Format format) {
    return format.exoMediaCryptoType == null
        || FrameworkMediaCrypto.class.equals(format.exoMediaCryptoType);
  }

  /**
   * Returns whether a {@link DrmSession} may require a secure decoder for a given {@link Format}.
   *
   * @param drmSession The {@link DrmSession}.
   * @param format The {@link Format}.
   * @return Whether a secure decoder may be required.
   */
  private boolean maybeRequiresSecureDecoder(DrmSession drmSession, Format format)
      throws ExoPlaybackException {
    // MediaCrypto type is checked during track selection.
    @Nullable FrameworkMediaCrypto sessionMediaCrypto = getFrameworkMediaCrypto(drmSession);
    if (sessionMediaCrypto == null) {
      // We'd only expect this to happen if the CDM from which the pending session is obtained needs
      // provisioning. This is unlikely to happen (it probably requires a switch from one DRM scheme
      // to another, where the new CDM hasn't been used before and needs provisioning). Assume that
      // a secure decoder may be required.
      return true;
    }
    if (sessionMediaCrypto.forceAllowInsecureDecoderComponents) {
      return false;
    }
    MediaCrypto mediaCrypto;
    try {
      mediaCrypto = new MediaCrypto(sessionMediaCrypto.uuid, sessionMediaCrypto.sessionId);
    } catch (MediaCryptoException e) {
      // This shouldn't happen, but if it does then assume that a secure decoder may be required.
      return true;
    }
    try {
      return mediaCrypto.requiresSecureDecoderComponent(format.sampleMimeType);
    } finally {
      mediaCrypto.release();
    }
  }

  private void reinitializeCodec() throws ExoPlaybackException {
    releaseCodec();
    maybeInitCodecOrBypass();
  }

  private boolean isDecodeOnlyBuffer(long presentationTimeUs) {
    // We avoid using decodeOnlyPresentationTimestamps.remove(presentationTimeUs) because it would
    // box presentationTimeUs, creating a Long object that would need to be garbage collected.
    int size = decodeOnlyPresentationTimestamps.size();
    for (int i = 0; i < size; i++) {
      if (decodeOnlyPresentationTimestamps.get(i) == presentationTimeUs) {
        decodeOnlyPresentationTimestamps.remove(i);
        return true;
      }
    }
    return false;
  }

  @RequiresApi(23)
  private void updateDrmSessionOrReinitializeCodecV23() throws ExoPlaybackException {
    @Nullable FrameworkMediaCrypto sessionMediaCrypto = getFrameworkMediaCrypto(sourceDrmSession);
    if (sessionMediaCrypto == null) {
      // We'd only expect this to happen if the CDM from which the pending session is obtained needs
      // provisioning. This is unlikely to happen (it probably requires a switch from one DRM scheme
      // to another, where the new CDM hasn't been used before and needs provisioning). It would be
      // possible to handle this case more efficiently (i.e. with a new renderer state that waits
      // for provisioning to finish and then calls mediaCrypto.setMediaDrmSession), but the extra
      // complexity is not warranted given how unlikely the case is to occur.
      reinitializeCodec();
      return;
    }
    if (C.PLAYREADY_UUID.equals(sessionMediaCrypto.uuid)) {
      // The PlayReady CDM does not implement setMediaDrmSession.
      // TODO: Add API check once [Internal ref: b/128835874] is fixed.
      reinitializeCodec();
      return;
    }

    if (flushOrReinitializeCodec()) {
      // The codec was reinitialized. The new codec will be using the new DRM session, so there's
      // nothing more to do.
      return;
    }

    try {
      mediaCrypto.setMediaDrmSession(sessionMediaCrypto.sessionId);
    } catch (MediaCryptoException e) {
      throw createRendererException(e, inputFormat);
    }
    setCodecDrmSession(sourceDrmSession);
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
  }

  @Nullable
  private FrameworkMediaCrypto getFrameworkMediaCrypto(DrmSession drmSession)
      throws ExoPlaybackException {
    @Nullable ExoMediaCrypto mediaCrypto = drmSession.getMediaCrypto();
    if (mediaCrypto != null && !(mediaCrypto instanceof FrameworkMediaCrypto)) {
      // This should not happen if the track went through a supportsFormatDrm() check, during track
      // selection.
      throw createRendererException(
          new IllegalArgumentException("Expecting FrameworkMediaCrypto but found: " + mediaCrypto),
          inputFormat);
    }
    return (FrameworkMediaCrypto) mediaCrypto;
  }

  /**
   * Processes any pending batch of buffers without using a decoder, and drains a new batch of
   * buffers from the source.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the current
   *     iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} in microseconds, measured at the
   *     start of the current iteration of the rendering loop.
   * @return If more buffers are ready to be rendered.
   * @throws ExoPlaybackException If an error occurred while processing a buffer or handling a
   *     format change.
   */
  private boolean bypassRender(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    BatchBuffer batchBuffer = bypassBatchBuffer;

    // Let's process the pending buffer if any.
    checkState(!outputStreamEnded);
    if (!batchBuffer.isEmpty()) { // Optimisation: Do not process buffer if empty.
      if (processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          /* codec= */ null,
          batchBuffer.data,
          outputIndex,
          /* bufferFlags= */ 0,
          batchBuffer.getAccessUnitCount(),
          batchBuffer.getFirstAccessUnitTimeUs(),
          batchBuffer.isDecodeOnly(),
          batchBuffer.isEndOfStream(),
          outputFormat)) {
        // Buffer completely processed
        onProcessedOutputBuffer(batchBuffer.getLastAccessUnitTimeUs());
      } else {
        return false; // Could not process buffer, let's try later.
      }
    }
    if (batchBuffer.isEndOfStream()) {
      outputStreamEnded = true;
      return false;
    }
    batchBuffer.batchWasConsumed();

    if (bypassDrainAndReinitialize) {
      if (!batchBuffer.isEmpty()) {
        return true; // Drain the batch buffer before propagating the format change.
      }
      disableBypass(); // The new format might require a codec.
      bypassDrainAndReinitialize = false;
      maybeInitCodecOrBypass();
      if (!bypassEnabled) {
        return false; // The new format is not supported in codec bypass.
      }
    }

    // Now refill the empty buffer for the next iteration.
    checkState(!inputStreamEnded);
    FormatHolder formatHolder = getFormatHolder();
    boolean formatChange = readBatchFromSource(formatHolder, batchBuffer);

    if (!batchBuffer.isEmpty() && waitingForFirstSampleInFormat) {
      // This is the first buffer in a new format, the output format must be updated.
      outputFormat = Assertions.checkNotNull(inputFormat);
      onOutputFormatChanged(outputFormat, /* mediaFormat= */ null);
      waitingForFirstSampleInFormat = false;
    }

    if (formatChange) {
      onInputFormatChanged(formatHolder);
    }

    if (batchBuffer.isEndOfStream()) {
      inputStreamEnded = true;
    }

    if (batchBuffer.isEmpty()) {
      return false; // The buffer could not be filled, there is nothing more to do.
    }
    batchBuffer.flip(); // Buffer at least partially full, it can now be processed.
    // MediaCodec outputs buffers in native endian:
    // https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers
    // and code called from processOutputBuffer expects this endianness.
    batchBuffer.data.order(ByteOrder.nativeOrder());
    return true;
  }

  /**
   * Fills the buffer with multiple access unit from the source. Has otherwise the same semantic as
   * {@link #readSource(FormatHolder, DecoderInputBuffer, boolean)}. Will stop early on format
   * change, EOS or source starvation.
   *
   * @return If the format has changed.
   */
  private boolean readBatchFromSource(FormatHolder formatHolder, BatchBuffer batchBuffer) {
    while (!batchBuffer.isFull() && !batchBuffer.isEndOfStream()) {
      @SampleStream.ReadDataResult
      int result =
          readSource(
              formatHolder, batchBuffer.getNextAccessUnitBuffer(), /* formatRequired= */ false);
      switch (result) {
        case C.RESULT_FORMAT_READ:
          return true;
        case C.RESULT_NOTHING_READ:
          return false;
        case C.RESULT_BUFFER_READ:
          batchBuffer.commitNextAccessUnit();
          break;
        default:
          throw new IllegalStateException(); // Unsupported result
      }
    }
    return false;
  }

  private static boolean isMediaCodecException(IllegalStateException error) {
    if (Util.SDK_INT >= 21 && isMediaCodecExceptionV21(error)) {
      return true;
    }
    StackTraceElement[] stackTrace = error.getStackTrace();
    return stackTrace.length > 0 && stackTrace[0].getClassName().equals("android.media.MediaCodec");
  }

  @RequiresApi(21)
  private static boolean isMediaCodecExceptionV21(IllegalStateException error) {
    return error instanceof MediaCodec.CodecException;
  }

  /**
   * Returns whether the decoder is known to fail when flushed.
   * <p>
   * If true is returned, the renderer will work around the issue by releasing the decoder and
   * instantiating a new one rather than flushing the current instance.
   * <p>
   * See [Internal: b/8347958, b/8543366].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to fail when flushed.
   */
  private static boolean codecNeedsFlushWorkaround(String name) {
    return Util.SDK_INT < 18
        || (Util.SDK_INT == 18
        && ("OMX.SEC.avc.dec".equals(name) || "OMX.SEC.avc.dec.secure".equals(name)))
        || (Util.SDK_INT == 19 && Util.MODEL.startsWith("SM-G800")
        && ("OMX.Exynos.avc.dec".equals(name) || "OMX.Exynos.avc.dec.secure".equals(name)));
  }

  /**
   * Returns a mode that specifies when the adaptation workaround should be enabled.
   *
   * <p>When enabled, the workaround queues and discards a blank frame with a resolution whose width
   * and height both equal {@link #ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT}, to reset the decoder's
   * internal state when a format change occurs.
   *
   * <p>See [Internal: b/27807182]. See <a
   * href="https://github.com/google/ExoPlayer/issues/3257">GitHub issue #3257</a>.
   *
   * @param name The name of the decoder.
   * @return The mode specifying when the adaptation workaround should be enabled.
   */
  private @AdaptationWorkaroundMode int codecAdaptationWorkaroundMode(String name) {
    if (Util.SDK_INT <= 25 && "OMX.Exynos.avc.dec.secure".equals(name)
        && (Util.MODEL.startsWith("SM-T585") || Util.MODEL.startsWith("SM-A510")
        || Util.MODEL.startsWith("SM-A520") || Util.MODEL.startsWith("SM-J700"))) {
      return ADAPTATION_WORKAROUND_MODE_ALWAYS;
    } else if (Util.SDK_INT < 24
        && ("OMX.Nvidia.h264.decode".equals(name) || "OMX.Nvidia.h264.decode.secure".equals(name))
        && ("flounder".equals(Util.DEVICE) || "flounder_lte".equals(Util.DEVICE)
        || "grouper".equals(Util.DEVICE) || "tilapia".equals(Util.DEVICE))) {
      return ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION;
    } else {
      return ADAPTATION_WORKAROUND_MODE_NEVER;
    }
  }

  /**
   * Returns whether the decoder is known to fail when an attempt is made to reconfigure it with a
   * new format's configuration data.
   *
   * <p>When enabled, the workaround will always release and recreate the decoder, rather than
   * attempting to reconfigure the existing instance.
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to fail when an attempt is made to reconfigure it with a
   *     new format's configuration data.
   */
  private static boolean codecNeedsReconfigureWorkaround(String name) {
    return Util.MODEL.startsWith("SM-T230") && "OMX.MARVELL.VIDEO.HW.CODA7542DECODER".equals(name);
  }

  /**
   * Returns whether the decoder is an H.264/AVC decoder known to fail if NAL units are queued
   * before the codec specific data.
   *
   * <p>If true is returned, the renderer will work around the issue by discarding data up to the
   * SPS.
   *
   * @param name The name of the decoder.
   * @param format The {@link Format} used to configure the decoder.
   * @return True if the decoder is known to fail if NAL units are queued before CSD.
   */
  private static boolean codecNeedsDiscardToSpsWorkaround(String name, Format format) {
    return Util.SDK_INT < 21 && format.initializationData.isEmpty()
        && "OMX.MTK.VIDEO.DECODER.AVC".equals(name);
  }

  /**
   * Returns whether the decoder is known to handle the propagation of the {@link
   * MediaCodec#BUFFER_FLAG_END_OF_STREAM} flag incorrectly on the host device.
   *
   * <p>If true is returned, the renderer will work around the issue by approximating end of stream
   * behavior without relying on the flag being propagated through to an output buffer by the
   * underlying decoder.
   *
   * @param codecInfo Information about the {@link MediaCodec}.
   * @return True if the decoder is known to handle {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM}
   *     propagation incorrectly on the host device. False otherwise.
   */
  private static boolean codecNeedsEosPropagationWorkaround(MediaCodecInfo codecInfo) {
    String name = codecInfo.name;
    return (Util.SDK_INT <= 25 && "OMX.rk.video_decoder.avc".equals(name))
        || (Util.SDK_INT <= 17 && "OMX.allwinner.video.decoder.avc".equals(name))
        || (Util.SDK_INT <= 29
            && ("OMX.broadcom.video_decoder.tunnel".equals(name)
                || "OMX.broadcom.video_decoder.tunnel.secure".equals(name)))
        || ("Amazon".equals(Util.MANUFACTURER) && "AFTS".equals(Util.MODEL) && codecInfo.secure);
  }

  /**
   * Returns whether the decoder is known to behave incorrectly if flushed after receiving an input
   * buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set.
   * <p>
   * If true is returned, the renderer will work around the issue by instantiating a new decoder
   * when this case occurs.
   * <p>
   * See [Internal: b/8578467, b/23361053].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to behave incorrectly if flushed after receiving an input
   *     buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set. False otherwise.
   */
  private static boolean codecNeedsEosFlushWorkaround(String name) {
    return (Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder".equals(name))
        || (Util.SDK_INT <= 19
            && ("hb2000".equals(Util.DEVICE) || "stvm8".equals(Util.DEVICE))
            && ("OMX.amlogic.avc.decoder.awesome".equals(name)
                || "OMX.amlogic.avc.decoder.awesome.secure".equals(name)));
  }

  /**
   * Returns whether the decoder may throw an {@link IllegalStateException} from
   * {@link MediaCodec#dequeueOutputBuffer(MediaCodec.BufferInfo, long)} or
   * {@link MediaCodec#releaseOutputBuffer(int, boolean)} after receiving an input
   * buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set.
   * <p>
   * See [Internal: b/17933838].
   *
   * @param name The name of the decoder.
   * @return True if the decoder may throw an exception after receiving an end-of-stream buffer.
   */
  private static boolean codecNeedsEosOutputExceptionWorkaround(String name) {
    return Util.SDK_INT == 21 && "OMX.google.aac.decoder".equals(name);
  }

  /**
   * Returns whether the decoder is known to set the number of audio channels in the output {@link
   * Format} to 2 for the given input {@link Format}, whilst only actually outputting a single
   * channel.
   *
   * <p>If true is returned then we explicitly override the number of channels in the output {@link
   * Format}, setting it to 1.
   *
   * @param name The decoder name.
   * @param format The input {@link Format}.
   * @return True if the decoder is known to set the number of audio channels in the output {@link
   *     Format} to 2 for the given input {@link Format}, whilst only actually outputting a single
   *     channel. False otherwise.
   */
  private static boolean codecNeedsMonoChannelCountWorkaround(String name, Format format) {
    return Util.SDK_INT <= 18 && format.channelCount == 1
        && "OMX.MTK.AUDIO.DECODER.MP3".equals(name);
  }

  /**
   * Returns whether the decoder is known to behave incorrectly if flushed prior to having output a
   * {@link MediaFormat}.
   *
   * <p>If true is returned, the renderer will work around the issue by instantiating a new decoder
   * when this case occurs.
   *
   * <p>See [Internal: b/141097367].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to behave incorrectly if flushed prior to having output a
   *     {@link MediaFormat}. False otherwise.
   */
  private static boolean codecNeedsSosFlushWorkaround(String name) {
    return Util.SDK_INT == 29 && "c2.android.aac.decoder".equals(name);
  }
}
