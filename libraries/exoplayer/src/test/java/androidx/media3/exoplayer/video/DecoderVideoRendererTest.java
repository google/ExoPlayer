/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.exoplayer.video;

import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.DecoderException;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.Phaser;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link DecoderVideoRenderer}. */
@RunWith(AndroidJUnit4.class)
public final class DecoderVideoRendererTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Format H264_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .build();

  private Surface surface;
  private DecoderVideoRenderer renderer;
  @Mock private VideoRendererEventListener eventListener;

  @Before
  public void setUp() {
    surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    renderer =
        new DecoderVideoRenderer(
            /* allowedJoiningTimeMs= */ 0,
            new Handler(),
            eventListener,
            /* maxDroppedFramesToNotify= */ -1) {

          private final Phaser inputBuffersInCodecPhaser = new Phaser();
          private @C.VideoOutputMode int outputMode;

          @Override
          public String getName() {
            return "TestVideoRenderer";
          }

          @Override
          public @Capabilities int supportsFormat(Format format) {
            return RendererCapabilities.create(C.FORMAT_HANDLED);
          }

          @Override
          protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
            this.outputMode = outputMode;
          }

          @Override
          protected void renderOutputBufferToSurface(
              VideoDecoderOutputBuffer outputBuffer, Surface surface) {
            // Do nothing.
          }

          @Override
          protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
            // Decoding is done on a background thread we have no control about from the test.
            // Ensure the background calls are predictably serialized by waiting for them to finish:
            //  1. Register queued input buffers here.
            //  2. Deregister the input buffer when it's cleared. If an input buffer is cleared it
            //     will have been fully handled by the decoder.
            //  3. Send a message on the test thread to wait for all currently pending input buffers
            //     to be cleared.
            //  4. The tests need to call ShadowLooper.idleMainThread() to execute the wait message
            //     sent in step (3).
            int currentPhase = inputBuffersInCodecPhaser.register();
            new Handler().post(() -> inputBuffersInCodecPhaser.awaitAdvance(currentPhase));
            super.onQueueInputBuffer(buffer);
          }

          @Override
          protected SimpleDecoder<
                  DecoderInputBuffer,
                  ? extends VideoDecoderOutputBuffer,
                  ? extends DecoderException>
              createDecoder(Format format, @Nullable CryptoConfig cryptoConfig) {
            return new SimpleDecoder<
                DecoderInputBuffer, VideoDecoderOutputBuffer, DecoderException>(
                new DecoderInputBuffer[10], new VideoDecoderOutputBuffer[10]) {
              @Override
              protected DecoderInputBuffer createInputBuffer() {
                return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT) {
                  @Override
                  public void clear() {
                    super.clear();
                    inputBuffersInCodecPhaser.arriveAndDeregister();
                  }
                };
              }

              @Override
              protected VideoDecoderOutputBuffer createOutputBuffer() {
                return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
              }

              @Override
              protected DecoderException createUnexpectedDecodeException(Throwable error) {
                return new DecoderException("error", error);
              }

              @Nullable
              @Override
              protected DecoderException decode(
                  DecoderInputBuffer inputBuffer,
                  VideoDecoderOutputBuffer outputBuffer,
                  boolean reset) {
                outputBuffer.init(inputBuffer.timeUs, outputMode, /* supplementalData= */ null);
                return null;
              }

              @Override
              public String getName() {
                return "TestDecoder";
              }
            };
          }
        };
    renderer.setOutput(surface);
  }

  @After
  public void shutDown() {
    if (renderer.getState() == Renderer.STATE_STARTED) {
      renderer.stop();
    }
    if (renderer.getState() == Renderer.STATE_ENABLED) {
      renderer.disable();
    }
    surface.release();
  }

  @Test
  public void enable_withMayRenderStartOfStream_rendersFirstFrameBeforeStart() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {H264_FORMAT},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0L,
        /* offsetUs */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    for (int i = 0; i < 10; i++) {
      renderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
      // Ensure pending messages are delivered.
      ShadowLooper.idleMainLooper();
    }

    verify(eventListener).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }

  @Test
  public void enable_withoutMayRenderStartOfStream_doesNotRenderFirstFrameBeforeStart()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {H264_FORMAT},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    for (int i = 0; i < 10; i++) {
      renderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
      // Ensure pending messages are delivered.
      ShadowLooper.idleMainLooper();
    }

    verify(eventListener, never()).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }

  @Test
  public void enable_withoutMayRenderStartOfStream_rendersFirstFrameAfterStart() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {H264_FORMAT},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.start();
    for (int i = 0; i < 10; i++) {
      renderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
      // Ensure pending messages are delivered.
      ShadowLooper.idleMainLooper();
    }

    verify(eventListener).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }

  // TODO: Fix rendering of first frame at stream transition.
  @Ignore
  @Test
  public void replaceStream_whenStarted_rendersFirstFrameOfNewStream() throws Exception {
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0), END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    MediaSource.MediaPeriodId mediaPeriodId1 = new MediaSource.MediaPeriodId(new Object());
    MediaSource.MediaPeriodId mediaPeriodId2 = new MediaSource.MediaPeriodId(new Object());
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {H264_FORMAT},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0,
        mediaPeriodId1);
    renderer.start();

    boolean replacedStream = false;
    for (int i = 0; i <= 10; i++) {
      renderer.render(/* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && renderer.hasReadStreamToEnd()) {
        renderer.replaceStream(
            new Format[] {H264_FORMAT},
            fakeSampleStream2,
            /* startPositionUs= */ 100,
            /* offsetUs= */ 100,
            mediaPeriodId2);
        replacedStream = true;
      }
      // Ensure pending messages are delivered.
      ShadowLooper.idleMainLooper();
    }

    verify(eventListener, times(2))
        .onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }

  // TODO: Fix rendering of first frame at stream transition.
  @Ignore
  @Test
  public void replaceStream_whenNotStarted_doesNotRenderFirstFrameOfNewStream() throws Exception {
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ H264_FORMAT,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0), END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    MediaSource.MediaPeriodId mediaPeriodId1 = new MediaSource.MediaPeriodId(new Object());
    MediaSource.MediaPeriodId mediaPeriodId2 = new MediaSource.MediaPeriodId(new Object());
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {H264_FORMAT},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0,
        mediaPeriodId1);

    boolean replacedStream = false;
    for (int i = 0; i < 10; i++) {
      renderer.render(/* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && renderer.hasReadStreamToEnd()) {
        renderer.replaceStream(
            new Format[] {H264_FORMAT},
            fakeSampleStream2,
            /* startPositionUs= */ 100,
            /* offsetUs= */ 100,
            mediaPeriodId2);
        replacedStream = true;
      }
      // Ensure pending messages are delivered.
      ShadowLooper.idleMainLooper();
    }

    verify(eventListener).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());

    // Render to streamOffsetUs and verify the new first frame gets rendered.
    renderer.render(/* positionUs= */ 100, SystemClock.elapsedRealtime() * 1000);

    verify(eventListener, times(2))
        .onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }
}
