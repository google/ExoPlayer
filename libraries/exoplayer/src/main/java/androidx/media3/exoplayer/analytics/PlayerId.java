/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.analytics;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.media.metrics.LogSessionId;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer.Builder;
import java.util.Objects;

/** Identifier for a player instance. */
@UnstableApi
public final class PlayerId {

  /**
   * A player identifier with unset default values that can be used as a placeholder or for testing.
   */
  public static final PlayerId UNSET =
      Util.SDK_INT < 31
          ? new PlayerId(/* playerName= */ "")
          : new PlayerId(LogSessionIdApi31.UNSET, /* playerName= */ "");

  /**
   * A name to identify the player. Use {@link Builder#setName(String)} to set the name, otherwise
   * an empty string is used as the default.
   */
  public final String name;

  @Nullable private final LogSessionIdApi31 logSessionIdApi31;

  /**
   * An object used for equals/hashCode below API 31 or when the MediaMetricsService is unavailable.
   */
  @Nullable private final Object equalityToken;

  /**
   * Creates an instance for API &lt; 31.
   *
   * @param playerName The name of the player, for informational purpose only.
   */
  public PlayerId(String playerName) {
    checkState(Util.SDK_INT < 31);
    this.name = playerName;
    this.logSessionIdApi31 = null;
    equalityToken = new Object();
  }

  /**
   * Creates an instance for API &ge; 31.
   *
   * @param logSessionId The {@link LogSessionId} used for this player.
   * @param playerName The name of the player, for informational purpose only.
   */
  @RequiresApi(31)
  public PlayerId(LogSessionId logSessionId, String playerName) {
    this(new LogSessionIdApi31(logSessionId), playerName);
  }

  private PlayerId(LogSessionIdApi31 logSessionIdApi31, String playerName) {
    this.logSessionIdApi31 = logSessionIdApi31;
    this.name = playerName;
    equalityToken = new Object();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PlayerId)) {
      return false;
    }
    PlayerId playerId = (PlayerId) o;
    return Objects.equals(name, playerId.name)
        && Objects.equals(logSessionIdApi31, playerId.logSessionIdApi31)
        && Objects.equals(equalityToken, playerId.equalityToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, logSessionIdApi31, equalityToken);
  }

  /** Returns the {@link LogSessionId} for this player instance. */
  @RequiresApi(31)
  public LogSessionId getLogSessionId() {
    return checkNotNull(logSessionIdApi31).logSessionId;
  }

  @RequiresApi(31)
  private static final class LogSessionIdApi31 {

    public static final LogSessionIdApi31 UNSET =
        new LogSessionIdApi31(LogSessionId.LOG_SESSION_ID_NONE);

    public final LogSessionId logSessionId;

    public LogSessionIdApi31(LogSessionId logSessionId) {
      this.logSessionId = logSessionId;
    }
  }
}
