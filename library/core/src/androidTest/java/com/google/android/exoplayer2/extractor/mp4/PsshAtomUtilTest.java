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
package com.google.android.exoplayer2.extractor.mp4;

import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.UUID;
import junit.framework.TestCase;

/**
 * Tests for {@link PsshAtomUtil}.
 */
public class PsshAtomUtilTest extends TestCase {

  public void testBuildPsshAtom() {
    byte[] schemeData = new byte[]{0, 1, 2, 3, 4, 5};
    byte[] psshAtom = PsshAtomUtil.buildPsshAtom(C.WIDEVINE_UUID, schemeData);
    // Read the PSSH atom back and assert its content is as expected.
    ParsableByteArray parsablePsshAtom = new ParsableByteArray(psshAtom);
    assertEquals(psshAtom.length, parsablePsshAtom.readUnsignedIntToInt()); // length
    assertEquals(Atom.TYPE_pssh, parsablePsshAtom.readInt()); // type
    int fullAtomInt = parsablePsshAtom.readInt(); // version + flags
    assertEquals(0, Atom.parseFullAtomVersion(fullAtomInt));
    assertEquals(0, Atom.parseFullAtomFlags(fullAtomInt));
    UUID systemId = new UUID(parsablePsshAtom.readLong(), parsablePsshAtom.readLong());
    assertEquals(C.WIDEVINE_UUID, systemId);
    assertEquals(schemeData.length, parsablePsshAtom.readUnsignedIntToInt());
    byte[] psshSchemeData = new byte[schemeData.length];
    parsablePsshAtom.readBytes(psshSchemeData, 0, schemeData.length);
    MoreAsserts.assertEquals(schemeData, psshSchemeData);
  }

}
