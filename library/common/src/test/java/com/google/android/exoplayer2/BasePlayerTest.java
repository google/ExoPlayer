/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.StubPlayer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link BasePlayer}. */
@RunWith(AndroidJUnit4.class)
public class BasePlayerTest {

  @Test
  public void seekTo_withIndexAndPosition_usesCommandSeekToMediaItem() {
    BasePlayer player = spy(new TestBasePlayer());

    player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 4000);

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 2,
            /* positionMs= */ 4000,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekTo_withPosition_usesCommandSeekInCurrentMediaItem() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }
            });

    player.seekTo(/* positionMs= */ 4000);

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 1,
            /* positionMs= */ 4000,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToDefaultPosition_withIndex_usesCommandSeekToMediaItem() {
    BasePlayer player = spy(new TestBasePlayer());

    player.seekToDefaultPosition(/* mediaItemIndex= */ 2);

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 2,
            /* positionMs= */ C.TIME_UNSET,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToDefaultPosition_withoutIndex_usesCommandSeekToDefaultPosition() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }
            });

    player.seekToDefaultPosition();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 1,
            /* positionMs= */ C.TIME_UNSET,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToNext_usesCommandSeekToNext() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }
            });

    player.seekToNext();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 2,
            /* positionMs= */ C.TIME_UNSET,
            Player.COMMAND_SEEK_TO_NEXT,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToNextMediaItem_usesCommandSeekToNextMediaItem() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }
            });

    player.seekToNextMediaItem();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 2,
            /* positionMs= */ C.TIME_UNSET,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekForward_usesCommandSeekForward() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public long getSeekForwardIncrement() {
                return 2000;
              }

              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }

              @Override
              public long getCurrentPosition() {
                return 5000;
              }
            });

    player.seekForward();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 1,
            /* positionMs= */ 7000,
            Player.COMMAND_SEEK_FORWARD,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToPrevious_usesCommandSeekToPrevious() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }

              @Override
              public long getMaxSeekToPreviousPosition() {
                return 4000;
              }

              @Override
              public long getCurrentPosition() {
                return 2000;
              }
            });

    player.seekToPrevious();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 0,
            /* positionMs= */ C.TIME_UNSET,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToPreviousMediaItem_usesCommandSeekToPreviousMediaItem() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }
            });

    player.seekToPreviousMediaItem();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 0,
            /* positionMs= */ C.TIME_UNSET,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekBack_usesCommandSeekBack() {
    BasePlayer player =
        spy(
            new TestBasePlayer() {
              @Override
              public long getSeekBackIncrement() {
                return 2000;
              }

              @Override
              public int getCurrentMediaItemIndex() {
                return 1;
              }

              @Override
              public long getCurrentPosition() {
                return 5000;
              }
            });

    player.seekBack();

    verify(player)
        .seekTo(
            /* mediaItemIndex= */ 1,
            /* positionMs= */ 3000,
            Player.COMMAND_SEEK_BACK,
            /* isRepeatingCurrentItem= */ false);
  }

  private static class TestBasePlayer extends StubPlayer {

    @Override
    public void seekTo(
        int mediaItemIndex,
        long positionMs,
        @Player.Command int seekCommand,
        boolean isRepeatingCurrentItem) {
      // Do nothing.
    }

    @Override
    public long getSeekBackIncrement() {
      return 2000;
    }

    @Override
    public long getSeekForwardIncrement() {
      return 2000;
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
      return 2000;
    }

    @Override
    public Timeline getCurrentTimeline() {
      return new FakeTimeline(/* windowCount= */ 3);
    }

    @Override
    public int getCurrentMediaItemIndex() {
      return 1;
    }

    @Override
    public long getCurrentPosition() {
      return 5000;
    }

    @Override
    public long getDuration() {
      return 20000;
    }

    @Override
    public boolean isPlayingAd() {
      return false;
    }

    @Override
    public int getRepeatMode() {
      return Player.REPEAT_MODE_OFF;
    }

    @Override
    public boolean getShuffleModeEnabled() {
      return false;
    }
  }
}
