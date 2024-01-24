/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.smoothstreaming;

import static com.google.android.exoplayer2.source.smoothstreaming.SsTestUtils.createSsManifest;
import static com.google.android.exoplayer2.source.smoothstreaming.SsTestUtils.createStreamElement;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts.FilterableManifestMediaPeriodFactory;
import com.google.android.exoplayer2.text.DefaultSubtitleParserFactory;
import com.google.android.exoplayer2.text.SubtitleParser;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SsMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public class SsMediaPeriodTest {

  @Test
  public void getSteamKeys_isCompatibleWithSsManifestFilter() {
    SsManifest testManifest =
        createSsManifest(
            createStreamElement(
                /* name= */ "video",
                C.TRACK_TYPE_VIDEO,
                createVideoFormat(/* bitrate= */ 200000),
                createVideoFormat(/* bitrate= */ 400000),
                createVideoFormat(/* bitrate= */ 800000)),
            createStreamElement(
                /* name= */ "audio",
                C.TRACK_TYPE_AUDIO,
                createAudioFormat(/* bitrate= */ 48000),
                createAudioFormat(/* bitrate= */ 96000)),
            createStreamElement(
                /* name= */ "text", C.TRACK_TYPE_TEXT, createTextFormat(/* language= */ "eng")));
    SsChunkSource.Factory chunkSourceFactory = mock(SsChunkSource.Factory.class);
    when(chunkSourceFactory.getOutputTextFormat(any())).thenCallRealMethod();

    FilterableManifestMediaPeriodFactory<SsManifest> mediaPeriodFactory =
        (manifest, periodIndex) -> {
          MediaPeriodId mediaPeriodId = new MediaPeriodId(/* periodUid= */ new Object());
          return createSsMediaPeriod(manifest, mediaPeriodId, chunkSourceFactory);
        };

    MediaPeriodAsserts.assertGetStreamKeysAndManifestFilterIntegration(
        mediaPeriodFactory, testManifest);
  }

  @Test
  public void getTrackGroups_withSubtitleParserFactory_matchesFormat() {
    SubtitleParser.Factory subtitleParserFactory = new DefaultSubtitleParserFactory();

    Format originalSubtitleFormat =
        new Format.Builder()
            .setContainerMimeType(MimeTypes.APPLICATION_MP4)
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("eng")
            .build();
    Format expectedSubtitleFormat =
        new Format.Builder()
            .setContainerMimeType(originalSubtitleFormat.containerMimeType)
            .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCodecs(originalSubtitleFormat.sampleMimeType)
            .setCueReplacementBehavior(
                subtitleParserFactory.getCueReplacementBehavior(originalSubtitleFormat))
            .setLanguage(originalSubtitleFormat.language)
            .build();

    SsManifest testManifest =
        createSsManifest(
            createStreamElement(
                /* name= */ "video",
                C.TRACK_TYPE_VIDEO,
                createVideoFormat(/* bitrate= */ 200000),
                createVideoFormat(/* bitrate= */ 400000),
                createVideoFormat(/* bitrate= */ 800000)),
            createStreamElement(
                /* name= */ "audio",
                C.TRACK_TYPE_AUDIO,
                createAudioFormat(/* bitrate= */ 48000),
                createAudioFormat(/* bitrate= */ 96000)),
            createStreamElement(/* name= */ "text", C.TRACK_TYPE_TEXT, originalSubtitleFormat));

    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            new FakeTimeline(/* windowCount= */ 2).getUidOfPeriod(/* periodIndex= */ 0),
            /* windowSequenceNumber= */ 0);

    SsChunkSource.Factory chunkSourceFactory = mock(SsChunkSource.Factory.class);
    // Default implementation of SsChunkSource.Factory.getOutputTextFormat doesn't transcode
    // DefaultSsChunkSource.Factory is final (not mockable) and has a null SubtitleParser.Factory
    when(chunkSourceFactory.getOutputTextFormat(any())).thenReturn(expectedSubtitleFormat);
    SsMediaPeriod period = createSsMediaPeriod(testManifest, mediaPeriodId, chunkSourceFactory);

    Format subtitleFormat = period.getTrackGroups().get(2).getFormat(0);
    assertThat(subtitleFormat).isEqualTo(expectedSubtitleFormat);
  }

  private static SsMediaPeriod createSsMediaPeriod(
      SsManifest manifest, MediaPeriodId mediaPeriodId, SsChunkSource.Factory chunkSourceFactory) {
    return new SsMediaPeriod(
        manifest,
        chunkSourceFactory,
        mock(TransferListener.class),
        mock(CompositeSequenceableLoaderFactory.class),
        /* cmcdConfiguration= */ null,
        mock(DrmSessionManager.class),
        new DrmSessionEventListener.EventDispatcher()
            .withParameters(/* windowIndex= */ 0, mediaPeriodId),
        mock(LoadErrorHandlingPolicy.class),
        new MediaSourceEventListener.EventDispatcher()
            .withParameters(/* windowIndex= */ 0, mediaPeriodId),
        mock(LoaderErrorThrower.class),
        mock(Allocator.class));
  }

  private static Format createVideoFormat(int bitrate) {
    return new Format.Builder()
        .setContainerMimeType(MimeTypes.VIDEO_MP4)
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setAverageBitrate(bitrate)
        .build();
  }

  private static Format createAudioFormat(int bitrate) {
    return new Format.Builder()
        .setContainerMimeType(MimeTypes.AUDIO_MP4)
        .setSampleMimeType(MimeTypes.AUDIO_AAC)
        .setAverageBitrate(bitrate)
        .build();
  }

  private static Format createTextFormat(String language) {
    return new Format.Builder()
        .setContainerMimeType(MimeTypes.APPLICATION_MP4)
        .setSampleMimeType(MimeTypes.TEXT_VTT)
        .setLanguage(language)
        .build();
  }
}
