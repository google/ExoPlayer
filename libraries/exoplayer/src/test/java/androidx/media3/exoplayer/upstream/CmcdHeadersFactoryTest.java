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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
                  public ImmutableMap<@CmcdConfiguration.HeaderKey String, String> getCustomData() {
                    return new ImmutableMap.Builder<String, String>()
                        .put("CMCD-Object", "key1=value1")
                        .put("CMCD-Request", "key2=\"stringValue\"")
                        .buildOrThrow();
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
                /* streamingFormat= */ CmcdHeadersFactory.STREAMING_FORMAT_DASH,
                /* isLive= */ true)
            .setChunkDurationUs(3_000_000)
            .createHttpRequestHeaders();

    assertThat(requestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=840,tb=1000,d=3000,key1=value1",
            "CMCD-Request",
            "bl=1800,mtp=500,key2=\"stringValue\"",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"sessionId\",sf=d,st=l",
            "CMCD-Status",
            "rtp=1700");
  }
}
