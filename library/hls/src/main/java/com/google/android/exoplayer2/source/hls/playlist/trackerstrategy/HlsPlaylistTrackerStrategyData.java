package com.google.android.exoplayer2.source.hls.playlist.trackerstrategy;

import android.support.annotation.NonNull;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;

/**
 * This class holds the data, needed by the {@link HlsPlaylistTrackerStrategy}.
 */
public class HlsPlaylistTrackerStrategyData {

  private HlsMediaPlaylist playlistSnapshot;
  private HlsMediaPlaylist oldPlaylistSnapShot;
  private long currentTimeMs;

  /**
   * Creates a new instance.
   */
  public HlsPlaylistTrackerStrategyData(
      @NonNull HlsMediaPlaylist playlistSnapshot,
      @NonNull HlsMediaPlaylist oldPlaylistSnapShot,
      long currentTimeMs) {

    this.playlistSnapshot = playlistSnapshot;
    this.oldPlaylistSnapShot = oldPlaylistSnapShot;
    this.currentTimeMs = currentTimeMs;
  }

  /**
   * @return the {@link #playlistSnapshot}
   */
  @SuppressWarnings("WeakerAccess") // Public modifier access needed for external usage.
  public HlsMediaPlaylist getPlaylistSnapshot() {
    return playlistSnapshot;
  }

  /**
   * @return the {@link #oldPlaylistSnapShot}
   */
  @SuppressWarnings("WeakerAccess") // Public modifier access needed for external usage.
  public HlsMediaPlaylist getOldPlaylistSnapShot() {
    return oldPlaylistSnapShot;
  }

  /**
   * @return the {@link #currentTimeMs}
   */
  @SuppressWarnings("WeakerAccess")  // Public modifier access needed for external usage.
  public long getCurrentTimeMs() {
    return currentTimeMs;
  }
}
