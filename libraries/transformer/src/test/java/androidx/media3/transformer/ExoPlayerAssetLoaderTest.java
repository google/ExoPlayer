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
package androidx.media3.transformer;

import static androidx.media3.test.utils.robolectric.RobolectricUtil.runLooperUntil;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
    AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    AtomicBoolean isAudioOutputFormatSet = new AtomicBoolean();
    AtomicBoolean isVideoOutputFormatSet = new AtomicBoolean();

    AssetLoader.Listener listener =
        new AssetLoader.Listener() {

          private volatile boolean isDurationSet;
          private volatile boolean isTrackCountSet;
          private volatile boolean isAudioTrackAdded;
          private volatile boolean isVideoTrackAdded;

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
          public boolean onTrackAdded(
              Format inputFormat, @AssetLoader.SupportedOutputTypes int supportedOutputTypes) {
            if (!isDurationSet) {
              exceptionRef.set(
                  new IllegalStateException("onTrackAdded() called before onDurationUs()"));
            } else if (!isTrackCountSet) {
              exceptionRef.set(
                  new IllegalStateException("onTrackAdded() called before onTrackCount()"));
            }
            sleep();
            @C.TrackType int trackType = getProcessedTrackType(inputFormat.sampleMimeType);
            if (trackType == C.TRACK_TYPE_AUDIO) {
              isAudioTrackAdded = true;
            } else if (trackType == C.TRACK_TYPE_VIDEO) {
              isVideoTrackAdded = true;
            }
            return false;
          }

          @Override
          public SampleConsumer onOutputFormat(Format format) {
            @C.TrackType int trackType = getProcessedTrackType(format.sampleMimeType);
            boolean isAudio = trackType == C.TRACK_TYPE_AUDIO;
            boolean isVideo = trackType == C.TRACK_TYPE_VIDEO;

            boolean isTrackAdded = (isAudio && isAudioTrackAdded) || (isVideo && isVideoTrackAdded);
            if (!isTrackAdded) {
              exceptionRef.set(
                  new IllegalStateException("onOutputFormat() called before onTrackAdded()"));
            }
            if (isAudio) {
              isAudioOutputFormatSet.set(true);
            } else if (isVideo) {
              isVideoOutputFormatSet.set(true);
            }
            return new FakeSampleConsumer();
          }

          @Override
          public void onError(ExportException e) {
            exceptionRef.set(e);
          }

          private void sleep() {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              exceptionRef.set(e);
            }
          }
        };
    // Use default clock so that messages sent on different threads are not always executed in the
    // order in which they are received.
    Clock clock = Clock.DEFAULT;
    AssetLoader assetLoader = getAssetLoader(listener, clock);

    assetLoader.start();
    runLooperUntil(
        Looper.myLooper(),
        () -> {
          ShadowSystemClock.advanceBy(Duration.ofMillis(10));
          return (isAudioOutputFormatSet.get() && isVideoOutputFormatSet.get())
              || exceptionRef.get() != null;
        });

    assertThat(exceptionRef.get()).isNull();
  }

  private static AssetLoader getAssetLoader(AssetLoader.Listener listener, Clock clock) {
    Context context = ApplicationProvider.getApplicationContext();
    Codec.DecoderFactory decoderFactory = new DefaultDecoderFactory(context);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri("asset:///media/mp4/sample.mp4")).build();
    return new ExoPlayerAssetLoader.Factory(
            context, decoderFactory, /* forceInterpretHdrAsSdr= */ false, clock)
        .createAssetLoader(editedMediaItem, Looper.myLooper(), listener);
  }

  private static final class FakeSampleConsumer implements SampleConsumer {

    @Nullable
    @Override
    public DecoderInputBuffer getInputBuffer() {
      return null;
    }

    @Override
    public boolean queueInputBuffer() {
      return true;
    }
  }
}
