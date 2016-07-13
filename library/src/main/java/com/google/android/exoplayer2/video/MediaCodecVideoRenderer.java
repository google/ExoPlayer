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
package com.google.android.exoplayer2.video;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener.EventDispatcher;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Decodes and renders video using {@link MediaCodec}.
 */
@TargetApi(16)
public class MediaCodecVideoRenderer extends MediaCodecRenderer {

  private static final String TAG = "MediaCodecVideoRenderer";
  private static final String KEY_CROP_LEFT = "crop-left";
  private static final String KEY_CROP_RIGHT = "crop-right";
  private static final String KEY_CROP_BOTTOM = "crop-bottom";
  private static final String KEY_CROP_TOP = "crop-top";

  private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
  private final EventDispatcher eventDispatcher;
  private final long allowedJoiningTimeMs;
  private final int videoScalingMode;
  private final int maxDroppedFrameCountToNotify;
  private final boolean deviceNeedsAutoFrcWorkaround;

  private int adaptiveMaxWidth;
  private int adaptiveMaxHeight;

  private Surface surface;
  private boolean reportedDrawnToSurface;
  private boolean renderedFirstFrame;
  private long joiningDeadlineMs;
  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrameCount;
  private int consecutiveDroppedFrameCount;

  private int pendingRotationDegrees;
  private float pendingPixelWidthHeightRatio;
  private int currentWidth;
  private int currentHeight;
  private int currentUnappliedRotationDegrees;
  private float currentPixelWidthHeightRatio;
  private int lastReportedWidth;
  private int lastReportedHeight;
  private int lastReportedUnappliedRotationDegrees;
  private float lastReportedPixelWidthHeightRatio;

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      int videoScalingMode) {
    this(context, mediaCodecSelector, videoScalingMode, 0);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      int videoScalingMode, long allowedJoiningTimeMs) {
    this(context, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, null, -1);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      int videoScalingMode, long allowedJoiningTimeMs, Handler eventHandler,
      VideoRendererEventListener eventListener, int maxDroppedFrameCountToNotify) {
    this(context, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, false,
        eventHandler, eventListener, maxDroppedFrameCountToNotify);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      int videoScalingMode, long allowedJoiningTimeMs, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler,
      VideoRendererEventListener eventListener, int maxDroppedFrameCountToNotify) {
    super(mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys);
    this.videoScalingMode = videoScalingMode;
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.maxDroppedFrameCountToNotify = maxDroppedFrameCountToNotify;
    frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(context);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    deviceNeedsAutoFrcWorkaround = deviceNeedsAutoFrcWorkaround();
    joiningDeadlineMs = -1;
    currentWidth = -1;
    currentHeight = -1;
    currentPixelWidthHeightRatio = -1;
    pendingPixelWidthHeightRatio = -1;
    lastReportedWidth = -1;
    lastReportedHeight = -1;
    lastReportedPixelWidthHeightRatio = -1;
  }

  @Override
  public int getTrackType() {
    return C.TRACK_TYPE_VIDEO;
  }

  @Override
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isVideo(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    MediaCodecInfo decoderInfo = mediaCodecSelector.getDecoderInfo(mimeType,
        format.requiresSecureDecryption);
    if (decoderInfo == null) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    }

    boolean decoderCapable;
    if (format.width > 0 && format.height > 0) {
      if (Util.SDK_INT >= 21) {
        if (format.frameRate > 0) {
          decoderCapable = decoderInfo.isVideoSizeAndRateSupportedV21(format.width, format.height,
              format.frameRate);
        } else {
          decoderCapable = decoderInfo.isVideoSizeSupportedV21(format.width, format.height);
        }
        decoderCapable &= decoderInfo.isCodecSupported(format.codecs);
      } else {
        decoderCapable = format.width * format.height <= MediaCodecUtil.maxH264DecodableFrameSize();
      }
    } else {
      // We don't know any better, so assume true.
      decoderCapable = true;
    }

    int adaptiveSupport = decoderInfo.adaptive ? ADAPTIVE_SEAMLESS : ADAPTIVE_NOT_SEAMLESS;
    int formatSupport = decoderCapable ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
    return adaptiveSupport | formatSupport;
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    super.onEnabled(joining);
    eventDispatcher.enabled(decoderCounters);
    frameReleaseTimeHelper.enable();
  }

  @Override
  protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
    adaptiveMaxWidth = Format.NO_VALUE;
    adaptiveMaxHeight = Format.NO_VALUE;
    if (formats.length > 1) {
      for (Format format : formats) {
        adaptiveMaxWidth = Math.max(adaptiveMaxWidth, format.width);
        adaptiveMaxHeight = Math.max(adaptiveMaxHeight, format.height);
      }
      if (adaptiveMaxWidth == Format.NO_VALUE || adaptiveMaxHeight == Format.NO_VALUE) {
        Log.w(TAG, "Maximum dimensions unknown. Assuming 1920x1080.");
        adaptiveMaxWidth = 1920;
        adaptiveMaxHeight = 1080;
      }
    }
    super.onStreamChanged(formats);
  }

  @Override
  protected void onReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onReset(positionUs, joining);
    renderedFirstFrame = false;
    consecutiveDroppedFrameCount = 0;
    joiningDeadlineMs = joining && allowedJoiningTimeMs > 0
        ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs) : -1;
  }

  @Override
  protected boolean isReady() {
    if (renderedFirstFrame && super.isReady()) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineMs = -1;
      return true;
    } else if (joiningDeadlineMs == -1) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() < joiningDeadlineMs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineMs = -1;
      return false;
    }
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    droppedFrameCount = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
  }

  @Override
  protected void onStopped() {
    joiningDeadlineMs = -1;
    maybeNotifyDroppedFrameCount();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    currentWidth = -1;
    currentHeight = -1;
    currentPixelWidthHeightRatio = -1;
    pendingPixelWidthHeightRatio = -1;
    lastReportedWidth = -1;
    lastReportedHeight = -1;
    lastReportedPixelWidthHeightRatio = -1;
    frameReleaseTimeHelper.disable();
    try {
      super.onDisabled();
    } finally {
      decoderCounters.ensureUpdated();
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_SURFACE) {
      setSurface((Surface) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }

  /**
   * @param surface The surface to set.
   * @throws ExoPlaybackException
   */
  private void setSurface(Surface surface) throws ExoPlaybackException {
    if (this.surface == surface) {
      return;
    }
    this.surface = surface;
    this.reportedDrawnToSurface = false;
    int state = getState();
    if (state == STATE_ENABLED || state == STATE_STARTED) {
      releaseCodec();
      maybeInitCodec();
    }
  }

  @Override
  protected boolean shouldInitCodec() {
    return super.shouldInitCodec() && surface != null && surface.isValid();
  }

  // Override configureCodec to provide the surface.
  @Override
  protected void configureCodec(MediaCodec codec, Format format, MediaCrypto crypto) {
    codec.configure(getFrameworkMediaFormat(format), surface, crypto, 0);
  }

  @Override
  protected void onCodecInitialized(String name, long initializedTimestampMs,
      long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
  }

  @Override
  protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
    super.onInputFormatChanged(newFormat);
    eventDispatcher.inputFormatChanged(newFormat);
    pendingPixelWidthHeightRatio = newFormat.pixelWidthHeightRatio == Format.NO_VALUE ? 1
        : newFormat.pixelWidthHeightRatio;
    pendingRotationDegrees = newFormat.rotationDegrees == Format.NO_VALUE ? 0
        : newFormat.rotationDegrees;
  }

  /**
   * @return True if the first frame has been rendered (playback has not necessarily begun).
   */
  protected final boolean haveRenderedFirstFrame() {
    return renderedFirstFrame;
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, android.media.MediaFormat outputFormat) {
    boolean hasCrop = outputFormat.containsKey(KEY_CROP_RIGHT)
        && outputFormat.containsKey(KEY_CROP_LEFT) && outputFormat.containsKey(KEY_CROP_BOTTOM)
        && outputFormat.containsKey(KEY_CROP_TOP);
    currentWidth = hasCrop
        ? outputFormat.getInteger(KEY_CROP_RIGHT) - outputFormat.getInteger(KEY_CROP_LEFT) + 1
        : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
    currentHeight = hasCrop
        ? outputFormat.getInteger(KEY_CROP_BOTTOM) - outputFormat.getInteger(KEY_CROP_TOP) + 1
        : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
    currentPixelWidthHeightRatio = pendingPixelWidthHeightRatio;
    if (Util.SDK_INT >= 21) {
      // On API level 21 and above the decoder applies the rotation when rendering to the surface.
      // Hence currentUnappliedRotation should always be 0. For 90 and 270 degree rotations, we need
      // to flip the width, height and pixel aspect ratio to reflect the rotation that was applied.
      if (pendingRotationDegrees == 90 || pendingRotationDegrees == 270) {
        int rotatedHeight = currentWidth;
        currentWidth = currentHeight;
        currentHeight = rotatedHeight;
        currentPixelWidthHeightRatio = 1 / currentPixelWidthHeightRatio;
      }
    } else {
      // On API level 20 and below the decoder does not apply the rotation.
      currentUnappliedRotationDegrees = pendingRotationDegrees;
    }
    // Must be applied each time the output format changes.
    codec.setVideoScalingMode(videoScalingMode);
  }

  @Override
  protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
      Format oldFormat, Format newFormat) {
    return newFormat.sampleMimeType.equals(oldFormat.sampleMimeType)
        && (codecIsAdaptive
        || (oldFormat.width == newFormat.width && oldFormat.height == newFormat.height));
  }

  @Override
  protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec,
      ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs,
      boolean shouldSkip) {
    if (shouldSkip) {
      skipOutputBuffer(codec, bufferIndex);
      return true;
    }

    if (!renderedFirstFrame) {
      if (Util.SDK_INT >= 21) {
        renderOutputBufferV21(codec, bufferIndex, System.nanoTime());
      } else {
        renderOutputBuffer(codec, bufferIndex);
      }
      return true;
    }

    if (getState() != STATE_STARTED) {
      return false;
    }

    // Compute how many microseconds it is until the buffer's presentation time.
    long elapsedSinceStartOfLoopUs = (SystemClock.elapsedRealtime() * 1000) - elapsedRealtimeUs;
    long earlyUs = bufferPresentationTimeUs - positionUs - elapsedSinceStartOfLoopUs;

    // Compute the buffer's desired release time in nanoseconds.
    long systemTimeNs = System.nanoTime();
    long unadjustedFrameReleaseTimeNs = systemTimeNs + (earlyUs * 1000);

    // Apply a timestamp adjustment, if there is one.
    long adjustedReleaseTimeNs = frameReleaseTimeHelper.adjustReleaseTime(
        bufferPresentationTimeUs, unadjustedFrameReleaseTimeNs);
    earlyUs = (adjustedReleaseTimeNs - systemTimeNs) / 1000;

    if (earlyUs < -30000) {
      // We're more than 30ms late rendering the frame.
      dropOutputBuffer(codec, bufferIndex);
      return true;
    }

    if (Util.SDK_INT >= 21) {
      // Let the underlying framework time the release.
      if (earlyUs < 50000) {
        renderOutputBufferV21(codec, bufferIndex, adjustedReleaseTimeNs);
        return true;
      }
    } else {
      // We need to time the release ourselves.
      if (earlyUs < 30000) {
        if (earlyUs > 11000) {
          // We're a little too early to render the frame. Sleep until the frame can be rendered.
          // Note: The 11ms threshold was chosen fairly arbitrarily.
          try {
            // Subtracting 10000 rather than 11000 ensures the sleep time will be at least 1ms.
            Thread.sleep((earlyUs - 10000) / 1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        renderOutputBuffer(codec, bufferIndex);
        return true;
      }
    }

    // We're either not playing, or it's not time to render the frame yet.
    return false;
  }

  protected void skipOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("skipVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    decoderCounters.skippedOutputBufferCount++;
  }

  protected void dropOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("dropVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    decoderCounters.droppedOutputBufferCount++;
    droppedFrameCount++;
    consecutiveDroppedFrameCount++;
    decoderCounters.maxConsecutiveDroppedOutputBufferCount = Math.max(consecutiveDroppedFrameCount,
        decoderCounters.maxConsecutiveDroppedOutputBufferCount);
    if (droppedFrameCount == maxDroppedFrameCountToNotify) {
      maybeNotifyDroppedFrameCount();
    }
  }

  protected void renderOutputBuffer(MediaCodec codec, int bufferIndex) {
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(bufferIndex, true);
    TraceUtil.endSection();
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    renderedFirstFrame = true;
    maybeNotifyDrawnToSurface();
  }

  @TargetApi(21)
  protected void renderOutputBufferV21(MediaCodec codec, int bufferIndex, long releaseTimeNs) {
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(bufferIndex, releaseTimeNs);
    TraceUtil.endSection();
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    renderedFirstFrame = true;
    maybeNotifyDrawnToSurface();
  }

  @SuppressLint("InlinedApi")
  private android.media.MediaFormat getFrameworkMediaFormat(Format format) {
    android.media.MediaFormat frameworkMediaFormat = format.getFrameworkMediaFormatV16();

    if (deviceNeedsAutoFrcWorkaround) {
      frameworkMediaFormat.setInteger("auto-frc", 0);
    }

    // Set the maximum adaptive video dimensions if applicable.
    if (adaptiveMaxWidth != Format.NO_VALUE && adaptiveMaxHeight != Format.NO_VALUE) {
      frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, adaptiveMaxWidth);
      frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, adaptiveMaxHeight);
    }

    if (format.maxInputSize > 0) {
      // The format already has a maximum input size.
      return frameworkMediaFormat;
    }

    // If the format doesn't define a maximum input size, determine one ourselves.
    int maxWidth = Math.max(adaptiveMaxWidth, format.width);
    int maxHeight = Math.max(adaptiveMaxHeight, format.height);
    int maxPixels;
    int minCompressionRatio;
    switch (format.sampleMimeType) {
      case MimeTypes.VIDEO_H263:
      case MimeTypes.VIDEO_MP4V:
        maxPixels = maxWidth * maxHeight;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H264:
        if ("BRAVIA 4K 2015".equals(Util.MODEL)) {
          // The Sony BRAVIA 4k TV has input buffers that are too small for the calculated 4k video
          // maximum input size, so use the default value.
          return frameworkMediaFormat;
        }
        // Round up width/height to an integer number of macroblocks.
        maxPixels = ((maxWidth + 15) / 16) * ((maxHeight + 15) / 16) * 16 * 16;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_VP8:
        // VPX does not specify a ratio so use the values from the platform's SoftVPX.cpp.
        maxPixels = maxWidth * maxHeight;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H265:
      case MimeTypes.VIDEO_VP9:
        maxPixels = maxWidth * maxHeight;
        minCompressionRatio = 4;
        break;
      default:
        // Leave the default max input size.
        return frameworkMediaFormat;
    }
    // Estimate the maximum input size assuming three channel 4:2:0 subsampled input frames.
    int maxInputSize = (maxPixels * 3) / (2 * minCompressionRatio);
    frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
    return frameworkMediaFormat;
  }

  private void maybeNotifyDrawnToSurface() {
    if (!reportedDrawnToSurface) {
      eventDispatcher.drawnToSurface(surface);
      reportedDrawnToSurface = true;
    }
  }

  private void maybeNotifyVideoSizeChanged() {
    if (lastReportedWidth != currentWidth || lastReportedHeight != currentHeight
        || lastReportedUnappliedRotationDegrees != currentUnappliedRotationDegrees
        || lastReportedPixelWidthHeightRatio != currentPixelWidthHeightRatio) {
      eventDispatcher.videoSizeChanged(currentWidth, currentHeight, currentUnappliedRotationDegrees,
          currentPixelWidthHeightRatio);
      lastReportedWidth = currentWidth;
      lastReportedHeight = currentHeight;
      lastReportedUnappliedRotationDegrees = currentUnappliedRotationDegrees;
      lastReportedPixelWidthHeightRatio = currentPixelWidthHeightRatio;
    }
  }

  private void maybeNotifyDroppedFrameCount() {
    if (droppedFrameCount > 0) {
      long now = SystemClock.elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrameCount(droppedFrameCount, elapsedMs);
      droppedFrameCount = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

  /**
   * Returns whether the device is known to enable frame-rate conversion logic that negatively
   * impacts ExoPlayer.
   * <p>
   * If true is returned then we explicitly disable the feature.
   *
   * @return True if the device is known to enable frame-rate conversion logic that negatively
   *     impacts ExoPlayer. False otherwise.
   */
  private static boolean deviceNeedsAutoFrcWorkaround() {
    // nVidia Shield prior to M tries to adjust the playback rate to better map the frame-rate of
    // content to the refresh rate of the display. For example playback of 23.976fps content is
    // adjusted to play at 1.001x speed when the output display is 60Hz. Unfortunately the
    // implementation causes ExoPlayer's reported playback position to drift out of sync. Captions
    // also lose sync [Internal: b/26453592].
    return Util.SDK_INT <= 22 && "foster".equals(Util.DEVICE) && "NVIDIA".equals(Util.MANUFACTURER);
  }

}
