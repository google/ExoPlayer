/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.MediaFormatUtil.createMediaFormatFromFormat;
import static com.google.android.exoplayer2.util.Util.SDK_INT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A default implementation of {@link Codec.DecoderFactory}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class DefaultDecoderFactory implements Codec.DecoderFactory {

  private static final String TAG = "DefaultDecoderFactory";

  private final Context context;

  private final boolean decoderSupportsKeyAllowFrameDrop;

  public DefaultDecoderFactory(Context context) {
    this.context = context;

    decoderSupportsKeyAllowFrameDrop =
        SDK_INT >= 29
            && context.getApplicationContext().getApplicationInfo().targetSdkVersion >= 29;
  }

  @Override
  public Codec createForAudioDecoding(Format format) throws ExportException {
    checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat = createMediaFormatFromFormat(format);

    String mediaCodecName;
    try {
      @Nullable MediaCodecInfo decoderInfo = getDecoderInfo(format);
      if (decoderInfo == null) {
        throw createExportException(format, /* reason= */ "No decoders for format");
      }
      mediaCodecName = decoderInfo.name;
      String codecMimeType = decoderInfo.codecMimeType;
      // Does not alter format.sampleMimeType to keep the original MimeType.
      // The MIME type of the selected decoder may differ from Format.sampleMimeType.
      mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    } catch (MediaCodecUtil.DecoderQueryException e) {
      Log.e(TAG, "Error querying decoders", e);
      throw createExportException(format, /* reason= */ "Querying codecs failed.");
    }

    return new DefaultCodec(
        context,
        format,
        mediaFormat,
        mediaCodecName,
        /* isDecoder= */ true,
        /* outputSurface= */ null);
  }

  @SuppressLint("InlinedApi")
  @Override
  public Codec createForVideoDecoding(
      Format format, Surface outputSurface, boolean requestSdrToneMapping) throws ExportException {
    checkNotNull(format.sampleMimeType);

    if (ColorInfo.isTransferHdr(format.colorInfo)) {
      if (requestSdrToneMapping
          && (SDK_INT < 31
              || deviceNeedsDisableToneMappingWorkaround(
                  checkNotNull(format.colorInfo).colorTransfer))) {
        throw createExportException(
            format, /* reason= */ "Tone-mapping HDR is not supported on this device.");
      }
      if (SDK_INT < 29) {
        // TODO(b/266837571, b/267171669): Remove API version restriction after fixing linked bugs.
        throw createExportException(
            format, /* reason= */ "Decoding HDR is not supported on this device.");
      }
    }

    MediaFormat mediaFormat = createMediaFormatFromFormat(format);
    if (decoderSupportsKeyAllowFrameDrop) {
      // This key ensures no frame dropping when the decoder's output surface is full. This allows
      // transformer to decode as many frames as possible in one render cycle.
      mediaFormat.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0);
    }
    if (SDK_INT >= 31 && requestSdrToneMapping) {
      mediaFormat.setInteger(
          MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
    }

    String mediaCodecName;
    try {
      @Nullable MediaCodecInfo decoderInfo = getDecoderInfo(format);
      if (decoderInfo == null) {
        throw createExportException(format, /* reason= */ "No decoders for format");
      }
      mediaCodecName = decoderInfo.name;
      String codecMimeType = decoderInfo.codecMimeType;
      // Does not alter format.sampleMimeType to keep the original MimeType.
      // The MIME type of the selected decoder may differ from Format.sampleMimeType, for example,
      // video/hevc is used instead of video/dolby-vision for some specific DolbyVision videos.
      mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    } catch (MediaCodecUtil.DecoderQueryException e) {
      Log.e(TAG, "Error querying decoders", e);
      throw createExportException(format, /* reason= */ "Querying codecs failed");
    }

    @Nullable
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
    if (codecProfileAndLevel != null) {
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_PROFILE, codecProfileAndLevel.first);
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_LEVEL, codecProfileAndLevel.second);
    }

    return new DefaultCodec(
        context, format, mediaFormat, mediaCodecName, /* isDecoder= */ true, outputSurface);
  }

  private static boolean deviceNeedsDisableToneMappingWorkaround(
      @C.ColorTransfer int colorTransfer) {
    if (Util.MANUFACTURER.equals("Google") && Build.ID.startsWith("TP1A")) {
      // Some Pixel 6 builds report support for tone mapping but the feature doesn't work
      // (see b/249297370#comment8).
      return true;
    }
    if (colorTransfer == C.COLOR_TRANSFER_HLG
        && (Util.MODEL.startsWith("SM-F936") || Util.MODEL.startsWith("SM-F916"))) {
      // Some Samsung Galaxy Z Fold devices report support for HLG tone mapping but the feature only
      // works on PQ (see b/282791751#comment7).
      return true;
    }
    return false;
  }

  @RequiresNonNull("#1.sampleMimeType")
  private static ExportException createExportException(Format format, String reason) {
    return ExportException.createForCodec(
        new IllegalArgumentException(reason),
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        MimeTypes.isVideo(format.sampleMimeType),
        /* isDecoder= */ true,
        format);
  }

  @VisibleForTesting
  @Nullable
  /* package */ static MediaCodecInfo getDecoderInfo(Format format)
      throws MediaCodecUtil.DecoderQueryException {
    checkNotNull(format.sampleMimeType);
    List<MediaCodecInfo> decoderInfos =
        MediaCodecUtil.getDecoderInfosSortedByFormatSupport(
            MediaCodecUtil.getDecoderInfosSoftMatch(
                MediaCodecSelector.DEFAULT,
                format,
                /* requiresSecureDecoder= */ false,
                /* requiresTunnelingDecoder= */ false),
            format);
    if (decoderInfos.isEmpty()) {
      return null;
    }
    return decoderInfos.get(0);
  }
}
