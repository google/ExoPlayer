/*
 * Copyright 2023 The Android Open Source Project
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
import static com.google.common.truth.Truth.assertThat;

import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link TextureAssetLoader}. */
@RunWith(AndroidJUnit4.class)
public class TextureAssetLoaderTest {

  @Test
  public void textureAssetLoader_callsListenerCallbacksInRightOrder() throws Exception {
    AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    AtomicBoolean isOutputFormatSet = new AtomicBoolean();
    AssetLoader.Listener listener =
        new AssetLoader.Listener() {

          private volatile boolean isDurationSet;
          private volatile boolean isTrackCountSet;
          private volatile boolean isTrackAdded;

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
            isTrackAdded = true;
            return false;
          }

          @Override
          public SampleConsumer onOutputFormat(Format format) {
            if (!isTrackAdded) {
              exceptionRef.set(
                  new IllegalStateException("onOutputFormat() called before onTrackAdded()"));
            }
            isOutputFormatSet.set(true);
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
    TextureAssetLoader assetLoader = getAssetLoader(listener);

    runTextureAssetLoader(assetLoader);
    runLooperUntil(
        Looper.myLooper(),
        () -> {
          ShadowSystemClock.advanceBy(Duration.ofMillis(10));
          return isOutputFormatSet.get() || exceptionRef.get() != null;
        });

    assertThat(exceptionRef.get()).isNull();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static void runTextureAssetLoader(TextureAssetLoader assetLoader) {
    assetLoader.start();

    Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              assetLoader.queueInputTexture(/* texId= */ 0, /* presentationTimeUs= */ 0);
              assetLoader.signalEndOfVideoInput();
            });
  }

  private static TextureAssetLoader getAssetLoader(AssetLoader.Listener listener) {
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(new MediaItem.Builder().build())
            .setDurationUs(C.MICROS_PER_SECOND)
            .build();
    Format format = new Format.Builder().setWidth(10).setHeight(10).build();
    OnInputFrameProcessedListener frameProcessedListener = (unused, unused2) -> {};
    return new TextureAssetLoader(editedMediaItem, listener, format, frameProcessedListener);
  }

  private static final class FakeSampleConsumer implements SampleConsumer {

    @Override
    public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {}

    @Override
    public @InputResult int queueInputTexture(int texId, long presentationTimeUs) {
      return INPUT_RESULT_SUCCESS;
    }

    @Override
    public void signalEndOfVideoInput() {}
  }
}
