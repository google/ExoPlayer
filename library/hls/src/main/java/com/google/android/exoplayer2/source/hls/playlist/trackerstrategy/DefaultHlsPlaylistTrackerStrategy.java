package com.google.android.exoplayer2.source.hls.playlist.trackerstrategy;

import android.support.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;

/**
 * Provides the default HLS playlist tracker strategy and associated factory.
 */
public class DefaultHlsPlaylistTrackerStrategy implements HlsPlaylistTrackerStrategy {

  /**
   * Creates new instance of {@link DefaultHlsPlaylistTrackerStrategy}.
   */
  public static class Factory implements HlsPlaylistTrackerStrategyFactory {

    /**
     * @return the new instance of {@link DefaultHlsPlaylistTrackerStrategy}
     */
    @Override
    public HlsPlaylistTrackerStrategy createHlsPlaylistTrackerStrategy() {
      return new DefaultHlsPlaylistTrackerStrategy();
    }
  }

  /**
   * @return the newly calculated earliest next load time for loading of playlists
   */
  @Override
  public long calculateEarliestNextLoadTimeMs(@NonNull HlsPlaylistTrackerStrategyData data) {
    // Do not allow the playlist to load again within the target duration if we obtained a new
    // snapshot, or half the target duration otherwise.
    HlsMediaPlaylist playlistSnapshot = data.getPlaylistSnapshot();
    long targetDurationUs = playlistSnapshot != data.getOldPlaylistSnapShot() ?
        playlistSnapshot.targetDurationUs :
        playlistSnapshot.targetDurationUs / 2;

    return data.getCurrentTimeMs() + C.usToMs(targetDurationUs);
  }
}
