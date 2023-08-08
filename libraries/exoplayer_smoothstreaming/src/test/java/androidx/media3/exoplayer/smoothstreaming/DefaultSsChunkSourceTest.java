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
package androidx.media3.exoplayer.smoothstreaming;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Assertions;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifest;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifestParser;
import androidx.media3.exoplayer.source.chunk.ChunkHolder;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.test.utils.FakeDataSource;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultSsChunkSource}. */
@RunWith(AndroidJUnit4.class)
public class DefaultSsChunkSourceTest {
  private static final String SAMPLE_ISMC_1 = "media/smooth-streaming/sample_ismc_1";

  @Test
  public void getNextChunk_chunkSourceWithDefaultCmcdConfiguration_setsCmcdLoggingHeaders()
      throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    SsChunkSource chunkSource = createSsChunkSource(/* numberOfTracks= */ 2, cmcdConfiguration);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=308,tb=1536,d=1968,ot=v",
            "CMCD-Request",
            "bl=0,mtp=1000",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"" + cmcdConfiguration.sessionId + "\",sf=s,st=v");
  }

  @Test
  public void getNextChunk_chunkSourceWithCustomCmcdConfiguration_setsCmcdLoggingHeaders()
      throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public boolean isKeyAllowed(String key) {
                  return !key.equals(CmcdConfiguration.KEY_SESSION_ID);
                }

                @Override
                public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                  return 5 * throughputKbps;
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId",
              /* contentId= */ mediaItem.mediaId + "contentIdSuffix",
              cmcdRequestConfig);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    SsChunkSource chunkSource = createSsChunkSource(/* numberOfTracks= */ 2, cmcdConfiguration);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=308,tb=1536,d=1968,ot=v",
            "CMCD-Request",
            "bl=0,mtp=1000",
            "CMCD-Session",
            "cid=\"mediaIdcontentIdSuffix\",sf=s,st=v",
            "CMCD-Status",
            "rtp=1500");
  }

  @Test
  public void
      getNextChunk_chunkSourceWithCustomCmcdConfigurationAndCustomData_setsCmcdLoggingHeaders()
          throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public ImmutableMap<@CmcdConfiguration.HeaderKey String, String> getCustomData() {
                  return new ImmutableMap.Builder<@CmcdConfiguration.HeaderKey String, String>()
                      .put(CmcdConfiguration.KEY_CMCD_OBJECT, "key1=value1")
                      .put(CmcdConfiguration.KEY_CMCD_REQUEST, "key2=\"stringValue\"")
                      .put(CmcdConfiguration.KEY_CMCD_SESSION, "key3=1")
                      .put(CmcdConfiguration.KEY_CMCD_STATUS, "key4=5.0")
                      .buildOrThrow();
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId", /* contentId= */ mediaItem.mediaId, cmcdRequestConfig);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    SsChunkSource chunkSource = createSsChunkSource(/* numberOfTracks= */ 2, cmcdConfiguration);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=308,tb=1536,d=1968,ot=v,key1=value1",
            "CMCD-Request",
            "bl=0,mtp=1000,key2=\"stringValue\"",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"" + cmcdConfiguration.sessionId + "\",sf=s,st=v,key3=1",
            "CMCD-Status",
            "key4=5.0");
  }

  private SsChunkSource createSsChunkSource(
      int numberOfTracks, @Nullable CmcdConfiguration cmcdConfiguration) throws IOException {
    Assertions.checkArgument(numberOfTracks < 6);
    SsManifestParser parser = new SsManifestParser();
    SsManifest manifest =
        parser.parse(
            Uri.parse("https://example.com/test.ismc"),
            TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), SAMPLE_ISMC_1));
    int[] selectedTracks = new int[numberOfTracks];
    Format[] formats = new Format[numberOfTracks];
    for (int i = 0; i < numberOfTracks; i++) {
      selectedTracks[i] = i;
      formats[i] = manifest.streamElements[0].formats[i];
    }
    AdaptiveTrackSelection adaptiveTrackSelection =
        new AdaptiveTrackSelection(
            new TrackGroup(formats),
            selectedTracks,
            new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build());
    return new DefaultSsChunkSource(
        new LoaderErrorThrower.Placeholder(),
        manifest,
        /* streamElementIndex= */ 0,
        adaptiveTrackSelection,
        new FakeDataSource(),
        cmcdConfiguration);
  }
}
