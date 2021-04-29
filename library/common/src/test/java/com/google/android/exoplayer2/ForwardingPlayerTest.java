/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.Player.COMMAND_PLAY_PAUSE;
import static com.google.android.exoplayer2.Player.COMMAND_PREPARE_STOP;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.EVENT_AVAILABLE_COMMANDS_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.StubExoPlayer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link ForwardingPlayer}. */
@RunWith(AndroidJUnit4.class)
public class ForwardingPlayerTest {
  @Test
  public void getAvailableCommands_withDisabledCommands_filtersDisabledCommands() {
    Player player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);

    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_PREPARE_STOP));

    assertThat(forwardingPlayer.getAvailableCommands())
        .isEqualTo(buildCommands(COMMAND_PLAY_PAUSE));
  }

  @Test
  public void getAvailableCommands_playerAvailableCommandsChanged_returnsFreshCommands() {
    FakePlayer player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);

    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_PREPARE_STOP));
    assertThat(forwardingPlayer.getAvailableCommands())
        .isEqualTo(buildCommands(COMMAND_PLAY_PAUSE));
    player.setAvailableCommands(
        buildCommands(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP, COMMAND_SEEK_TO_MEDIA_ITEM));

    assertThat(forwardingPlayer.getAvailableCommands())
        .isEqualTo(buildCommands(COMMAND_PLAY_PAUSE, COMMAND_SEEK_TO_MEDIA_ITEM));
  }

  @Test
  public void isCommandAvailable_withDisabledCommands_filtersDisabledCommands() {
    Player player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);

    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_PREPARE_STOP));

    assertThat(forwardingPlayer.isCommandAvailable(COMMAND_PLAY_PAUSE)).isTrue();
    assertThat(forwardingPlayer.isCommandAvailable(COMMAND_PREPARE_STOP)).isFalse();
  }

  @Test
  public void setDisabledCommands_triggersOnCommandsAvailableChanged() {
    Player player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    Player.Listener listener = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener);

    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_PREPARE_STOP));
    ShadowLooper.idleMainLooper();

    InOrder inOrder = inOrder(listener);
    inOrder.verify(listener).onAvailableCommandsChanged(buildCommands(COMMAND_PLAY_PAUSE));
    inOrder
        .verify(listener)
        .onEvents(
            same(forwardingPlayer), argThat(new EventsMatcher(EVENT_AVAILABLE_COMMANDS_CHANGED)));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void setDisabledCommands_withoutChangingAvailableCommands_noCallbackTriggered() {
    Player player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    Player.Listener listener = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener);

    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_SEEK_TO_MEDIA_ITEM));
    ShadowLooper.idleMainLooper();

    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setDisabledCommands_multipleTimes_availableCommandsUpdated() {
    Player player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);

    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_SEEK_TO_MEDIA_ITEM));
    assertThat(forwardingPlayer.getAvailableCommands())
        .isEqualTo(buildCommands(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP));

    forwardingPlayer.setDisabledCommands(
        buildCommands(COMMAND_PREPARE_STOP, COMMAND_SEEK_TO_MEDIA_ITEM));
    assertThat(forwardingPlayer.getAvailableCommands())
        .isEqualTo(buildCommands(COMMAND_PLAY_PAUSE));
  }

  @Test
  public void onCommandsAvailableChanged_listenerChangesCommandsRecursively_secondCallbackCalled() {
    FakePlayer player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    Player.Listener listener =
        spy(
            new Player.Listener() {
              @Override
              public void onAvailableCommandsChanged(Player.Commands availableCommands) {
                // The callback changes the forwarding player's disabled commands triggering
                // exactly one more callback.
                forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_PREPARE_STOP));
              }
            });
    forwardingPlayer.addListener(listener);

    Player.Commands updatedCommands =
        buildCommands(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP, COMMAND_SEEK_TO_MEDIA_ITEM);
    player.setAvailableCommands(updatedCommands);
    player.forwardingListener.onAvailableCommandsChanged(updatedCommands);
    ShadowLooper.idleMainLooper();

    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onAvailableCommandsChanged(
            buildCommands(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP, COMMAND_SEEK_TO_MEDIA_ITEM));
    inOrder
        .verify(listener)
        .onAvailableCommandsChanged(buildCommands(COMMAND_PLAY_PAUSE, COMMAND_SEEK_TO_MEDIA_ITEM));
    inOrder
        .verify(listener)
        .onEvents(
            same(forwardingPlayer), argThat(new EventsMatcher(EVENT_AVAILABLE_COMMANDS_CHANGED)));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void
      interceptingOnAvailableCommandsChanged_withDisabledCommands_filtersDisabledCommands() {
    FakePlayer player = new FakePlayer();
    Player.Listener listener = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener);

    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_PREPARE_STOP));
    ShadowLooper.idleMainLooper();
    // Setting the disabled commands did not affect the available commands, hence no callback was
    // triggered.
    verifyNoMoreInteractions(listener);

    // The wrapped player advertises new available commands.
    Player.Commands updatedCommands = buildCommands(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    player.setAvailableCommands(updatedCommands);
    player.forwardingListener.onAvailableCommandsChanged(updatedCommands);
    ShadowLooper.idleMainLooper();
    verify(listener).onAvailableCommandsChanged(buildCommands(COMMAND_PLAY_PAUSE));
    verify(listener)
        .onEvents(
            same(forwardingPlayer), argThat(new EventsMatcher(EVENT_AVAILABLE_COMMANDS_CHANGED)));
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void
      interceptingOnAvailableCommandsChanged_withDisabledCommandsButAvailableCommandsNotChanged_doesNotForwardCallback() {
    FakePlayer player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    Player.Listener listener = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener);

    // Disable commands that do not affect the available commands.
    forwardingPlayer.setDisabledCommands(buildCommands(COMMAND_SEEK_TO_MEDIA_ITEM));
    ShadowLooper.idleMainLooper();
    verifyNoMoreInteractions(listener);

    // The wrapped player advertises new available commands which, after filtering the disabled
    // commands, do not change the available commands.
    Player.Commands updatedCommands =
        buildCommands(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP, COMMAND_SEEK_TO_MEDIA_ITEM);
    player.setAvailableCommands(updatedCommands);
    player.forwardingListener.onAvailableCommandsChanged(updatedCommands);
    ShadowLooper.idleMainLooper();

    verifyNoMoreInteractions(listener);
  }

  @Test
  public void removeListener_removesListenerFromPlayer() {
    FakePlayer player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    Player.Listener listener = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);

    forwardingPlayer.addListener(listener);
    assertThat(player.forwardingListener).isNotNull();
    forwardingPlayer.removeListener(listener);
    assertThat(player.forwardingListener).isNull();
  }

  @Test
  public void addEventListener_forwardsEventListenerEvents() {
    FakePlayer player = new FakePlayer(COMMAND_PLAY_PAUSE, COMMAND_PREPARE_STOP);
    Player.EventListener eventListener = mock(Player.EventListener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);

    forwardingPlayer.addListener(eventListener);
    player.forwardingListener.onPlaybackStateChanged(STATE_READY);
    ShadowLooper.idleMainLooper();

    InOrder inOrder = inOrder(eventListener);
    inOrder.verify(eventListener).onPlaybackStateChanged(STATE_READY);
    inOrder
        .verify(eventListener)
        .onEvents(same(forwardingPlayer), argThat(new EventsMatcher(EVENT_PLAYBACK_STATE_CHANGED)));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void forwardingListener_overridesAllListenerMethods() throws Exception {
    // Check with reflection that ForwardingListener in ForwardingPlayer overrides all Listener
    // methods.
    Class<?> forwardingListenerClass = getNestedClass("ForwardingListener");
    List<Method> publicListenerMethods = getPublicMethods(Player.Listener.class);
    for (Method method : publicListenerMethods) {
      assertThat(
              forwardingListenerClass.getDeclaredMethod(
                  method.getName(), method.getParameterTypes()))
          .isNotNull();
    }
  }

  @Test
  public void eventListenerWrapper_overridesAllEventListenerMethods() throws Exception {
    // Check with reflection that EventListenerWrapper in ForwardingPlayer overrides all
    // EventListener methods.
    Class<?> listenerWrapperClass = getNestedClass("EventListenerWrapper");
    List<Method> publicListenerMethods = getPublicMethods(Player.EventListener.class);
    for (Method method : publicListenerMethods) {
      assertThat(
              listenerWrapperClass.getDeclaredMethod(method.getName(), method.getParameterTypes()))
          .isNotNull();
    }
  }

  private static class FakePlayer extends StubExoPlayer {
    private Commands availableCommands;
    /**
     * Supports up to 1 registered listener, named deliberately forwardingListener to emphasize its
     * purpose.
     */
    @Nullable private Listener forwardingListener;

    public FakePlayer() {
      this.availableCommands = Commands.EMPTY;
    }

    public FakePlayer(@Command int... commands) {
      this.availableCommands = new Commands.Builder().addAll(commands).build();
    }

    @Override
    public void addListener(Listener listener) {
      checkState(this.forwardingListener == null);
      this.forwardingListener = listener;
    }

    @Override
    public void removeListener(Listener listener) {
      checkState(this.forwardingListener.equals(listener));
      this.forwardingListener = null;
    }

    @Override
    public Commands getAvailableCommands() {
      return availableCommands;
    }

    @Override
    public Looper getApplicationLooper() {
      return Looper.getMainLooper();
    }

    public void setAvailableCommands(Commands availableCommands) {
      this.availableCommands = availableCommands;
    }
  }

  private static Player.Commands buildCommands(@Player.Command int... commands) {
    return new Player.Commands.Builder().addAll(commands).build();
  }

  private Class<?> getNestedClass(String className) {
    for (Class<?> declaredClass : ForwardingPlayer.class.getDeclaredClasses()) {
      if (declaredClass.getSimpleName().equals(className)) {
        return declaredClass;
      }
    }
    throw new IllegalStateException();
  }

  private static class EventsMatcher implements ArgumentMatcher<Player.Events> {
    private final int[] events;

    private EventsMatcher(int... events) {
      this.events = events;
    }

    @Override
    public boolean matches(Player.Events argument) {
      if (events.length != argument.size()) {
        return false;
      }
      for (int event : events) {
        if (!argument.contains(event)) {
          return false;
        }
      }
      return true;
    }
  }

  /** Returns all the methods of Java interface. */
  private static List<Method> getPublicMethods(Class<?> anInterface) {
    assertThat(anInterface.isInterface()).isTrue();
    // Run a BFS over all extended interfaces to inspect them all.
    Queue<Class<?>> interfacesQueue = new ArrayDeque<>();
    interfacesQueue.add(anInterface);
    Set<Class<?>> interfaces = new HashSet<>();
    while (!interfacesQueue.isEmpty()) {
      Class<?> currentInterface = interfacesQueue.remove();
      if (interfaces.add(currentInterface)) {
        Collections.addAll(interfacesQueue, currentInterface.getInterfaces());
      }
    }

    List<Method> list = new ArrayList<>();
    for (Class<?> currentInterface : interfaces) {
      for (Method method : currentInterface.getDeclaredMethods()) {
        if (Modifier.isPublic(method.getModifiers())) {
          list.add(method);
        }
      }
    }

    return list;
  }
}
