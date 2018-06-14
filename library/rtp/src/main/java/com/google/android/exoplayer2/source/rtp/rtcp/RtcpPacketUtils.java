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

import java.util.Arrays;

/**
 * This class provides generic utility functions for RTCP packets
 */

/* package */ final class RtcpPacketUtils {
    /**
     * Append two byte arrays.
     * Appends packet B at the end of Packet A (Assuming Bytes as elements).
     * Returns packet ( AB ).
     *
     * @param packetA The first packet.
     * @param packetB The second packet.
     * @return The desired packet which is A concatenated with B.
     */
    public static synchronized byte[] append(byte[] packetA, byte[] packetB) {
        // Create a new array whose size is equal to sum of packets being added
        int from = packetA.length;
        byte[] packetAB = Arrays.copyOf(packetA, from + packetB.length);
        System.arraycopy(packetB, 0, packetAB, from, packetB.length);

        return packetAB;
    }

    /**
     * Convert signed int to long by taking 2's complement if necessary.
     *
     * @param intToConvert The signed integer which will be converted to Long.
     *
     * @return The unsigned long representation of the signed int.
     */
    public static synchronized long convertSignedIntToLong(int intToConvert)
    {
        int in = intToConvert;
        in = ( in << 1 ) >> 1;
        long lin = (long) in;
        lin = lin + 0x7FFFFFFF;

        return lin;
    }

    /**
     * Convert 64 bit long to n bytes.
     *
     * @param ldata The long from which the n byte array will be constructed.
     * @param n The desired number of bytes to convert the long to.
     *
     * @return The desired byte array which is populated with the long value.
     */
    public static synchronized byte[] longToBytes(long ldata, int n)
    {
        byte[] buff = new byte [ n ];
        for ( int i=n-1; i>=0; i--) {
            // Keep assigning the right most 8 bits to the
            // byte arrays while shift 8 bits during each iteration
            buff [ i ] = (byte) ldata;
            ldata = ldata >> 8;
        }

        return buff;
    }

    /**
     * Calculate number of octets required to fit the
     * given number of octets into 32 bit boundary.
     *
     * @param lengthOfUnpaddedPacket The setLength of an unpadded packet
     *
     * @return The required number of octets which must be appended to this
     * packet to make it fit into a 32 bit boundary.
     */
    public static synchronized int calculatePadLength(int lengthOfUnpaddedPacket) {
        // Determine the number of 8 bit words required to fit the packet in
        // 32 bit boundary
        int remain =
                (int) Math.IEEEremainder ( (double) lengthOfUnpaddedPacket ,
                        (double) 4 );
        int padLen = 0;
        // e.g. remainder -1, then we need to pad 1 extra
        // byte to the end to make it to the 32 bit boundary
        if ( remain < 0 )
            padLen = Math.abs ( remain );
            // e.g. remainder is +1 then we need to pad 3 extra bytes
        else if ( remain > 0 )
            padLen = 4-remain;

        return ( padLen );
    }

    /**
     * Calculate number of octets required to fit the
     * given number of octets into 64 bit boundary.
     *
     * @param lengthOfUnpaddedPacket The setLength of an unpadded packet
     *
     * @return The required number of octets which must be appended to this
     * packet to make it fit into a 64 bit boundary.
     */
    public static synchronized int calculate64PadLength(int lengthOfUnpaddedPacket) {
        // Determine the number of 8 bit words required to fit the packet in
        // 64 bit boundary
        int remain =
                (int) Math.IEEEremainder ( (double) lengthOfUnpaddedPacket ,
                        (double) 16 );
        int padLen = 0;
        // e.g. remainder -1, then we need to pad 1 extra
        // byte to the end to make it to the 64 bit boundary
        if ( remain < 0 )
            padLen = Math.abs ( remain );
            // e.g. remainder is +1 then we need to pad 3 extra bytes
        else if ( remain > 0 )
            padLen = 16-remain;

        return ( padLen );
    }

    /** Byte swap */

    public static synchronized byte[] swapBytes(byte[] bdata)
    {
        byte[] buff = new byte [ bdata.length ];
        for ( int i=bdata.length-1; i>=0; i--) {
            buff [ i ] = bdata[(bdata.length -1) - i ];
        }

        return buff;
    }

}
