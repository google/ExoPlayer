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
package com.google.android.exoplayer2.audio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link MediaCodecAudioRenderer} */
@RunWith(AndroidJUnit4.class)
public class MediaCodecAudioRendererTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Format AUDIO_AAC =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_AAC)
          .setPcmEncoding(C.ENCODING_PCM_16BIT)
          .setChannelCount(2)
          .setSampleRate(44100)
          .setEncoderDelay(100)
          .setEncoderPadding(150)
          .build();

  private MediaCodecAudioRenderer mediaCodecAudioRenderer;

  @Mock private AudioSink audioSink;

  @Before
  public void setUp() throws Exception {
    // audioSink isEnded can always be true because the MediaCodecAudioRenderer isEnded =
    // super.isEnded && audioSink.isEnded.
    when(audioSink.isEnded()).thenReturn(true);

    when(audioSink.handleBuffer(any(), anyLong(), anyInt())).thenReturn(true);

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
        };

    mediaCodecAudioRenderer =
        new MediaCodecAudioRenderer(
            ApplicationProvider.getApplicationContext(),
            mediaCodecSelector,
            /* enableDecoderFallback= */ false,
            /* eventHandler= */ null,
            /* eventListener= */ null,
            audioSink) {
          @Override
          protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
              throws DecoderQueryException {
            return RendererCapabilities.create(FORMAT_HANDLED);
          }
        };
  }

  @Test
  public void render_configuresAudioSink() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            /* format= */ AUDIO_AAC,
            DrmSessionManager.DUMMY,
            /* eventDispatcher= */ null,
            /* firstSampleTimeUs= */ 0,
            /* timeUsIncrement= */ 50,
            new FakeSampleStreamItem(new byte[] {0}, C.BUFFER_FLAG_KEY_FRAME),
            FakeSampleStreamItem.END_OF_STREAM_ITEM);

    mediaCodecAudioRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {AUDIO_AAC},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ false,
        /* offsetUs */ 0);

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
            AUDIO_AAC.pcmEncoding,
            AUDIO_AAC.channelCount,
            AUDIO_AAC.sampleRate,
            /* specifiedBufferSize= */ 0,
            /* outputChannels= */ null,
            AUDIO_AAC.encoderDelay,
            AUDIO_AAC.encoderPadding);
  }
}
