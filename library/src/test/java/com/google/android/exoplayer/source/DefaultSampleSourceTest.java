/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.source;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.util.MimeTypes;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link DefaultSampleSource}. */
public final class DefaultSampleSourceTest extends TestCase {

  private static final int RENDERER_COUNT = 2;
  private static final MediaFormat FAKE_MEDIA_FORMAT =
      MediaFormat.createFormatForMimeType(MimeTypes.AUDIO_AAC);
  private DefaultSampleSource defaultSampleSource;
  @Mock SampleExtractor mockSampleExtractor;

  @Override
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(mockSampleExtractor.prepare()).thenReturn(true);
    when(mockSampleExtractor.getTrackCount()).thenReturn(2);
    when(mockSampleExtractor.getMediaFormat(anyInt())).thenReturn(FAKE_MEDIA_FORMAT);

    defaultSampleSource = new DefaultSampleSource(mockSampleExtractor, RENDERER_COUNT);
  }

  public void testSourceReleasedWhenRenderersReleased() throws Exception {
    // Given a prepared sample source
    defaultSampleSource.prepare();

    // When releasing it once, it is not released.
    defaultSampleSource.release();
    verify(mockSampleExtractor, never()).release();

    // When releasing RENDERER_COUNT times, it is released.
    defaultSampleSource.release();
    verify(mockSampleExtractor).release();
  }

  public void testEnablingTracksAtStartDoesNotSeek() throws Exception {
    // Given a prepared sample source
    defaultSampleSource.prepare();

    // When the first track is enabled at t=0, the sample extractor does not seek.
    defaultSampleSource.enable(0, 0L);
    verify(mockSampleExtractor, never()).seekTo(0);

    // When the second track is enabled at t=0, the sample extractor does not seek.
    defaultSampleSource.enable(1, 0L);
    verify(mockSampleExtractor, never()).seekTo(0);
  }

  public void testEnablingTracksInMiddleDoesSeek() throws Exception {
    // Given a prepared sample source
    defaultSampleSource.prepare();

    // When the first track is enabled at t!=0, the sample extractor does seek.
    defaultSampleSource.enable(0, 1000L);
    verify(mockSampleExtractor, times(1)).seekTo(1000L);

    // When the second track is enabled at t!=0, the sample extractor does seek.
    defaultSampleSource.enable(1, 1000L);
    verify(mockSampleExtractor, times(2)).seekTo(1000L);
  }

  public void testEnablingTrackSelectsTrack() throws Exception {
    // Given a prepared sample source
    defaultSampleSource.prepare();

    // When the first track is enabled, it selects the first track.
    defaultSampleSource.enable(0, 0L);
    verify(mockSampleExtractor).selectTrack(0);
  }

  public void testReadDataInitiallyReadsFormat() throws Exception {
    // Given a prepared sample source with the first track selected
    defaultSampleSource.prepare();
    defaultSampleSource.enable(0, 0L);

    // A format is read.
    MediaFormatHolder mediaFormatHolder = new MediaFormatHolder();
    assertEquals(SampleSource.FORMAT_READ,
        defaultSampleSource.readData(0, 0, mediaFormatHolder, null, false));
  }

  public void testSeekAndReadDataReadsDiscontinuity() throws Exception {
    // Given a prepared sample source with the first track selected
    defaultSampleSource.prepare();
    defaultSampleSource.enable(0, 1L);

    // A discontinuity is read.
    assertEquals(
        SampleSource.DISCONTINUITY_READ, defaultSampleSource.readData(0, 0, null, null, false));
  }

}
