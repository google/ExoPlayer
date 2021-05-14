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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.format;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link MediaCodecVideoRenderer}. */
@RunWith(AndroidJUnit4.class)
public class MediaCodecVideoRendererTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Format VIDEO_H264 =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .build();

  private Looper testMainLooper;
  private Surface surface;
  private MediaCodecVideoRenderer mediaCodecVideoRenderer;
  @Nullable private Format currentOutputFormat;

  @Mock private VideoRendererEventListener eventListener;

  @Before
  public void setUp() throws Exception {
    testMainLooper = Looper.getMainLooper();
    MediaCodecSelector mediaCodecSelector =
        (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) ->
            Collections.singletonList(
                MediaCodecInfo.newInstance(
                    /* name= */ "name",
                    /* mimeType= */ mimeType,
                    /* codecMimeType= */ mimeType,
                    /* capabilities= */ null,
                    /* hardwareAccelerated= */ false,
                    /* softwareOnly= */ true,
                    /* vendor= */ false,
                    /* forceDisableAdaptive= */ false,
                    /* forceSecure= */ false));

    mediaCodecVideoRenderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* eventHandler= */ new Handler(testMainLooper),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1) {
          @Override
          @Capabilities
          protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format) {
            return RendererCapabilities.create(C.FORMAT_HANDLED);
          }

          @Override
          protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat) {
            super.onOutputFormatChanged(format, mediaFormat);
            currentOutputFormat = format;
          }
        };

    surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    mediaCodecVideoRenderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, surface);
  }

  @After
  public void cleanUp() {
    surface.release();
  }

  @Test
  public void render_dropsLateBuffer() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), // First buffer.
                oneByteSample(/* timeUs= */ 50_000), // Late buffer.
                oneByteSample(/* timeUs= */ 100_000), // Last buffer.
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);

    mediaCodecVideoRenderer.start();
    mediaCodecVideoRenderer.render(0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecVideoRenderer.render(40_000, SystemClock.elapsedRealtime() * 1000);
    mediaCodecVideoRenderer.setCurrentStreamFinal();
    int posUs = 80_001; // Ensures buffer will be 30_001us late.
    while (!mediaCodecVideoRenderer.isEnded()) {
      mediaCodecVideoRenderer.render(posUs, SystemClock.elapsedRealtime() * 1000);
      posUs += 40_000;
    }
    shadowOf(testMainLooper).idle();

    verify(eventListener).onDroppedFrames(eq(1), anyLong());
  }

  @Test
  public void render_sendsVideoSizeChangeWithCurrentFormatValues() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.setCurrentStreamFinal();
    mediaCodecVideoRenderer.start();

    int positionUs = 0;
    do {
      mediaCodecVideoRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 10;
    } while (!mediaCodecVideoRenderer.isEnded());
    shadowOf(testMainLooper).idle();

    verify(eventListener)
        .onVideoSizeChanged(
            new VideoSize(
                VIDEO_H264.width,
                VIDEO_H264.height,
                VIDEO_H264.rotationDegrees,
                VIDEO_H264.pixelWidthHeightRatio));
  }

  @Test
  public void
      render_withMultipleQueued_sendsVideoSizeChangedWithCorrectPixelAspectRatioWhenMultipleQueued()
          throws Exception {
    Format pAsp1 = VIDEO_H264.buildUpon().setPixelWidthHeightRatio(1f).build();
    Format pAsp2 = VIDEO_H264.buildUpon().setPixelWidthHeightRatio(2f).build();
    Format pAsp3 = VIDEO_H264.buildUpon().setPixelWidthHeightRatio(3f).build();

    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ pAsp1,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {pAsp1, pAsp2, pAsp3},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();
    mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecVideoRenderer.render(/* positionUs= */ 250, SystemClock.elapsedRealtime() * 1000);

    fakeSampleStream.append(
        ImmutableList.of(
            format(pAsp2),
            oneByteSample(/* timeUs= */ 5_000),
            oneByteSample(/* timeUs= */ 10_000),
            format(pAsp3),
            oneByteSample(/* timeUs= */ 15_000),
            oneByteSample(/* timeUs= */ 20_000),
            END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 5_000);
    mediaCodecVideoRenderer.setCurrentStreamFinal();

    int pos = 500;
    do {
      mediaCodecVideoRenderer.render(/* positionUs= */ pos, SystemClock.elapsedRealtime() * 1000);
      pos += 250;
    } while (!mediaCodecVideoRenderer.isEnded());
    shadowOf(testMainLooper).idle();

    ArgumentCaptor<VideoSize> videoSizesCaptor = ArgumentCaptor.forClass(VideoSize.class);
    verify(eventListener, times(3)).onVideoSizeChanged(videoSizesCaptor.capture());
    assertThat(
            videoSizesCaptor.getAllValues().stream()
                .map(videoSize -> videoSize.pixelWidthHeightRatio)
                .collect(Collectors.toList()))
        .containsExactly(1f, 2f, 3f);
  }

  @Test
  public void render_includingResetPosition_keepsOutputFormatInVideoFrameMetadataListener()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);

    mediaCodecVideoRenderer.start();
    mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecVideoRenderer.resetPosition(0);
    mediaCodecVideoRenderer.setCurrentStreamFinal();
    fakeSampleStream.append(
        ImmutableList.of(
            oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    int positionUs = 10;
    do {
      mediaCodecVideoRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 10;
    } while (!mediaCodecVideoRenderer.isEnded());
    shadowOf(testMainLooper).idle();

    assertThat(currentOutputFormat).isEqualTo(VIDEO_H264);
  }

  @Test
  public void enable_withMayRenderStartOfStream_rendersFirstFrameBeforeStart() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    }
    shadowOf(testMainLooper).idle();

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
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    }
    shadowOf(testMainLooper).idle();

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
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    }
    shadowOf(testMainLooper).idle();

    verify(eventListener).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }

  @Test
  public void replaceStream_rendersFirstFrameOnlyAfterStartPosition() throws Exception {
    ShadowLooper shadowLooper = shadowOf(testMainLooper);
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME),
                END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();

    boolean replacedStream = false;
    for (int i = 0; i <= 10; i++) {
      mediaCodecVideoRenderer.render(
          /* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && mediaCodecVideoRenderer.hasReadStreamToEnd()) {
        mediaCodecVideoRenderer.replaceStream(
            new Format[] {VIDEO_H264},
            fakeSampleStream2,
            /* startPositionUs= */ 100,
            /* offsetUs= */ 100);
        replacedStream = true;
      }
    }

    // Expect only the first frame of the first stream to have been rendered.
    shadowLooper.idle();
    verify(eventListener, times(2))
        .onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }

  @Test
  public void replaceStream_whenNotStarted_doesNotRenderFirstFrameOfNewStream() throws Exception {
    ShadowLooper shadowLooper = shadowOf(testMainLooper);
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs */ 0);

    boolean replacedStream = false;
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(
          /* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && mediaCodecVideoRenderer.hasReadStreamToEnd()) {
        mediaCodecVideoRenderer.replaceStream(
            new Format[] {VIDEO_H264},
            fakeSampleStream2,
            /* startPositionUs= */ 100,
            /* offsetUs= */ 100);
        replacedStream = true;
      }
    }

    shadowLooper.idle();
    verify(eventListener).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());

    // Render to streamOffsetUs and verify the new first frame gets rendered.
    mediaCodecVideoRenderer.render(/* positionUs= */ 100, SystemClock.elapsedRealtime() * 1000);

    shadowLooper.idle();
    verify(eventListener, times(2))
        .onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
  }
}
