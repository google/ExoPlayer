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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.MediaFormatUtil.createMediaFormatFromFormat;
import static androidx.media3.common.util.Util.SDK_INT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Default implementation of {@link Codec.DecoderFactory} that uses {@link MediaCodec} for decoding.
 */
@UnstableApi
public final class DefaultDecoderFactory implements Codec.DecoderFactory {

  private static final String TAG = "DefaultDecoderFactory";

  private final Context context;

  private final boolean decoderSupportsKeyAllowFrameDrop;

  /** Creates a new factory. */
  public DefaultDecoderFactory(Context context) {
    this.context = context;

    decoderSupportsKeyAllowFrameDrop =
        SDK_INT >= 29
            && context.getApplicationContext().getApplicationInfo().targetSdkVersion >= 29;
  }

  @Override
  public DefaultCodec createForAudioDecoding(Format format) throws ExportException {
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
  public DefaultCodec createForVideoDecoding(
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
    if (deviceNeedsDisable8kWorkaround(format)) {
      throw createExportException(
          format, /* reason= */ "Decoding 8k is not supported on this device.");
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

  private static boolean deviceNeedsDisable8kWorkaround(Format format) {
    // Fixed on API 31+. See http://b/278234847#comment40 for more information.
    return SDK_INT < 31
        && format.width >= 7680
        && format.height >= 4320
        && format.sampleMimeType != null
        && format.sampleMimeType.equals(MimeTypes.VIDEO_H265)
        && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1"));
  }

  private static boolean deviceNeedsDisableToneMappingWorkaround(
      @C.ColorTransfer int colorTransfer) {
    if (Util.MANUFACTURER.equals("Google") && Build.ID.startsWith("TP1A")) {
      // Some Pixel 6 builds report support for tone mapping but the feature doesn't work
      // (see b/249297370#comment8).
      return true;
    }
    if (colorTransfer == C.COLOR_TRANSFER_HLG
        && (Util.MODEL.startsWith("SM-F936")
            || Util.MODEL.startsWith("SM-F916")
            || Util.MODEL.startsWith("SM-F721")
            || Util.MODEL.equals("SM-X900"))) {
      // Some Samsung Galaxy Z Fold devices report support for HLG tone mapping but the feature only
      // works on PQ (see b/282791751#comment7).
      return true;
    }
    if (SDK_INT < 34
        && colorTransfer == C.COLOR_TRANSFER_ST2084
        && Util.MODEL.startsWith("SM-F936")) {
      // The Samsung Fold 4 HDR10 codec plugin for tonemapping sets incorrect crop values, so block
      // using it (see b/290725189).
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
