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
package androidx.media3.exoplayer.audio;

import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.format;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.sample;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RendererCapabilities.Capabilities;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.annotation.Config;

/** Unit tests for {@link MediaCodecAudioRenderer} */
@RunWith(AndroidJUnit4.class)
public class MediaCodecAudioRendererTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  /** Payload for Ogg ID Header Page in accordance with RFC 7845. */
  private static final byte[] OGG_OPUS_ID_HEADER_PAYLOAD =
      TestUtil.createByteArray(
          0x4F, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64, 0x1, 0x2, 0x38, 0x1, 0x80, 0xBB, 0x0, 0x0,
          0x0, 0x0, 0x0);

  private static final byte[] SAMPLE_ONE_BYTE_WITH_SUPPLEMENTAL_DATA =
      TestUtil.createByteArray(0x0, 0x0, 0x0, 0x1, 0x64, 0xA, 0x2C, 0x2A, 0x0, 0x0, 0x0, 0x0, 0x0);
  private static final Format AUDIO_AAC =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_AAC)
          .setPcmEncoding(C.ENCODING_PCM_16BIT)
          .setChannelCount(2)
          .setSampleRate(44100)
          .setEncoderDelay(100)
          .setEncoderPadding(150)
          .build();
  private static final Format AUDIO_OPUS =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_OPUS)
          .setChannelCount(2)
          .setSampleRate(48000)
          .setInitializationData(ImmutableList.of(OGG_OPUS_ID_HEADER_PAYLOAD))
          .build();
  private static final AudioOffloadSupport AUDIO_OFFLOAD_SUPPORTED_GAPLESS_NOT_SUPPORTED =
      new AudioOffloadSupport.Builder()
          .setIsFormatSupported(true)
          .setIsGaplessSupported(false)
          .build();
  private static final AudioOffloadSupport AUDIO_OFFLOAD_SUPPORTED_GAPLESS_SUPPORTED =
      new AudioOffloadSupport.Builder()
          .setIsFormatSupported(true)
          .setIsGaplessSupported(true)
          .build();
  private static final RendererConfiguration
      RENDERER_CONFIGURATION_OFFLOAD_ENABLED_GAPLESS_REQUIRED =
          new RendererConfiguration(
              AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED, /* tunneling= */ false);

  private MediaCodecAudioRenderer mediaCodecAudioRenderer;
  private MediaCodecSelector mediaCodecSelector;

  @Mock private AudioSink audioSink;
  @Mock private AudioRendererEventListener audioRendererEventListener;
  @Mock private RendererCapabilities.Listener rendererCapabilitiesListener;

  @Before
  public void setUp() throws Exception {
    // audioSink isEnded can always be true because the MediaCodecAudioRenderer isEnded =
    // super.isEnded && audioSink.isEnded.
    when(audioSink.isEnded()).thenReturn(true);
    when(audioSink.handleBuffer(any(), anyLong(), anyInt())).thenReturn(true);
    when(audioSink.supportsFormat(any()))
        .thenAnswer(
            invocation -> {
              Format format = invocation.getArgument(/* index= */ 0, Format.class);
              return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)
                  && format.pcmEncoding == C.ENCODING_PCM_16BIT;
            });
    when(audioSink.getFormatOffloadSupport(any()))
        .thenReturn(AudioOffloadSupport.DEFAULT_UNSUPPORTED);

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

    Handler eventHandler = new Handler(Looper.getMainLooper());

    mediaCodecAudioRenderer =
        new MediaCodecAudioRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* enableDecoderFallback= */ false,
            eventHandler,
            audioRendererEventListener,
            audioSink);
    mediaCodecAudioRenderer.init(/* index= */ 0, PlayerId.UNSET, Clock.DEFAULT);
  }

  @Test
  public void render_configuresAudioSink_afterFormatChange() throws Exception {
    Format changedFormat = AUDIO_AAC.buildUpon().setSampleRate(48_000).setEncoderDelay(400).build();

    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ AUDIO_AAC,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 50, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 100, C.BUFFER_FLAG_KEY_FRAME),
                format(changedFormat),
                oneByteSample(/* timeUs= */ 150, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 200, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 250, C.BUFFER_FLAG_KEY_FRAME),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecAudioRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {AUDIO_AAC, changedFormat},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));

    mediaCodecAudioRenderer.start();
    mediaCodecAudioRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecAudioRenderer.render(/* positionUs= */ 250, SystemClock.elapsedRealtime() * 1000);
    mediaCodecAudioRenderer.setCurrentStreamFinal();

    int positionUs = 500;
    do {
      mediaCodecAudioRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 250;
    } while (!mediaCodecAudioRenderer.isEnded());

    verify(audioSink)
        .configure(
            getAudioSinkFormat(AUDIO_AAC),
            /* specifiedBufferSize= */ 0,
            /* outputChannels= */ null);

    verify(audioSink)
        .configure(
            getAudioSinkFormat(changedFormat),
            /* specifiedBufferSize= */ 0,
            /* outputChannels= */ null);
  }

  @Test
  public void render_configuresAudioSink_afterGaplessFormatChange() throws Exception {
    Format changedFormat =
        AUDIO_AAC.buildUpon().setEncoderDelay(400).setEncoderPadding(232).build();

    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ AUDIO_AAC,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 50, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 100, C.BUFFER_FLAG_KEY_FRAME),
                format(changedFormat),
                oneByteSample(/* timeUs= */ 150, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 200, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 250, C.BUFFER_FLAG_KEY_FRAME),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecAudioRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {AUDIO_AAC, changedFormat},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));

    mediaCodecAudioRenderer.start();
    mediaCodecAudioRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    mediaCodecAudioRenderer.render(/* positionUs= */ 250, SystemClock.elapsedRealtime() * 1000);
    mediaCodecAudioRenderer.setCurrentStreamFinal();

    int positionUs = 500;
    do {
      mediaCodecAudioRenderer.render(positionUs, SystemClock.elapsedRealtime() * 1000);
      positionUs += 250;
    } while (!mediaCodecAudioRenderer.isEnded());

    verify(audioSink)
        .configure(
            getAudioSinkFormat(AUDIO_AAC),
            /* specifiedBufferSize= */ 0,
            /* outputChannels= */ null);

    verify(audioSink)
        .configure(
            getAudioSinkFormat(changedFormat),
            /* specifiedBufferSize= */ 0,
            /* outputChannels= */ null);
  }

  @Test
  public void render_throwsExoPlaybackExceptionJustOnce_whenSet() throws Exception {
    MediaCodecAudioRenderer exceptionThrowingRenderer =
        new MediaCodecAudioRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* eventHandler= */ null,
            /* eventListener= */ null) {
          @Override
          protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat)
              throws ExoPlaybackException {
            super.onOutputFormatChanged(format, mediaFormat);
            if (!format.equals(AUDIO_AAC)) {
              setPendingPlaybackException(
                  ExoPlaybackException.createForRenderer(
                      new AudioSink.ConfigurationException("Test", format),
                      "rendererName",
                      /* rendererIndex= */ 0,
                      format,
                      C.FORMAT_HANDLED,
                      /* isRecoverable= */ false,
                      PlaybackException.ERROR_CODE_UNSPECIFIED));
            }
          }
        };

    Format changedFormat = AUDIO_AAC.buildUpon().setSampleRate(32_000).build();

    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ AUDIO_AAC,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    exceptionThrowingRenderer.init(/* index= */ 0, PlayerId.UNSET, Clock.DEFAULT);
    exceptionThrowingRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {AUDIO_AAC, changedFormat},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));

    exceptionThrowingRenderer.start();
    exceptionThrowingRenderer.render(/* positionUs= */ 0, SystemClock.elapsedRealtime() * 1000);
    exceptionThrowingRenderer.render(/* positionUs= */ 250, SystemClock.elapsedRealtime() * 1000);

    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 32_000);
    // Simulating the exception being thrown when not traceable back to render.
    exceptionThrowingRenderer.onOutputFormatChanged(changedFormat, mediaFormat);

    assertThrows(
        ExoPlaybackException.class,
        () ->
            exceptionThrowingRenderer.render(
                /* positionUs= */ 500, SystemClock.elapsedRealtime() * 1000));

    // Doesn't throw an exception because it's cleared after being thrown in the previous call to
    // render.
    exceptionThrowingRenderer.render(/* positionUs= */ 750, SystemClock.elapsedRealtime() * 1000);
  }

  @Test
  public void
      render_callsAudioRendererEventListener_whenAudioSinkListenerOnAudioSinkErrorIsCalled() {
    final ArgumentCaptor<AudioSink.Listener> listenerCaptor =
        ArgumentCaptor.forClass(AudioSink.Listener.class);
    verify(audioSink, atLeastOnce()).setListener(listenerCaptor.capture());
    AudioSink.Listener audioSinkListener = listenerCaptor.getValue();

    Exception error =
        new AudioSink.WriteException(
            /* errorCode= */ 1, new Format.Builder().build(), /* isRecoverable= */ true);
    audioSinkListener.onAudioSinkError(error);

    shadowOf(Looper.getMainLooper()).idle();
    verify(audioRendererEventListener).onAudioSinkError(error);
  }

  @Test
  public void
      renderer_callsRendererCapabilitiesListener_whenAudioSinkListenerOnAudioCapabilitiesChangedIsCalled() {
    final ArgumentCaptor<AudioSink.Listener> listenerCaptor =
        ArgumentCaptor.forClass(AudioSink.Listener.class);
    verify(audioSink, atLeastOnce()).setListener(listenerCaptor.capture());
    AudioSink.Listener audioSinkListener = listenerCaptor.getValue();
    mediaCodecAudioRenderer.getCapabilities().setListener(rendererCapabilitiesListener);

    audioSinkListener.onAudioCapabilitiesChanged();

    shadowOf(Looper.getMainLooper()).idle();
    verify(rendererCapabilitiesListener).onRendererCapabilitiesChanged(mediaCodecAudioRenderer);
  }

  @Test
  public void render_callsAudioSinkSetOutputStreamOffset_whenReplaceStream() throws Exception {
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            AUDIO_AAC,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            AUDIO_AAC,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_001_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    MediaSource.MediaPeriodId mediaPeriodId = new MediaSource.MediaPeriodId(new Object());
    mediaCodecAudioRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {AUDIO_AAC},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        mediaPeriodId);

    mediaCodecAudioRenderer.start();
    while (!mediaCodecAudioRenderer.hasReadStreamToEnd()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    mediaCodecAudioRenderer.replaceStream(
        new Format[] {AUDIO_AAC},
        fakeSampleStream2,
        /* startPositionUs= */ 1_000_000,
        /* offsetUs= */ 1_000_000,
        mediaPeriodId);
    mediaCodecAudioRenderer.setCurrentStreamFinal();
    while (!mediaCodecAudioRenderer.isEnded()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    InOrder inOrderAudioSink = inOrder(audioSink);
    inOrderAudioSink.verify(audioSink).setOutputStreamOffsetUs(0);
    inOrderAudioSink.verify(audioSink).setOutputStreamOffsetUs(1_000_000);
  }

  @Test
  public void supportsFormat_withEac3JocMediaAndEac3Decoder_returnsTrue() throws Exception {
    Format mediaFormat =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC)
            .setCodecs(MimeTypes.CODEC_E_AC3_JOC)
            .build();
    MediaCodecSelector mediaCodecSelector =
        (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) ->
            !mimeType.equals(MimeTypes.AUDIO_E_AC3)
                ? ImmutableList.of()
                : ImmutableList.of(
                    MediaCodecInfo.newInstance(
                        /* name= */ "eac3-codec",
                        /* mimeType= */ mimeType,
                        /* codecMimeType= */ mimeType,
                        /* capabilities= */ null,
                        /* hardwareAccelerated= */ false,
                        /* softwareOnly= */ true,
                        /* vendor= */ false,
                        /* forceDisableAdaptive= */ false,
                        /* forceSecure= */ false));
    MediaCodecAudioRenderer renderer =
        new MediaCodecAudioRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* enableDecoderFallback= */ false,
            /* eventHandler= */ new Handler(Looper.getMainLooper()),
            audioRendererEventListener,
            audioSink);
    renderer.init(/* index= */ 0, PlayerId.UNSET, Clock.DEFAULT);

    @Capabilities int capabilities = renderer.supportsFormat(mediaFormat);

    assertThat(RendererCapabilities.getFormatSupport(capabilities)).isEqualTo(C.FORMAT_HANDLED);
  }

  @Test
  public void render_withOffloadConfiguredWithoutOffloadSupport_setsOffloadModeDisabled()
      throws Exception {
    when(audioSink.getFormatOffloadSupport(AUDIO_AAC))
        .thenReturn(AudioOffloadSupport.DEFAULT_UNSUPPORTED);
    Format format = AUDIO_AAC.buildUpon().setEncoderDelay(0).setEncoderPadding(0).build();
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            format,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    mediaCodecAudioRenderer.enable(
        RENDERER_CONFIGURATION_OFFLOAD_ENABLED_GAPLESS_REQUIRED,
        new Format[] {format},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    mediaCodecAudioRenderer.start();
    mediaCodecAudioRenderer.setCurrentStreamFinal();

    while (!mediaCodecAudioRenderer.isEnded()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    verify(audioSink).setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED);
  }

  @Test
  public void render_withOffloadConfiguredWithoutGaplessOffloadSupport_setsOffloadModeDisabled()
      throws Exception {
    when(audioSink.getFormatOffloadSupport(AUDIO_AAC))
        .thenReturn(AUDIO_OFFLOAD_SUPPORTED_GAPLESS_NOT_SUPPORTED);
    Format format = AUDIO_AAC.buildUpon().setEncoderDelay(100).setEncoderPadding(100).build();
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            format,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    mediaCodecAudioRenderer.enable(
        RENDERER_CONFIGURATION_OFFLOAD_ENABLED_GAPLESS_REQUIRED,
        new Format[] {format},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    mediaCodecAudioRenderer.start();
    mediaCodecAudioRenderer.setCurrentStreamFinal();

    while (!mediaCodecAudioRenderer.isEnded()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    verify(audioSink).setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED);
  }

  @Test
  public void render_replaceStreamWithOffloadSupport_setsOffloadModeEnabled() throws Exception {
    when(audioSink.getFormatOffloadSupport(any()))
        .thenReturn(AUDIO_OFFLOAD_SUPPORTED_GAPLESS_NOT_SUPPORTED);
    Format format1 = AUDIO_AAC.buildUpon().setEncoderDelay(100).setEncoderPadding(100).build();
    Format format2 = AUDIO_AAC.buildUpon().setEncoderDelay(0).setEncoderPadding(0).build();
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            format1,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            format2,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_001_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    MediaSource.MediaPeriodId mediaPeriodId = new MediaSource.MediaPeriodId(new Object());
    mediaCodecAudioRenderer.enable(
        RENDERER_CONFIGURATION_OFFLOAD_ENABLED_GAPLESS_REQUIRED,
        new Format[] {format1},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        mediaPeriodId);
    mediaCodecAudioRenderer.start();
    while (!mediaCodecAudioRenderer.hasReadStreamToEnd()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    mediaCodecAudioRenderer.replaceStream(
        new Format[] {format2},
        fakeSampleStream2,
        /* startPositionUs= */ 1_000_000,
        /* offsetUs= */ 1_000_000,
        mediaPeriodId);
    mediaCodecAudioRenderer.setCurrentStreamFinal();
    while (!mediaCodecAudioRenderer.isEnded()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    InOrder inOrder = inOrder(audioSink);
    inOrder.verify(audioSink).setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED);
    inOrder.verify(audioSink).setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED);
  }

  @Test
  public void render_replaceStreamWithoutGaplessSupport_setsOffloadModeDisabled() throws Exception {
    when(audioSink.getFormatOffloadSupport(any()))
        .thenReturn(AUDIO_OFFLOAD_SUPPORTED_GAPLESS_NOT_SUPPORTED);
    Format format1 = AUDIO_AAC.buildUpon().setEncoderDelay(0).setEncoderPadding(0).build();
    Format format2 = AUDIO_AAC.buildUpon().setEncoderDelay(100).setEncoderPadding(100).build();
    FakeSampleStream fakeSampleStream1 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            format1,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream1.writeData(/* startPositionUs= */ 0);
    FakeSampleStream fakeSampleStream2 =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            format2,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_001_000),
                END_OF_STREAM_ITEM));
    fakeSampleStream2.writeData(/* startPositionUs= */ 0);
    MediaSource.MediaPeriodId mediaPeriodId1 = new MediaSource.MediaPeriodId(new Object());
    MediaSource.MediaPeriodId mediaPeriodId2 = new MediaSource.MediaPeriodId(new Object());

    mediaCodecAudioRenderer.enable(
        RENDERER_CONFIGURATION_OFFLOAD_ENABLED_GAPLESS_REQUIRED,
        new Format[] {format1},
        fakeSampleStream1,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        mediaPeriodId1);
    mediaCodecAudioRenderer.start();
    while (!mediaCodecAudioRenderer.hasReadStreamToEnd()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }
    verify(audioSink).setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED);

    mediaCodecAudioRenderer.replaceStream(
        new Format[] {format2},
        fakeSampleStream2,
        /* startPositionUs= */ 1_000_000,
        /* offsetUs= */ 1_000_000,
        mediaPeriodId2);
    mediaCodecAudioRenderer.setCurrentStreamFinal();
    while (!mediaCodecAudioRenderer.isEnded()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    verify(audioSink).setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED);
  }

  @Test
  @Config(minSdk = 30)
  public void render_offloadedOpusWithPadding_callsSetOffloadDelayPadding() throws Exception {
    when(audioSink.getFormatOffloadSupport(any()))
        .thenReturn(AUDIO_OFFLOAD_SUPPORTED_GAPLESS_SUPPORTED);
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ AUDIO_OPUS,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 50, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 100, C.BUFFER_FLAG_KEY_FRAME),
                sample(
                    /* timeUs= */ 150,
                    C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA,
                    SAMPLE_ONE_BYTE_WITH_SUPPLEMENTAL_DATA),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);

    mediaCodecAudioRenderer.enable(
        RENDERER_CONFIGURATION_OFFLOAD_ENABLED_GAPLESS_REQUIRED,
        new Format[] {AUDIO_OPUS},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    mediaCodecAudioRenderer.setCurrentStreamFinal();
    while (!mediaCodecAudioRenderer.isEnded()) {
      mediaCodecAudioRenderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    verify(audioSink).setOffloadDelayPadding(/* delayInFrames= */ 312, /* paddingInFrames= */ 132);
  }

  private static Format getAudioSinkFormat(Format inputFormat) {
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setChannelCount(inputFormat.channelCount)
        .setSampleRate(inputFormat.sampleRate)
        .setEncoderDelay(inputFormat.encoderDelay)
        .setEncoderPadding(inputFormat.encoderPadding)
        .build();
  }
}
