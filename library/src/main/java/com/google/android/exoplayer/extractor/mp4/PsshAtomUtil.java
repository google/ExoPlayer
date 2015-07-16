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
package com.google.android.exoplayer.extractor.mp4;

import com.google.android.exoplayer.util.ParsableByteArray;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Utility methods for handling PSSH atoms.
 */
public final class PsshAtomUtil {

  private PsshAtomUtil() {}

  /**
   * Builds a PSSH atom for a given {@link UUID} containing the given scheme specific data.
   *
   * @param uuid The UUID of the scheme.
   * @param data The scheme specific data.
   * @return The PSSH atom.
   */
  public static byte[] buildPsshAtom(UUID uuid, byte[] data) {
    int psshBoxLength = Atom.FULL_HEADER_SIZE + 16 /* UUID */ + 4 /* DataSize */ + data.length;
    ByteBuffer psshBox = ByteBuffer.allocate(psshBoxLength);
    psshBox.putInt(psshBoxLength);
    psshBox.putInt(Atom.TYPE_pssh);
    psshBox.putInt(0 /* version=0, flags=0 */);
    psshBox.putLong(uuid.getMostSignificantBits());
    psshBox.putLong(uuid.getLeastSignificantBits());
    psshBox.putInt(data.length);
    psshBox.put(data);
    return psshBox.array();
  }

  /**
   * Parses the UUID from a PSSH atom.
   * <p>
   * The UUID is only parsed if the data is a valid PSSH atom.
   *
   * @param atom The atom to parse.
   * @return The parsed UUID. Null if the data is not a valid PSSH atom.
   */
  public static UUID parseUuid(byte[] atom) {
    ParsableByteArray atomData = new ParsableByteArray(atom);
    if (!isPsshAtom(atomData, null)) {
      return null;
    }
    atomData.setPosition(Atom.FULL_HEADER_SIZE);
    return new UUID(atomData.readLong(), atomData.readLong());
  }

  /**
   * Parses the scheme specific data from a PSSH atom.
   * <p>
   * The scheme specific data is only parsed if the data is a valid PSSH atom matching the given
   * UUID, or if the data is a valid PSSH atom of any type in the case that the passed UUID is null.
   *
   * @param atom The atom to parse.
   * @param uuid The required UUID of the PSSH atom, or null to accept any UUID.
   * @return The parsed scheme specific data. Null if the data is not a valid PSSH atom or if its
   *     UUID does not match the one provided.
   */
  public static byte[] parseSchemeSpecificData(byte[] atom, UUID uuid) {
    ParsableByteArray atomData = new ParsableByteArray(atom);
    if (!isPsshAtom(atomData, uuid)) {
      return null;
    }
    atomData.setPosition(Atom.FULL_HEADER_SIZE + 16 /* UUID */);
    int dataSize = atomData.readInt();
    byte[] data = new byte[dataSize];
    atomData.readBytes(data, 0, dataSize);
    return data;
  }

  private static boolean isPsshAtom(ParsableByteArray atomData, UUID uuid) {
    if (atomData.limit() < Atom.FULL_HEADER_SIZE + 16 /* UUID */ + 4 /* DataSize */) {
      // Data too short.
      return false;
    }
    atomData.setPosition(0);
    int atomSize = atomData.readInt();
    if (atomSize != atomData.bytesLeft() + 4) {
      // Not an atom, or incorrect atom size.
      return false;
    }
    int atomType = atomData.readInt();
    if (atomType != Atom.TYPE_pssh) {
      // Not an atom, or incorrect atom type.
      return false;
    }
    atomData.setPosition(Atom.FULL_HEADER_SIZE);
    if (uuid == null) {
      atomData.skipBytes(16);
    } else if (atomData.readLong() != uuid.getMostSignificantBits()
        || atomData.readLong() != uuid.getLeastSignificantBits()) {
      // UUID doesn't match.
      return false;
    }
    int dataSize = atomData.readInt();
    if (dataSize != atomData.bytesLeft()) {
      // Incorrect dataSize.
      return false;
    }
    return true;
  }

}
