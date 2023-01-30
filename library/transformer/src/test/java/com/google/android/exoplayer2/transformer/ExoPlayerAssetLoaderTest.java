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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link ExoPlayerAssetLoader}. */
@RunWith(AndroidJUnit4.class)
public class ExoPlayerAssetLoaderTest {

  @Test
  public void exoPlayerAssetLoader_callsListenerCallbacksInRightOrder() throws Exception {
    HandlerThread assetLoaderThread = new HandlerThread("AssetLoaderThread");
    assetLoaderThread.start();
    Looper assetLoaderLooper = assetLoaderThread.getLooper();
    AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    AtomicBoolean isTrackAdded = new AtomicBoolean();
    AssetLoader.Listener listener =
        new AssetLoader.Listener() {

          private volatile boolean isDurationSet;
          private volatile boolean isTrackCountSet;

          @Override
          public void onDurationUs(long durationUs) {
            // Sleep to increase the chances of the test failing.
            sleep();
            isDurationSet = true;
          }

          @Override
          public void onTrackCount(int trackCount) {
            // Sleep to increase the chances of the test failing.
            sleep();
            isTrackCountSet = true;
          }

          @Override
          public SampleConsumer onTrackAdded(
              Format format,
              @AssetLoader.SupportedOutputTypes int supportedOutputTypes,
              long streamStartPositionUs,
              long streamOffsetUs) {
            if (!isDurationSet) {
              exceptionRef.set(
                  new IllegalStateException("onTrackAdded() called before onDurationUs()"));
            } else if (!isTrackCountSet) {
              exceptionRef.set(
                  new IllegalStateException("onTrackAdded() called before onTrackCount()"));
            }
            isTrackAdded.set(true);
            return new FakeSampleConsumer();
          }

          @Override
          public void onTransformationError(TransformationException e) {
            exceptionRef.set(e);
          }

          private void sleep() {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              exceptionRef.set(e);
            }
          }
        };
    // Use default clock so that messages sent on different threads are not always executed in the
    // order in which they are received.
    Clock clock = Clock.DEFAULT;
    AssetLoader assetLoader = getAssetLoader(assetLoaderLooper, listener, clock);

    new Handler(assetLoaderLooper).post(assetLoader::start);
    runLooperUntil(
        Looper.myLooper(),
        () -> {
          ShadowSystemClock.advanceBy(Duration.ofMillis(10));
          return isTrackAdded.get() || exceptionRef.get() != null;
        });

    assertThat(exceptionRef.get()).isNull();
  }

  private static AssetLoader getAssetLoader(
      Looper looper, AssetLoader.Listener listener, Clock clock) {
    Context context = ApplicationProvider.getApplicationContext();
    Codec.DecoderFactory decoderFactory = new DefaultDecoderFactory(context);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri("asset:///media/mp4/sample.mp4")).build();
    return new ExoPlayerAssetLoader.Factory(context, decoderFactory, clock)
        .createAssetLoader(editedMediaItem, looper, listener);
  }

  private static final class FakeSampleConsumer implements SampleConsumer {

    @Override
    public boolean expectsDecodedData() {
      return false;
    }

    @Nullable
    @Override
    public DecoderInputBuffer getInputBuffer() {
      return null;
    }

    @Override
    public void queueInputBuffer() {}
  }
}
