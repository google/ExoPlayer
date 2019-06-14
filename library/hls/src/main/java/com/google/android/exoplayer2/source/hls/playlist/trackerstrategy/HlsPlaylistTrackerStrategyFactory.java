package com.google.android.exoplayer2.source.hls.playlist.trackerstrategy;

/**
 * Creates new instances of {@link HlsPlaylistTrackerStrategy}.
 */
public interface HlsPlaylistTrackerStrategyFactory {

  /**
   * @return the new {@link HlsPlaylistTrackerStrategy} instance.
   */
  HlsPlaylistTrackerStrategy createHlsPlaylistTrackerStrategy();
}
