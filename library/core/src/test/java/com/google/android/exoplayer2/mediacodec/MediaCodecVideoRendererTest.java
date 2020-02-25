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
package com.google.android.exoplayer2.mediacodec;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
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
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit test for {@link MediaCodecVideoRenderer}. */
@ClosedSource(reason = "See b/147731297.")
@RunWith(AndroidJUnit4.class)
public class MediaCodecVideoRendererTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Format BASIC_MP4_1080 =
      Format.createVideoSampleFormat(
          /* id= */ null,
          /* sampleMimeType= */ MimeTypes.VIDEO_MP4,
          /* codecs= */ null,
          /* bitrate= */ Format.NO_VALUE,
          /* maxInputSize= */ Format.NO_VALUE,
          /* width= */ 1920,
          /* height= */ 1080,
          /* frameRate= */ Format.NO_VALUE,
          /* initializationData= */ null,
          /* rotationDegrees= */ 0,
          /* pixelWidthHeightRatio= */ 1f,
          /* drmInitData= */ null);

  private MediaCodecVideoRenderer mediaCodecVideoRenderer;
  @Mock private VideoRendererEventListener eventListener;

  @Before
  public void setUp() throws Exception {
    MediaCodecSelector mediaCodecSelector =
        new MediaCodecSelector() {
          @Override
          public List<MediaCodecInfo> getDecoderInfos(
              String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) {
            return Collections.singletonList(
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
          }

          @Override
          @Nullable
          public MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
            throw new UnsupportedOperationException();
          }
        };

    mediaCodecVideoRenderer =
        new MediaCodecVideoRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* allowedJoiningTimeMs= */ 0,
            /* eventHandler= */ new Handler(),
            /* eventListener= */ eventListener,
            /* maxDroppedFramesToNotify= */ -1) {
          @Override
          @Capabilities
          protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
              throws DecoderQueryException {
            return RendererCapabilities.create(FORMAT_HANDLED);
          }
        };

    mediaCodecVideoRenderer.handleMessage(
        Renderer.MSG_SET_SURFACE, new Surface(new SurfaceTexture(/* texName= */ 0)));
  }

  @Test
  public void render_sendsVideoSizeChangeWithCurrentFormatValues() throws Exception {
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
        new FakeSampleStream(
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 0,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM),
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
            BASIC_MP4_1080.width,
            BASIC_MP4_1080.height,
            BASIC_MP4_1080.rotationDegrees,
            BASIC_MP4_1080.pixelWidthHeightRatio);
  }

  @Test
  public void render_includingResetPosition_keepsOutputFormatInVideoFrameMetadataListener()
      throws Exception {
    AtomicReference<Format> lastRenderedFrameFormat = new AtomicReference<>();
    VideoFrameMetadataListener frameMetadataListener =
        new VideoFrameMetadataListener() {
          @Override
          public void onVideoFrameAboutToBeRendered(
              long presentationTimeUs,
              long releaseTimeNs,
              Format format,
              @Nullable MediaFormat mediaFormat) {
            lastRenderedFrameFormat.set(format);
          }
        };
    mediaCodecVideoRenderer.handleMessage(
        Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER, frameMetadataListener);
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME));
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
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
        new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME));
    fakeSampleStream.addFakeSampleStreamItem(FakeSampleStreamItem.END_OF_STREAM_ITEM);
    int positionUs = 10;
    do {
      mediaCodecVideoRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 10;
    } while (!mediaCodecVideoRenderer.isEnded());

    assertThat(lastRenderedFrameFormat.get()).isEqualTo(BASIC_MP4_1080);
  }

  @Test
  public void enable_withMayRenderStartOfStream_rendersFirstFrameBeforeStart() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
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
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
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
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME));

    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
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
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);
    mediaCodecVideoRenderer.start();

    boolean replacedStream = false;
    for (int i = 0; i < 200; i += 10) {
      mediaCodecVideoRenderer.render(
          /* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && mediaCodecVideoRenderer.hasReadStreamToEnd()) {
        mediaCodecVideoRenderer.replaceStream(
            new Format[] {BASIC_MP4_1080}, fakeSampleStream2, /* offsetUs= */ 100);
        replacedStream = true;
      }
    }

    verify(eventListener, times(2)).onRenderedFirstFrame(any());
  }

  // TODO: Fix this by not rendering the first frame of a new stream unless started.
  @Ignore
  @Test
  public void replaceStream_whenNotStarted_doesNotRenderFirstFrameOfNewStream() throws Exception {
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            /* format= */ BASIC_MP4_1080,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM);
    mediaCodecVideoRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {BASIC_MP4_1080},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* offsetUs */ 0);

    boolean replacedStream = false;
    for (int i = 0; i < 200; i += 10) {
      mediaCodecVideoRenderer.render(
          /* positionUs= */ i * 10, SystemClock.elapsedRealtime() * 1000);
      if (!replacedStream && mediaCodecVideoRenderer.hasReadStreamToEnd()) {
        mediaCodecVideoRenderer.replaceStream(
            new Format[] {BASIC_MP4_1080}, fakeSampleStream2, /* offsetUs= */ 100);
        replacedStream = true;
      }
    }

    verify(eventListener).onRenderedFirstFrame(any());
  }

  /**
   * Tests that {@link VideoRendererEventListener#onVideoFrameProcessingOffset} is called for every
   * output format change triggered from {@link MediaCodec}.
   *
   * <p>This test is needed to ensure that {@link MediaCodecRenderer#updateOutputFormatForTime}
   * (which updates the value returned from {@link MediaCodecRenderer#getCurrentOutputFormat()} is
   * called after {@link MediaCodecVideoRenderer} handles the {@link MediaCodec MediaCodec's }
   * output format change.
   */
  @Test
  public void onVideoFrameProcessingOffset_isCalledAfterOutputFormatChanges()
      throws ExoPlaybackException {
    Format mp4Uhd = BASIC_MP4_1080.buildUpon().setWidth(3840).setHeight(2160).build();
    byte[] sampleData = new byte[0];
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* format= */ mp4Uhd,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(mp4Uhd),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            new FakeSampleStreamItem(BASIC_MP4_1080),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            new FakeSampleStreamItem(mp4Uhd),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            new FakeSampleStreamItem(BASIC_MP4_1080),
            new FakeSampleStreamItem(sampleData, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM);

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
        .onVideoFrameProcessingOffset(anyLong(), eq(2), eq(BASIC_MP4_1080));
    orderVerifier.verify(eventListener).onVideoFrameProcessingOffset(anyLong(), eq(3), eq(mp4Uhd));
    orderVerifier
        .verify(eventListener)
        .onVideoFrameProcessingOffset(anyLong(), eq(1), eq(BASIC_MP4_1080));
    orderVerifier.verifyNoMoreInteractions();
  }
}
