/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.image;

import static androidx.media3.common.C.FIRST_FRAME_NOT_RENDERED;
import static androidx.media3.common.C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
import static androidx.media3.common.C.FIRST_FRAME_RENDERED;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A {@link Renderer} implementation for images. */
@UnstableApi
public class ImageRenderer extends BaseRenderer {

  private static final String TAG = "ImageRenderer";

  /** Decoder reinitialization states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReinitializationState {}

  /** The decoder does not need to be re-initialized. */
  private static final int REINITIALIZATION_STATE_NONE = 0;

  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT = 2;

  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, and we've
   * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
   * end of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 3;

  /**
   * A time threshold, in microseconds, for the window during which an image should be presented.
   */
  private static final long IMAGE_PRESENTATION_WINDOW_THRESHOLD_US = 30_000;

  private final ImageDecoder.Factory decoderFactory;
  private final DecoderInputBuffer flagsOnlyBuffer;

  /**
   * Pending {@link OutputStreamInfo} for following streams. All {@code OutputStreamInfo} added to
   * this list will have {@linkplain OutputStreamInfo#previousStreamLastBufferTimeUs
   * previousStreamLastBufferTimeUs} and {@linkplain OutputStreamInfo#streamOffsetUs streamOffsetUs}
   * set.
   */
  private final ArrayDeque<OutputStreamInfo> pendingOutputStreamChanges;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private OutputStreamInfo outputStreamInfo;
  private long lastProcessedOutputBufferTimeUs;
  private long largestQueuedPresentationTimeUs;
  private @ReinitializationState int decoderReinitializationState;
  private @C.FirstFrameState int firstFrameState;
  private @Nullable Format inputFormat;
  private @Nullable ImageDecoder decoder;
  private @Nullable DecoderInputBuffer inputBuffer;
  private ImageOutput imageOutput;
  private @Nullable Bitmap outputBitmap;
  private boolean readyToOutputTiles;
  private @Nullable TileInfo tileInfo;
  private @Nullable TileInfo nextTileInfo;
  private int currentTileIndex;

  /**
   * Creates an instance.
   *
   * @param decoderFactory A {@link ImageDecoder.Factory} that supplies a decoder depending on the
   *     format provided.
   * @param imageOutput The rendering component to send the {@link Bitmap} and rendering commands
   *     to, or {@code null} if no bitmap output is required.
   */
  public ImageRenderer(ImageDecoder.Factory decoderFactory, @Nullable ImageOutput imageOutput) {
    super(C.TRACK_TYPE_IMAGE);
    this.decoderFactory = decoderFactory;
    this.imageOutput = getImageOutput(imageOutput);
    flagsOnlyBuffer = DecoderInputBuffer.newNoDataInstance();
    outputStreamInfo = OutputStreamInfo.UNSET;
    pendingOutputStreamChanges = new ArrayDeque<>();
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    lastProcessedOutputBufferTimeUs = C.TIME_UNSET;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    firstFrameState = FIRST_FRAME_NOT_RENDERED;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public @Capabilities int supportsFormat(Format format) {
    return decoderFactory.supportsFormat(format);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @SampleStream.ReadDataResult
      int result = readSource(formatHolder, flagsOnlyBuffer, FLAG_REQUIRE_FORMAT);
      if (result == C.RESULT_FORMAT_READ) {
        // Note that this works because we only expect to enter this if-condition once per playback.
        inputFormat = checkStateNotNull(formatHolder.format);
        initDecoder();
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        outputStreamEnded = true;
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }
    try {
      // Rendering loop.
      TraceUtil.beginSection("drainAndFeedDecoder");
      while (drainOutput(positionUs, elapsedRealtimeUs)) {}
      while (feedInputBuffer(positionUs)) {}
      TraceUtil.endSection();
    } catch (ImageDecoderException e) {
      throw createRendererException(e, null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    }
  }

  @Override
  public boolean isReady() {
    return firstFrameState == FIRST_FRAME_RENDERED
        || (firstFrameState == FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED
            && readyToOutputTiles);
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    firstFrameState =
        mayRenderStartOfStream
            ? C.FIRST_FRAME_NOT_RENDERED
            : C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    // TODO: b/319484746 - Take startPositionUs into account to not output images too early.
    super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
    if (outputStreamInfo.streamOffsetUs == C.TIME_UNSET
        || (pendingOutputStreamChanges.isEmpty()
            && (largestQueuedPresentationTimeUs == C.TIME_UNSET
                || (lastProcessedOutputBufferTimeUs != C.TIME_UNSET
                    && lastProcessedOutputBufferTimeUs >= largestQueuedPresentationTimeUs)))) {
      // Either the first stream, or all previous streams have never queued any samples or have been
      // fully output already.
      outputStreamInfo =
          new OutputStreamInfo(/* previousStreamLastBufferTimeUs= */ C.TIME_UNSET, offsetUs);
    } else {
      pendingOutputStreamChanges.add(
          new OutputStreamInfo(
              /* previousStreamLastBufferTimeUs= */ largestQueuedPresentationTimeUs, offsetUs));
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    lowerFirstFrameState(FIRST_FRAME_NOT_RENDERED);
    outputStreamEnded = false;
    inputStreamEnded = false;
    outputBitmap = null;
    tileInfo = null;
    nextTileInfo = null;
    readyToOutputTiles = false;
    inputBuffer = null;
    if (decoder != null) {
      decoder.flush();
    }
    pendingOutputStreamChanges.clear();
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    outputStreamInfo = OutputStreamInfo.UNSET;
    pendingOutputStreamChanges.clear();
    releaseDecoderResources();
    imageOutput.onDisabled();
  }

  @Override
  protected void onReset() {
    releaseDecoderResources();
    lowerFirstFrameState(FIRST_FRAME_NOT_RENDERED);
  }

  @Override
  protected void onRelease() {
    releaseDecoderResources();
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_IMAGE_OUTPUT:
        @Nullable ImageOutput imageOutput =
            message instanceof ImageOutput ? (ImageOutput) message : null;
        setImageOutput(imageOutput);
        break;
      default:
        super.handleMessage(messageType, message);
    }
  }

  /**
   * Checks if there is data to output. If there is no data to output, it attempts dequeuing the
   * output buffer from the decoder. If there is data to output, it attempts to render it.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to output more data.
   * @throws ImageDecoderException If an error occurs draining the output buffer.
   */
  private boolean drainOutput(long positionUs, long elapsedRealtimeUs)
      throws ImageDecoderException, ExoPlaybackException {
    // If tileInfo and outputBitmap are both null, we must not return early. The EOS may have been
    // queued to the decoder, and we must stay in this method to deque it further down.
    if (outputBitmap != null && tileInfo == null) {
      return false;
    }
    if (firstFrameState == FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED
        && getState() != STATE_STARTED) {
      return false;
    }
    if (outputBitmap == null) {
      checkStateNotNull(decoder);
      ImageOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      if (checkStateNotNull(outputBuffer).isEndOfStream()) {
        if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
          // We're waiting to re-initialize the decoder, and have now processed all final buffers.
          releaseDecoderResources();
          checkStateNotNull(inputFormat);
          initDecoder();
        } else {
          checkStateNotNull(outputBuffer).release();
          if (pendingOutputStreamChanges.isEmpty()) {
            outputStreamEnded = true;
          }
        }
        return false;
      }
      checkStateNotNull(
          outputBuffer.bitmap, "Non-EOS buffer came back from the decoder without bitmap.");
      outputBitmap = outputBuffer.bitmap;
      checkStateNotNull(outputBuffer).release();
    }

    if (readyToOutputTiles && outputBitmap != null && tileInfo != null) {
      checkStateNotNull(inputFormat);
      boolean isThumbnailGrid =
          (inputFormat.tileCountHorizontal != 1 || inputFormat.tileCountVertical != 1)
              && inputFormat.tileCountHorizontal != Format.NO_VALUE
              && inputFormat.tileCountVertical != Format.NO_VALUE;
      // Lazily crop and store the bitmap to ensure we only have one tile in memory rather than
      // proactively storing a tile whenever creating TileInfos.
      if (!tileInfo.hasTileBitmap()) {
        tileInfo.setTileBitmap(
            isThumbnailGrid
                ? cropTileFromImageGrid(tileInfo.getTileIndex())
                : checkStateNotNull(outputBitmap));
      }
      if (!processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          checkStateNotNull(tileInfo.getTileBitmap()),
          tileInfo.getPresentationTimeUs())) {
        return false;
      }
      onProcessedOutputBuffer(checkStateNotNull(tileInfo).getPresentationTimeUs());
      firstFrameState = FIRST_FRAME_RENDERED;
      if (!isThumbnailGrid
          || checkStateNotNull(tileInfo).getTileIndex()
              == checkStateNotNull(inputFormat).tileCountVertical
                      * checkStateNotNull(inputFormat).tileCountHorizontal
                  - 1) {
        outputBitmap = null;
      }
      tileInfo = nextTileInfo;
      nextTileInfo = null;
      return true;
    }
    return false;
  }

  private boolean shouldForceRender() {
    boolean isStarted = getState() == STATE_STARTED;
    switch (firstFrameState) {
      case C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED:
        return isStarted;
      case C.FIRST_FRAME_NOT_RENDERED:
        return true;
      case C.FIRST_FRAME_RENDERED:
        return false;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Processes an output image.
   *
   * @param positionUs The current playback position in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} in microseconds, measured at the
   *     start of the current iteration of the rendering loop.
   * @param outputBitmap The {@link Bitmap}.
   * @param bufferPresentationTimeUs The presentation time of the output buffer in microseconds.
   * @return Whether the output image was fully processed (for example, rendered or skipped).
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  protected boolean processOutputBuffer(
      long positionUs, long elapsedRealtimeUs, Bitmap outputBitmap, long bufferPresentationTimeUs)
      throws ExoPlaybackException {
    // TODO: b/319484746 - ImageRenderer should consider startPositionUs when choosing to output an
    // image.
    long earlyUs = bufferPresentationTimeUs - positionUs;
    if (shouldForceRender() || earlyUs < IMAGE_PRESENTATION_WINDOW_THRESHOLD_US) {
      imageOutput.onImageAvailable(
          bufferPresentationTimeUs - outputStreamInfo.streamOffsetUs, outputBitmap);
      return true;
    }
    return false;
  }

  /**
   * Called when an output buffer is successfully processed.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  private void onProcessedOutputBuffer(long presentationTimeUs) {
    lastProcessedOutputBufferTimeUs = presentationTimeUs;
    while (!pendingOutputStreamChanges.isEmpty()
        && presentationTimeUs >= pendingOutputStreamChanges.peek().previousStreamLastBufferTimeUs) {
      outputStreamInfo = pendingOutputStreamChanges.removeFirst();
    }
  }

  /**
   * @param positionUs The current playback position in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @return Whether we can feed more input data to the decoder.
   */
  @SuppressWarnings("deprecation") // Clearing C.BUFFER_FLAG_DECODE_ONLY for compatibility
  private boolean feedInputBuffer(long positionUs) throws ImageDecoderException {
    if (readyToOutputTiles && tileInfo != null) {
      return false;
    }
    FormatHolder formatHolder = getFormatHolder();
    if (decoder == null
        || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false;
    }
    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }
    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT) {
      checkStateNotNull(inputBuffer);
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      checkStateNotNull(decoder).queueInputBuffer(inputBuffer);
      inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }
    switch (readSource(formatHolder, inputBuffer, /* readFlags= */ 0)) {
      case C.RESULT_NOTHING_READ:
        return false;
      case C.RESULT_BUFFER_READ:
        inputBuffer.flip();
        // Input buffers with no data that are also non-EOS, only carry the timestamp for a grid
        // tile. These buffers are not queued.
        boolean shouldQueueBuffer =
            checkStateNotNull(inputBuffer.data).remaining() > 0
                || checkStateNotNull(inputBuffer).isEndOfStream();
        if (shouldQueueBuffer) {
          // TODO: b/318696449 - Don't use the deprecated BUFFER_FLAG_DECODE_ONLY with image chunks.
          checkStateNotNull(inputBuffer).clearFlag(C.BUFFER_FLAG_DECODE_ONLY);
          checkStateNotNull(decoder).queueInputBuffer(checkStateNotNull(inputBuffer));
          currentTileIndex = 0;
        }
        maybeAdvanceTileInfo(positionUs, checkStateNotNull(inputBuffer));
        if (checkStateNotNull(inputBuffer).isEndOfStream()) {
          inputStreamEnded = true;
          inputBuffer = null;
          return false;
        } else {
          largestQueuedPresentationTimeUs =
              max(largestQueuedPresentationTimeUs, checkStateNotNull(inputBuffer).timeUs);
        }
        // If inputBuffer was queued, the decoder already cleared it. Otherwise, inputBuffer is
        // cleared here.
        if (shouldQueueBuffer) {
          inputBuffer = null;
        } else {
          checkStateNotNull(inputBuffer).clear();
        }
        return !readyToOutputTiles;
      case C.RESULT_FORMAT_READ:
        inputFormat = checkStateNotNull(formatHolder.format);
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT;
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  @RequiresNonNull("inputFormat")
  @EnsuresNonNull("decoder")
  private void initDecoder() throws ExoPlaybackException {
    if (canCreateDecoderForFormat(inputFormat)) {
      if (decoder != null) {
        decoder.release();
      }
      decoder = decoderFactory.createImageDecoder();
    } else {
      throw createRendererException(
          new ImageDecoderException("Provided decoder factory can't create decoder for format."),
          inputFormat,
          PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  private boolean canCreateDecoderForFormat(Format format) {
    @Capabilities int supportsFormat = decoderFactory.supportsFormat(format);
    return supportsFormat == RendererCapabilities.create(C.FORMAT_HANDLED)
        || supportsFormat == RendererCapabilities.create(C.FORMAT_EXCEEDS_CAPABILITIES);
  }

  private void lowerFirstFrameState(@C.FirstFrameState int firstFrameState) {
    this.firstFrameState = min(this.firstFrameState, firstFrameState);
  }

  private void releaseDecoderResources() {
    inputBuffer = null;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    if (decoder != null) {
      decoder.release();
      decoder = null;
    }
  }

  private void setImageOutput(@Nullable ImageOutput imageOutput) {
    this.imageOutput = getImageOutput(imageOutput);
  }

  private void maybeAdvanceTileInfo(long positionUs, DecoderInputBuffer inputBuffer) {
    if (inputBuffer.isEndOfStream()) {
      readyToOutputTiles = true;
      return;
    }
    nextTileInfo = new TileInfo(currentTileIndex, inputBuffer.timeUs);
    currentTileIndex++;
    // TODO: b/319484746 - ImageRenderer should consider startPositionUs when choosing to output an
    // image.
    if (!readyToOutputTiles) {
      long tilePresentationTimeUs = nextTileInfo.getPresentationTimeUs();
      boolean isNextTileWithinPresentationThreshold =
          tilePresentationTimeUs - IMAGE_PRESENTATION_WINDOW_THRESHOLD_US <= positionUs
              && positionUs <= tilePresentationTimeUs + IMAGE_PRESENTATION_WINDOW_THRESHOLD_US;
      boolean isPositionBetweenTiles =
          tileInfo != null
              && tileInfo.getPresentationTimeUs() <= positionUs
              && positionUs < tilePresentationTimeUs;
      boolean isNextTileLastInGrid = isTileLastInGrid(checkStateNotNull(nextTileInfo));
      readyToOutputTiles =
          isNextTileWithinPresentationThreshold || isPositionBetweenTiles || isNextTileLastInGrid;
      if (isPositionBetweenTiles && !isNextTileWithinPresentationThreshold) {
        return;
      }
    }
    tileInfo = nextTileInfo;
    nextTileInfo = null;
  }

  private boolean isTileLastInGrid(TileInfo tileInfo) {
    return checkStateNotNull(inputFormat).tileCountHorizontal == Format.NO_VALUE
        || inputFormat.tileCountVertical == Format.NO_VALUE
        || (tileInfo.getTileIndex()
            == checkStateNotNull(inputFormat).tileCountVertical * inputFormat.tileCountHorizontal
                - 1);
  }

  private Bitmap cropTileFromImageGrid(int tileIndex) {
    checkStateNotNull(outputBitmap);
    int tileWidth = outputBitmap.getWidth() / checkStateNotNull(inputFormat).tileCountHorizontal;
    int tileHeight = outputBitmap.getHeight() / checkStateNotNull(inputFormat).tileCountVertical;
    int tileStartXCoordinate = tileWidth * (tileIndex % inputFormat.tileCountVertical);
    int tileStartYCoordinate = tileHeight * (tileIndex / inputFormat.tileCountHorizontal);
    return Bitmap.createBitmap(
        outputBitmap, tileStartXCoordinate, tileStartYCoordinate, tileWidth, tileHeight);
  }

  private static ImageOutput getImageOutput(@Nullable ImageOutput imageOutput) {
    return imageOutput == null ? ImageOutput.NO_OP : imageOutput;
  }

  private static class TileInfo {
    private final int tileIndex;
    private final long presentationTimeUs;
    private @MonotonicNonNull Bitmap tileBitmap;

    public TileInfo(int tileIndex, long presentationTimeUs) {
      this.tileIndex = tileIndex;
      this.presentationTimeUs = presentationTimeUs;
    }

    public int getTileIndex() {
      return this.tileIndex;
    }

    public long getPresentationTimeUs() {
      return presentationTimeUs;
    }

    public @Nullable Bitmap getTileBitmap() {
      return tileBitmap;
    }

    public void setTileBitmap(Bitmap tileBitmap) {
      this.tileBitmap = tileBitmap;
    }

    public boolean hasTileBitmap() {
      return tileBitmap != null;
    }
  }

  private static final class OutputStreamInfo {

    public static final OutputStreamInfo UNSET =
        new OutputStreamInfo(
            /* previousStreamLastBufferTimeUs= */ C.TIME_UNSET, /* streamOffsetUs= */ C.TIME_UNSET);

    public final long previousStreamLastBufferTimeUs;
    public final long streamOffsetUs;

    public OutputStreamInfo(long previousStreamLastBufferTimeUs, long streamOffsetUs) {
      this.previousStreamLastBufferTimeUs = previousStreamLastBufferTimeUs;
      this.streamOffsetUs = streamOffsetUs;
    }
  }
}
