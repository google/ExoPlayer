/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.util.UUID;

/** Utility methods for handling PSSH atoms. */
@UnstableApi
public final class PsshAtomUtil {

  private static final String TAG = "PsshAtomUtil";

  private PsshAtomUtil() {}

  /**
   * Builds a version 0 PSSH atom for a given system id, containing the given data.
   *
   * @param systemId The system id of the scheme.
   * @param data The scheme specific data.
   * @return The PSSH atom.
   */
  public static byte[] buildPsshAtom(UUID systemId, @Nullable byte[] data) {
    return buildPsshAtom(systemId, null, data);
  }

  /**
   * Builds a PSSH atom for the given system id, containing the given key ids and data.
   *
   * @param systemId The system id of the scheme.
   * @param keyIds The key ids for a version 1 PSSH atom, or null for a version 0 PSSH atom.
   * @param data The scheme specific data.
   * @return The PSSH atom.
   */
  public static byte[] buildPsshAtom(
      UUID systemId, @Nullable UUID[] keyIds, @Nullable byte[] data) {
    int dataLength = data != null ? data.length : 0;
    int psshBoxLength = Atom.FULL_HEADER_SIZE + 16 /* SystemId */ + 4 /* DataSize */ + dataLength;
    if (keyIds != null) {
      psshBoxLength += 4 /* KID_count */ + (keyIds.length * 16) /* KIDs */;
    }
    ByteBuffer psshBox = ByteBuffer.allocate(psshBoxLength);
    psshBox.putInt(psshBoxLength);
    psshBox.putInt(Atom.TYPE_pssh);
    psshBox.putInt(keyIds != null ? 0x01000000 : 0 /* version=(buildV1Atom ? 1 : 0), flags=0 */);
    psshBox.putLong(systemId.getMostSignificantBits());
    psshBox.putLong(systemId.getLeastSignificantBits());
    if (keyIds != null) {
      psshBox.putInt(keyIds.length);
      for (UUID keyId : keyIds) {
        psshBox.putLong(keyId.getMostSignificantBits());
        psshBox.putLong(keyId.getLeastSignificantBits());
      }
    }
    if (data != null && data.length != 0) {
      psshBox.putInt(data.length);
      psshBox.put(data);
    } else {
      psshBox.putInt(0);
    }
    return psshBox.array();
  }

  /**
   * Returns whether the data is a valid PSSH atom.
   *
   * @param data The data to parse.
   * @return Whether the data is a valid PSSH atom.
   */
  public static boolean isPsshAtom(byte[] data) {
    return parsePsshAtom(data) != null;
  }

  /**
   * Parses the UUID from a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * <p>The UUID is only parsed if the data is a valid PSSH atom.
   *
   * @param atom The atom to parse.
   * @return The parsed UUID. Null if the input is not a valid PSSH atom, or if the PSSH atom has an
   *     unsupported version.
   */
  @Nullable
  public static UUID parseUuid(byte[] atom) {
    @Nullable PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return null;
    }
    return parsedAtom.uuid;
  }

  /**
   * Parses the version from a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * <p>The version is only parsed if the data is a valid PSSH atom.
   *
   * @param atom The atom to parse.
   * @return The parsed version. -1 if the input is not a valid PSSH atom, or if the PSSH atom has
   *     an unsupported version.
   */
  public static int parseVersion(byte[] atom) {
    @Nullable PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return -1;
    }
    return parsedAtom.version;
  }

  /**
   * Parses the scheme specific data from a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * <p>The scheme specific data is only parsed if the data is a valid PSSH atom matching the given
   * UUID, or if the data is a valid PSSH atom of any type in the case that the passed UUID is null.
   *
   * @param atom The atom to parse.
   * @param uuid The required UUID of the PSSH atom, or null to accept any UUID.
   * @return The parsed scheme specific data. Null if the input is not a valid PSSH atom, or if the
   *     PSSH atom has an unsupported version, or if the PSSH atom does not match the passed UUID.
   */
  @Nullable
  public static byte[] parseSchemeSpecificData(byte[] atom, UUID uuid) {
    @Nullable PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return null;
    }
    if (!uuid.equals(parsedAtom.uuid)) {
      Log.w(TAG, "UUID mismatch. Expected: " + uuid + ", got: " + parsedAtom.uuid + ".");
      return null;
    }
    return parsedAtom.schemeData;
  }

  /**
   * Parses a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * @param atom The atom to parse.
   * @return The parsed PSSH atom. Null if the input is not a valid PSSH atom, or if the PSSH atom
   *     has an unsupported version.
   */
  @Nullable
  public static PsshAtom parsePsshAtom(byte[] atom) {
    ParsableByteArray atomData = new ParsableByteArray(atom);
    if (atomData.limit() < Atom.FULL_HEADER_SIZE + 16 /* UUID */ + 4 /* DataSize */) {
      // Data too short.
      return null;
    }
    atomData.setPosition(0);
    int bufferLength = atomData.bytesLeft();
    int atomSize = atomData.readInt();
    if (atomSize != bufferLength) {
      Log.w(
          TAG,
          "Advertised atom size (" + atomSize + ") does not match buffer size: " + bufferLength);
      return null;
    }
    int atomType = atomData.readInt();
    if (atomType != Atom.TYPE_pssh) {
      Log.w(TAG, "Atom type is not pssh: " + atomType);
      return null;
    }
    int atomVersion = Atom.parseFullAtomVersion(atomData.readInt());
    if (atomVersion > 1) {
      Log.w(TAG, "Unsupported pssh version: " + atomVersion);
      return null;
    }
    UUID uuid = new UUID(atomData.readLong(), atomData.readLong());
    UUID[] keyIds = null;
    if (atomVersion == 1) {
      int keyIdCount = atomData.readUnsignedIntToInt();
      keyIds = new UUID[keyIdCount];
      for (int i = 0; i < keyIdCount; ++i) {
        keyIds[i] = new UUID(atomData.readLong(), atomData.readLong());
      }
    }
    int dataSize = atomData.readUnsignedIntToInt();
    bufferLength = atomData.bytesLeft();
    if (dataSize != bufferLength) {
      Log.w(
          TAG, "Atom data size (" + dataSize + ") does not match the bytes left: " + bufferLength);
      return null;
    }
    byte[] data = new byte[dataSize];
    atomData.readBytes(data, 0, dataSize);
    return new PsshAtom(uuid, atomVersion, data, keyIds);
  }

  /** A class representing the mp4 PSSH Atom as specified in ISO/IEC 23001-7. */
  public static final class PsshAtom {

    /** The UUID of the encryption system as specified in ISO/IEC 23009-1 section 5.8.4.1. */
    public final UUID uuid;

    /** The version of the PSSH atom, either 0 or 1. */
    public final int version;

    /** Binary scheme data. */
    public final byte[] schemeData;

    /** Array of key IDs. Always null for version 0 and non-null for version 1. */
    @Nullable public final UUID[] keyIds;

    /* package */ PsshAtom(UUID uuid, int version, byte[] schemeData, @Nullable UUID[] keyIds) {
      this.uuid = uuid;
      this.version = version;
      this.schemeData = schemeData;
      this.keyIds = keyIds;
    }
  }
}
