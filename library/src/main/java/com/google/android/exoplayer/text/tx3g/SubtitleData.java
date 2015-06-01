package com.google.android.exoplayer.text.tx3g;

import java.util.Comparator;

class SubtitleData implements Comparable <SubtitleData>, Comparator<SubtitleData> {

  private long mStartTimePosUs;
  private long mEndTimePosUs;
  private String strSubtitle;

  SubtitleData()
  {
    mStartTimePosUs = 0l;
    mEndTimePosUs = 0l;
    strSubtitle = "";
  }

  protected void setStartTimePos(long time)
  {
    mStartTimePosUs = time;
  }

  protected void setEndTimePos(long time)
  {
    mEndTimePosUs = time;
  }

  protected void setSubtitleText(String text)
  {
    strSubtitle = text;
  }

  protected long getStartTimePos()
  {
    return mStartTimePosUs;
  }

  protected long getEndTimePos()
  {
    return mEndTimePosUs;
  }

  protected String getsubtitleText()
  {
    return strSubtitle;
  }

  @Override
  public int compare(SubtitleData o1 , SubtitleData o2) {
    if (o1.getStartTimePos() < o2.getStartTimePos())
      return -1;
    if (o1.getStartTimePos() > o2.getStartTimePos())
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
