package com.google.android.exoplayer2.thumbnail;

import android.net.Uri;

public class ThumbnailDescription {

  private final String id;
  private final Uri uri;
  private final int bitrate;
  private final int rows;
  private final int columns;
  private final long startTimeMs;
  private final long durationMs;
  private final int imageWidth;            // Image width (Pixel)
  private final int imageHeight;           // Image height (Pixel)

  public ThumbnailDescription(String id, Uri uri, int bitrate, int rows, int columns, long startTimeMs, long durationMs, int imageWidth, int imageHeight) {
    this.id = id;
    this.uri = uri;
    this.bitrate = bitrate;
    this.rows = rows;
    this.columns = columns;
    this.startTimeMs = startTimeMs;
    this.durationMs = durationMs;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
  }

  public Uri getUri() {
    return uri;
  }

  public int getBitrate() {
    return bitrate;
  }

  public int getRows() {
    return rows;
  }

  public int getColumns() {
    return columns;
  }

  public long getStartTimeMs() {
    return startTimeMs;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public int getImageWidth() {
    return imageWidth;
  }

  public int getImageHeight() {
    return imageHeight;
  }
}
