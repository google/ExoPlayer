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

import com.google.android.exoplayer2.ParserException;
import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A value wrapper for a dumped RTP packet stream. */
/* package */ class RtpPacketStreamDump {
  /** The name of the RTP track. */
  public final String trackName;
  /** The sequence number of the first RTP packet in the dump file. */
  public final int firstSequenceNumber;
  /** The timestamp of the first RTP packet in the dump file. */
  public final long firstTimestamp;
  /** The interval between transmitting two consecutive RTP packets, in milliseconds. */
  public final long transmissionIntervalMs;
  /** The description of the dumped media in SDP(RFC2327) format. */
  public final String mediaDescription;
  /** A list of hex strings. Each hex string represents a binary RTP packet. */
  public final ImmutableList<String> packets;

  /**
   * Parses a JSON string into an {@code RtpPacketStreamDump}.
   *
   * <p>The input JSON must include the following key-value pairs:
   *
   * <ul>
   *   <li>Key: "trackName", Value type: String. The name of the RTP track.
   *   <li>Key: "firstSequenceNumber", Value type: int. The sequence number of the first RTP packet
   *       in the dump file.
   *   <li>Key: "firstTimestamp", Value type: long. The timestamp of the first RTP packet in the
   *       dump file.
   *   <li>Key: "transmissionIntervalMs", Value type: long. The interval between transmitting two
   *       consecutive RTP packets, in milliseconds.
   *   <li>Key: "mediaDescription", Value type: String. The description of the dumped media in
   *       SDP(RFC2327) format.
   *   <li>Key: "packets", Value type: Array of hex strings. Each element is a hex string
   *       representing an RTP packet's binary data.
   * </ul>
   *
   * @param jsonString The JSON string that contains the dumped RTP packets and metadata.
   * @return The parsed {@code RtpDumpFile}.
   * @throws ParserException If the argument does not contain all required key-value pairs, or there
   *     are incorrect values.
   */
  public static RtpPacketStreamDump parse(String jsonString) throws ParserException {
    try {
      JSONObject jsonObject = new JSONObject(jsonString);
      String trackName = jsonObject.getString("trackName");
      int firstSequenceNumber = jsonObject.getInt("firstSequenceNumber");
      long firstTimestamp = jsonObject.getLong("firstTimestamp");
      long transmissionIntervalMs = jsonObject.getLong("transmitIntervalMs");
      String mediaDescription = jsonObject.getString("mediaDescription");

      ImmutableList.Builder<String> packetsBuilder = new ImmutableList.Builder<>();
      JSONArray jsonPackets = jsonObject.getJSONArray("packets");
      for (int i = 0; i < jsonPackets.length(); i++) {
        packetsBuilder.add(jsonPackets.getString(i));
      }

      return new RtpPacketStreamDump(
          trackName,
          firstSequenceNumber,
          firstTimestamp,
          transmissionIntervalMs,
          mediaDescription,
          packetsBuilder.build());
    } catch (JSONException e) {
      throw ParserException.createForMalformedManifest(/* message= */ null, e);
    }
  }

  private RtpPacketStreamDump(
      String trackName,
      int firstSequenceNumber,
      long firstTimestamp,
      long transmissionIntervalMs,
      String mediaDescription,
      ImmutableList<String> packets) {
    this.trackName = trackName;
    this.firstSequenceNumber = firstSequenceNumber;
    this.firstTimestamp = firstTimestamp;
    this.transmissionIntervalMs = transmissionIntervalMs;
    this.mediaDescription = mediaDescription;
    this.packets = ImmutableList.copyOf(packets);
  }
}
