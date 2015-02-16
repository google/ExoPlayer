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
package com.google.android.exoplayer.mp4;

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Atom {

  public static final int TYPE_avc1 = getAtomTypeInteger("avc1");
  public static final int TYPE_avc3 = getAtomTypeInteger("avc3");
  public static final int TYPE_esds = getAtomTypeInteger("esds");
  public static final int TYPE_mdat = getAtomTypeInteger("mdat");
  public static final int TYPE_mp4a = getAtomTypeInteger("mp4a");
  public static final int TYPE_ac_3 = getAtomTypeInteger("ac-3");
  public static final int TYPE_dac3 = getAtomTypeInteger("dac3");
  public static final int TYPE_ec_3 = getAtomTypeInteger("ec-3");
  public static final int TYPE_dec3 = getAtomTypeInteger("dec3");
  public static final int TYPE_tfdt = getAtomTypeInteger("tfdt");
  public static final int TYPE_tfhd = getAtomTypeInteger("tfhd");
  public static final int TYPE_trex = getAtomTypeInteger("trex");
  public static final int TYPE_trun = getAtomTypeInteger("trun");
  public static final int TYPE_sidx = getAtomTypeInteger("sidx");
  public static final int TYPE_moov = getAtomTypeInteger("moov");
  public static final int TYPE_mvhd = getAtomTypeInteger("mvhd");
  public static final int TYPE_trak = getAtomTypeInteger("trak");
  public static final int TYPE_mdia = getAtomTypeInteger("mdia");
  public static final int TYPE_minf = getAtomTypeInteger("minf");
  public static final int TYPE_stbl = getAtomTypeInteger("stbl");
  public static final int TYPE_avcC = getAtomTypeInteger("avcC");
  public static final int TYPE_moof = getAtomTypeInteger("moof");
  public static final int TYPE_traf = getAtomTypeInteger("traf");
  public static final int TYPE_mvex = getAtomTypeInteger("mvex");
  public static final int TYPE_tkhd = getAtomTypeInteger("tkhd");
  public static final int TYPE_mdhd = getAtomTypeInteger("mdhd");
  public static final int TYPE_hdlr = getAtomTypeInteger("hdlr");
  public static final int TYPE_stsd = getAtomTypeInteger("stsd");
  public static final int TYPE_pssh = getAtomTypeInteger("pssh");
  public static final int TYPE_sinf = getAtomTypeInteger("sinf");
  public static final int TYPE_schm = getAtomTypeInteger("schm");
  public static final int TYPE_schi = getAtomTypeInteger("schi");
  public static final int TYPE_tenc = getAtomTypeInteger("tenc");
  public static final int TYPE_encv = getAtomTypeInteger("encv");
  public static final int TYPE_enca = getAtomTypeInteger("enca");
  public static final int TYPE_frma = getAtomTypeInteger("frma");
  public static final int TYPE_saiz = getAtomTypeInteger("saiz");
  public static final int TYPE_uuid = getAtomTypeInteger("uuid");
  public static final int TYPE_senc = getAtomTypeInteger("senc");
  public static final int TYPE_pasp = getAtomTypeInteger("pasp");
  public static final int TYPE_TTML = getAtomTypeInteger("TTML");
  public static final int TYPE_vmhd = getAtomTypeInteger("vmhd");
  public static final int TYPE_smhd = getAtomTypeInteger("smhd");
  public static final int TYPE_mp4v = getAtomTypeInteger("mp4v");
  public static final int TYPE_stts = getAtomTypeInteger("stts");
  public static final int TYPE_stss = getAtomTypeInteger("stss");
  public static final int TYPE_ctts = getAtomTypeInteger("ctts");
  public static final int TYPE_stsc = getAtomTypeInteger("stsc");
  public static final int TYPE_stsz = getAtomTypeInteger("stsz");
  public static final int TYPE_stco = getAtomTypeInteger("stco");
  public static final int TYPE_co64 = getAtomTypeInteger("co64");

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

  private static String getAtomTypeString(int type) {
    return "" + (char) (type >> 24)
        + (char) ((type >> 16) & 0xFF)
        + (char) ((type >> 8) & 0xFF)
        + (char) (type & 0xFF);
  }

  private static int getAtomTypeInteger(String typeName) {
    Assertions.checkArgument(typeName.length() == 4);
    int result = 0;
    for (int i = 0; i < 4; i++) {
      result <<= 8;
      result |= typeName.charAt(i);
    }
    return result;
  }

}
