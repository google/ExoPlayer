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
package com.google.android.exoplayer.metadata.frame;

/**
 * A metadata that contains parsed ID3 GEOB (General Encapsulated Object) frame data associated
 * with time indices.
 */
public final class GeobFrame extends Id3Frame {

  public static final String ID = "GEOB";

  private final String mimeType;
  private final String filename;
  private final String description;
  private final byte[] data;

  public GeobFrame( String mimeType, String filename, String description, byte[] data) {
    super(ID);
    this.mimeType = mimeType;
    this.filename = filename;
    this.description = description;
    this.data = data;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getFilename() {
    return filename;
  }

  public String getDescription() {
    return description;
  }

  public byte[] getData() {
    return data;
  }
}
