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
package com.google.android.exoplayer2.source.dash;

import static org.mockito.Mockito.mock;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
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
                /* id= */ "0",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format,
                adaptationSets.get(2).representations.get(0).format,
                adaptationSets.get(2).representations.get(1).format,
                adaptationSets.get(3).representations.get(0).format),
            new TrackGroup(/* id= */ "3", adaptationSets.get(1).representations.get(0).format));

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
                /* id= */ "0",
                adaptationSets.get(0).representations.get(0).format,
                adaptationSets.get(0).representations.get(1).format,
                adaptationSets.get(1).representations.get(0).format),
            new TrackGroup(
                /* id= */ "2",
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
                /* id= */ "0",
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
    return new DashMediaPeriod(
        /* id= */ periodIndex,
        manifest,
        new BaseUrlExclusionList(),
        periodIndex,
        mock(DashChunkSource.Factory.class),
        mock(TransferListener.class),
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher()
            .withParameters(/* windowIndex= */ 0, mediaPeriodId),
        mock(LoadErrorHandlingPolicy.class),
        new MediaSourceEventListener.EventDispatcher()
            .withParameters(/* windowIndex= */ 0, mediaPeriodId, /* mediaTimeOffsetMs= */ 0),
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
