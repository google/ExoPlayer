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
package androidx.media3.exoplayer.rtsp;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.exoplayer.rtsp.MediaDescription.MEDIA_TYPE_AUDIO;
import static androidx.media3.exoplayer.rtsp.RtpPayloadFormat.getMimeTypeFromRtpMediaType;
import static androidx.media3.exoplayer.rtsp.SessionDescription.ATTR_CONTROL;
import static androidx.media3.extractor.NalUnitUtil.NAL_START_CODE;

import android.net.Uri;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.AacUtil;
import androidx.media3.extractor.NalUnitUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** Represents a media track in an RTSP playback. */
@UnstableApi
/* package */ final class RtspMediaTrack {
  // Format specific parameter names.
  private static final String PARAMETER_PROFILE_LEVEL_ID = "profile-level-id";
  private static final String PARAMETER_SPROP_PARAMS = "sprop-parameter-sets";
  private static final String PARAMETER_H265_SPROP_SPS = "sprop-sps";
  private static final String PARAMETER_H265_SPROP_PPS = "sprop-pps";
  private static final String PARAMETER_H265_SPROP_VPS = "sprop-vps";
  private static final String PARAMETER_H265_SPROP_MAX_DON_DIFF = "sprop-max-don-diff";

  /** Prefix for the RFC6381 codecs string for AAC formats. */
  private static final String AAC_CODECS_PREFIX = "mp4a.40.";
  /** Prefix for the RFC6381 codecs string for AVC formats. */
  private static final String H264_CODECS_PREFIX = "avc1.";

  private static final String GENERIC_CONTROL_ATTR = "*";

  /** Default width and height for H263. */
  private static final int DEFAULT_H263_WIDTH = 352;
  private static final int DEFAULT_H263_HEIGHT = 288;

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
    uri = extractTrackUri(sessionUri, castNonNull(mediaDescription.attributes.get(ATTR_CONTROL)));
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
      case MimeTypes.VIDEO_H263:
        // H263 does not require a FMTP attribute. So Setting default width and height.
        formatBuilder.setWidth(DEFAULT_H263_WIDTH).setHeight(DEFAULT_H263_HEIGHT);
        break;
      case MimeTypes.VIDEO_H264:
        checkArgument(!fmtpParameters.isEmpty());
        processH264FmtpAttribute(formatBuilder, fmtpParameters);
        break;
      case MimeTypes.VIDEO_H265:
        checkArgument(!fmtpParameters.isEmpty());
        processH265FmtpAttribute(formatBuilder, fmtpParameters);
        break;
      case MimeTypes.AUDIO_AC3:
        // AC3 does not require a FMTP attribute. Fall through.
      default:
        // Do nothing.
    }

    checkArgument(clockRate > 0);
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

  /** Returns H264/H265 initialization data from the RTP parameter set. */
  private static byte[] getInitializationDataFromParameterSet(String parameterSet) {
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

  private static void processH264FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    checkArgument(fmtpAttributes.containsKey(PARAMETER_SPROP_PARAMS));
    String spropParameterSets = checkNotNull(fmtpAttributes.get(PARAMETER_SPROP_PARAMS));
    String[] parameterSets = Util.split(spropParameterSets, ",");
    checkArgument(parameterSets.length == 2);
    ImmutableList<byte[]> initializationData =
        ImmutableList.of(
            getInitializationDataFromParameterSet(parameterSets[0]),
            getInitializationDataFromParameterSet(parameterSets[1]));
    formatBuilder.setInitializationData(initializationData);

    // Process SPS (Sequence Parameter Set).
    byte[] spsNalDataWithStartCode = initializationData.get(0);
    NalUnitUtil.SpsData spsData =
        NalUnitUtil.parseSpsNalUnit(
            spsNalDataWithStartCode, NAL_START_CODE.length, spsNalDataWithStartCode.length);
    formatBuilder.setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio);
    formatBuilder.setHeight(spsData.height);
    formatBuilder.setWidth(spsData.width);

    @Nullable String profileLevel = fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID);
    if (profileLevel != null) {
      formatBuilder.setCodecs(H264_CODECS_PREFIX + profileLevel);
    } else {
      formatBuilder.setCodecs(
          CodecSpecificDataUtil.buildAvcCodecString(
              spsData.profileIdc, spsData.constraintsFlagsAndReservedZero2Bits, spsData.levelIdc));
    }
  }

  private static void processH265FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    if (fmtpAttributes.containsKey(PARAMETER_H265_SPROP_MAX_DON_DIFF)) {
      int maxDonDiff =
          Integer.parseInt(checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_MAX_DON_DIFF)));
      checkArgument(
          maxDonDiff == 0, "non-zero sprop-max-don-diff " + maxDonDiff + " is not supported");
    }

    checkArgument(fmtpAttributes.containsKey(PARAMETER_H265_SPROP_VPS));
    String spropVPS = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_VPS));
    checkArgument(fmtpAttributes.containsKey(PARAMETER_H265_SPROP_SPS));
    String spropSPS = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_SPS));
    checkArgument(fmtpAttributes.containsKey(PARAMETER_H265_SPROP_PPS));
    String spropPPS = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_PPS));
    ImmutableList<byte[]> initializationData =
        ImmutableList.of(
            getInitializationDataFromParameterSet(spropVPS),
            getInitializationDataFromParameterSet(spropSPS),
            getInitializationDataFromParameterSet(spropPPS));
    formatBuilder.setInitializationData(initializationData);

    // Process the SPS (Sequence Parameter Set).
    byte[] spsNalDataWithStartCode = initializationData.get(1);
    NalUnitUtil.H265SpsData spsData =
        NalUnitUtil.parseH265SpsNalUnit(
            spsNalDataWithStartCode, NAL_START_CODE.length, spsNalDataWithStartCode.length);
    formatBuilder.setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio);
    formatBuilder.setHeight(spsData.height).setWidth(spsData.width);

    formatBuilder.setCodecs(
        CodecSpecificDataUtil.buildHevcCodecString(
            spsData.generalProfileSpace,
            spsData.generalTierFlag,
            spsData.generalProfileIdc,
            spsData.generalProfileCompatibilityFlags,
            spsData.constraintBytes,
            spsData.generalLevelIdc));
  }

  /**
   * Extracts the track URI.
   *
   * <p>The processing logic is specified in RFC2326 Section C.1.1.
   *
   * @param sessionUri The session URI.
   * @param controlAttributeString The control attribute from the track's {@link MediaDescription}.
   * @return The extracted track URI.
   */
  private static Uri extractTrackUri(Uri sessionUri, String controlAttributeString) {
    Uri controlAttributeUri = Uri.parse(controlAttributeString);
    if (controlAttributeUri.isAbsolute()) {
      return controlAttributeUri;
    } else if (controlAttributeString.equals(GENERIC_CONTROL_ATTR)) {
      return sessionUri;
    } else {
      return sessionUri.buildUpon().appendEncodedPath(controlAttributeString).build();
    }
  }
}
