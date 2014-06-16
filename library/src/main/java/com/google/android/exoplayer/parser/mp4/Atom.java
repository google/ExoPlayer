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
package com.google.android.exoplayer.parser.mp4;

import java.util.ArrayList;
import java.util.List;

/* package */ abstract class Atom {

  public static final int TYPE_avc1 = 0x61766331;
  public static final int TYPE_esds = 0x65736473;
  public static final int TYPE_mdat = 0x6D646174;
  public static final int TYPE_mfhd = 0x6D666864;
  public static final int TYPE_mp4a = 0x6D703461;
  public static final int TYPE_tfdt = 0x74666474;
  public static final int TYPE_tfhd = 0x74666864;
  public static final int TYPE_trex = 0x74726578;
  public static final int TYPE_trun = 0x7472756E;
  public static final int TYPE_sidx = 0x73696478;
  public static final int TYPE_moov = 0x6D6F6F76;
  public static final int TYPE_trak = 0x7472616B;
  public static final int TYPE_mdia = 0x6D646961;
  public static final int TYPE_minf = 0x6D696E66;
  public static final int TYPE_stbl = 0x7374626C;
  public static final int TYPE_avcC = 0x61766343;
  public static final int TYPE_moof = 0x6D6F6F66;
  public static final int TYPE_traf = 0x74726166;
  public static final int TYPE_mvex = 0x6D766578;
  public static final int TYPE_tkhd = 0x746B6864;
  public static final int TYPE_mdhd = 0x6D646864;
  public static final int TYPE_hdlr = 0x68646C72;
  public static final int TYPE_stsd = 0x73747364;
  public static final int TYPE_pssh = 0x70737368;
  public static final int TYPE_sinf = 0x73696E66;
  public static final int TYPE_schm = 0x7363686D;
  public static final int TYPE_schi = 0x73636869;
  public static final int TYPE_tenc = 0x74656E63;
  public static final int TYPE_encv = 0x656E6376;
  public static final int TYPE_enca = 0x656E6361;
  public static final int TYPE_frma = 0x66726D61;
  public static final int TYPE_saiz = 0x7361697A;
  public static final int TYPE_uuid = 0x75756964;

  public final int type;

  Atom(int type) {
    this.type = type;
  }

  public final static class LeafAtom extends Atom {

    private final ParsableByteArray data;

    public LeafAtom(int type, ParsableByteArray data) {
      super(type);
      this.data = data;
    }

    public ParsableByteArray getData() {
      return data;
    }

  }

  public final static class ContainerAtom extends Atom {

    public final ArrayList<Atom> children;

    public ContainerAtom(int type) {
      super(type);
      children = new ArrayList<Atom>();
    }

    public void add(Atom atom) {
      children.add(atom);
    }

    public LeafAtom getLeafAtomOfType(int type) {
      for (int i = 0; i < children.size(); i++) {
        Atom atom = children.get(i);
        if (atom.type == type) {
          return (LeafAtom) atom;
        }
      }
      return null;
    }

    public ContainerAtom getContainerAtomOfType(int type) {
      for (int i = 0; i < children.size(); i++) {
        Atom atom = children.get(i);
        if (atom.type == type) {
          return (ContainerAtom) atom;
        }
      }
      return null;
    }

    public List<Atom> getChildren() {
      return children;
    }

  }

}
