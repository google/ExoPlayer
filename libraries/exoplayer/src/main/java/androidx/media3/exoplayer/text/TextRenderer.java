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
package androidx.media3.exoplayer.text;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import androidx.media3.extractor.text.CueDecoder;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleDecoder;
import androidx.media3.extractor.text.SubtitleDecoderException;
import androidx.media3.extractor.text.SubtitleInputBuffer;
import androidx.media3.extractor.text.SubtitleOutputBuffer;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.SideEffectFree;

/**
 * A {@link Renderer} for text.
 *
 * <p>This implementations decodes sample data to {@link Cue} instances. The actual rendering is
 * delegated to a {@link TextOutput}.
 */
// TODO: b/289916598 - Add an opt-in method for the legacy subtitle decoding flow, and throw an
//  exception if it's not used and a recognized subtitle MIME type (that isn't
//  application/x-media3-cues) is passed in.
@UnstableApi
public final class TextRenderer extends BaseRenderer implements Callback {

  private static final String TAG = "TextRenderer";

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REPLACEMENT_STATE_NONE,
    REPLACEMENT_STATE_SIGNAL_END_OF_STREAM,
    REPLACEMENT_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReplacementState {}

  /** The decoder does not need to be replaced. */
  private static final int REPLACEMENT_STATE_NONE = 0;

  /**
   * The decoder needs to be replaced, but we haven't yet signaled an end of stream to the existing
   * decoder. We need to do so in order to ensure that it outputs any remaining buffers before we
   * release it.
   */
  private static final int REPLACEMENT_STATE_SIGNAL_END_OF_STREAM = 1;

  /**
   * The decoder needs to be replaced, and we've signaled an end of stream to the existing decoder.
   * We're waiting for the decoder to output an end of stream signal to indicate that it has output
   * any remaining buffers before we release it.
   */
  private static final int REPLACEMENT_STATE_WAIT_END_OF_STREAM = 2;

  private static final int MSG_UPDATE_OUTPUT = 0;

  // Fields used when handling CuesWithTiming objects from application/x-media3-cues samples.
  private final CueDecoder cueDecoder;
  private final DecoderInputBuffer cueDecoderInputBuffer;
  private @MonotonicNonNull CuesResolver cuesResolver;

  // Fields used when handling Subtitle objects from legacy samples.
  private final SubtitleDecoderFactory subtitleDecoderFactory;
  private boolean waitingForKeyFrame;
  private @ReplacementState int decoderReplacementState;
  @Nullable private SubtitleDecoder subtitleDecoder;
  @Nullable private SubtitleInputBuffer nextSubtitleInputBuffer;
  @Nullable private SubtitleOutputBuffer subtitle;
  @Nullable private SubtitleOutputBuffer nextSubtitle;
  private int nextSubtitleEventIndex;

  // Fields used with both CuesWithTiming and Subtitle objects
  @Nullable private final Handler outputHandler;
  private final TextOutput output;
  private final FormatHolder formatHolder;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  @Nullable private Format streamFormat;
  private long outputStreamOffsetUs;
  private long lastRendererPositionUs;
  private long finalStreamEndPositionUs;
  private boolean legacyDecodingEnabled;

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using {@link
   *     android.app.Activity#getMainLooper()}. Null may be passed if the output should be called
   *     directly on the player's internal rendering thread.
   */
  public TextRenderer(TextOutput output, @Nullable Looper outputLooper) {
    this(output, outputLooper, SubtitleDecoderFactory.DEFAULT);
  }

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using {@link
   *     android.app.Activity#getMainLooper()}. Null may be passed if the output should be called
   *     directly on the player's internal rendering thread.
   * @param subtitleDecoderFactory A factory from which to obtain {@link SubtitleDecoder} instances.
   */
  public TextRenderer(
      TextOutput output,
      @Nullable Looper outputLooper,
      SubtitleDecoderFactory subtitleDecoderFactory) {
    super(C.TRACK_TYPE_TEXT);
    this.output = checkNotNull(output);
    this.outputHandler =
        outputLooper == null ? null : Util.createHandler(outputLooper, /* callback= */ this);
    this.subtitleDecoderFactory = subtitleDecoderFactory;
    this.cueDecoder = new CueDecoder();
    this.cueDecoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    formatHolder = new FormatHolder();
    finalStreamEndPositionUs = C.TIME_UNSET;
    outputStreamOffsetUs = C.TIME_UNSET;
    lastRendererPositionUs = C.TIME_UNSET;
    legacyDecodingEnabled = true;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public @Capabilities int supportsFormat(Format format) {
    // TODO: b/289983417 - Return UNSUPPORTED for non-media3-queues once we stop supporting them
    //   completely. In the meantime, we return SUPPORTED here and then throw later  if
    //   legacyDecodingEnabled is false (when receiving the first Format or sample). This ensures
    //   apps are aware (via the playback failure) they're using a legacy/deprecated code path.
    if (isCuesWithTiming(format) || subtitleDecoderFactory.supportsFormat(format)) {
      return RendererCapabilities.create(
          format.cryptoType == C.CRYPTO_TYPE_NONE ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_DRM);
    } else if (MimeTypes.isText(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    } else {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
  }

  /**
   * Sets the position at which to stop rendering the current stream.
   *
   * <p>Must be called after {@link #setCurrentStreamFinal()}.
   *
   * @param streamEndPositionUs The position to stop rendering at or {@link C#LENGTH_UNSET} to
   *     render until the end of the current stream.
   */
  // TODO(internal b/181312195): Remove this when it's no longer needed once subtitles are decoded
  // on the loading side of SampleQueue.
  public void setFinalStreamEndPositionUs(long streamEndPositionUs) {
    checkState(isCurrentStreamFinal());
    this.finalStreamEndPositionUs = streamEndPositionUs;
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId) {
    outputStreamOffsetUs = offsetUs;
    streamFormat = formats[0];
    if (!isCuesWithTiming(streamFormat)) {
      assertLegacyDecodingEnabledIfRequired();
      if (subtitleDecoder != null) {
        decoderReplacementState = REPLACEMENT_STATE_SIGNAL_END_OF_STREAM;
      } else {
        initSubtitleDecoder();
      }
    } else {
      this.cuesResolver =
          streamFormat.cueReplacementBehavior == Format.CUE_REPLACEMENT_BEHAVIOR_MERGE
              ? new MergingCuesResolver()
              : new ReplacingCuesResolver();
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    lastRendererPositionUs = positionUs;
    if (cuesResolver != null) {
      cuesResolver.clear();
    }
    clearOutput();
    inputStreamEnded = false;
    outputStreamEnded = false;
    finalStreamEndPositionUs = C.TIME_UNSET;
    if (streamFormat != null && !isCuesWithTiming(streamFormat)) {
      if (decoderReplacementState != REPLACEMENT_STATE_NONE) {
        replaceSubtitleDecoder();
      } else {
        releaseSubtitleBuffers();
        checkNotNull(subtitleDecoder).flush();
      }
    }
  }

  // Setting deprecated decode-only flag for compatibility with decoders that are still using it.
  @SuppressWarnings("deprecation")
  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    if (isCurrentStreamFinal()
        && finalStreamEndPositionUs != C.TIME_UNSET
        && positionUs >= finalStreamEndPositionUs) {
      releaseSubtitleBuffers();
      outputStreamEnded = true;
    }

    if (outputStreamEnded) {
      return;
    }

    if (isCuesWithTiming(checkNotNull(streamFormat))) {
      checkNotNull(cuesResolver);
      renderFromCuesWithTiming(positionUs);
    } else {
      assertLegacyDecodingEnabledIfRequired();
      renderFromSubtitles(positionUs);
    }
  }

  /**
   * Sets whether to decode subtitle data during rendering.
   *
   * <p>If this is enabled, then the {@link SubtitleDecoderFactory} passed to the constructor is
   * used to decode subtitle data during rendering.
   *
   * <p>If this is disabled this text renderer can only handle tracks with MIME type {@link
   * MimeTypes#APPLICATION_MEDIA3_CUES} (which have been parsed from their original format during
   * extraction), and will throw an exception if passed data of a different type.
   *
   * <p>This is enabled by default.
   *
   * <p>This method is experimental. It may change behavior, be renamed, or removed in a future
   * release.
   */
  public void experimentalSetLegacyDecodingEnabled(boolean legacyDecodingEnabled) {
    this.legacyDecodingEnabled = legacyDecodingEnabled;
  }

  @RequiresNonNull("this.cuesResolver")
  private void renderFromCuesWithTiming(long positionUs) {
    boolean outputNeedsUpdating = readAndDecodeCuesWithTiming(positionUs);

    long nextCueChangeTimeUs = cuesResolver.getNextCueChangeTimeUs(lastRendererPositionUs);
    if (nextCueChangeTimeUs == C.TIME_END_OF_SOURCE && inputStreamEnded && !outputNeedsUpdating) {
      outputStreamEnded = true;
    }
    if (nextCueChangeTimeUs != C.TIME_END_OF_SOURCE && nextCueChangeTimeUs <= positionUs) {
      outputNeedsUpdating = true;
    }

    if (outputNeedsUpdating) {
      ImmutableList<Cue> cuesAtTimeUs = cuesResolver.getCuesAtTimeUs(positionUs);
      long previousCueChangeTimeUs = cuesResolver.getPreviousCueChangeTimeUs(positionUs);
      updateOutput(new CueGroup(cuesAtTimeUs, getPresentationTimeUs(previousCueChangeTimeUs)));
      cuesResolver.discardCuesBeforeTimeUs(previousCueChangeTimeUs);
    }
    lastRendererPositionUs = positionUs;
  }

  /**
   * Tries to {@linkplain #readSource(FormatHolder, DecoderInputBuffer, int) read} a buffer, and if
   * one is read decodes it to a {@link CuesWithTiming} and adds it to {@link MergingCuesResolver}.
   *
   * @return true if a {@link CuesWithTiming} was read that changes what should be on screen now.
   */
  @RequiresNonNull("this.cuesResolver")
  private boolean readAndDecodeCuesWithTiming(long positionUs) {
    if (inputStreamEnded) {
      return false;
    }
    @ReadDataResult
    int readResult = readSource(formatHolder, cueDecoderInputBuffer, /* readFlags= */ 0);
    switch (readResult) {
      case C.RESULT_BUFFER_READ:
        if (cueDecoderInputBuffer.isEndOfStream()) {
          inputStreamEnded = true;
          return false;
        }
        cueDecoderInputBuffer.flip();
        ByteBuffer cueData = checkNotNull(cueDecoderInputBuffer.data);
        CuesWithTiming cuesWithTiming =
            cueDecoder.decode(
                cueDecoderInputBuffer.timeUs,
                cueData.array(),
                cueData.arrayOffset(),
                cueData.limit());
        cueDecoderInputBuffer.clear();

        return cuesResolver.addCues(cuesWithTiming, positionUs);
      case C.RESULT_FORMAT_READ:
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }

  @SuppressWarnings("deprecation") // Using deprecated C.BUFFER_FLAG_DECODE_ONLY for compatibility
  private void renderFromSubtitles(long positionUs) {
    lastRendererPositionUs = positionUs;
    if (nextSubtitle == null) {
      checkNotNull(subtitleDecoder).setPositionUs(positionUs);
      try {
        nextSubtitle = checkNotNull(subtitleDecoder).dequeueOutputBuffer();
      } catch (SubtitleDecoderException e) {
        handleDecoderError(e);
        return;
      }
    }

    if (getState() != STATE_STARTED) {
      return;
    }

    boolean textRendererNeedsUpdate = false;
    if (subtitle != null) {
      // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
      // advance to the next event.
      long subtitleNextEventTimeUs = getNextEventTime();
      while (subtitleNextEventTimeUs <= positionUs) {
        nextSubtitleEventIndex++;
        subtitleNextEventTimeUs = getNextEventTime();
        textRendererNeedsUpdate = true;
      }
    }
    if (nextSubtitle != null) {
      SubtitleOutputBuffer nextSubtitle = this.nextSubtitle;
      if (nextSubtitle.isEndOfStream()) {
        if (!textRendererNeedsUpdate && getNextEventTime() == Long.MAX_VALUE) {
          if (decoderReplacementState == REPLACEMENT_STATE_WAIT_END_OF_STREAM) {
            replaceSubtitleDecoder();
          } else {
            releaseSubtitleBuffers();
            outputStreamEnded = true;
          }
        }
      } else if (nextSubtitle.timeUs <= positionUs) {
        // Advance to the next subtitle. Sync the next event index and trigger an update.
        if (subtitle != null) {
          subtitle.release();
        }
        nextSubtitleEventIndex = nextSubtitle.getNextEventTimeIndex(positionUs);
        subtitle = nextSubtitle;
        this.nextSubtitle = null;
        textRendererNeedsUpdate = true;
      }
    }

    if (textRendererNeedsUpdate) {
      // If textRendererNeedsUpdate then subtitle must be non-null.
      checkNotNull(subtitle);
      // textRendererNeedsUpdate is set and we're playing. Update the renderer.
      long presentationTimeUs = getPresentationTimeUs(getCurrentEventTimeUs(positionUs));
      CueGroup cueGroup = new CueGroup(subtitle.getCues(positionUs), presentationTimeUs);
      updateOutput(cueGroup);
    }

    if (decoderReplacementState == REPLACEMENT_STATE_WAIT_END_OF_STREAM) {
      return;
    }

    try {
      while (!inputStreamEnded) {
        @Nullable SubtitleInputBuffer nextInputBuffer = this.nextSubtitleInputBuffer;
        if (nextInputBuffer == null) {
          nextInputBuffer = checkNotNull(subtitleDecoder).dequeueInputBuffer();
          if (nextInputBuffer == null) {
            return;
          }
          this.nextSubtitleInputBuffer = nextInputBuffer;
        }
        if (decoderReplacementState == REPLACEMENT_STATE_SIGNAL_END_OF_STREAM) {
          nextInputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
          checkNotNull(subtitleDecoder).queueInputBuffer(nextInputBuffer);
          this.nextSubtitleInputBuffer = null;
          decoderReplacementState = REPLACEMENT_STATE_WAIT_END_OF_STREAM;
          return;
        }
        // Try and read the next subtitle from the source.
        @ReadDataResult int result = readSource(formatHolder, nextInputBuffer, /* readFlags= */ 0);
        if (result == C.RESULT_BUFFER_READ) {
          if (nextInputBuffer.isEndOfStream()) {
            inputStreamEnded = true;
            waitingForKeyFrame = false;
          } else {
            @Nullable Format format = formatHolder.format;
            if (format == null) {
              // We haven't received a format yet.
              return;
            }
            nextInputBuffer.subsampleOffsetUs = format.subsampleOffsetUs;
            nextInputBuffer.flip();
            waitingForKeyFrame &= !nextInputBuffer.isKeyFrame();
          }
          if (!waitingForKeyFrame) {
            if (nextInputBuffer.timeUs < getLastResetPositionUs()) {
              nextInputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
            }
            checkNotNull(subtitleDecoder).queueInputBuffer(nextInputBuffer);
            this.nextSubtitleInputBuffer = null;
          }
        } else if (result == C.RESULT_NOTHING_READ) {
          return;
        }
      }
    } catch (SubtitleDecoderException e) {
      handleDecoderError(e);
    }
  }

  @Override
  protected void onDisabled() {
    streamFormat = null;
    finalStreamEndPositionUs = C.TIME_UNSET;
    clearOutput();
    outputStreamOffsetUs = C.TIME_UNSET;
    lastRendererPositionUs = C.TIME_UNSET;
    if (subtitleDecoder != null) {
      releaseSubtitleDecoder();
    }
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
    return true;
  }

  private void releaseSubtitleBuffers() {
    nextSubtitleInputBuffer = null;
    nextSubtitleEventIndex = C.INDEX_UNSET;
    if (subtitle != null) {
      subtitle.release();
      subtitle = null;
    }
    if (nextSubtitle != null) {
      nextSubtitle.release();
      nextSubtitle = null;
    }
  }

  private void releaseSubtitleDecoder() {
    releaseSubtitleBuffers();
    checkNotNull(subtitleDecoder).release();
    subtitleDecoder = null;
    decoderReplacementState = REPLACEMENT_STATE_NONE;
  }

  private void initSubtitleDecoder() {
    waitingForKeyFrame = true;
    subtitleDecoder = subtitleDecoderFactory.createDecoder(checkNotNull(streamFormat));
  }

  private void replaceSubtitleDecoder() {
    releaseSubtitleDecoder();
    initSubtitleDecoder();
  }

  private long getNextEventTime() {
    if (nextSubtitleEventIndex == C.INDEX_UNSET) {
      return Long.MAX_VALUE;
    }
    checkNotNull(subtitle);
    return nextSubtitleEventIndex >= subtitle.getEventTimeCount()
        ? Long.MAX_VALUE
        : subtitle.getEventTime(nextSubtitleEventIndex);
  }

  private void updateOutput(CueGroup cueGroup) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_UPDATE_OUTPUT, cueGroup).sendToTarget();
    } else {
      invokeUpdateOutputInternal(cueGroup);
    }
  }

  private void clearOutput() {
    updateOutput(new CueGroup(ImmutableList.of(), getPresentationTimeUs(lastRendererPositionUs)));
  }

  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OUTPUT:
        invokeUpdateOutputInternal((CueGroup) msg.obj);
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  @SuppressWarnings("deprecation") // We need to call both onCues method for backward compatibility.
  private void invokeUpdateOutputInternal(CueGroup cueGroup) {
    output.onCues(cueGroup.cues);
    output.onCues(cueGroup);
  }

  /**
   * Called when {@link #subtitleDecoder} throws an exception, so it can be logged and playback can
   * continue.
   *
   * <p>Logs {@code e} and resets state to allow decoding the next sample.
   */
  private void handleDecoderError(SubtitleDecoderException e) {
    Log.e(TAG, "Subtitle decoding failed. streamFormat=" + streamFormat, e);
    clearOutput();
    replaceSubtitleDecoder();
  }

  @RequiresNonNull("subtitle")
  @SideEffectFree
  private long getCurrentEventTimeUs(long positionUs) {
    int nextEventTimeIndex = subtitle.getNextEventTimeIndex(positionUs);
    if (nextEventTimeIndex == 0 || subtitle.getEventTimeCount() == 0) {
      return subtitle.timeUs;
    }

    return nextEventTimeIndex == C.INDEX_UNSET
        ? subtitle.getEventTime(subtitle.getEventTimeCount() - 1)
        : subtitle.getEventTime(nextEventTimeIndex - 1);
  }

  @SideEffectFree
  private long getPresentationTimeUs(long positionUs) {
    checkState(positionUs != C.TIME_UNSET);
    checkState(outputStreamOffsetUs != C.TIME_UNSET);

    return positionUs - outputStreamOffsetUs;
  }

  @RequiresNonNull("streamFormat")
  private void assertLegacyDecodingEnabledIfRequired() {
    checkState(
        legacyDecodingEnabled
            || Objects.equals(streamFormat.sampleMimeType, MimeTypes.APPLICATION_CEA608)
            || Objects.equals(streamFormat.sampleMimeType, MimeTypes.APPLICATION_MP4CEA608)
            || Objects.equals(streamFormat.sampleMimeType, MimeTypes.APPLICATION_CEA708),
        "Legacy decoding is disabled, can't handle "
            + streamFormat.sampleMimeType
            + " samples (expected "
            + MimeTypes.APPLICATION_MEDIA3_CUES
            + ").");
  }

  /** Returns whether {@link Format#sampleMimeType} is {@link MimeTypes#APPLICATION_MEDIA3_CUES}. */
  @SideEffectFree
  private static boolean isCuesWithTiming(Format format) {
    return Objects.equals(format.sampleMimeType, MimeTypes.APPLICATION_MEDIA3_CUES);
  }
}
