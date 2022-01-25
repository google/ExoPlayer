package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.C;

public interface IStreamFormat {
  String getMimeType();
  boolean isAllKeyFrames();
  @C.TrackType int getTrackType();
}
