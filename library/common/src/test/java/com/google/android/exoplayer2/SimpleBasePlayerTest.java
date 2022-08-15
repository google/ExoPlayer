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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player.Commands;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.SimpleBasePlayer.State;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SimpleBasePlayer}. */
@RunWith(AndroidJUnit4.class)
public class SimpleBasePlayerTest {

  @Test
  public void allPlayerInterfaceMethods_declaredFinal() throws Exception {
    for (Method method : Player.class.getDeclaredMethods()) {
      assertThat(
              SimpleBasePlayer.class
                      .getMethod(method.getName(), method.getParameterTypes())
                      .getModifiers()
                  & Modifier.FINAL)
          .isNotEqualTo(0);
    }
  }

  @Test
  public void stateBuildUpon_build_isEqual() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .build();

    State newState = state.buildUpon().build();

    assertThat(newState).isEqualTo(state);
    assertThat(newState.hashCode()).isEqualTo(state.hashCode());
  }

  @Test
  public void stateBuilderSetAvailableCommands_setsAvailableCommands() {
    Commands commands =
        new Commands.Builder()
            .addAll(Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_GET_TIMELINE)
            .build();
    State state = new State.Builder().setAvailableCommands(commands).build();

    assertThat(state.availableCommands).isEqualTo(commands);
  }

  @Test
  public void stateBuilderSetPlayWhenReady_setsStatePlayWhenReadyAndReason() {
    State state =
        new State.Builder()
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .build();

    assertThat(state.playWhenReady).isTrue();
    assertThat(state.playWhenReadyChangeReason)
        .isEqualTo(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
  }

  @Test
  public void getterMethods_noOtherMethodCalls_returnCurrentState() {
    Commands commands =
        new Commands.Builder()
            .addAll(Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_GET_TIMELINE)
            .build();
    State state =
        new State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }
        };

    assertThat(player.getApplicationLooper()).isEqualTo(Looper.myLooper());
    assertThat(player.getAvailableCommands()).isEqualTo(commands);
    assertThat(player.getPlayWhenReady()).isTrue();
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void invalidateState_updatesStateAndInformsListeners() {
    State state1 =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .build();
    Commands commands = new Commands.Builder().add(Player.COMMAND_GET_TEXT).build();
    State state2 =
        new State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);
    // Verify state1 is used.
    assertThat(player.getPlayWhenReady()).isTrue();

    returnState2.set(true);
    player.invalidateState();

    // Verify updated state.
    assertThat(player.getAvailableCommands()).isEqualTo(commands);
    assertThat(player.getPlayWhenReady()).isFalse();
    // Verify listener calls.
    verify(listener).onAvailableCommandsChanged(commands);
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void invalidateState_duringAsyncMethodHandling_isIgnored() {
    State state1 =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State state2 =
        state1
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    AtomicReference<State> currentState = new AtomicReference<>(state1);
    SettableFuture<?> asyncFuture = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return currentState.get();
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            return asyncFuture;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);
    // Verify state1 is used trigger async method.
    assertThat(player.getPlayWhenReady()).isTrue();
    player.setPlayWhenReady(true);

    currentState.set(state2);
    player.invalidateState();

    // Verify placeholder state is used (and not state2).
    assertThat(player.getPlayWhenReady()).isTrue();

    // Finish async operation and verify no listeners are informed.
    currentState.set(state1);
    asyncFuture.set(null);

    assertThat(player.getPlayWhenReady()).isTrue();
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void overlappingAsyncMethodHandling_onlyUpdatesStateAfterAllDone() {
    State state1 =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State state2 =
        state1
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    AtomicReference<State> currentState = new AtomicReference<>(state1);
    ArrayList<SettableFuture<?>> asyncFutures = new ArrayList<>();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return currentState.get();
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            SettableFuture<?> future = SettableFuture.create();
            asyncFutures.add(future);
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);
    // Verify state1 is used.
    assertThat(player.getPlayWhenReady()).isTrue();

    // Trigger multiple parallel async calls and set state2 (which should never be used).
    player.setPlayWhenReady(true);
    currentState.set(state2);
    assertThat(player.getPlayWhenReady()).isTrue();
    player.setPlayWhenReady(true);
    assertThat(player.getPlayWhenReady()).isTrue();
    player.setPlayWhenReady(true);
    assertThat(player.getPlayWhenReady()).isTrue();

    // Finish async operation and verify state2 is not used while operations are pending.
    asyncFutures.get(1).set(null);
    assertThat(player.getPlayWhenReady()).isTrue();
    asyncFutures.get(2).set(null);
    assertThat(player.getPlayWhenReady()).isTrue();
    verifyNoMoreInteractions(listener);

    // Finish last async operation and verify updated state and listener calls.
    asyncFutures.get(0).set(null);
    assertThat(player.getPlayWhenReady()).isFalse();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void setPlayWhenReady_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State updatedState =
        state
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    AtomicBoolean stateUpdated = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return stateUpdated.get() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            stateUpdated.set(true);
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    // Intentionally use parameter that doesn't match final result.
    player.setPlayWhenReady(false);

    assertThat(player.getPlayWhenReady()).isTrue();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ true, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void setPlayWhenReady_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State updatedState =
        state
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setPlayWhenReady(true);

    // Verify placeholder state and listener calls.
    assertThat(player.getPlayWhenReady()).isTrue();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ true, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getPlayWhenReady()).isTrue();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setPlayWhenReady_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder().addAllCommands().remove(Player.COMMAND_PLAY_PAUSE).build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setPlayWhenReady(true);

    assertThat(callForwarded.get()).isFalse();
  }
}
