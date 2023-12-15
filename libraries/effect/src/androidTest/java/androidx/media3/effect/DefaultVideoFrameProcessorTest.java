/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link DefaultVideoFrameProcessor}. */
@RunWith(AndroidJUnit4.class)
public class DefaultVideoFrameProcessorTest {

  private static final long INPUT_REGISTRATION_TIMEOUT_MS = 1_000L;

  private DefaultVideoFrameProcessor.@MonotonicNonNull Factory factory;

  @Before
  public void setUp() {
    factory = new DefaultVideoFrameProcessor.Factory.Builder().build();
  }

  @Test
  public void registerInputStream_withBlockingVideoFrameProcessorConfiguration_succeeds()
      throws Exception {
    AtomicReference<Exception> videoFrameProcessingException = new AtomicReference<>();
    CountDownLatch inputStreamRegisteredCountDownLatch = new CountDownLatch(1);
    DefaultVideoFrameProcessor defaultVideoFrameProcessor =
        createDefaultVideoFrameProcessor(
            new VideoFrameProcessor.Listener() {
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  List<Effect> effects,
                  FrameInfo frameInfo) {
                inputStreamRegisteredCountDownLatch.countDown();
              }

              @Override
              public void onOutputSizeChanged(int width, int height) {}

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {}

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
              }

              @Override
              public void onEnded() {}
            });

    CountDownLatch videoFrameProcessorConfigurationCountDownLatch = new CountDownLatch(1);
    // Blocks VideoFrameProcessor configuration.
    defaultVideoFrameProcessor
        .getTaskExecutor()
        .submit(
            () -> {
              try {
                videoFrameProcessorConfigurationCountDownLatch.await();
              } catch (InterruptedException e) {
                throw new VideoFrameProcessingException(e);
              }
            });
    defaultVideoFrameProcessor.registerInputStream(
        VideoFrameProcessor.INPUT_TYPE_BITMAP,
        ImmutableList.of(),
        new FrameInfo.Builder(ColorInfo.SRGB_BT709_FULL, /* width= */ 100, /* height= */ 100)
            .build());

    assertThat(defaultVideoFrameProcessor.getPendingInputFrameCount()).isEqualTo(0);
    // Unblocks configuration.
    videoFrameProcessorConfigurationCountDownLatch.countDown();
    assertThat(
            inputStreamRegisteredCountDownLatch.await(INPUT_REGISTRATION_TIMEOUT_MS, MILLISECONDS))
        .isTrue();
    assertThat(videoFrameProcessingException.get()).isNull();
  }

  @Test
  public void
      registerInputStream_threeTimesConsecutively_onInputStreamRegisteredIsInvokedCorrectly()
          throws Exception {
    AtomicReference<Exception> videoFrameProcessingException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(3);
    Queue<InputStreamInfo> registeredInputStreamInfoWidths = new ConcurrentLinkedQueue<>();
    DefaultVideoFrameProcessor defaultVideoFrameProcessor =
        createDefaultVideoFrameProcessor(
            new VideoFrameProcessor.Listener() {
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  List<Effect> effects,
                  FrameInfo frameInfo) {
                registeredInputStreamInfoWidths.add(
                    new InputStreamInfo(inputType, effects, frameInfo));
                countDownLatch.countDown();
              }

              @Override
              public void onOutputSizeChanged(int width, int height) {}

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {}

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
              }

              @Override
              public void onEnded() {}
            });

    InputStreamInfo stream1 =
        new InputStreamInfo(
            VideoFrameProcessor.INPUT_TYPE_BITMAP,
            ImmutableList.of(),
            new FrameInfo.Builder(ColorInfo.SRGB_BT709_FULL, /* width= */ 100, /* height= */ 100)
                .build());
    InputStreamInfo stream2 =
        new InputStreamInfo(
            VideoFrameProcessor.INPUT_TYPE_BITMAP,
            ImmutableList.of(new Contrast(.5f)),
            new FrameInfo.Builder(ColorInfo.SRGB_BT709_FULL, /* width= */ 200, /* height= */ 200)
                .build());
    InputStreamInfo stream3 =
        new InputStreamInfo(
            VideoFrameProcessor.INPUT_TYPE_BITMAP,
            ImmutableList.of(),
            new FrameInfo.Builder(ColorInfo.SRGB_BT709_FULL, /* width= */ 300, /* height= */ 300)
                .build());

    registerInputStream(defaultVideoFrameProcessor, stream1);
    registerInputStream(defaultVideoFrameProcessor, stream2);
    registerInputStream(defaultVideoFrameProcessor, stream3);

    assertThat(countDownLatch.await(INPUT_REGISTRATION_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(videoFrameProcessingException.get()).isNull();
    assertThat(registeredInputStreamInfoWidths)
        .containsExactly(stream1, stream2, stream3)
        .inOrder();
  }

  private DefaultVideoFrameProcessor createDefaultVideoFrameProcessor(
      VideoFrameProcessor.Listener listener) throws Exception {
    return checkNotNull(factory)
        .create(
            getApplicationContext(),
            DebugViewProvider.NONE,
            /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
            /* renderFramesAutomatically= */ true,
            /* listenerExecutor= */ MoreExecutors.directExecutor(),
            listener);
  }

  private static void registerInputStream(
      DefaultVideoFrameProcessor defaultVideoFrameProcessor, InputStreamInfo inputStreamInfo) {
    defaultVideoFrameProcessor.registerInputStream(
        inputStreamInfo.inputType, inputStreamInfo.effects, inputStreamInfo.frameInfo);
  }

  private static final class InputStreamInfo {
    public final @VideoFrameProcessor.InputType int inputType;
    public final List<Effect> effects;
    public final FrameInfo frameInfo;

    private InputStreamInfo(
        @VideoFrameProcessor.InputType int inputType, List<Effect> effects, FrameInfo frameInfo) {
      this.inputType = inputType;
      this.effects = effects;
      this.frameInfo = frameInfo;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof InputStreamInfo)) {
        return false;
      }
      InputStreamInfo that = (InputStreamInfo) o;
      return inputType == that.inputType
          && Util.areEqual(this.effects, that.effects)
          && Util.areEqual(this.frameInfo, that.frameInfo);
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + inputType;
      result = 31 * result + effects.hashCode();
      result = 31 * result + frameInfo.hashCode();
      return result;
    }
  }
}
