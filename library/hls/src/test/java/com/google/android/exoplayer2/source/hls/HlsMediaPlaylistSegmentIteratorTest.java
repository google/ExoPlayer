/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link HlsChunkSource.HlsMediaPlaylistSegmentIterator}. */
@RunWith(AndroidJUnit4.class)
public class HlsMediaPlaylistSegmentIteratorTest {

  public static final String LOW_LATENCY_SEGMENTS_AND_PARTS =
      "media/m3u8/live_low_latency_segments_and_parts";
  public static final String SEGMENTS_ONLY = "media/m3u8/live_low_latency_segments_only";

  @Test
  public void create_withMediaSequenceBehindLiveWindow_isEmpty() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);

    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist, mediaPlaylist.mediaSequence - 1, /* partIndex= */ C.INDEX_UNSET));

    assertThat(hlsMediaPlaylistSegmentIterator.next()).isFalse();
  }

  @Test
  public void create_withMediaSequenceBeforeTrailingPartSegment_isEmpty() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);

    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist,
                mediaPlaylist.mediaSequence + mediaPlaylist.segments.size() + 1,
                /* partIndex= */ C.INDEX_UNSET));

    assertThat(hlsMediaPlaylistSegmentIterator.next()).isFalse();
  }

  @Test
  public void create_withPartIndexBeforeLastTrailingPartSegment_isEmpty() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);

    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist,
                mediaPlaylist.mediaSequence + mediaPlaylist.segments.size(),
                /* partIndex= */ 3));

    assertThat(hlsMediaPlaylistSegmentIterator.next()).isFalse();
  }

  @Test
  public void next_conventionalLiveStartIteratorAtSecondSegment_correctElements() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(SEGMENTS_ONLY);
    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist, /* mediaSequence= */ 11, /* partIndex= */ C.INDEX_UNSET));

    List<DataSpec> datasSpecs = new ArrayList<>();
    while (hlsMediaPlaylistSegmentIterator.next()) {
      datasSpecs.add(hlsMediaPlaylistSegmentIterator.getDataSpec());
    }

    assertThat(datasSpecs).hasSize(5);
    assertThat(datasSpecs.get(0).uri.toString()).isEqualTo("fileSequence11.ts");
    assertThat(Iterables.getLast(datasSpecs).uri.toString()).isEqualTo("fileSequence15.ts");
  }

  @Test
  public void next_startIteratorAtFirstSegment_correctElements() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);
    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist, /* mediaSequence= */ 10, /* partIndex= */ C.INDEX_UNSET));

    List<DataSpec> datasSpecs = new ArrayList<>();
    while (hlsMediaPlaylistSegmentIterator.next()) {
      datasSpecs.add(hlsMediaPlaylistSegmentIterator.getDataSpec());
    }

    assertThat(datasSpecs).hasSize(9);
    // The iterator starts with 6 segments.
    assertThat(datasSpecs.get(0).uri.toString()).isEqualTo("fileSequence10.ts");
    // Followed by trailing parts.
    assertThat(datasSpecs.get(6).uri.toString()).isEqualTo("fileSequence16.0.ts");
    // The preload part is the last.
    assertThat(Iterables.getLast(datasSpecs).uri.toString()).isEqualTo("fileSequence16.2.ts");
  }

  @Test
  public void next_startIteratorAtFirstPartInaSegment_usesFullSegment() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);
    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist, /* mediaSequence= */ 14, /* partIndex= */ 0));

    List<DataSpec> datasSpecs = new ArrayList<>();
    while (hlsMediaPlaylistSegmentIterator.next()) {
      datasSpecs.add(hlsMediaPlaylistSegmentIterator.getDataSpec());
    }

    assertThat(datasSpecs).hasSize(5);
    // The iterator starts with 6 segments.
    assertThat(datasSpecs.get(0).uri.toString()).isEqualTo("fileSequence14.ts");
    assertThat(datasSpecs.get(1).uri.toString()).isEqualTo("fileSequence15.ts");
    // Followed by trailing parts.
    assertThat(datasSpecs.get(2).uri.toString()).isEqualTo("fileSequence16.0.ts");
    assertThat(datasSpecs.get(3).uri.toString()).isEqualTo("fileSequence16.1.ts");
    // The preload part is the last.
    assertThat(Iterables.getLast(datasSpecs).uri.toString()).isEqualTo("fileSequence16.2.ts");
  }

  @Test
  public void next_startIteratorAtTrailingPart_correctElements() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);
    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist, /* mediaSequence= */ 16, /* partIndex= */ 1));

    List<DataSpec> datasSpecs = new ArrayList<>();
    while (hlsMediaPlaylistSegmentIterator.next()) {
      datasSpecs.add(hlsMediaPlaylistSegmentIterator.getDataSpec());
    }

    assertThat(datasSpecs).hasSize(2);
    // The iterator starts with 2 parts.
    assertThat(datasSpecs.get(0).uri.toString()).isEqualTo("fileSequence16.1.ts");
    // The preload part is the last.
    assertThat(Iterables.getLast(datasSpecs).uri.toString()).isEqualTo("fileSequence16.2.ts");
  }

  @Test
  public void next_startIteratorAtPartWithinSegment_correctElements() {
    HlsMediaPlaylist mediaPlaylist = getHlsMediaPlaylist(LOW_LATENCY_SEGMENTS_AND_PARTS);
    HlsChunkSource.HlsMediaPlaylistSegmentIterator hlsMediaPlaylistSegmentIterator =
        new HlsChunkSource.HlsMediaPlaylistSegmentIterator(
            mediaPlaylist.baseUri,
            /* startOfPlaylistInPeriodUs= */ 0,
            HlsChunkSource.getSegmentBaseList(
                mediaPlaylist, /* mediaSequence= */ 14, /* partIndex= */ 1));

    List<DataSpec> datasSpecs = new ArrayList<>();
    while (hlsMediaPlaylistSegmentIterator.next()) {
      datasSpecs.add(hlsMediaPlaylistSegmentIterator.getDataSpec());
    }

    assertThat(datasSpecs).hasSize(7);
    // The iterator starts with 11 parts.
    assertThat(datasSpecs.get(0).uri.toString()).isEqualTo("fileSequence14.1.ts");
    assertThat(datasSpecs.get(1).uri.toString()).isEqualTo("fileSequence14.2.ts");
    assertThat(datasSpecs.get(2).uri.toString()).isEqualTo("fileSequence14.3.ts");
    // Use a segment in between if possible.
    assertThat(datasSpecs.get(3).uri.toString()).isEqualTo("fileSequence15.ts");
    // Then parts again.
    assertThat(datasSpecs.get(4).uri.toString()).isEqualTo("fileSequence16.0.ts");
    assertThat(datasSpecs.get(5).uri.toString()).isEqualTo("fileSequence16.1.ts");
    assertThat(datasSpecs.get(6).uri.toString()).isEqualTo("fileSequence16.2.ts");
  }

  private static HlsMediaPlaylist getHlsMediaPlaylist(String file) {
    try {
      return (HlsMediaPlaylist)
          new HlsPlaylistParser()
              .parse(
                  Uri.EMPTY,
                  TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), file));
    } catch (IOException e) {
      fail(e.getMessage());
    }
    return null;
  }
}
