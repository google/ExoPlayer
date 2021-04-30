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

import static com.google.android.exoplayer2.source.rtsp.MediaDescription.MEDIA_TYPE_AUDIO;
import static com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat.getMimeTypeFromRtpMediaType;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_CONTROL;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_RTPMAP;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.NalUnitUtil.NAL_START_CODE;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AacUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** Represents a media track in an RTSP playback. */
/* package */ final class RtspMediaTrack {
  // Format specific parameter names.
  private static final String PARAMETER_PROFILE_LEVEL_ID = "profile-level-id";
  private static final String PARAMETER_SPROP_PARAMS = "sprop-parameter-sets";
  /** Prefix for the RFC6381 codecs string for AAC formats. */
  private static final String AAC_CODECS_PREFIX = "mp4a.40.";
  /** Prefix for the RFC6381 codecs string for AVC formats. */
  private static final String H264_CODECS_PREFIX = "avc1.";

  /** The track's associated {@link RtpPayloadFormat}. */
  public final RtpPayloadFormat payloadFormat;
  /** The track's URI. */
  public final Uri uri;

  /**
   * Creates a new instance from a {@link MediaDescription}.
   *
   * @param mediaDescription The {@link MediaDescription} of this track.
   * @param sessionUri The {@link Uri} of the RTSP playback session.
   */
  public RtspMediaTrack(MediaDescription mediaDescription, Uri sessionUri) {
    checkArgument(mediaDescription.attributes.containsKey(ATTR_CONTROL));
    payloadFormat = generatePayloadFormat(mediaDescription);
    uri =
        sessionUri
            .buildUpon()
            .appendEncodedPath(castNonNull(mediaDescription.attributes.get(ATTR_CONTROL)))
            .build();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RtspMediaTrack that = (RtspMediaTrack) o;
    return payloadFormat.equals(that.payloadFormat) && uri.equals(that.uri);
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + payloadFormat.hashCode();
    result = 31 * result + uri.hashCode();
    return result;
  }

  @VisibleForTesting
  /* package */ static RtpPayloadFormat generatePayloadFormat(MediaDescription mediaDescription) {
    Format.Builder formatBuilder = new Format.Builder();

    if (mediaDescription.bitrate > 0) {
      formatBuilder.setAverageBitrate(mediaDescription.bitrate);
    }

    // rtpmap is mandatory in an RTSP session with dynamic payload types (RFC2326 Section C.1.3).
    checkArgument(mediaDescription.attributes.containsKey(ATTR_RTPMAP));
    String rtpmapAttribute = castNonNull(mediaDescription.attributes.get(ATTR_RTPMAP));

    // rtpmap string format: RFC2327 Page 22.
    String[] rtpmap = Util.split(rtpmapAttribute, " ");
    checkArgument(rtpmap.length == 2);
    int rtpPayloadType = mediaDescription.rtpMapAttribute.payloadType;

    String mimeType = getMimeTypeFromRtpMediaType(mediaDescription.rtpMapAttribute.mediaEncoding);
    formatBuilder.setSampleMimeType(mimeType);

    int clockRate = mediaDescription.rtpMapAttribute.clockRate;
    int channelCount = C.INDEX_UNSET;
    if (MEDIA_TYPE_AUDIO.equals(mediaDescription.mediaType)) {
      channelCount =
          inferChannelCount(mediaDescription.rtpMapAttribute.encodingParameters, mimeType);
      formatBuilder.setSampleRate(clockRate).setChannelCount(channelCount);
    }

    ImmutableMap<String, String> fmtpParameters = mediaDescription.getFmtpParametersAsMap();
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
        checkArgument(channelCount != C.INDEX_UNSET);
        checkArgument(!fmtpParameters.isEmpty());
        processAacFmtpAttribute(formatBuilder, fmtpParameters, channelCount, clockRate);
        break;
      case MimeTypes.VIDEO_H264:
        checkArgument(!fmtpParameters.isEmpty());
        processH264FmtpAttribute(formatBuilder, fmtpParameters);
        break;
      case MimeTypes.AUDIO_AC3:
        // AC3 does not require a FMTP attribute. Fall through.
      default:
        // Do nothing.
    }

    checkArgument(clockRate > 0);
    // Checks if payload type is "dynamic" as defined in RFC3551 Section 3.
    checkArgument(rtpPayloadType >= 96);
    return new RtpPayloadFormat(formatBuilder.build(), rtpPayloadType, clockRate, fmtpParameters);
  }

  private static int inferChannelCount(int encodingParameter, String mimeType) {
    if (encodingParameter != C.INDEX_UNSET) {
      // The encoding parameter specifies the number of channels in audio streams when
      // present. If omitted, the number of channels is one. This parameter has no significance in
      // video streams. (RFC2327 Page 22).
      return encodingParameter;
    }

    if (mimeType.equals(MimeTypes.AUDIO_AC3)) {
      // If RTPMAP attribute does not include channel count for AC3, default to 6.
      return 6;
    }

    return 1;
  }

  private static void processAacFmtpAttribute(
      Format.Builder formatBuilder,
      ImmutableMap<String, String> fmtpAttributes,
      int channelCount,
      int sampleRate) {
    checkArgument(fmtpAttributes.containsKey(PARAMETER_PROFILE_LEVEL_ID));
    String profileLevel = checkNotNull(fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID));
    formatBuilder.setCodecs(AAC_CODECS_PREFIX + profileLevel);
    formatBuilder.setInitializationData(
        ImmutableList.of(
            // Clock rate equals to sample rate in RTP.
            AacUtil.buildAacLcAudioSpecificConfig(sampleRate, channelCount)));
  }

  private static void processH264FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    checkArgument(fmtpAttributes.containsKey(PARAMETER_PROFILE_LEVEL_ID));
    String profileLevel = checkNotNull(fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID));
    formatBuilder.setCodecs(H264_CODECS_PREFIX + profileLevel);

    checkArgument(fmtpAttributes.containsKey(PARAMETER_SPROP_PARAMS));
    String spropParameterSets = checkNotNull(fmtpAttributes.get(PARAMETER_SPROP_PARAMS));
    String[] parameterSets = Util.split(spropParameterSets, ",");
    checkArgument(parameterSets.length == 2);
    ImmutableList<byte[]> initializationData =
        ImmutableList.of(
            getH264InitializationDataFromParameterSet(parameterSets[0]),
            getH264InitializationDataFromParameterSet(parameterSets[1]));
    formatBuilder.setInitializationData(initializationData);

    // Process SPS (Sequence Parameter Set).
    byte[] spsNalDataWithStartCode = initializationData.get(0);
    NalUnitUtil.SpsData spsData =
        NalUnitUtil.parseSpsNalUnit(
            spsNalDataWithStartCode, NAL_START_CODE.length, spsNalDataWithStartCode.length);
    formatBuilder.setPixelWidthHeightRatio(spsData.pixelWidthAspectRatio);
    formatBuilder.setHeight(spsData.height);
    formatBuilder.setWidth(spsData.width);
  }

  private static byte[] getH264InitializationDataFromParameterSet(String parameterSet) {
    byte[] decodedParameterNalData = Base64.decode(parameterSet, Base64.DEFAULT);
    byte[] decodedParameterNalUnit =
        new byte[decodedParameterNalData.length + NAL_START_CODE.length];
    System.arraycopy(
        NAL_START_CODE,
        /* srcPos= */ 0,
        decodedParameterNalUnit,
        /* destPos= */ 0,
        NAL_START_CODE.length);
    System.arraycopy(
        decodedParameterNalData,
        /* srcPos= */ 0,
        decodedParameterNalUnit,
        /* destPos= */ NAL_START_CODE.length,
        decodedParameterNalData.length);
    return decodedParameterNalUnit;
  }
}
