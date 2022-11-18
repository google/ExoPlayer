package com.google.android.exoplayer2.demo;

import android.graphics.Bitmap;

public interface ThumbnailProvider {

  public Bitmap getThumbnail(long position);

}
