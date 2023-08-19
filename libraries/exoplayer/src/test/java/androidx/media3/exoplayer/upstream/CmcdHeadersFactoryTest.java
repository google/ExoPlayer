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
package androidx.media3.exoplayer.upstream;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CmcdHeadersFactory}. */
@RunWith(AndroidJUnit4.class)
public class CmcdHeadersFactoryTest {

  @Test
  public void createInstance_populatesCmcdHeaders() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId",
                mediaItem.mediaId,
                new CmcdConfiguration.RequestConfig() {
                  @Override
                  public ImmutableListMultimap<@CmcdConfiguration.HeaderKey String, String>
                      getCustomData() {
                    return new ImmutableListMultimap.Builder<String, String>()
                        .putAll("CMCD-Object", "key-1=1", "key-2-separated-by-multiple-hyphens=2")
                        .put("CMCD-Request", "key-3=\"stringValue1,stringValue2\"")
                        .put("CMCD-Status", "key-4=\"stringValue3=stringValue4\"")
                        .build();
                  }

                  @Override
                  public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                    return 2 * throughputKbps;
                  }
                });
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format = new Format.Builder().setPeakBitrate(840_000).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);

    ImmutableMap<@CmcdConfiguration.HeaderKey String, String> requestHeaders =
        new CmcdHeadersFactory(
                cmcdConfiguration,
                trackSelection,
                /* bufferedDurationUs= */ 1_760_000,
                /* playbackRate= */ 2.0f,
                /* streamingFormat= */ CmcdHeadersFactory.STREAMING_FORMAT_DASH,
                /* isLive= */ true,
                /* didRebuffer= */ true,
                /* isBufferEmpty= */ false)
            .setChunkDurationUs(3_000_000)
            .createHttpRequestHeaders();

    assertThat(requestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=840,d=3000,key-1=1,key-2-separated-by-multiple-hyphens=2,tb=1000",
            "CMCD-Request",
            "bl=1800,dl=900,key-3=\"stringValue1,stringValue2\",mtp=500,su",
            "CMCD-Session",
            "cid=\"mediaId\",pr=2.00,sf=d,sid=\"sessionId\",st=l",
            "CMCD-Status",
            "bs,key-4=\"stringValue3=stringValue4\",rtp=1700");
  }

  @Test
  public void createInstance_withInvalidNonHyphenatedCustomKey_throwsIllegalStateException() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                null,
                null,
                new CmcdConfiguration.RequestConfig() {
                  @Override
                  public ImmutableListMultimap<@CmcdConfiguration.HeaderKey String, String>
                      getCustomData() {
                    return ImmutableListMultimap.of("CMCD-Object", "key1=1");
                  }
                });
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    when(trackSelection.getSelectedFormat()).thenReturn(new Format.Builder().build());

    assertThrows(
        IllegalStateException.class,
        () ->
            new CmcdHeadersFactory(
                    cmcdConfiguration,
                    trackSelection,
                    /* bufferedDurationUs= */ 0,
                    /* playbackRate= */ 1.0f,
                    /* streamingFormat= */ CmcdHeadersFactory.STREAMING_FORMAT_DASH,
                    /* isLive= */ true,
                    /* didRebuffer= */ true,
                    /* isBufferEmpty= */ false)
                .createHttpRequestHeaders());
  }
}
