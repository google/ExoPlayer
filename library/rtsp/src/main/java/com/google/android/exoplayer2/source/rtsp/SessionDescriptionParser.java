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

import static com.google.android.exoplayer2.source.rtsp.SessionDescription.SUPPORTED_SDP_VERSION;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a String based SDP message into {@link SessionDescription}. */
/* package */ final class SessionDescriptionParser {
  // SDP line always starts with an one letter tag, followed by an equal sign. The information
  // under the given tag follows an optional space.
  private static final Pattern SDP_LINE_PATTERN = Pattern.compile("([a-z])=\\s?(.+)");
  // Matches an attribute line (with a= sdp tag removed. Example: range:npt=0-50.0).
  // Attribute can also be a flag, i.e. without a value, like recvonly. Reference RFC4566 Section 9
  // Page 43, under "token-char".
  private static final Pattern ATTRIBUTE_PATTERN =
      Pattern.compile(
          "([\\x21\\x23-\\x27\\x2a\\x2b\\x2d\\x2e\\x30-\\x39\\x41-\\x5a\\x5e-\\x7e]+)(?::(.*))?");
  // SDP media description line: <mediaType> <port> <transmissionProtocol> <rtpPayloadType>
  // For instance: audio 0 RTP/AVP 97
  private static final Pattern MEDIA_DESCRIPTION_PATTERN =
      Pattern.compile("(\\S+)\\s(\\S+)\\s(\\S+)\\s(\\S+)");

  private static final String VERSION_TYPE = "v";
  private static final String ORIGIN_TYPE = "o";
  private static final String SESSION_TYPE = "s";
  private static final String INFORMATION_TYPE = "i";
  private static final String URI_TYPE = "u";
  private static final String EMAIL_TYPE = "e";
  private static final String PHONE_NUMBER_TYPE = "p";
  private static final String CONNECTION_TYPE = "c";
  private static final String BANDWIDTH_TYPE = "b";
  private static final String TIMING_TYPE = "t";
  private static final String KEY_TYPE = "k";
  private static final String ATTRIBUTE_TYPE = "a";
  private static final String MEDIA_TYPE = "m";
  private static final String REPEAT_TYPE = "r";
  private static final String ZONE_TYPE = "z";


  /**
   * Parses a String based SDP message into {@link SessionDescription}.
   *
   * @throws ParserException On SDP message line that cannot be parsed, or when one or more of the
   *     mandatory SDP fields {@link SessionDescription#timing}, {@link SessionDescription#origin}
   *     and {@link SessionDescription#sessionName} are not set.
   */
  public static SessionDescription customCreateDescription() throws ParserException {
    //TODO: hardcode the required params to run the steam here
      SessionDescription.Builder sessionDescriptionBuilder = new SessionDescription.Builder();
      @Nullable MediaDescription.Builder mediaDescriptionBuilder = null;
    //NOTE: The ordering of the params in the parse() method bellow is needed. We will just hardcode directly what is found.

    try {
      //TODO: LIVE CLOCK STREAM
      /*String ORIGIN_TYPE = "- 8265319122185249563 1 IN IP4 192.168.1.51"; // TODO: what is the first param found here?
      String SESSION_TYPE = "Session streamed with GStreamer";
      String BANDWIDTH_TYPE = "AS:50000";
      String TIMING_TYPE = "0 0";
      String MEDIA_TYPE = "video 0 RTP/AVP 96";

        //Attributes
      String tool = "GStreamer";

      String type = "broadcast";
      String range = "npt=now-";
      String control = "rtsp://ipvmdemo.dyndns.org:5541/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast";
      String rtpmap = "96 H264/90000";
      String fmtp = "96 packetization-mode=1;profile-level-id=4d001f;sprop-parameter-sets=Z00AH5pkAoAt//+H/4gANwEBAUAAAPpAAB1MOhgBOcABOcLvLjQwAnOAAnOF3lw31A==,aO48gA==";

          //ts-refclk
      String ts_refclk = "local";
      String mediaclk = "sender";
      String framerate = "15.000000";
      String transform = "1.000000,0.000000,0.000000;0.000000,1.000000,0.000000;0.000000,0.000000,1.000000 ";
      String recvonly = "recvonly";
      */
      // TODO: Bunny video
      String ORIGIN_TYPE = "- 1712741543 1712741543 IN IP4 34.227.104.115"; // TODO: what is the first param found here?
      String SESSION_TYPE = "BigBuckBunny_115k.mov";
      String BANDWIDTH_TYPE = null; // this is not used by the bunny video for some reason
      String TIMING_TYPE = "0 0";

      //SessionDescription Attributes
      String sdplang = "en";
      String range = "npt=0- 634.625";
      String control = "*";

      //Media
      String MEDIA_TYPE = "video 0 RTP/AVP 97";

      try {
        //ORIGIN_TYPE
        sessionDescriptionBuilder.setOrigin(ORIGIN_TYPE);

        //SESSION_TYPE
        sessionDescriptionBuilder.setSessionName(SESSION_TYPE);

        //BANDWIDTH_TYPE
        if (BANDWIDTH_TYPE != null) {
          String[] bandwidthComponents = Util.split(BANDWIDTH_TYPE, ":\\s?");
          checkArgument(bandwidthComponents.length == 2);
          int bitrateKbps = Integer.parseInt(bandwidthComponents[1]);
          // Converting kilobits per second to bits per second.
          if (mediaDescriptionBuilder == null) {
            sessionDescriptionBuilder.setBitrate(bitrateKbps * 1000);
          } else {
            mediaDescriptionBuilder.setBitrate(bitrateKbps * 1000);
          }
        }

        //TIMING_TYPE
        sessionDescriptionBuilder.setTiming(TIMING_TYPE);

        //ATTRIBUTE_TYPE
        //SessionDescription Attributes
        //TODO: IGNORE BECAUSE AUDIO?
        sessionDescriptionBuilder.addAttribute("sdplang", sdplang);
        sessionDescriptionBuilder.addAttribute("range", range);
        sessionDescriptionBuilder.addAttribute("control", control);

        //MEDIA_TYPE
        if (mediaDescriptionBuilder != null) {
          addMediaDescriptionToSession(sessionDescriptionBuilder, mediaDescriptionBuilder);
        }
        mediaDescriptionBuilder = parseMediaDescriptionLine(MEDIA_TYPE);

        //ATTRIBUTE_TYPE
        //MediaDescription Attributes
        //  TODO: 3 bellow  not accepted for some reason
        String rtpmap = "96 mpeg4-generic/12000/2";
        String fmtp = "96 profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=149056e500";
        control = "trackID=1";
        mediaDescriptionBuilder.addAttribute("rtpmap", rtpmap);
        mediaDescriptionBuilder.addAttribute("fmtp", fmtp);
        mediaDescriptionBuilder.addAttribute("control", control);

        //accepted
        String rtpmap_2 = "97 H264/90000"; // why is this happening twice?
        String fmtp_2 = "97 packetization-mode=1;profile-level-id=64000C;sprop-parameter-sets=Z2QADKzZQ8Vv/ACAAGxAAAADAEAAAAwDxQplgA==,aOvssiw=";
        String cliprect = "0,0,160,240";
        String framesize = "97 240-160";
        String framerate = "24.0";
        String control_2 = "trackID=2";
        mediaDescriptionBuilder.addAttribute("rtpmap", rtpmap_2);
        mediaDescriptionBuilder.addAttribute("fmtp", fmtp_2);
        mediaDescriptionBuilder.addAttribute("cliprect", cliprect);
        mediaDescriptionBuilder.addAttribute("framesize", framesize);
        mediaDescriptionBuilder.addAttribute("framerate", framerate);
        mediaDescriptionBuilder.addAttribute("control", control_2);


      } catch (Exception e) {
        Log.e("PARSER: Adding MediaType ", e.toString());
      }

      if (mediaDescriptionBuilder != null) {
        addMediaDescriptionToSession(sessionDescriptionBuilder, mediaDescriptionBuilder);
      }

      return sessionDescriptionBuilder.build();

    }
    catch (IllegalArgumentException | IllegalStateException e) {
      throw ParserException.createForMalformedManifest(/* message= */ null, e);
    }
      /*
      //TODO: ATTRIBUTE_TYPE
      String tool = "GStreamer";

      String type = "broadcast";
      String range = "npt=now-";
      String control = "rtsp://ipvmdemo.dyndns.org:5541/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast";
      String rtpmap = "96 H264/90000";
      String fmtp = "96 packetization-mode=1;profile-level-id=4d001f;sprop-parameter-sets=Z00AH5pkAoAt//+H/4gANwEBAUAAAPpAAB1MOhgBOcABOcLvLjQwAnOAAnOF3lw31A==,aO48gA==";

      //ts-refclk
      String ts_refclk = "local";
      String mediaclk = "sender";
      String framerate = "15.000000";
      String transform = "1.000000,0.000000,0.000000;0.000000,1.000000,0.000000;0.000000,0.000000,1.000000 ";
      String recvonly = "recvonly";
      String attributeName = checkNotNull(matcher.group(1));
      // The second catching group is optional and thus could be null.
      String attributeValue = nullToEmpty(matcher.group(2));

      if (mediaDescriptionBuilder == null) {
        sessionDescriptionBuilder.addAttribute(attributeName, attributeValue);
      } else {
        mediaDescriptionBuilder.addAttribute(attributeName, attributeValue);
      }
      */

  }
  public static SessionDescription parse(String sdpString) throws ParserException {

    SessionDescription.Builder sessionDescriptionBuilder = new SessionDescription.Builder();
    @Nullable MediaDescription.Builder mediaDescriptionBuilder = null;


    String TAG = "PARSER: ";
    // Lines are separated by an CRLF.
    for (String line : RtspMessageUtil.splitRtspMessageBody(sdpString)) {
      if ("".equals(line)) {
        continue;
      }

      Matcher matcher = SDP_LINE_PATTERN.matcher(line);
      if (!matcher.matches()) {
        throw ParserException.createForMalformedManifest(
            "Malformed SDP line: " + line, /* cause= */ null);
      }

      String sdpType = checkNotNull(matcher.group(1));
      String sdpValue = checkNotNull(matcher.group(2));

      switch (sdpType) {
        case ORIGIN_TYPE:
          Log.i(TAG, "ORIGIN_TYPE: " + sdpType + " and  " + sdpValue );
          sessionDescriptionBuilder.setOrigin(sdpValue);
          break;

        case SESSION_TYPE:
          Log.i(TAG, "SESSION_TYPE: " + sdpType + " " + sdpValue );
          sessionDescriptionBuilder.setSessionName(sdpValue);
          break;

        case BANDWIDTH_TYPE:
          Log.i(TAG, "BANDWIDTH_TYPE: " + sdpType + " " + sdpValue );
          String[] bandwidthComponents = Util.split(sdpValue, ":\\s?");
          checkArgument(bandwidthComponents.length == 2);
          int bitrateKbps = Integer.parseInt(bandwidthComponents[1]);

          // Converting kilobits per second to bits per second.
          if (mediaDescriptionBuilder == null) {
            sessionDescriptionBuilder.setBitrate(bitrateKbps * 1000);
          } else {
            mediaDescriptionBuilder.setBitrate(bitrateKbps * 1000);
          }
          break;

        case TIMING_TYPE:
          Log.i(TAG, "TIMING_TYPE: " + sdpType + " " + sdpValue );
          sessionDescriptionBuilder.setTiming(sdpValue);
          break;

        case KEY_TYPE:
          Log.i(TAG, "KEY_TYPE: " + sdpType + " " + sdpValue );
          if (mediaDescriptionBuilder == null) {
            sessionDescriptionBuilder.setKey(sdpValue);
          } else {
            mediaDescriptionBuilder.setKey(sdpValue);
          }
          break;

        case ATTRIBUTE_TYPE:

          matcher = ATTRIBUTE_PATTERN.matcher(sdpValue);
          if (!matcher.matches()) {
            throw ParserException.createForMalformedManifest(
                "Malformed Attribute line: " + line, /* cause= */ null);
          }

          String attributeName = checkNotNull(matcher.group(1));
          // The second catching group is optional and thus could be null.
          String attributeValue = nullToEmpty(matcher.group(2));

          if(!attributeValue.equals("en") && !attributeValue.equals("npt=0- 634.625")  && !attributeValue.equals("*") ){ // Trying to skip audio for media type and attribute type.
            Log.i(TAG, "ATTRIBUTE_TYPE: " + sdpType + " " + sdpValue );
            if (mediaDescriptionBuilder == null) {
              sessionDescriptionBuilder.addAttribute(attributeName, attributeValue);
            } else {
              mediaDescriptionBuilder.addAttribute(attributeName, attributeValue);
            }
          }

          break;

        case MEDIA_TYPE:
          if(!sdpValue.equals("audio 0 RTP/AVP 96")){
            Log.i(TAG, "MEDIA_TYPE: " + sdpType + " " + sdpValue );
            if (mediaDescriptionBuilder != null) {
              addMediaDescriptionToSession(sessionDescriptionBuilder, mediaDescriptionBuilder);
            }
            mediaDescriptionBuilder = parseMediaDescriptionLine(sdpValue);
          }

         break;
        default:
          // Not handled.
      }
    }

    if (mediaDescriptionBuilder != null) {
      addMediaDescriptionToSession(sessionDescriptionBuilder, mediaDescriptionBuilder);
    }

    try {
      return sessionDescriptionBuilder.build();
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw ParserException.createForMalformedManifest(/* message= */ null, e);
    }
  }

  private static void addMediaDescriptionToSession(
      SessionDescription.Builder sessionDescriptionBuilder,
      MediaDescription.Builder mediaDescriptionBuilder)
      throws ParserException {
    try {
      sessionDescriptionBuilder.addMediaDescription(mediaDescriptionBuilder.build());
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw ParserException.createForMalformedManifest(/* message= */ null, e);
    }
  }

  private static MediaDescription.Builder parseMediaDescriptionLine(String line)
      throws ParserException {
    Matcher matcher = MEDIA_DESCRIPTION_PATTERN.matcher(line);
    if (!matcher.matches()) {
      throw ParserException.createForMalformedManifest(
          "Malformed SDP media description line: " + line, /* cause= */ null);
    }
    String mediaType = checkNotNull(matcher.group(1));
    String portString = checkNotNull(matcher.group(2));
    String transportProtocol = checkNotNull(matcher.group(3));
    String payloadTypeString = checkNotNull(matcher.group(4));

    try {
      return new MediaDescription.Builder(
          mediaType,
          Integer.parseInt(portString),
          transportProtocol,
          Integer.parseInt(payloadTypeString));
    } catch (NumberFormatException e) {
      throw ParserException.createForMalformedManifest(
          "Malformed SDP media description line: " + line, e);
    }
  }

  /** Prevents initialization. */
  private SessionDescriptionParser() {}
}
