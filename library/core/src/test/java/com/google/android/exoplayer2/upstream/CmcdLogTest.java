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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CmcdLog}. */
@RunWith(AndroidJUnit4.class)
public class CmcdLogTest {

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
    when(trackSelection.getSelectedFormat())
        .thenReturn(new Format.Builder().setPeakBitrate(840_000).build());
    CmcdLog cmcdLog =
        CmcdLog.createInstance(
            cmcdConfiguration,
            trackSelection,
            /* playbackPositionUs= */ 1_000_000,
            /* loadPositionUs= */ 2_760_000);

    ImmutableMap<@CmcdConfiguration.HeaderKey String, String> requestHeaders =
        cmcdLog.getHttpRequestHeaders();

    assertThat(requestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=840,key1=value1",
            "CMCD-Request",
            "bl=1800,key2=\"stringValue\"",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"sessionId\"",
            "CMCD-Status",
            "rtp=1700");
  }
}
