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

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.format;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.annotation.LooperMode;

/** Unit test for {@link MediaCodecVideoRenderer}. */
@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class MediaCodecVideoRendererTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Format VIDEO_H264 =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .build();

  private MediaCodecVideoRenderer mediaCodecVideoRenderer;
  @Nullable private Format currentOutputFormat;

  @Mock private VideoRendererEventListener eventListener;

  @Before
  public void setUp() throws Exception {
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
            /* eventHandler= */ new Handler(),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ 1) {
          @Override
          @Capabilities
          protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
              throws DecoderQueryException {
            return RendererCapabilities.create(FORMAT_HANDLED);
          }

          @Override
          protected void onOutputFormatChanged(Format outputFormat) {
            super.onOutputFormatChanged(outputFormat);
            currentOutputFormat = outputFormat;
          }
        };

    mediaCodecVideoRenderer.handleMessage(
        Renderer.MSG_SET_SURFACE, new Surface(new SurfaceTexture(/* texName= */ 0)));
  }

  @Test
  public void render_dropsLateBuffer() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), // First buffer.
                oneByteSample(/* timeUs= */ 50_000), // Late buffer.
                oneByteSample(/* timeUs= */ 100_000), // Last buffer.
                FakeSampleStreamItem.END_OF_STREAM_ITEM));
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
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

    verify(eventListener).onDroppedFrames(eq(1), anyLong());
  }

  @Test
  public void render_sendsVideoSizeChangeWithCurrentFormatValues() throws Exception {
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                FakeSampleStreamItem.END_OF_STREAM_ITEM)),
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.setCurrentStreamFinal();
    mediaCodecVideoRenderer.start();

    int positionUs = 0;
    do {
      mediaCodecVideoRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 10;
    } while (!mediaCodecVideoRenderer.isEnded());

    verify(eventListener)
        .onVideoSizeChanged(
            VIDEO_H264.width,
            VIDEO_H264.height,
            VIDEO_H264.rotationDegrees,
            VIDEO_H264.pixelWidthHeightRatio);
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
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ pAsp1,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {pAsp1, pAsp2, pAsp3},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();
    mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecVideoRenderer.render(/* positionUs= */ 250, SystemClock.elapsedRealtime() * 1000);

    fakeSampleStream.addFakeSampleStreamItem(format(pAsp2));
    fakeSampleStream.addFakeSampleStreamItem(oneByteSample(/* timeUs= */ 5_000));
    fakeSampleStream.addFakeSampleStreamItem(oneByteSample(/* timeUs= */ 10_000));
    fakeSampleStream.addFakeSampleStreamItem(format(pAsp3));
    fakeSampleStream.addFakeSampleStreamItem(oneByteSample(/* timeUs= */ 15_000));
    fakeSampleStream.addFakeSampleStreamItem(oneByteSample(/* timeUs= */ 20_000));
    fakeSampleStream.addFakeSampleStreamItem(FakeSampleStreamItem.END_OF_STREAM_ITEM);
    mediaCodecVideoRenderer.setCurrentStreamFinal();

    int pos = 500;
    do {
      mediaCodecVideoRenderer.render(/* positionUs= */ pos, SystemClock.elapsedRealtime() * 1000);
      pos += 250;
    } while (!mediaCodecVideoRenderer.isEnded());

    InOrder orderVerifier = inOrder(eventListener);
    orderVerifier.verify(eventListener).onVideoSizeChanged(anyInt(), anyInt(), anyInt(), eq(1f));
    orderVerifier.verify(eventListener).onVideoSizeChanged(anyInt(), anyInt(), anyInt(), eq(2f));
    orderVerifier.verify(eventListener).onVideoSizeChanged(anyInt(), anyInt(), anyInt(), eq(3f));
    orderVerifier.verifyNoMoreInteractions();
  }

  @Test
  public void render_includingResetPosition_keepsOutputFormatInVideoFrameMetadataListener()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);

    mediaCodecVideoRenderer.start();
    mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecVideoRenderer.resetPosition(0);
    mediaCodecVideoRenderer.setCurrentStreamFinal();
    fakeSampleStream.addFakeSampleStreamItem(
        oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME));
    fakeSampleStream.addFakeSampleStreamItem(FakeSampleStreamItem.END_OF_STREAM_ITEM);
    int positionUs = 10;
    do {
      mediaCodecVideoRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 10;
    } while (!mediaCodecVideoRenderer.isEnded());

    assertThat(currentOutputFormat).isEqualTo(VIDEO_H264);
  }

  @Test
  public void enable_withMayRenderStartOfStream_rendersFirstFrameBeforeStart() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    }

    verify(eventListener).onRenderedFirstFrame(any());
  }

  @Test
  public void enable_withoutMayRenderStartOfStream_doesNotRenderFirstFrameBeforeStart()
      throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0)));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* offsetUs */ 0);
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    }

    verify(eventListener, never()).onRenderedFirstFrame(any());
  }

  @Test
  public void enable_withoutMayRenderStartOfStream_rendersFirstFrameAfterStart() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME)));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    }

    verify(eventListener).onRenderedFirstFrame(any());
  }

  @Test
  public void replaceStream_whenStarted_rendersFirstFrameOfNewStream() throws Exception {
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                FakeSampleStreamItem.END_OF_STREAM_ITEM));
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                FakeSampleStreamItem.END_OF_STREAM_ITEM));
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();

    boolean replacedStream = false;
    for (int i = 0; i <= 10; i++) {
      mediaCodecVideoRenderer.render(
          /* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && mediaCodecVideoRenderer.hasReadStreamToEnd()) {
        mediaCodecVideoRenderer.replaceStream(
            new Format[] {VIDEO_H264}, fakeSampleStream2, /* offsetUs= */ 100);
        replacedStream = true;
      }
    }

    verify(eventListener, times(2)).onRenderedFirstFrame(any());
  }

  @Test
  public void replaceStream_whenNotStarted_doesNotRenderFirstFrameOfNewStream() throws Exception {
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                FakeSampleStreamItem.END_OF_STREAM_ITEM));
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ VIDEO_H264,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                FakeSampleStreamItem.END_OF_STREAM_ITEM));
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {VIDEO_H264},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);

    boolean replacedStream = false;
    for (int i = 0; i < 10; i++) {
      mediaCodecVideoRenderer.render(
          /* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && mediaCodecVideoRenderer.hasReadStreamToEnd()) {
        mediaCodecVideoRenderer.replaceStream(
            new Format[] {VIDEO_H264}, fakeSampleStream2, /* offsetUs= */ 100);
        replacedStream = true;
      }
    }

    verify(eventListener).onRenderedFirstFrame(any());

    // Render to streamOffsetUs and verify the new first frame gets rendered.
    mediaCodecVideoRenderer.render(/* positionUs= */ 100, SystemClock.elapsedRealtime() * 1000);

    verify(eventListener, times(2)).onRenderedFirstFrame(any());
  }

  @Test
  public void onVideoFrameProcessingOffset_isCalledAfterOutputFormatChanges()
      throws ExoPlaybackException {
    Format mp4Uhd = VIDEO_H264.buildUpon().setWidth(3840).setHeight(2160).build();
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ mp4Uhd,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                format(VIDEO_H264),
                oneByteSample(/* timeUs= */ 50, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 100),
                format(mp4Uhd),
                oneByteSample(/* timeUs= */ 150, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 200),
                oneByteSample(/* timeUs= */ 250),
                format(VIDEO_H264),
                oneByteSample(/* timeUs= */ 300, C.BUFFER_FLAG_KEY_FRAME),
                FakeSampleStreamItem.END_OF_STREAM_ITEM));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {mp4Uhd},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);

    mediaCodecVideoRenderer.setCurrentStreamFinal();
    mediaCodecVideoRenderer.start();

    int positionUs = 10;
    do {
      mediaCodecVideoRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 10;
    } while (!mediaCodecVideoRenderer.isEnded());
    mediaCodecVideoRenderer.stop();

    InOrder orderVerifier = inOrder(eventListener);
    orderVerifier.verify(eventListener).onVideoFrameProcessingOffset(anyLong(), eq(1), eq(mp4Uhd));
    orderVerifier
        .verify(eventListener)
        .onVideoFrameProcessingOffset(anyLong(), eq(2), eq(VIDEO_H264));
    orderVerifier.verify(eventListener).onVideoFrameProcessingOffset(anyLong(), eq(3), eq(mp4Uhd));
    orderVerifier
        .verify(eventListener)
        .onVideoFrameProcessingOffset(anyLong(), eq(1), eq(VIDEO_H264));
    orderVerifier.verifyNoMoreInteractions();
  }
}
