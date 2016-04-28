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

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Encapsulates DRM initialization data for possibly multiple DRM schemes.
 */
public interface DrmInitData {

  /**
   * Retrieves initialization data for a given DRM scheme, specified by its UUID.
   *
   * @param schemeUuid The DRM scheme's UUID.
   * @return The initialization data for the scheme, or null if the scheme is not supported.
   */
  SchemeInitData get(UUID schemeUuid);

  /**
   * A {@link DrmInitData} implementation that maps UUID onto scheme specific data.
   */
  final class Mapped implements DrmInitData {

    private final UuidSchemeInitDataTuple[] schemeDatas;

    // Lazily initialized hashcode.
    private int hashCode;

    public Mapped(UuidSchemeInitDataTuple... schemeDatas) {
      this.schemeDatas = schemeDatas.clone();
      Arrays.sort(this.schemeDatas); // Required for correct equals and hashcode implementations.
    }

    public Mapped(List<UuidSchemeInitDataTuple> schemeDatas) {
      this.schemeDatas = schemeDatas.toArray(new UuidSchemeInitDataTuple[schemeDatas.size()]);
      Arrays.sort(this.schemeDatas); // Required for correct equals and hashcode implementations.
    }

    @Override
    public SchemeInitData get(UUID schemeUuid) {
      for (UuidSchemeInitDataTuple schemeData : schemeDatas) {
        if (schemeUuid.equals(schemeData.uuid)) {
          return schemeData.data;
        }
      }
      return null;
    }

    @Override
    public int hashCode() {
      if (hashCode == 0) {
        hashCode = Arrays.hashCode(schemeDatas);
      }
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      return Arrays.equals(schemeDatas, ((Mapped) obj).schemeDatas);
    }

  }

  /**
   * A {@link DrmInitData} implementation that returns the same data for all schemes.
   */
  final class Universal implements DrmInitData {

    private final SchemeInitData data;

    public Universal(SchemeInitData data) {
      this.data = data;
    }

    @Override
    public SchemeInitData get(UUID schemeUuid) {
      return data;
    }

    @Override
    public int hashCode() {
      return data == null ? 0 : data.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      return Util.areEqual(data, ((Universal) obj).data);
    }

  }

  /**
   * Scheme initialization data.
   */
  final class SchemeInitData {

    // Lazily initialized hashcode.
    private int hashCode;

    /**
     * The mimeType of {@link #data}.
     */
    public final String mimeType;
    /**
     * The initialization data.
     */
    public final byte[] data;

    /**
     * @param mimeType The mimeType of the initialization data.
     * @param data The initialization data.
     */
    public SchemeInitData(String mimeType, byte[] data) {
      this.mimeType = Assertions.checkNotNull(mimeType);
      this.data = Assertions.checkNotNull(data);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SchemeInitData)) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      SchemeInitData other = (SchemeInitData) obj;
      return mimeType.equals(other.mimeType) && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
      if (hashCode == 0) {
        hashCode = mimeType.hashCode() + 31 * Arrays.hashCode(data);
      }
      return hashCode;
    }

  }

  /**
   * A tuple consisting of a {@link UUID} and a {@link SchemeInitData}.
   * <p>
   * Implements {@link Comparable} based on {@link UUID} ordering.
   */
  final class UuidSchemeInitDataTuple implements Comparable<UuidSchemeInitDataTuple> {

    public final UUID uuid;
    public final SchemeInitData data;

    public UuidSchemeInitDataTuple(UUID uuid, SchemeInitData data) {
      this.uuid = Assertions.checkNotNull(uuid);
      this.data = Assertions.checkNotNull(data);
    }

    @Override
    public int compareTo(UuidSchemeInitDataTuple another) {
      return uuid.compareTo(another.uuid);
    }

    @Override
    public int hashCode() {
      return uuid.hashCode() + 31 * data.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      UuidSchemeInitDataTuple other = (UuidSchemeInitDataTuple) obj;
      return Util.areEqual(uuid, other.uuid) && Util.areEqual(data, other.data);
    }

  }

}
