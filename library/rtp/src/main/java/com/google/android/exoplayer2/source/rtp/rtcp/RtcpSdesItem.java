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

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class wraps a RTCP SDES Item.
 *
 */
public class RtcpSdesItem {
    public static final int RTCP_SDES_ITEM_FIXED_SIZE = 2; /* RFC 3550 */

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({END, CNAME, NAME, EMAIL, PHONE, LOC, TOOL, NOTE, PRIV})
    public @interface ItemType {}
    public static final int END = 0;
    public static final int CNAME = 1;
    public static final int NAME = 2;
    public static final int EMAIL = 3;
    public static final int PHONE = 4;
    public static final int LOC = 5;
    public static final int TOOL = 6;
    public static final int NOTE = 7;
    public static final int PRIV = 8;

    private final @ItemType int type;
    private final byte[] value;

    RtcpSdesItem(Builder builder) {
        this.type = builder.type;
        this.value = builder.value;
    }

    public @ItemType int getType() {
        return type;
    }

    public int getLength() {
        return value.length;
    }

    public byte[] getValue() {
        return value;
    }

    /** Builder for {@link RtcpSdesItem}. */
    public static final class Builder {
        @ItemType int type;
        byte[] value;

        public Builder() {
            value = new byte[0];
        }

        public Builder setType(@ItemType int type) {
            this.type = type;
            return this;
        }

        public Builder setValue(byte[] value) {
            if (value != null) {
                this.value = value;
            }

            return this;
        }

        /** Creates a {@link RtcpSdesItem}. */
        public RtcpSdesItem build() {
            return new RtcpSdesItem(this);
        }
    }
}
