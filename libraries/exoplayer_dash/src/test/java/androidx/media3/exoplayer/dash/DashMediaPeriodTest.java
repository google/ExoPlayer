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
package androidx.media3.exoplayer.dash;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.test.utils.MediaPeriodAsserts;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DashMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public final class DashMediaPeriodTest {

  @Test
  public void getStreamKeys_isCompatibleWithDashManifestFilter() throws IOException {
    // Test manifest which covers various edge cases:
    //  - Multiple periods.
    //  - Single and multiple representations per adaptation set.
    //  - Switch descriptors combining multiple adaptations sets.
    //  - Embedded track groups.
    // All cases are deliberately combined in one test to catch potential indexing problems which
    // only occur in combination.
    DashManifest manifest = parseManifest("media/mpd/sample_mpd_stream_keys");

    // Ignore embedded metadata as we don't want to select primary group just to get embedded track.
    MediaPeriodAsserts.assertGetStreamKeysAndManifestFilterIntegration(
        DashMediaPeriodTest::createDashMediaPeriod,
        manifest,
        /* periodIndex= */ 1,
        /* ignoredMimeType= */ "application/x-emsg");
  }

  @Test
  public void adaptationSetSwitchingProperty_mergesTrackGroups() throws IOException {
    DashManifest manifest = parseManifest("media/mpd/sample_mpd_switching_property");
    DashMediaPeriod dashMediaPeriod = createDashMediaPeriod(manifest, 0);
    List<AdaptationSet> adaptationSets = manifest.getPeriod(0).adaptationSets;

    // We expect the three adaptation sets with the switch descriptor to be merged, retaining the
    // representations in their original order.
    TrackGroupArray expectedTrackGroups =
        new TrackGroupArray(
            new TrackGroup(
                /* id= */ "3000000000",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format,
                adaptationSets.get(2).representations.get(0).format,
                adaptationSets.get(2).representations.get(1).format,
                adaptationSets.get(3).representations.get(0).format),
            new TrackGroup(
                /* id= */ "3000000003", adaptationSets.get(1).representations.get(0).format));

    MediaPeriodAsserts.assertTrackGroups(dashMediaPeriod, expectedTrackGroups);
  }

  @Test
  public void trickPlayProperty_mergesTrackGroups() throws IOException {
    DashManifest manifest = parseManifest("media/mpd/sample_mpd_trick_play_property");
    DashMediaPeriod dashMediaPeriod = createDashMediaPeriod(manifest, 0);
    List<AdaptationSet> adaptationSets = manifest.getPeriod(0).adaptationSets;

    // We expect the trick play adaptation sets to be merged with the ones to which they refer,
    // retaining representations in their original order.
    TrackGroupArray expectedTrackGroups =
        new TrackGroupArray(
            new TrackGroup(
                /* id= */ "3000000000",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format,
                adaptationSets.get(1).representations.get(0).format),
            new TrackGroup(
                /* id= */ "3000000002",
                adaptationSets.get(2).representations.get(0).format,
                adaptationSets.get(2).representations.get(1).format,
                adaptationSets.get(3).representations.get(0).format));

    MediaPeriodAsserts.assertTrackGroups(dashMediaPeriod, expectedTrackGroups);
  }

  @Test
  public void adaptationSetSwitchingProperty_andTrickPlayProperty_mergesTrackGroups()
      throws IOException {
    DashManifest manifest = parseManifest("media/mpd/sample_mpd_switching_and_trick_play_property");
    DashMediaPeriod dashMediaPeriod = createDashMediaPeriod(manifest, 0);
    List<AdaptationSet> adaptationSets = manifest.getPeriod(0).adaptationSets;

    // We expect all adaptation sets to be merged into one group, retaining representations in their
    // original order.
    TrackGroupArray expectedTrackGroups =
        new TrackGroupArray(
            new TrackGroup(
                /* id= */ "3000000000",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format,
                adaptationSets.get(1).representations.get(0).format,
                adaptationSets.get(2).representations.get(0).format,
                adaptationSets.get(2).representations.get(1).format,
                adaptationSets.get(3).representations.get(0).format));

    MediaPeriodAsserts.assertTrackGroups(dashMediaPeriod, expectedTrackGroups);
  }

  @Test
  public void cea608AccessibilityDescriptor_createsCea608TrackGroup() throws IOException {
    DashManifest manifest = parseManifest("media/mpd/sample_mpd_cea_608_accessibility");
    DashMediaPeriod dashMediaPeriod = createDashMediaPeriod(manifest, 0);
    List<AdaptationSet> adaptationSets = manifest.getPeriod(0).adaptationSets;

    // We expect two adaptation sets. The first containing the video representations, and the second
    // containing the embedded CEA-608 tracks.
    Format.Builder cea608FormatBuilder =
        new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608);
    TrackGroupArray expectedTrackGroups =
        new TrackGroupArray(
            new TrackGroup(
                /* id= */ "123",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format),
            new TrackGroup(
                /* id= */ "123:cc",
                cea608FormatBuilder
                    .setId("123:cea608:1")
                    .setLanguage("eng")
                    .setAccessibilityChannel(1)
                    .build(),
                cea608FormatBuilder
                    .setId("123:cea608:3")
                    .setLanguage("deu")
                    .setAccessibilityChannel(3)
                    .build()));

    MediaPeriodAsserts.assertTrackGroups(dashMediaPeriod, expectedTrackGroups);
  }

  @Test
  public void cea708AccessibilityDescriptor_createsCea708TrackGroup() throws IOException {
    DashManifest manifest = parseManifest("media/mpd/sample_mpd_cea_708_accessibility");
    DashMediaPeriod dashMediaPeriod = createDashMediaPeriod(manifest, 0);
    List<AdaptationSet> adaptationSets = manifest.getPeriod(0).adaptationSets;

    // We expect two adaptation sets. The first containing the video representations, and the second
    // containing the embedded CEA-708 tracks.
    Format.Builder cea608FormatBuilder =
        new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA708);
    TrackGroupArray expectedTrackGroups =
        new TrackGroupArray(
            new TrackGroup(
                /* id= */ "123",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format),
            new TrackGroup(
                /* id= */ "123:cc",
                cea608FormatBuilder
                    .setId("123:cea708:1")
                    .setLanguage("eng")
                    .setAccessibilityChannel(1)
                    .build(),
                cea608FormatBuilder
                    .setId("123:cea708:2")
                    .setLanguage("deu")
                    .setAccessibilityChannel(2)
                    .build()));

    MediaPeriodAsserts.assertTrackGroups(dashMediaPeriod, expectedTrackGroups);
  }

  private static DashMediaPeriod createDashMediaPeriod(DashManifest manifest, int periodIndex) {
    MediaPeriodId mediaPeriodId = new MediaPeriodId(/* periodUid= */ new Object());
    DashChunkSource.Factory chunkSourceFactory = mock(DashChunkSource.Factory.class);
    when(chunkSourceFactory.getOutputTextFormat(any())).thenCallRealMethod();
    return new DashMediaPeriod(
        /* id= */ periodIndex,
        manifest,
        new BaseUrlExclusionList(),
        periodIndex,
        chunkSourceFactory,
        mock(TransferListener.class),
        /* cmcdConfiguration= */ null,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher()
            .withParameters(/* windowIndex= */ 0, mediaPeriodId),
        mock(LoadErrorHandlingPolicy.class),
        new MediaSourceEventListener.EventDispatcher()
            .withParameters(/* windowIndex= */ 0, mediaPeriodId),
        /* elapsedRealtimeOffsetMs= */ 0,
        mock(LoaderErrorThrower.class),
        mock(Allocator.class),
        mock(CompositeSequenceableLoaderFactory.class),
        mock(PlayerEmsgCallback.class),
        PlayerId.UNSET);
  }

  private static DashManifest parseManifest(String fileName) throws IOException {
    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), fileName);
    return new DashManifestParser().parse(Uri.EMPTY, inputStream);
  }
}
