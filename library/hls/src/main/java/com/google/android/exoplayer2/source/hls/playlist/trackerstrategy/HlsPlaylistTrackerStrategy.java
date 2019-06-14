package com.google.android.exoplayer2.source.hls.playlist.trackerstrategy;

import android.support.annotation.NonNull;

/**
 * A strategy for calculating the earliest next load time for loading of playlists.
 */
public interface HlsPlaylistTrackerStrategy {

  /**
   * @return the newly calculated earliest next load time for loading of playlists
   */
  long calculateEarliestNextLoadTimeMs(@NonNull HlsPlaylistTrackerStrategyData data);
}
