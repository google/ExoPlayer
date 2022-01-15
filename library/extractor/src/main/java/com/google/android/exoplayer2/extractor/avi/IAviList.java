package com.google.android.exoplayer2.extractor.avi;

public interface IAviList {
  int LIST = 'L' | ('I' << 8) | ('S' << 16) | ('T' << 24);
  //Header List
  int TYPE_HDRL = 'h' | ('d' << 8) | ('r' << 16) | ('l' << 24);

  int getListType();
}
