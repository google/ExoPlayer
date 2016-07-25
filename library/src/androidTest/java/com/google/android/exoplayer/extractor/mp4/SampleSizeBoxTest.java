package com.google.android.exoplayer.extractor.mp4;

import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import junit.framework.TestCase;

public class SampleSizeBoxTest extends TestCase {
    final String ATOM_HEADER = "000000000000000000000000";
    final String SAMPLE_COUNT = "00000004";
    final byte[] FOUR_BIT_STZ2 = Util.getBytesFromHexString(ATOM_HEADER + "00000004"
            + SAMPLE_COUNT + "1234");
    final byte[] EIGHT_BIT_STZ2 = Util.getBytesFromHexString(ATOM_HEADER + "00000008"
            + SAMPLE_COUNT + "01020304");
    final byte[] SIXTEEN_BIT_STZ2 = Util.getBytesFromHexString(ATOM_HEADER + "00000010"
            + SAMPLE_COUNT + "0001000200030004");

    public void testStz2Parsing4BitFieldSize() {
        final Atom.LeafAtom stz2Atom = new Atom.LeafAtom(Atom.TYPE_stsz,
                new ParsableByteArray(FOUR_BIT_STZ2));
        verifyParsing(stz2Atom);
    }

    public void testStz2Parsing8BitFieldSize() {
        final Atom.LeafAtom stz2Atom = new Atom.LeafAtom(Atom.TYPE_stsz,
                new ParsableByteArray(EIGHT_BIT_STZ2));
        verifyParsing(stz2Atom);
    }

    public void testStz2Parsing16BitFieldSize() {
        final Atom.LeafAtom stz2Atom = new Atom.LeafAtom(Atom.TYPE_stsz,
                new ParsableByteArray(SIXTEEN_BIT_STZ2));
        verifyParsing(stz2Atom);
    }

    private void verifyParsing(Atom.LeafAtom stz2Atom) {
        final AtomParsers.Stz2SampleSizeBox box = new AtomParsers.Stz2SampleSizeBox(stz2Atom);
        assertEquals(4, box.getSampleCount());
        assertFalse(box.isFixedSampleSize());

        for (int i = 0; i < box.getSampleCount(); i++) {
            assertEquals(i + 1, box.readNextSampleSize());
        }
    }
}
