package com.google.android.exoplayer.text.tx3g;

import java.util.Comparator;

class SubtitleData implements Comparable <SubtitleData>, Comparator<SubtitleData> {

  final private long mStartTimePosUs;
  final private String strSubtitle;

  SubtitleData(String text, long startTime)
  {
    this.mStartTimePosUs = startTime;

    this.strSubtitle = text;
  }

  protected long getStartTimePos()
  {
    return mStartTimePosUs;
  }

  protected String getsubtitleText()
  {
    return strSubtitle;
  }

  @Override
  public int compare(SubtitleData lhs , SubtitleData rhs) {
    if (lhs.getStartTimePos() < rhs.getStartTimePos())
      return -1;
    if (lhs.getStartTimePos() > rhs.getStartTimePos())
      return 1;
    return 0;
  }

  @Override
  public int compareTo(SubtitleData another) {
    if (getStartTimePos() < another.getStartTimePos())
      return -1;
    if (getStartTimePos() > another.getStartTimePos())
      return 1;
    return 0;
  }
}
