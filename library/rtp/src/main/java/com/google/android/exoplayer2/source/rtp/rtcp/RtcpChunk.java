/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtp.rtcp;

import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * This class wraps a RTCP Chunk.
 *
 *         0                   1                   2                   3
 *         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                            SSRC/CSRC                          |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        :                            SDES items                         :
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

public class RtcpChunk {
    public static final int RTCP_CHUNK_FIXED_SIZE = 4; /* RFC 3550 */

    private int length;
    private final long ssrc;
    private final RtcpSdesItem[] sdesItems;

    RtcpChunk(Builder builder) {
        ssrc = builder.ssrc;
        sdesItems = builder.sdesItems;

        for (RtcpSdesItem sdesItem : sdesItems) {
            length += sdesItem.getLength() + 2;
        }

        length += 4;
    }

    public long getSsrc() {
        return ssrc;
    }

    public RtcpSdesItem[] getSdesItems() {
        return sdesItems;
    }

    public int getLength() { return length; }

    @Nullable
    public static RtcpChunk parse(byte[] packet, int length) {
        if (length < RTCP_CHUNK_FIXED_SIZE || length > packet.length) {
            return null;
        }

        long ssrc = ((((long)packet[0]) & 0xff) << 24) | ((((long)packet[1]) & 0xff) << 16) |
                ((((long)packet[2]) & 0xff) << 8) | (((long)packet[3]) & 0xff);

        int offset = RTCP_CHUNK_FIXED_SIZE;
        RtcpSdesItem[] sdesItems = new RtcpSdesItem[0];

        while (offset < length) {
            // Read the element type
            @RtcpSdesItem.ItemType int itemType = (int)(((int)packet[offset]) & 0xFF);
            if (itemType < RtcpSdesItem.END || itemType > RtcpSdesItem.PRIV) {
                return null;
            }

            if (itemType != RtcpSdesItem.END) {
                // Read the element length
                int itemLen = packet[offset + 1] & 0xFF;
                byte[] itemText = new byte[itemLen];
                System.arraycopy(packet, offset + RtcpSdesItem.RTCP_SDES_ITEM_FIXED_SIZE,
                        itemText, 0, itemLen);

                int streamCount = sdesItems.length;
                sdesItems = Arrays.copyOf(sdesItems, streamCount + 1);
                sdesItems[streamCount] = new RtcpSdesItem.Builder()
                        .setType(itemType)
                        .setValue(itemText)
                        .build();

                offset+=(RtcpSdesItem.RTCP_SDES_ITEM_FIXED_SIZE + itemLen);
                continue;
            }

            break;
        }

        return new RtcpChunk.Builder().setSsrc(ssrc).setSdesItems(sdesItems).build();
    }

    /** Builder for {@link RtcpChunk}. */
    public static final class Builder {
        long ssrc;
        RtcpSdesItem[] sdesItems;

        public Builder() {
            sdesItems = new RtcpSdesItem[0];
        }

        public Builder setSsrc(long ssrc) {
            this.ssrc = ssrc;
            return this;
        }

        public Builder setSdesItems(RtcpSdesItem[] sdesItems) {
            if (sdesItems != null) {
                this.sdesItems = sdesItems;
            }

            return this;
        }

        /** Creates a {@link RtcpChunk}. */
        public RtcpChunk build() {
            return new RtcpChunk(this);
        }
    }
}
