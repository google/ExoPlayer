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
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* package*/ abstract class Atom {

  /** Size of an atom header, in bytes. */
  public static final int HEADER_SIZE = 8;

  /** Size of a full atom header, in bytes. */
  public static final int FULL_HEADER_SIZE = 12;

  /** Size of a long atom header, in bytes. */
  public static final int LONG_HEADER_SIZE = 16;

  /** Value for the first 32 bits of atomSize when the atom size is actually a long value. */
  public static final int LONG_SIZE_PREFIX = 1;

  public static final int TYPE_ftyp = Util.getIntegerCodeForString("ftyp");
  public static final int TYPE_avc1 = Util.getIntegerCodeForString("avc1");
  public static final int TYPE_avc3 = Util.getIntegerCodeForString("avc3");
  public static final int TYPE_esds = Util.getIntegerCodeForString("esds");
  public static final int TYPE_mdat = Util.getIntegerCodeForString("mdat");
  public static final int TYPE_mp4a = Util.getIntegerCodeForString("mp4a");
  public static final int TYPE_ac_3 = Util.getIntegerCodeForString("ac-3");
  public static final int TYPE_dac3 = Util.getIntegerCodeForString("dac3");
  public static final int TYPE_ec_3 = Util.getIntegerCodeForString("ec-3");
  public static final int TYPE_dec3 = Util.getIntegerCodeForString("dec3");
  public static final int TYPE_tfdt = Util.getIntegerCodeForString("tfdt");
  public static final int TYPE_tfhd = Util.getIntegerCodeForString("tfhd");
  public static final int TYPE_trex = Util.getIntegerCodeForString("trex");
  public static final int TYPE_trun = Util.getIntegerCodeForString("trun");
  public static final int TYPE_sidx = Util.getIntegerCodeForString("sidx");
  public static final int TYPE_moov = Util.getIntegerCodeForString("moov");
  public static final int TYPE_mvhd = Util.getIntegerCodeForString("mvhd");
  public static final int TYPE_trak = Util.getIntegerCodeForString("trak");
  public static final int TYPE_mdia = Util.getIntegerCodeForString("mdia");
  public static final int TYPE_minf = Util.getIntegerCodeForString("minf");
  public static final int TYPE_stbl = Util.getIntegerCodeForString("stbl");
  public static final int TYPE_avcC = Util.getIntegerCodeForString("avcC");
  public static final int TYPE_moof = Util.getIntegerCodeForString("moof");
  public static final int TYPE_traf = Util.getIntegerCodeForString("traf");
  public static final int TYPE_mvex = Util.getIntegerCodeForString("mvex");
  public static final int TYPE_tkhd = Util.getIntegerCodeForString("tkhd");
  public static final int TYPE_mdhd = Util.getIntegerCodeForString("mdhd");
  public static final int TYPE_hdlr = Util.getIntegerCodeForString("hdlr");
  public static final int TYPE_stsd = Util.getIntegerCodeForString("stsd");
  public static final int TYPE_pssh = Util.getIntegerCodeForString("pssh");
  public static final int TYPE_sinf = Util.getIntegerCodeForString("sinf");
  public static final int TYPE_schm = Util.getIntegerCodeForString("schm");
  public static final int TYPE_schi = Util.getIntegerCodeForString("schi");
  public static final int TYPE_tenc = Util.getIntegerCodeForString("tenc");
  public static final int TYPE_encv = Util.getIntegerCodeForString("encv");
  public static final int TYPE_enca = Util.getIntegerCodeForString("enca");
  public static final int TYPE_frma = Util.getIntegerCodeForString("frma");
  public static final int TYPE_saiz = Util.getIntegerCodeForString("saiz");
  public static final int TYPE_uuid = Util.getIntegerCodeForString("uuid");
  public static final int TYPE_senc = Util.getIntegerCodeForString("senc");
  public static final int TYPE_pasp = Util.getIntegerCodeForString("pasp");
  public static final int TYPE_TTML = Util.getIntegerCodeForString("TTML");
  public static final int TYPE_vmhd = Util.getIntegerCodeForString("vmhd");
  public static final int TYPE_smhd = Util.getIntegerCodeForString("smhd");
  public static final int TYPE_mp4v = Util.getIntegerCodeForString("mp4v");
  public static final int TYPE_stts = Util.getIntegerCodeForString("stts");
  public static final int TYPE_stss = Util.getIntegerCodeForString("stss");
  public static final int TYPE_ctts = Util.getIntegerCodeForString("ctts");
  public static final int TYPE_stsc = Util.getIntegerCodeForString("stsc");
  public static final int TYPE_stsz = Util.getIntegerCodeForString("stsz");
  public static final int TYPE_stco = Util.getIntegerCodeForString("stco");
  public static final int TYPE_co64 = Util.getIntegerCodeForString("co64");

  public final int type;

  Atom(int type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return getAtomTypeString(type);
  }

  /** An MP4 atom that is a leaf. */
  public static final class LeafAtom extends Atom {

    public final ParsableByteArray data;

    public LeafAtom(int type, ParsableByteArray data) {
      super(type);
      this.data = data;
    }

  }

  /** An MP4 atom that has child atoms. */
  public static final class ContainerAtom extends Atom {

    public final long endByteOffset;
    public final List<LeafAtom> leafChildren;
    public final List<ContainerAtom> containerChildren;

    public ContainerAtom(int type, long endByteOffset) {
      super(type);

      leafChildren = new ArrayList<LeafAtom>();
      containerChildren = new ArrayList<ContainerAtom>();
      this.endByteOffset = endByteOffset;
    }

    public void add(LeafAtom atom) {
      leafChildren.add(atom);
    }

    public void add(ContainerAtom atom) {
      containerChildren.add(atom);
    }

    public LeafAtom getLeafAtomOfType(int type) {
      int childrenSize = leafChildren.size();
      for (int i = 0; i < childrenSize; i++) {
        LeafAtom atom = leafChildren.get(i);
        if (atom.type == type) {
          return atom;
        }
      }
      return null;
    }

    public ContainerAtom getContainerAtomOfType(int type) {
      int childrenSize = containerChildren.size();
      for (int i = 0; i < childrenSize; i++) {
        ContainerAtom atom = containerChildren.get(i);
        if (atom.type == type) {
          return atom;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return getAtomTypeString(type)
          + " leaves: " + Arrays.toString(leafChildren.toArray(new LeafAtom[0]))
          + " containers: " + Arrays.toString(containerChildren.toArray(new ContainerAtom[0]));
    }

  }

  /**
   * Parses the version number out of the additional integer component of a full atom.
   */
  public static int parseFullAtomVersion(int fullAtomInt) {
    return 0x000000FF & (fullAtomInt >> 24);
  }

  /**
   * Parses the atom flags out of the additional integer component of a full atom.
   */
  public static int parseFullAtomFlags(int fullAtomInt) {
    return 0x00FFFFFF & fullAtomInt;
  }

  private static String getAtomTypeString(int type) {
    return "" + (char) (type >> 24)
        + (char) ((type >> 16) & 0xFF)
        + (char) ((type >> 8) & 0xFF)
        + (char) (type & 0xFF);
  }

}
