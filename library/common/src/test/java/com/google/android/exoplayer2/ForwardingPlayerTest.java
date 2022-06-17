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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.Player.EVENT_IS_PLAYING_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_MEDIA_ITEM_TRANSITION;
import static com.google.android.exoplayer2.Player.EVENT_TIMELINE_CHANGED;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.StubPlayer;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.FlagSet;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link ForwardingPlayer}. */
@RunWith(AndroidJUnit4.class)
public class ForwardingPlayerTest {

  @Test
  public void addListener_addsForwardingListener() {
    FakePlayer player = new FakePlayer();
    Player.Listener listener1 = mock(Player.Listener.class);
    Player.Listener listener2 = mock(Player.Listener.class);

    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener1);
    // Add listener1 again.
    forwardingPlayer.addListener(listener1);
    forwardingPlayer.addListener(listener2);

    assertThat(player.listeners).hasSize(2);
  }

  @Test
  public void removeListener_removesForwardingListener() {
    FakePlayer player = new FakePlayer();
    Player.Listener listener1 = mock(Player.Listener.class);
    Player.Listener listener2 = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener1);
    forwardingPlayer.addListener(listener2);

    forwardingPlayer.removeListener(listener1);
    assertThat(player.listeners).hasSize(1);
    // Remove same listener again.
    forwardingPlayer.removeListener(listener1);
    assertThat(player.listeners).hasSize(1);
    forwardingPlayer.removeListener(listener2);
    assertThat(player.listeners).isEmpty();
  }

  @Test
  public void onEvents_passesForwardingPlayerAsArgument() {
    FakePlayer player = new FakePlayer();
    Player.Listener listener = mock(Player.Listener.class);
    ForwardingPlayer forwardingPlayer = new ForwardingPlayer(player);
    forwardingPlayer.addListener(listener);
    Player.Listener forwardingListener = player.listeners.iterator().next();

    forwardingListener.onEvents(
        player,
        new Player.Events(
            new FlagSet.Builder()
                .addAll(
                    EVENT_TIMELINE_CHANGED, EVENT_MEDIA_ITEM_TRANSITION, EVENT_IS_PLAYING_CHANGED)
                .build()));

    ArgumentCaptor<Player.Events> eventsArgumentCaptor =
        ArgumentCaptor.forClass(Player.Events.class);
    verify(listener).onEvents(same(forwardingPlayer), eventsArgumentCaptor.capture());
    Player.Events receivedEvents = eventsArgumentCaptor.getValue();
    assertThat(receivedEvents.size()).isEqualTo(3);
    assertThat(receivedEvents.contains(EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(receivedEvents.contains(EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
    assertThat(receivedEvents.contains(EVENT_IS_PLAYING_CHANGED)).isTrue();
  }

  @Test
  public void forwardingPlayer_overridesAllPlayerMethods() throws Exception {
    // Check with reflection that ForwardingPlayer overrides all Player methods.
    List<Method> methods = TestUtil.getPublicMethods(Player.class);
    for (Method method : methods) {
      assertThat(
              ForwardingPlayer.class
                  .getDeclaredMethod(method.getName(), method.getParameterTypes())
                  .getDeclaringClass())
          .isEqualTo(ForwardingPlayer.class);
    }
  }

  @Test
  public void forwardingListener_overridesAllListenerMethods() throws Exception {
    // Check with reflection that ForwardingListener overrides all Listener methods.
    Class<?> forwardingListenerClass = getInnerClass("ForwardingListener");
    List<Method> methods = TestUtil.getPublicMethods(Player.Listener.class);
    for (Method method : methods) {
      assertThat(
              forwardingListenerClass
                  .getMethod(method.getName(), method.getParameterTypes())
                  .getDeclaringClass())
          .isEqualTo(forwardingListenerClass);
    }
  }

  private static Class<?> getInnerClass(String className) {
    for (Class<?> innerClass : ForwardingPlayer.class.getDeclaredClasses()) {
      if (innerClass.getSimpleName().equals(className)) {
        return innerClass;
      }
    }
    throw new IllegalStateException();
  }

  private static class FakePlayer extends StubPlayer {

    private final Set<Listener> listeners = new HashSet<>();

    @Override
    public void addListener(Listener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
      listeners.remove(listener);
    }
  }
}
