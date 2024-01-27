/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static androidx.media3.common.C.WIDEVINE_UUID;
import static androidx.media3.extractor.mp4.Atom.TYPE_pssh;
import static androidx.media3.extractor.mp4.Atom.parseFullAtomFlags;
import static androidx.media3.extractor.mp4.Atom.parseFullAtomVersion;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link PsshAtomUtil}. */
@RunWith(AndroidJUnit4.class)
public final class PsshAtomUtilTest {

  @Test
  public void buildPsshAtom() {
    byte[] schemeData = new byte[] {0, 1, 2, 3, 4, 5};
    byte[] psshAtom = PsshAtomUtil.buildPsshAtom(C.WIDEVINE_UUID, schemeData);
    // Read the PSSH atom back and assert its content is as expected.
    ParsableByteArray parsablePsshAtom = new ParsableByteArray(psshAtom);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(psshAtom.length); // length
    assertThat(parsablePsshAtom.readInt()).isEqualTo(TYPE_pssh); // type
    int fullAtomInt = parsablePsshAtom.readInt(); // version + flags
    assertThat(parseFullAtomVersion(fullAtomInt)).isEqualTo(0);
    assertThat(parseFullAtomFlags(fullAtomInt)).isEqualTo(0);
    UUID systemId = new UUID(parsablePsshAtom.readLong(), parsablePsshAtom.readLong());
    assertThat(systemId).isEqualTo(WIDEVINE_UUID);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(schemeData.length);
    byte[] psshSchemeData = new byte[schemeData.length];
    parsablePsshAtom.readBytes(psshSchemeData, 0, schemeData.length);
    assertThat(psshSchemeData).isEqualTo(schemeData);
  }

  @Test
  public void buildVersion1Pssh() {
    byte[] schemeData = new byte[] {0, 1, 2, 3, 4, 5};
    UUID[] keyIds = new UUID[2];
    keyIds[0] = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed");
    keyIds[1] = UUID.fromString("dc03d7f3-334d-b858-f114-9ab759a925fb");
    byte[] psshAtom = PsshAtomUtil.buildPsshAtom(C.WIDEVINE_UUID, keyIds, schemeData);
    ParsableByteArray parsablePsshAtom = new ParsableByteArray(psshAtom);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(psshAtom.length);
    assertThat(parsablePsshAtom.readInt()).isEqualTo(TYPE_pssh); // type
    int fullAtomInt = parsablePsshAtom.readInt(); // version + flags
    assertThat(parseFullAtomVersion(fullAtomInt)).isEqualTo(1);
    assertThat(parseFullAtomFlags(fullAtomInt)).isEqualTo(0);
    UUID systemId = new UUID(parsablePsshAtom.readLong(), parsablePsshAtom.readLong());
    assertThat(systemId).isEqualTo(WIDEVINE_UUID);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(2);
    UUID keyId0 = new UUID(parsablePsshAtom.readLong(), parsablePsshAtom.readLong());
    assertThat(keyId0).isEqualTo(keyIds[0]);
    UUID keyId1 = new UUID(parsablePsshAtom.readLong(), parsablePsshAtom.readLong());
    assertThat(keyId1).isEqualTo(keyIds[1]);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(schemeData.length);
    byte[] psshSchemeData = new byte[schemeData.length];
    parsablePsshAtom.readBytes(psshSchemeData, 0, schemeData.length);
    assertThat(psshSchemeData).isEqualTo(schemeData);
  }

  @Test
  public void parseV1Atom() {
    byte[] psshBuffer = {
        0x00, 0x00, 0x00, 0x44, 0x70, 0x73, 0x73, 0x68, // BMFF box header (68 bytes, 'pssh')
        0x01, 0x00, 0x00, 0x00, // Full box header (version = 1, flags = 0)
        0x10, 0x77, -0x11, -0x14, -0x40, -0x4e, 0x4d, 0x02, // SystemID
        -0x54, -0x1d, 0x3c, 0x1e, 0x52, -0x1e, -0x05, 0x4b, 0x00, 0x00, 0x00, 0x02, // KID_count (2)
        0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, // First KID ("0123456789012345")
        0x38, 0x39, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
        0x48, // Second KID ("ABCDEFGHIJKLMNOP")
        0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x00, 0x00, 0x00, 0x00, // Size of Data (0)
    };
    PsshAtomUtil.PsshAtom psshAtom = PsshAtomUtil.parsePsshAtom(psshBuffer);
    assertThat(psshAtom).isNotNull();
    System.out.println("psshAtom: " + psshAtom);
    assertThat(psshAtom.version).isEqualTo(1);
    assertThat(psshAtom.uuid)
        .isEqualTo(UUID.fromString("1077efec-c0b2-4d02-ace3-3c1e52e2fb4b"));
    assertThat(psshAtom.keyIds).isNotNull();
    assertThat(psshAtom.keyIds.length).isEqualTo(2);
    assertThat(psshAtom.schemeData).isEmpty();
  }
}
