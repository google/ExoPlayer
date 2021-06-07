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

import static com.google.android.exoplayer2.source.rtsp.RtspMessageUtil.parseInt;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_FMTP;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_RTPMAP;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents one media description section in a SDP message. */
/* package */ final class MediaDescription {

  /** Represents the mandatory RTPMAP attribute in MediaDescription. Reference RFC 2327 Page 22. */
  public static final class RtpMapAttribute {

    /** Parses the RTPMAP attribute value (with the part "a=rtpmap:" removed). */
    public static RtpMapAttribute parse(String rtpmapString) throws ParserException {
      String[] rtpmapInfo = Util.split(rtpmapString, " ");
      checkArgument(rtpmapInfo.length == 2);
      int payloadType = parseInt(rtpmapInfo[0]);

      String[] mediaInfo = Util.split(rtpmapInfo[1], "/");
      checkArgument(mediaInfo.length >= 2);
      int clockRate = parseInt(mediaInfo[1]);
      int encodingParameters = C.INDEX_UNSET;
      if (mediaInfo.length == 3) {
        encodingParameters = parseInt(mediaInfo[2]);
      }
      return new RtpMapAttribute(
          payloadType, /* mediaEncoding= */ mediaInfo[0], clockRate, encodingParameters);
    }

    /** The assigned RTP payload type. */
    public final int payloadType;
    /** The encoding method used in the RTP stream. */
    public final String mediaEncoding;
    /** The clock rate used in the RTP stream. */
    public final int clockRate;
    /** The optional encoding parameter. */
    public final int encodingParameters;

    private RtpMapAttribute(
        int payloadType, String mediaEncoding, int clockRate, int encodingParameters) {
      this.payloadType = payloadType;
      this.mediaEncoding = mediaEncoding;
      this.clockRate = clockRate;
      this.encodingParameters = encodingParameters;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RtpMapAttribute that = (RtpMapAttribute) o;
      return payloadType == that.payloadType
          && mediaEncoding.equals(that.mediaEncoding)
          && clockRate == that.clockRate
          && encodingParameters == that.encodingParameters;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + payloadType;
      result = 31 * result + mediaEncoding.hashCode();
      result = 31 * result + clockRate;
      result = 31 * result + encodingParameters;
      return result;
    }
  }

  /** Builder class for {@link MediaDescription}. */
  public static final class Builder {
    private final String mediaType;
    private final int port;
    private final String transportProtocol;
    private final int payloadType;
    private final ImmutableMap.Builder<String, String> attributesBuilder;

    private int bitrate;
    @Nullable private String mediaTitle;
    @Nullable private String connection;
    @Nullable private String key;

    /**
     * Creates a new instance.
     *
     * @param mediaType The media type.
     * @param port The associated port number.
     * @param transportProtocol The protocol used for data transport.
     * @param payloadType The RTP payload type used for data transport.
     */
    public Builder(String mediaType, int port, String transportProtocol, int payloadType) {
      this.mediaType = mediaType;
      this.port = port;
      this.transportProtocol = transportProtocol;
      this.payloadType = payloadType;
      attributesBuilder = new ImmutableMap.Builder<>();
      bitrate = Format.NO_VALUE;
    }

    /**
     * Sets {@link MediaDescription#mediaTitle}. The default is {@code null}.
     *
     * @param mediaTitle The assigned media title.
     * @return This builder.
     */
    public Builder setMediaTitle(String mediaTitle) {
      this.mediaTitle = mediaTitle;
      return this;
    }

    /**
     * Sets {@link MediaDescription#connection}. The default is {@code null}.
     *
     * @param connection The connection parameter.
     * @return This builder.
     */
    public Builder setConnection(String connection) {
      this.connection = connection;
      return this;
    }

    /**
     * Sets {@link MediaDescription#bitrate}. The default is {@link Format#NO_VALUE}.
     *
     * @param bitrate The estimated bitrate measured in bits per second.
     * @return This builder.
     */
    public Builder setBitrate(int bitrate) {
      this.bitrate = bitrate;
      return this;
    }

    /**
     * Sets {@link MediaDescription#key}. The default is {@code null}.
     *
     * @param key The encryption parameter.
     * @return This builder.
     */
    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    /**
     * Adds an attribute entry to {@link MediaDescription#attributes}.
     *
     * <p>Previously added attribute under the same name will be overwritten.
     *
     * @param attributeName The name of the attribute.
     * @param attributeValue The value of the attribute, or "" if the attribute bears no value.
     * @return This builder.
     */
    public Builder addAttribute(String attributeName, String attributeValue) {
      attributesBuilder.put(attributeName, attributeValue);
      return this;
    }

    /**
     * Builds a new {@link MediaDescription} instance.
     *
     * @throws IllegalStateException When the rtpmap attribute (RFC 2327 Page 22) is not set, or
     *     cannot be parsed.
     */
    public MediaDescription build() {
      ImmutableMap<String, String> attributes = attributesBuilder.build();
      try {
        // rtpmap attribute is mandatory in RTSP (RFC2326 Section C.1.3).
        checkState(attributes.containsKey(ATTR_RTPMAP));
        RtpMapAttribute rtpMapAttribute =
            RtpMapAttribute.parse(castNonNull(attributes.get(ATTR_RTPMAP)));
        return new MediaDescription(this, attributes, rtpMapAttribute);
      } catch (ParserException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /** The media types allowed in a SDP media description. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({MEDIA_TYPE_VIDEO, MEDIA_TYPE_AUDIO})
  @Documented
  public @interface MediaType {}
  /** Audio media type. */
  public static final String MEDIA_TYPE_AUDIO = "audio";
  /** Video media type. */
  public static final String MEDIA_TYPE_VIDEO = "video";
  /** Default RTP/AVP profile. */
  public static final String RTP_AVP_PROFILE = "RTP/AVP";

  /** The {@link MediaType}. */
  @MediaType public final String mediaType;
  /** The associated port number. */
  public final int port;
  /** The protocol used for data transport. */
  public final String transportProtocol;
  /** The assigned RTP payload type. */
  public final int payloadType;
  /** The estimated connection bitrate in bits per second. */
  public final int bitrate;
  /** The assigned media title. */
  @Nullable public final String mediaTitle;
  // TODO(internal b/172331505) Parse the String representations into objects.
  /** The connection parameters. */
  @Nullable public final String connection;
  /** The encryption parameter. */
  @Nullable public final String key;
  /** The media-specific attributes. */
  public final ImmutableMap<String, String> attributes;
  /** The mandatory rtpmap attribute in the media description (RFC2327 Page 22). */
  public final RtpMapAttribute rtpMapAttribute;

  /** Creates a new instance. */
  private MediaDescription(
      Builder builder, ImmutableMap<String, String> attributes, RtpMapAttribute rtpMapAttribute) {
    this.mediaType = builder.mediaType;
    this.port = builder.port;
    this.transportProtocol = builder.transportProtocol;
    this.payloadType = builder.payloadType;
    this.mediaTitle = builder.mediaTitle;
    this.connection = builder.connection;
    this.bitrate = builder.bitrate;
    this.key = builder.key;
    this.attributes = attributes;
    this.rtpMapAttribute = rtpMapAttribute;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MediaDescription other = (MediaDescription) o;
    return mediaType.equals(other.mediaType)
        && port == other.port
        && transportProtocol.equals(other.transportProtocol)
        && payloadType == other.payloadType
        && bitrate == other.bitrate
        && attributes.equals(other.attributes)
        && rtpMapAttribute.equals(other.rtpMapAttribute)
        && Util.areEqual(mediaTitle, other.mediaTitle)
        && Util.areEqual(connection, other.connection)
        && Util.areEqual(key, other.key);
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + mediaType.hashCode();
    result = 31 * result + port;
    result = 31 * result + transportProtocol.hashCode();
    result = 31 * result + payloadType;
    result = 31 * result + bitrate;
    result = 31 * result + attributes.hashCode();
    result = 31 * result + rtpMapAttribute.hashCode();
    result = 31 * result + (mediaTitle == null ? 0 : mediaTitle.hashCode());
    result = 31 * result + (connection == null ? 0 : connection.hashCode());
    result = 31 * result + (key == null ? 0 : key.hashCode());
    return result;
  }

  /**
   * Returns the FMTP attribute as a map of FMTP parameter names to values; or an empty map if the
   * {@link MediaDescription} does not contain any FMTP attribute.
   *
   * <p>FMTP format reference: RFC2327 Page 27. The spaces around the FMTP attribute delimiters are
   * removed. For example,
   */
  public ImmutableMap<String, String> getFmtpParametersAsMap() {
    @Nullable String fmtpAttributeValue = attributes.get(ATTR_FMTP);
    if (fmtpAttributeValue == null) {
      return ImmutableMap.of();
    }

    // fmtp format: RFC2327 Page 27.
    String[] fmtpComponents = Util.splitAtFirst(fmtpAttributeValue, " ");
    checkArgument(fmtpComponents.length == 2, fmtpAttributeValue);

    // Format of the parameter: RFC3640 Section 4.4.1:
    //   <parameter name>=<value>[; <parameter name>=<value>].
    String[] parameters = Util.split(fmtpComponents[1], ";\\s?");
    ImmutableMap.Builder<String, String> formatParametersBuilder = new ImmutableMap.Builder<>();
    for (String parameter : parameters) {
      // The parameter values can bear equal signs, so splitAtFirst must be used.
      String[] parameterPair = Util.splitAtFirst(parameter, "=");
      formatParametersBuilder.put(parameterPair[0], parameterPair[1]);
    }
    return formatParametersBuilder.build();
  }
}
