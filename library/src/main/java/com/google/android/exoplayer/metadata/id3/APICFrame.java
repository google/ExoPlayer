/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.metadata.id3;

/**
 * APIC (Attached Picture) ID3 frame.
 */
public class APICFrame extends Id3Frame{
    public static final String ID = "APIC";

    public final byte encoding;
    public final String mimeType;
    public final byte pictureType;
    public final String description;
    public final byte[] pictureData;

    public APICFrame(byte encoding, String mimeType, byte pictureType, String description, byte[] pictureData) {
        super(ID);
        this.encoding = encoding;
        this.mimeType = mimeType;
        this.pictureType = pictureType;
        this.description = description;
        this.pictureData = pictureData;
    }



}
