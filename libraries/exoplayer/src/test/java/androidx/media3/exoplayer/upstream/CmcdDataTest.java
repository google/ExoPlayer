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

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableListMultimap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CmcdData}. */
@RunWith(AndroidJUnit4.class)
public class CmcdDataTest {

  @Test
  public void createInstance_populatesCmcdHttRequestHeaders() {
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
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(
                cmcdConfiguration,
                trackSelection,
                /* bufferedDurationUs= */ 1_760_000,
                /* playbackRate= */ 2.0f,
                /* streamingFormat= */ CmcdData.Factory.STREAMING_FORMAT_DASH,
                /* isLive= */ true,
                /* didRebuffer= */ true,
                /* isBufferEmpty= */ false)
            .setChunkDurationUs(3_000_000)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    assertThat(dataSpec.httpRequestHeaders)
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
  public void createInstance_populatesCmcdHttpQueryParameters() {
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
                        .put("CMCD-Object", "key-1=1")
                        .put("CMCD-Request", "key-2=\"stringVälue1,stringVälue2\"")
                        .build();
                  }

                  @Override
                  public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                    return 2 * throughputKbps;
                  }
                },
                CmcdConfiguration.MODE_QUERY_PARAMETER);
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format = new Format.Builder().setPeakBitrate(840_000).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(
                cmcdConfiguration,
                trackSelection,
                /* bufferedDurationUs= */ 1_760_000,
                /* playbackRate= */ 2.0f,
                /* streamingFormat= */ CmcdData.Factory.STREAMING_FORMAT_DASH,
                /* isLive= */ true,
                /* didRebuffer= */ true,
                /* isBufferEmpty= */ false)
            .setChunkDurationUs(3_000_000)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    // Confirm that the values above are URL-encoded
    assertThat(dataSpec.uri.toString()).doesNotContain("ä");
    assertThat(dataSpec.uri.toString()).contains(Uri.encode("ä"));
    assertThat(dataSpec.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY))
        .isEqualTo(
            "bl=1800,br=840,bs,cid=\"mediaId\",d=3000,dl=900,key-1=1,"
                + "key-2=\"stringVälue1,stringVälue2\",mtp=500,pr=2.00,rtp=1700,sf=d,"
                + "sid=\"sessionId\",st=l,su,tb=1000");
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
            new CmcdData.Factory(
                    cmcdConfiguration,
                    trackSelection,
                    /* bufferedDurationUs= */ 0,
                    /* playbackRate= */ 1.0f,
                    /* streamingFormat= */ CmcdData.Factory.STREAMING_FORMAT_DASH,
                    /* isLive= */ true,
                    /* didRebuffer= */ true,
                    /* isBufferEmpty= */ false)
                .createCmcdData());
  }
}
