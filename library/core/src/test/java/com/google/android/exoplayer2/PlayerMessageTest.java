/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.HandlerThread;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

/** Unit test for {@link PlayerMessage}. */
@RunWith(AndroidJUnit4.class)
public class PlayerMessageTest {

  private static final long TIMEOUT_MS = 10;

  @Mock Clock clock;
  private HandlerThread handlerThread;
  private PlayerMessage message;

  @Before
  public void setUp() {
    initMocks(this);
    PlayerMessage.Sender sender = (message) -> {};
    PlayerMessage.Target target = (messageType, payload) -> {};
    handlerThread = new HandlerThread("TestHandler");
    handlerThread.start();
    message =
        new PlayerMessage(
            sender,
            target,
            Timeline.EMPTY,
            /* defaultWindowIndex= */ 0,
            clock,
            handlerThread.getLooper());
  }

  @After
  public void tearDown() {
    handlerThread.quit();
  }

  @Test
  public void blockUntilDelivered_timesOut() throws Exception {
    when(clock.elapsedRealtime()).thenReturn(0L).thenReturn(TIMEOUT_MS * 2);

    assertThrows(TimeoutException.class, () -> message.send().blockUntilDelivered(TIMEOUT_MS));

    // Ensure blockUntilDelivered() entered the blocking loop.
    verify(clock, Mockito.times(2)).elapsedRealtime();
  }

  @Test
  public void blockUntilDelivered_onAlreadyProcessed_succeeds() throws Exception {
    when(clock.elapsedRealtime()).thenReturn(0L);

    message.send().markAsProcessed(/* isDelivered= */ true);

    assertThat(message.blockUntilDelivered(TIMEOUT_MS)).isTrue();
  }

  @Test
  public void blockUntilDelivered_markAsProcessedWhileBlocked_succeeds() throws Exception {
    message.send();

    // Use a separate Thread to mark the message as processed.
    CountDownLatch prepareLatch = new CountDownLatch(1);
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<Boolean> future =
        executorService.submit(
            () -> {
              prepareLatch.await();
              message.markAsProcessed(true);
              return true;
            });

    when(clock.elapsedRealtime())
        .thenReturn(0L)
        .then(
            (invocation) -> {
              // Signal the background thread to call PlayerMessage#markAsProcessed.
              prepareLatch.countDown();
              return TIMEOUT_MS - 1;
            });

    try {
      assertThat(message.blockUntilDelivered(TIMEOUT_MS)).isTrue();
      // Ensure blockUntilDelivered() entered the blocking loop.
      verify(clock, Mockito.atLeast(2)).elapsedRealtime();
      future.get(1, SECONDS);
    } finally {
      executorService.shutdown();
    }
  }
}
