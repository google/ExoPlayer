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

import static android.view.Display.DEFAULT_DISPLAY;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.format;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.android.exoplayer2.util.Util.msToUs;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.SynchronousMediaCodecAdapter;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDisplay;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

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

  private static final MediaCodecInfo H264_PROFILE8_LEVEL4_HW_MEDIA_CODEC_INFO =
      MediaCodecInfo.newInstance(
          /* name= */ "h264-codec-hw",
          /* mimeType= */ MimeTypes.VIDEO_H264,
          /* codecMimeType= */ MimeTypes.VIDEO_H264,
          /* capabilities= */ createCodecCapabilities(
              CodecProfileLevel.AVCProfileHigh, CodecProfileLevel.AVCLevel4),
          /* hardwareAccelerated= */ true,
          /* softwareOnly= */ false,
          /* vendor= */ false,
          /* forceDisableAdaptive= */ false,
          /* forceSecure= */ false);

  private static final MediaCodecInfo H264_PROFILE8_LEVEL5_SW_MEDIA_CODEC_INFO =
      MediaCodecInfo.newInstance(
          /* name= */ "h264-codec-sw",
          /* mimeType= */ MimeTypes.VIDEO_H264,
          /* codecMimeType= */ MimeTypes.VIDEO_H264,
          /* capabilities= */ createCodecCapabilities(
              CodecProfileLevel.AVCProfileHigh, CodecProfileLevel.AVCLevel5),
          /* hardwareAccelerated= */ false,
          /* softwareOnly= */ true,
          /* vendor= */ false,
          /* forceDisableAdaptive= */ false,
          /* forceSecure= */ false);

  private Looper testMainLooper;
  private Surface surface;
  private MediaCodecVideoRenderer mediaCodecVideoRenderer;
  private MediaCodecSelector mediaCodecSelector;
  @Nullable private Format currentOutputFormat;

  @Mock private VideoRendererEventListener eventListener;

  @Before
  public void setUp() throws Exception {
    testMainLooper = Looper.getMainLooper();
    mediaCodecSelector =
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
          protected @Capabilities int supportsFormat(
              MediaCodecSelector mediaCodecSelector, Format format) {
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
  public void render_withBufferLimitEqualToNumberOfSamples_rendersLastFrameAfterEndOfStream()
      throws Exception {
    ArgumentCaptor<DecoderCounters> argumentDecoderCounters =
        ArgumentCaptor.forClass(DecoderCounters.class);
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), // First buffer.
                oneByteSample(/* timeUs= */ 10_000),
                oneByteSample(/* timeUs= */ 20_000), // Last buffer.
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    // Seek to time after samples.
    fakeSampleStream.seekToUs(30_000, /* allowTimeBeyondBuffer= */ true);
    mediaCodecVideoRenderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            new ForwardingSynchronousMediaCodecAdapterWithBufferLimit.Factory(/* bufferLimit= */ 3),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* enableDecoderFallback= */ false,
            /* eventHandler= */ new Handler(testMainLooper),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1);
    mediaCodecVideoRenderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, surface);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0);

    mediaCodecVideoRenderer.start();
    mediaCodecVideoRenderer.setCurrentStreamFinal();
    mediaCodecVideoRenderer.render(0, SystemClock.elapsedRealtime() * 1000);
    // Call to render should have read all samples up to but not including the END_OF_STREAM_ITEM.
    assertThat(mediaCodecVideoRenderer.hasReadStreamToEnd()).isFalse();
    int posUs = 30_000;
    while (!mediaCodecVideoRenderer.isEnded()) {
      mediaCodecVideoRenderer.render(posUs, SystemClock.elapsedRealtime() * 1000);
      posUs += 40_000;
    }
    shadowOf(testMainLooper).idle();

    verify(eventListener).onRenderedFirstFrame(eq(surface), /* renderTimeMs= */ anyLong());
    verify(eventListener).onVideoEnabled(argumentDecoderCounters.capture());
    assertThat(argumentDecoderCounters.getValue().renderedOutputBufferCount).isEqualTo(1);
    assertThat(argumentDecoderCounters.getValue().skippedOutputBufferCount).isEqualTo(2);
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
    SystemClock.setCurrentTimeMillis(876_000_000);
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
    mediaCodecVideoRenderer.render(/* positionUs= */ 0, msToUs(SystemClock.elapsedRealtime()));
    ShadowSystemClock.advanceBy(10, TimeUnit.MILLISECONDS);
    mediaCodecVideoRenderer.render(/* positionUs= */ 10_000, msToUs(SystemClock.elapsedRealtime()));

    fakeSampleStream.append(
        ImmutableList.of(
            format(pAsp2),
            oneByteSample(/* timeUs= */ 20_000),
            oneByteSample(/* timeUs= */ 40_000),
            format(pAsp3),
            oneByteSample(/* timeUs= */ 60_000),
            oneByteSample(/* timeUs= */ 80_000),
            END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 20_000);
    mediaCodecVideoRenderer.setCurrentStreamFinal();

    int positionUs = 20_000;
    do {
      ShadowSystemClock.advanceBy(10, TimeUnit.MILLISECONDS);
      mediaCodecVideoRenderer.render(positionUs, msToUs(SystemClock.elapsedRealtime()));
      positionUs += 10_000;
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

  @Test
  public void supportsFormat_withDolbyVisionMedia_returnsTrueWhenFallbackToH265orH264Allowed()
      throws Exception {
    // Create Dolby media formats that could fall back to H265 or H264.
    Format formatDvheDtrFallbackToH265 =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.04.01")
            .build();
    Format formatDvheStFallbackToH265 =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.08.01")
            .build();
    Format formatDvavSeFallbackToH264 =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvav.09.01")
            .build();
    Format formatNoFallbackPossible =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvav.01.01")
            .build();
    // Only provide H264 and H265 decoders with codec profiles needed for fallback.
    MediaCodecSelector mediaCodecSelector =
        (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
          switch (mimeType) {
            case MimeTypes.VIDEO_H264:
              CodecCapabilities capabilitiesH264 = new CodecCapabilities();
              capabilitiesH264.profileLevels =
                  new CodecProfileLevel[] {new CodecProfileLevel(), new CodecProfileLevel()};
              capabilitiesH264.profileLevels[0].profile = CodecProfileLevel.AVCProfileBaseline;
              capabilitiesH264.profileLevels[0].level = CodecProfileLevel.AVCLevel42;
              capabilitiesH264.profileLevels[1].profile = CodecProfileLevel.AVCProfileHigh;
              capabilitiesH264.profileLevels[1].level = CodecProfileLevel.AVCLevel42;
              return ImmutableList.of(
                  MediaCodecInfo.newInstance(
                      /* name= */ "h264-codec",
                      /* mimeType= */ mimeType,
                      /* codecMimeType= */ mimeType,
                      /* capabilities= */ capabilitiesH264,
                      /* hardwareAccelerated= */ false,
                      /* softwareOnly= */ true,
                      /* vendor= */ false,
                      /* forceDisableAdaptive= */ false,
                      /* forceSecure= */ false));
            case MimeTypes.VIDEO_H265:
              CodecCapabilities capabilitiesH265 = new CodecCapabilities();
              capabilitiesH265.profileLevels =
                  new CodecProfileLevel[] {new CodecProfileLevel(), new CodecProfileLevel()};
              capabilitiesH265.profileLevels[0].profile = CodecProfileLevel.HEVCProfileMain;
              capabilitiesH265.profileLevels[0].level = CodecProfileLevel.HEVCMainTierLevel41;
              capabilitiesH265.profileLevels[1].profile = CodecProfileLevel.HEVCProfileMain10;
              capabilitiesH265.profileLevels[1].level = CodecProfileLevel.HEVCHighTierLevel51;
              return ImmutableList.of(
                  MediaCodecInfo.newInstance(
                      /* name= */ "h265-codec",
                      /* mimeType= */ mimeType,
                      /* codecMimeType= */ mimeType,
                      /* capabilities= */ capabilitiesH265,
                      /* hardwareAccelerated= */ false,
                      /* softwareOnly= */ true,
                      /* vendor= */ false,
                      /* forceDisableAdaptive= */ false,
                      /* forceSecure= */ false));
            default:
              return ImmutableList.of();
          }
        };
    MediaCodecVideoRenderer renderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* eventHandler= */ new Handler(testMainLooper),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1);
    renderer.init(/* index= */ 0, PlayerId.UNSET);

    @Capabilities
    int capabilitiesDvheDtrFallbackToH265 = renderer.supportsFormat(formatDvheDtrFallbackToH265);
    @Capabilities
    int capabilitiesDvheStFallbackToH265 = renderer.supportsFormat(formatDvheStFallbackToH265);
    @Capabilities
    int capabilitiesDvavSeFallbackToH264 = renderer.supportsFormat(formatDvavSeFallbackToH264);
    @Capabilities
    int capabilitiesNoFallbackPossible = renderer.supportsFormat(formatNoFallbackPossible);

    assertThat(RendererCapabilities.getFormatSupport(capabilitiesDvheDtrFallbackToH265))
        .isEqualTo(C.FORMAT_HANDLED);
    assertThat(RendererCapabilities.getFormatSupport(capabilitiesDvheStFallbackToH265))
        .isEqualTo(C.FORMAT_HANDLED);
    assertThat(RendererCapabilities.getFormatSupport(capabilitiesDvavSeFallbackToH264))
        .isEqualTo(C.FORMAT_HANDLED);
    assertThat(RendererCapabilities.getFormatSupport(capabilitiesNoFallbackPossible))
        .isEqualTo(C.FORMAT_UNSUPPORTED_SUBTYPE);
  }

  @Test
  public void supportsFormat_withDolbyVision_setsDecoderSupportFlagsByDisplayDolbyVisionSupport()
      throws Exception {
    Format formatDvheDtr =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.04.01")
            .build();
    // Provide supporting Dolby Vision and fallback HEVC decoders
    MediaCodecSelector mediaCodecSelector =
        (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
          switch (mimeType) {
            case MimeTypes.VIDEO_DOLBY_VISION:
              {
                CodecCapabilities capabilitiesDolby = new CodecCapabilities();
                capabilitiesDolby.profileLevels = new CodecProfileLevel[] {new CodecProfileLevel()};
                capabilitiesDolby.profileLevels[0].profile =
                    CodecProfileLevel.DolbyVisionProfileDvheDtr;
                capabilitiesDolby.profileLevels[0].level = CodecProfileLevel.DolbyVisionLevelFhd30;
                return ImmutableList.of(
                    MediaCodecInfo.newInstance(
                        /* name= */ "dvhe-codec",
                        /* mimeType= */ mimeType,
                        /* codecMimeType= */ mimeType,
                        /* capabilities= */ capabilitiesDolby,
                        /* hardwareAccelerated= */ true,
                        /* softwareOnly= */ false,
                        /* vendor= */ false,
                        /* forceDisableAdaptive= */ false,
                        /* forceSecure= */ false));
              }
            case MimeTypes.VIDEO_H265:
              {
                CodecCapabilities capabilitiesH265 = new CodecCapabilities();
                capabilitiesH265.profileLevels =
                    new CodecProfileLevel[] {new CodecProfileLevel(), new CodecProfileLevel()};
                capabilitiesH265.profileLevels[0].profile = CodecProfileLevel.HEVCProfileMain;
                capabilitiesH265.profileLevels[0].level = CodecProfileLevel.HEVCMainTierLevel41;
                capabilitiesH265.profileLevels[1].profile = CodecProfileLevel.HEVCProfileMain10;
                capabilitiesH265.profileLevels[1].level = CodecProfileLevel.HEVCHighTierLevel51;
                return ImmutableList.of(
                    MediaCodecInfo.newInstance(
                        /* name= */ "h265-codec",
                        /* mimeType= */ mimeType,
                        /* codecMimeType= */ mimeType,
                        /* capabilities= */ capabilitiesH265,
                        /* hardwareAccelerated= */ true,
                        /* softwareOnly= */ false,
                        /* vendor= */ false,
                        /* forceDisableAdaptive= */ false,
                        /* forceSecure= */ false));
              }
            default:
              return ImmutableList.of();
          }
        };
    MediaCodecVideoRenderer renderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* eventHandler= */ new Handler(testMainLooper),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1);
    renderer.init(/* index= */ 0, PlayerId.UNSET);

    @Capabilities int capabilitiesDvheDtr = renderer.supportsFormat(formatDvheDtr);

    assertThat(RendererCapabilities.getDecoderSupport(capabilitiesDvheDtr))
        .isEqualTo(RendererCapabilities.DECODER_SUPPORT_FALLBACK_MIMETYPE);

    // Set Display to have Dolby Vision support
    Context context = ApplicationProvider.getApplicationContext();
    DisplayManager displayManager =
        (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    Display display = (displayManager != null) ? displayManager.getDisplay(DEFAULT_DISPLAY) : null;
    ShadowDisplay shadowDisplay = Shadows.shadowOf(display);
    int[] hdrCapabilities =
        new int[] {
          Display.HdrCapabilities.HDR_TYPE_HDR10, Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
        };
    shadowDisplay.setDisplayHdrCapabilities(
        display.getDisplayId(),
        /* maxLuminance= */ 100f,
        /* maxAverageLuminance= */ 100f,
        /* minLuminance= */ 100f,
        hdrCapabilities);

    capabilitiesDvheDtr = renderer.supportsFormat(formatDvheDtr);

    assertThat(RendererCapabilities.getDecoderSupport(capabilitiesDvheDtr))
        .isEqualTo(RendererCapabilities.DECODER_SUPPORT_PRIMARY);
  }

  @Test
  public void getDecoderInfo_withNonPerformantHardwareDecoder_returnsHardwareDecoderFirst()
      throws Exception {
    // AVC Format, Profile: 8, Level: 8192
    Format avcFormat =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setCodecs("avc1.64002a")
            .build();
    // Provide hardware and software AVC decoders
    MediaCodecSelector mediaCodecSelector =
        (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
          if (!mimeType.equals(MimeTypes.VIDEO_H264)) {
            return ImmutableList.of();
          }
          // Hardware decoder supports above format functionally but not performantly as
          // it supports MIME type & Profile but not Level
          // Software decoder supports format functionally and peformantly as it supports
          // MIME type, Profile, and Level(assuming resolution/frame rate support too)
          return ImmutableList.of(
              H264_PROFILE8_LEVEL4_HW_MEDIA_CODEC_INFO, H264_PROFILE8_LEVEL5_SW_MEDIA_CODEC_INFO);
        };
    MediaCodecVideoRenderer renderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* eventHandler= */ new Handler(testMainLooper),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1);
    renderer.init(/* index= */ 0, PlayerId.UNSET);

    List<MediaCodecInfo> mediaCodecInfoList =
        renderer.getDecoderInfos(mediaCodecSelector, avcFormat, false);
    @Capabilities int capabilities = renderer.supportsFormat(avcFormat);

    assertThat(mediaCodecInfoList).hasSize(2);
    assertThat(mediaCodecInfoList.get(0).hardwareAccelerated).isTrue();
    assertThat(RendererCapabilities.getFormatSupport(capabilities)).isEqualTo(C.FORMAT_HANDLED);
    assertThat(RendererCapabilities.getDecoderSupport(capabilities))
        .isEqualTo(RendererCapabilities.DECODER_SUPPORT_FALLBACK);
  }

  @Test
  public void getDecoderInfo_softwareDecoderPreferred_returnsSoftwareDecoderFirst()
      throws Exception {
    // AVC Format, Profile: 8, Level: 8192
    Format avcFormat =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setCodecs("avc1.64002a")
            .build();
    // Provide software and hardware AVC decoders
    MediaCodecSelector mediaCodecSelector =
        (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
          if (!mimeType.equals(MimeTypes.VIDEO_H264)) {
            return ImmutableList.of();
          }
          // Hardware decoder supports above format functionally but not performantly as
          // it supports MIME type & Profile but not Level
          // Software decoder supports format functionally and peformantly as it supports
          // MIME type, Profile, and Level(assuming resolution/frame rate support too)
          return ImmutableList.of(
              H264_PROFILE8_LEVEL5_SW_MEDIA_CODEC_INFO, H264_PROFILE8_LEVEL4_HW_MEDIA_CODEC_INFO);
        };
    MediaCodecVideoRenderer renderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* eventHandler= */ new Handler(testMainLooper),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1);
    renderer.init(/* index= */ 0, PlayerId.UNSET);

    List<MediaCodecInfo> mediaCodecInfoList =
        renderer.getDecoderInfos(mediaCodecSelector, avcFormat, false);
    @Capabilities int capabilities = renderer.supportsFormat(avcFormat);

    assertThat(mediaCodecInfoList).hasSize(2);
    assertThat(mediaCodecInfoList.get(0).hardwareAccelerated).isFalse();
    assertThat(RendererCapabilities.getFormatSupport(capabilities)).isEqualTo(C.FORMAT_HANDLED);
    assertThat(RendererCapabilities.getDecoderSupport(capabilities))
        .isEqualTo(RendererCapabilities.DECODER_SUPPORT_PRIMARY);
  }

  private static CodecCapabilities createCodecCapabilities(int profile, int level) {
    CodecCapabilities capabilities = new CodecCapabilities();
    capabilities.profileLevels = new CodecProfileLevel[] {new CodecProfileLevel()};
    capabilities.profileLevels[0].profile = profile;
    capabilities.profileLevels[0].level = level;
    return capabilities;
  }

  @Test
  public void getCodecMaxInputSize_videoH263() {
    MediaCodecInfo codecInfo = createMediaCodecInfo(MimeTypes.VIDEO_H263);

    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_H263, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(230400);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_H263, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(691200);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_H263, 1920, 1080)))
        .isEqualTo(1555200);
  }

  @Test
  public void getCodecMaxInputSize_videoH264() {
    MediaCodecInfo codecInfo = createMediaCodecInfo(MimeTypes.VIDEO_H264);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_H264, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(230400);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_H264, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(691200);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_H264, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(1566720);
  }

  @Test
  public void getCodecMaxInputSize_videoHevc() {
    MediaCodecInfo codecInfo = createMediaCodecInfo(MimeTypes.VIDEO_H265);

    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_H265, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(2097152);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_H265, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(2097152);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_H265, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(2097152);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_H265, /* width= */ 3840, /* height= */ 2160)))
        .isEqualTo(6220800);
  }

  @Test
  public void getCodecMaxInputSize_videoMp4v() {
    MediaCodecInfo codecInfo = createMediaCodecInfo(MimeTypes.VIDEO_MP4V);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_MP4V, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(230400);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_MP4V, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(691200);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_MP4V, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(1555200);
  }

  @Test
  public void getCodecMaxInputSize_videoAv1() {
    MediaCodecInfo codecInfo = createMediaCodecInfo(MimeTypes.VIDEO_AV1);

    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_MP4V, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(230400);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_MP4V, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(691200);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_MP4V, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(1555200);
  }

  @Test
  public void getCodecMaxInputSize_videoVp8() {
    MediaCodecInfo vp8CodecInfo = createMediaCodecInfo(MimeTypes.VIDEO_VP8);

    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                vp8CodecInfo,
                createFormat(MimeTypes.VIDEO_VP8, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(230400);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                vp8CodecInfo,
                createFormat(MimeTypes.VIDEO_VP8, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(691200);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                vp8CodecInfo,
                createFormat(MimeTypes.VIDEO_VP8, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(1555200);
  }

  @Test
  public void getCodecMaxInputSize_dolbyVision_fallBack() {
    MediaCodecInfo dvCodecInfo = createMediaCodecInfo(MimeTypes.VIDEO_DOLBY_VISION);
    int h264MaxSampleSize =
        MediaCodecVideoRenderer.getCodecMaxInputSize(
            createMediaCodecInfo(MimeTypes.VIDEO_H264),
            createFormat(MimeTypes.VIDEO_H264, /* width= */ 1920, /* height= */ 1080));
    int hevcMaxSampleSize =
        MediaCodecVideoRenderer.getCodecMaxInputSize(
            createMediaCodecInfo(MimeTypes.VIDEO_H265),
            createFormat(MimeTypes.VIDEO_H265, /* width= */ 1920, /* height= */ 1080));

    // DV format without codec string fallbacks to HEVC.
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    // DV profiles "00", "01" and "09" fallback to H264.
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.00.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(h264MaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.01.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(h264MaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.09.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(h264MaxSampleSize);
    // DV profiles "02", "03", "04", "05", "06, "07" and "08" fallback to HEVC.
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.02.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.03.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.04.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.05.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.06.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.07.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                dvCodecInfo,
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs("dvhe.08.01")
                    .setWidth(1920)
                    .setHeight(1080)
                    .build()))
        .isEqualTo(hevcMaxSampleSize);
  }

  @Test
  public void getCodecMaxInputSize_videoVp9() {
    MediaCodecInfo codecInfo = createMediaCodecInfo(MimeTypes.VIDEO_VP9);

    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_VP9, /* width= */ 640, /* height= */ 480)))
        .isEqualTo(115200);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo, createFormat(MimeTypes.VIDEO_VP9, /* width= */ 1280, /* height= */ 720)))
        .isEqualTo(345600);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                codecInfo,
                createFormat(MimeTypes.VIDEO_VP9, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(777600);
  }

  @Test
  public void getCodecMaxInputSize_withUnsupportedFormat_returnsNoValue() {
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_MP43),
                createFormat(MimeTypes.VIDEO_MP43, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_MP42),
                createFormat(MimeTypes.VIDEO_MP42, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_MJPEG),
                createFormat(MimeTypes.VIDEO_MJPEG, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_AVI),
                createFormat(MimeTypes.VIDEO_AVI, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_OGG),
                createFormat(MimeTypes.VIDEO_OGG, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_FLV),
                createFormat(MimeTypes.VIDEO_FLV, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_VC1),
                createFormat(MimeTypes.VIDEO_VC1, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_MPEG2),
                createFormat(MimeTypes.VIDEO_MPEG2, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_PS),
                createFormat(MimeTypes.VIDEO_PS, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_MPEG),
                createFormat(MimeTypes.VIDEO_MPEG, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_MP2T),
                createFormat(MimeTypes.VIDEO_MP2T, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_WEBM),
                createFormat(MimeTypes.VIDEO_WEBM, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            MediaCodecVideoRenderer.getCodecMaxInputSize(
                createMediaCodecInfo(MimeTypes.VIDEO_DIVX),
                createFormat(MimeTypes.VIDEO_DIVX, /* width= */ 1920, /* height= */ 1080)))
        .isEqualTo(Format.NO_VALUE);
  }

  private static MediaCodecInfo createMediaCodecInfo(String mimeType) {
    return MediaCodecInfo.newInstance(
        /* name= */ mimeType,
        /* mimeType= */ mimeType,
        /* codecMimeType= */ mimeType,
        /* capabilities= */ new CodecCapabilities(),
        /* hardwareAccelerated= */ true,
        /* softwareOnly= */ false,
        /* vendor= */ true,
        /* forceDisableAdaptive= */ false,
        /* forceSecure= */ false);
  }

  private static Format createFormat(String mimeType, int width, int height) {
    return new Format.Builder()
        .setSampleMimeType(mimeType)
        .setWidth(width)
        .setHeight(height)
        .build();
  }

  private static final class ForwardingSynchronousMediaCodecAdapterWithBufferLimit
      extends ForwardingSynchronousMediaCodecAdapter {
    /** A factory for {@link ForwardingSynchronousMediaCodecAdapterWithBufferLimit} instances. */
    public static final class Factory implements MediaCodecAdapter.Factory {
      private final int bufferLimit;

      Factory(int bufferLimit) {
        this.bufferLimit = bufferLimit;
      }

      @Override
      public MediaCodecAdapter createAdapter(Configuration configuration) throws IOException {
        return new ForwardingSynchronousMediaCodecAdapterWithBufferLimit(
            bufferLimit, new SynchronousMediaCodecAdapter.Factory().createAdapter(configuration));
      }
    }

    private int bufferCounter;

    ForwardingSynchronousMediaCodecAdapterWithBufferLimit(
        int bufferCounter, MediaCodecAdapter adapter) {
      super(adapter);
      this.bufferCounter = bufferCounter;
    }

    @Override
    public int dequeueInputBufferIndex() {
      if (bufferCounter > 0) {
        bufferCounter--;
        return super.dequeueInputBufferIndex();
      }
      return -1;
    }

    @Override
    public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
      int outputIndex = super.dequeueOutputBufferIndex(bufferInfo);
      if (outputIndex > 0) {
        bufferCounter++;
      }
      return outputIndex;
    }
  }

  private abstract static class ForwardingSynchronousMediaCodecAdapter
      implements MediaCodecAdapter {
    private final MediaCodecAdapter adapter;

    ForwardingSynchronousMediaCodecAdapter(MediaCodecAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public int dequeueInputBufferIndex() {
      return adapter.dequeueInputBufferIndex();
    }

    @Override
    public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
      return adapter.dequeueOutputBufferIndex(bufferInfo);
    }

    @Override
    public MediaFormat getOutputFormat() {
      return adapter.getOutputFormat();
    }

    @Nullable
    @Override
    public ByteBuffer getInputBuffer(int index) {
      return adapter.getInputBuffer(index);
    }

    @Nullable
    @Override
    public ByteBuffer getOutputBuffer(int index) {
      return adapter.getOutputBuffer(index);
    }

    @Override
    public void queueInputBuffer(
        int index, int offset, int size, long presentationTimeUs, int flags) {
      adapter.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
    }

    @Override
    public void queueSecureInputBuffer(
        int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
      adapter.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
    }

    @Override
    public void releaseOutputBuffer(int index, boolean render) {
      adapter.releaseOutputBuffer(index, render);
    }

    @Override
    public void releaseOutputBuffer(int index, long renderTimeStampNs) {
      adapter.releaseOutputBuffer(index, renderTimeStampNs);
    }

    @Override
    public void flush() {
      adapter.flush();
    }

    @Override
    public void release() {
      adapter.release();
    }

    @Override
    public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
      adapter.setOnFrameRenderedListener(listener, handler);
    }

    @Override
    public void setOutputSurface(Surface surface) {
      adapter.setOutputSurface(surface);
    }

    @Override
    public void setParameters(Bundle params) {
      adapter.setParameters(params);
    }

    @Override
    public void setVideoScalingMode(int scalingMode) {
      adapter.setVideoScalingMode(scalingMode);
    }

    @Override
    public boolean needsReconfiguration() {
      return adapter.needsReconfiguration();
    }

    @Override
    public PersistableBundle getMetrics() {
      return adapter.getMetrics();
    }
  }
}
