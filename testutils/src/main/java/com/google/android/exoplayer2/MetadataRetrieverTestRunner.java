/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class to run {@link MetadataRetriever} tests.
 *
 * <p>The tests will be run on a separate thread with a looper.
 */
@ClosedSource(reason = "Not ready yet")
/* package */ final class MetadataRetrieverTestRunner
    implements MetadataRetriever.MediaSourceCallback, MetadataRetriever.MetadataCallback {

  /**
   * Represents callback data for {@link
   * MetadataRetriever.MediaSourceCallback#onTimelineUpdated(Timeline, Object, int)}.
   */
  @AutoValue
  public abstract static class PrepareCallbackData {
    abstract @Nullable Timeline timeline();

    abstract @Nullable Object manifest();

    abstract @TimelineChangeReason int reason();

    /** Creates a new {@link PrepareCallbackData}. */
    public static PrepareCallbackData prepareCallbackData(
        @Nullable Timeline expectedTimeline,
        @Nullable Object expectedManifest,
        @TimelineChangeReason int expectedReason) {
      return new AutoValue_MetadataRetrieverTestRunner_PrepareCallbackData(
          expectedTimeline, expectedManifest, expectedReason);
    }
  }

  /**
   * Represents callback data for {@link
   * MetadataRetriever.MetadataCallback#onMetadataAvailable(TrackGroupArray, Timeline, int, int)}.
   */
  @AutoValue
  public abstract static class MetadataCallbackData {
    abstract @Nullable TrackGroupArray trackGroupArray();

    abstract @Nullable Timeline timeline();

    abstract int windowIndex();

    abstract int periodIndex();

    /** Creates a new {@link MetadataCallbackData}. */
    public static MetadataCallbackData metadataCallbackData(
        @Nullable TrackGroupArray expectedTrackGroupArray,
        @Nullable Timeline expectedTimeline,
        int expectedWindowIndex,
        int expectedPeriodIndex) {
      return new AutoValue_MetadataRetrieverTestRunner_MetadataCallbackData(
          expectedTrackGroupArray, expectedTimeline, expectedWindowIndex, expectedPeriodIndex);
    }
  }

  /** Factory to create the {@link MetadataRetriever} under test. */
  /* package */ interface TestMetadataRetrieverFactory {
    MetadataRetriever createMetadataRetriever(
        Clock clock, Renderer[] renderers, Looper eventLooper);
  }

  private static final TestMetadataRetrieverFactory DEFAULT_TEST_METADATA_RETRIEVER_FACTORY =
      new TestMetadataRetrieverFactory() {
        @Override
        public MetadataRetriever createMetadataRetriever(
            Clock clock, Renderer[] renderers, Looper eventLooper) {
          return new MetadataRetrieverImpl(clock, renderers, eventLooper);
        }
      };

  private static final Renderer[] FAKE_RENDERERS = new Renderer[] {new FakeRenderer()};
  private static final long DEFAULT_TIMEOUT_MS = 50_000;

  private final Handler handler;
  private final HandlerThread testThread;

  private final List<PrepareCallbackData> preparedCallbackData;
  private final List<MetadataCallbackData> metadataCallbackData;
  private final List<Exception> failedQueryExceptions;

  private MetadataRetriever metadataRetriever;

  /**
   * Creates a new test runner, starts its test runner thread and creates a new {@link
   * MetadataRetriever} under test using the default factory.
   *
   * @return The newly created test runner.
   * @throws InterruptedException If the test thread gets interrupted while waiting for the {@link
   *     MetadataRetriever} under test being created.
   */
  public static MetadataRetrieverTestRunner newTestRunner() throws InterruptedException {
    return newTestRunner(DEFAULT_TEST_METADATA_RETRIEVER_FACTORY);
  }

  /**
   * Creates a new test runner, starts its test runner thread and creates a new {@link
   * MetadataRetriever} under test using the given {@link TestMetadataRetrieverFactory}.
   *
   * @param metadataRetrieverFactory The factory used to create the {@link MetadataRetriever} under
   *     test.
   * @return The newly created test runner.
   * @throws InterruptedException If the test thread gets interrupted while waiting for the {@link
   *     MetadataRetriever} under test being created.
   */
  /* package */ static MetadataRetrieverTestRunner newTestRunner(
      TestMetadataRetrieverFactory metadataRetrieverFactory) throws InterruptedException {
    MetadataRetrieverTestRunner metadataRetrieverTestRunner = new MetadataRetrieverTestRunner();
    metadataRetrieverTestRunner.startTestRunnerThreadBlocking(metadataRetrieverFactory);
    return metadataRetrieverTestRunner;
  }

  private MetadataRetrieverTestRunner() {
    testThread = new HandlerThread("Test thread");
    testThread.start();
    handler = new Handler(testThread.getLooper());
    preparedCallbackData = new ArrayList<>();
    metadataCallbackData = new ArrayList<>();
    failedQueryExceptions = new ArrayList<>();
  }

  /** Returns the {@link MetadataRetriever} under-test. */
  public MetadataRetriever getMetadataRetriever() {
    return metadataRetriever;
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to prepare the given media source on the
   * test runner thread, and return immediately.
   *
   * @param mediaSource The {@link MediaSource} to be prepared.
   */
  public void prepareAsync(MediaSource mediaSource) {
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever.prepare(mediaSource, MetadataRetrieverTestRunner.this);
          }
        });
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to prepare the given media source on the
   * test runner thread, and wait until one of the callbacks from {@link
   * MetadataRetriever.MediaSourceCallback} is called, or until the {@link #DEFAULT_TIMEOUT_MS}
   * passed.
   *
   * @param mediaSource The {@link MediaSource} to be prepared.
   * @throws TimeoutException If the test runner did not finish within the specified timeout.
   * @throws InterruptedException If the test thread gets interrupted while waiting.
   */
  public void prepareBlocking(MediaSource mediaSource)
      throws InterruptedException, TimeoutException {
    ConditionVariable callbackReceived = new ConditionVariable();
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever.prepare(
                mediaSource, new UnblockingMediaSourceCallback(callbackReceived));
          }
        });
    if (!callbackReceived.block(DEFAULT_TIMEOUT_MS)) {
      throw new TimeoutException(
          "Test metadata retriever timed out waiting for preparing media source.");
    }
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to call {@link
   * MetadataRetriever#getMetadata(MetadataRetriever.MetadataCallback)} and returns immediately.
   */
  public void getMetadataAsync() {
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever.getMetadata(MetadataRetrieverTestRunner.this);
          }
        });
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to call {@link
   * MetadataRetriever#getMetadata(long, MetadataRetriever.MetadataCallback)} and returns
   * immediately.
   */
  public void getMetadataAsync(long positionMs) {
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever.getMetadata(positionMs, MetadataRetrieverTestRunner.this);
          }
        });
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to call {@link
   * MetadataRetriever#getMetadata(MetadataRetriever.MetadataCallback)} on test runner thread, and
   * wait until one of the callbacks from {@link MetadataRetriever.MetadataCallback} is called, or
   * until the {@link #DEFAULT_TIMEOUT_MS} passed.
   *
   * @throws TimeoutException If the test runner did not finish within the specified timeout.
   * @throws InterruptedException If the test thread gets interrupted while waiting.
   */
  public void getMetadataBlocking() throws InterruptedException, TimeoutException {
    getMetadataBlockingImpl(/* callWithParam= */ false, /* positionMs= */ 0);
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to call {@link
   * MetadataRetriever#getMetadata(long, MetadataRetriever.MetadataCallback)} on test runner thread,
   * and wait until one of the callbacks from {@link MetadataRetriever.MetadataCallback}\ is called,
   * or until the {@link #DEFAULT_TIMEOUT_MS} passed.
   *
   * @throws TimeoutException If the test runner did not finish within the specified timeout.
   * @throws InterruptedException If the test thread gets interrupted while waiting.
   */
  public void getMetadataBlocking(long positionMs) throws InterruptedException, TimeoutException {
    getMetadataBlockingImpl(/* callWithParam= */ true, positionMs);
  }

  /**
   * Instructs the {@link MetadataRetriever} under test to call {@link
   * MetadataRetriever#setWindowIndex(int)} on test runner thread, and wait until it's done, or
   * until the {@link #DEFAULT_TIMEOUT_MS} passed.
   *
   * @throws InterruptedException If the test runner did not finish within the specified timeout.
   */
  public void setWindowIndex(int windowIndex) throws InterruptedException {
    runOnTestThreadBlocking(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever.setWindowIndex(windowIndex);
          }
        });
  }

  /** Releases the {@link MetadataRetriever} under test and stops the test thread. */
  public void release() throws InterruptedException {
    runOnTestThreadBlocking(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever.release();
          }
        });
    handler.removeCallbacksAndMessages(null);
    testThread.quit();
  }

  // Assertions on retriever behavior.

  /**
   * Asserts that the data reported by {@link
   * MetadataRetriever.MediaSourceCallback#onTimelineUpdated(Timeline, Object, int)} are equal to
   * the provided data.
   *
   * @param preparedCallbackData A list of expected {@link PrepareCallbackData}s.
   */
  public void assertPrepareCallbackDataEqual(PrepareCallbackData... preparedCallbackData) {
    assertThat(this.preparedCallbackData).containsExactlyElementsIn(preparedCallbackData).inOrder();
  }

  /**
   * Asserts that the data reported by {@link
   * MetadataRetriever.MetadataCallback#onMetadataAvailable(TrackGroupArray, Timeline, int, int)}
   * are equal to the provided data.
   *
   * @param metadataCallbackData A list of expected {@link MetadataCallbackData}s.
   */
  public void assertMetadataCallbackDataEqual(MetadataCallbackData... metadataCallbackData) {
    assertThat(this.metadataCallbackData).containsExactlyElementsIn(metadataCallbackData).inOrder();
  }

  /** Asserts that no exception occurred during the test. */
  public void assertNoException() {
    assertThat(this.failedQueryExceptions).isEmpty();
  }

  /**
   * Returns list of {@link PrepareCallbackData} that were reported in {@link
   * MetadataRetriever.MediaSourceCallback#onTimelineUpdated(Timeline, Object, int)} in order of
   * occurrence.
   */
  public List<PrepareCallbackData> getPrepareCallbackData() {
    return this.preparedCallbackData;
  }

  /**
   * Returns list of {@link MetadataCallbackData} that were reported in {@link
   * MetadataRetriever.MetadataCallback#onMetadataAvailable(TrackGroupArray, Timeline, int, int)}}
   * in order of occurrence.
   */
  public List<MetadataCallbackData> getMetadataCallbackData() {
    return this.metadataCallbackData;
  }

  /**
   * Returns list of {@link Exception} that were reported in either {@link
   * MetadataRetriever.MediaSourceCallback#onTimelineUnavailable(Exception)} and {@link
   * MetadataRetriever.MetadataCallback#onMetadataUnavailable(Exception)} in order of occurrence.
   */
  public List<Exception> getFailedQueryExceptions() {
    return this.failedQueryExceptions;
  }

  /**
   * Asserts that the {@link MetadataRetriever#getWindowDurationMs()} is equal to the given value.
   */
  public void assertWindowDurationMs(long windowDurationMs) throws InterruptedException {
    AtomicLong actualWindowDurationMs = new AtomicLong();
    runOnTestThreadBlocking(
        new Runnable() {
          @Override
          public void run() {
            actualWindowDurationMs.set(metadataRetriever.getWindowDurationMs());
          }
        });
    assertThat(actualWindowDurationMs.get()).isEqualTo(windowDurationMs);
  }

  // MetadataRetriever.MediaSourceCallback implementation.

  @Override
  public void onTimelineUpdated(Timeline timeline, @Nullable Object manifest, int reason) {
    preparedCallbackData.add(PrepareCallbackData.prepareCallbackData(timeline, manifest, reason));
  }

  @Override
  public void onTimelineUnavailable(Exception exception) {
    failedQueryExceptions.add(exception);
  }

  // MetadataRetriever.MetadataCallback implementation.

  @Override
  public void onMetadataAvailable(
      TrackGroupArray trackGroupArray, Timeline timeline, int windowIndex, int periodIndex) {
    metadataCallbackData.add(
        MetadataCallbackData.metadataCallbackData(
            trackGroupArray, timeline, windowIndex, periodIndex));
  }

  @Override
  public void onMetadataUnavailable(Exception exception) {
    failedQueryExceptions.add(exception);
  }

  /**
   * Starts the test runner on its own thread. This will trigger the creation of the {@link
   * MetadataRetriever}.
   *
   * @param metadataRetrieverFactory The factory to create the {@link MetadataRetriever} under test.
   */
  private void startTestRunnerThreadBlocking(TestMetadataRetrieverFactory metadataRetrieverFactory)
      throws InterruptedException {
    runOnTestThreadBlocking(
        new Runnable() {
          @Override
          public void run() {
            metadataRetriever =
                metadataRetrieverFactory.createMetadataRetriever(
                    Clock.DEFAULT, FAKE_RENDERERS, Looper.myLooper());
          }
        });
  }

  private void runOnTestThreadBlocking(Runnable runnable) throws InterruptedException {
    ConditionVariable conditionVariable = new ConditionVariable();
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            runnable.run();
            conditionVariable.open();
          }
        });
    conditionVariable.block(DEFAULT_TIMEOUT_MS);
  }

  private void getMetadataBlockingImpl(boolean callWithParam, long positionMs)
      throws InterruptedException, TimeoutException {
    ConditionVariable callbackReceived = new ConditionVariable();
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            MetadataRetriever.MetadataCallback unblockingMetadataCallback =
                new UnblockingMetadataCallback(callbackReceived);
            if (callWithParam) {
              metadataRetriever.getMetadata(positionMs, unblockingMetadataCallback);
            } else {
              metadataRetriever.getMetadata(unblockingMetadataCallback);
            }
          }
        });
    if (!callbackReceived.block(DEFAULT_TIMEOUT_MS)) {
      throw new TimeoutException(
          "Test metadata retriever timed out waiting for get metadata callback.");
    }
  }

  /**
   * A {@link MetadataRetriever.MediaSourceCallback} that will unblock a {@link ConditionVariable}
   * when one of the callback is called.
   */
  private class UnblockingMediaSourceCallback implements MetadataRetriever.MediaSourceCallback {
    private final ConditionVariable blockedCondition;

    public UnblockingMediaSourceCallback(ConditionVariable blockedCondition) {
      this.blockedCondition = blockedCondition;
    }

    @Override
    public void onTimelineUpdated(Timeline timeline, @Nullable Object manifest, int reason) {
      MetadataRetrieverTestRunner.this.onTimelineUpdated(timeline, manifest, reason);
      blockedCondition.open();
    }

    @Override
    public void onTimelineUnavailable(Exception exception) {
      MetadataRetrieverTestRunner.this.onTimelineUnavailable(exception);
      blockedCondition.open();
    }
  }

  /**
   * A {@link MetadataRetriever.MetadataCallback} that will unblock a {@link ConditionVariable} when
   * one of the callback is called.
   */
  private final class UnblockingMetadataCallback implements MetadataRetriever.MetadataCallback {
    private final ConditionVariable blockedCondition;

    private UnblockingMetadataCallback(ConditionVariable blockedCondition) {
      this.blockedCondition = blockedCondition;
    }

    @Override
    public void onMetadataAvailable(
        TrackGroupArray trackGroupArray, Timeline timeline, int windowIndex, int periodIndex) {
      MetadataRetrieverTestRunner.this.onMetadataAvailable(
          trackGroupArray, timeline, windowIndex, periodIndex);
      blockedCondition.open();
    }

    @Override
    public void onMetadataUnavailable(Exception exception) {
      MetadataRetrieverTestRunner.this.onMetadataUnavailable(exception);
      blockedCondition.open();
    }
  }
}
