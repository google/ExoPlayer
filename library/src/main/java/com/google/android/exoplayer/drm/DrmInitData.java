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
package com.google.android.exoplayer.drm;

import android.media.MediaDrm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates initialization data required by a {@link MediaDrm} instance.
 */
public abstract class DrmInitData {

  /**
   * The container mime type.
   */
  public final String mimeType;

  public DrmInitData(String mimeType) {
    this.mimeType = mimeType;
  }

  /**
   * Retrieves initialization data for a given DRM scheme, specified by its UUID.
   *
   * @param schemeUuid The DRM scheme's UUID.
   * @return The initialization data for the scheme, or null if the scheme is not supported.
   */
  public abstract byte[] get(UUID schemeUuid);

  /**
   * A {@link DrmInitData} implementation that maps UUID onto scheme specific data.
   */
  public static final class Mapped extends DrmInitData {

    private final Map<UUID, byte[]> schemeData;

    public Mapped(String mimeType) {
      super(mimeType);
      schemeData = new HashMap<UUID, byte[]>();
    }

    @Override
    public byte[] get(UUID schemeUuid) {
      return schemeData.get(schemeUuid);
    }

    /**
     * Inserts scheme specific initialization data.
     *
     * @param schemeUuid The scheme UUID.
     * @param data The corresponding initialization data.
     */
    public void put(UUID schemeUuid, byte[] data) {
      schemeData.put(schemeUuid, data);
    }

    /**
     * Inserts scheme specific initialization data.
     *
     * @param data A mapping from scheme UUID to initialization data.
     */
    public void putAll(Map<UUID, byte[]> data) {
      schemeData.putAll(data);
    }

  }

  /**
   * A {@link DrmInitData} implementation that returns the same initialization data for all schemes.
   */
  public static final class Universal extends DrmInitData {

    private byte[] data;

    public Universal(String mimeType, byte[] data) {
      super(mimeType);
      this.data = data;
    }

    @Override
    public byte[] get(UUID schemeUuid) {
      return data;
    }

  }

}
