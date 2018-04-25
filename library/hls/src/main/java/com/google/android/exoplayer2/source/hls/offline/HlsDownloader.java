/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls.offline;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.source.hls.playlist.RenditionKey;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.UriUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Helper class to download HLS streams.
 *
 * <p>A subset of renditions can be downloaded by selecting them using {@link
 * #selectRepresentations(Object[])}.
 */
public final class HlsDownloader extends SegmentDownloader<HlsMasterPlaylist, RenditionKey> {

  /**
   * @see SegmentDownloader#SegmentDownloader(Uri, DownloaderConstructorHelper)
   */
  public HlsDownloader(Uri manifestUri, DownloaderConstructorHelper constructorHelper)  {
    super(manifestUri, constructorHelper);
  }

  @Override
  public RenditionKey[] getAllRepresentationKeys() throws IOException {
    ArrayList<RenditionKey> renditionKeys = new ArrayList<>();
    HlsMasterPlaylist manifest = getManifest();
    extractUrls(manifest.variants, renditionKeys);
    extractUrls(manifest.audios, renditionKeys);
    extractUrls(manifest.subtitles, renditionKeys);
    return renditionKeys.toArray(new RenditionKey[renditionKeys.size()]);
  }

  @Override
  protected HlsMasterPlaylist getManifest(DataSource dataSource, Uri uri) throws IOException {
    HlsPlaylist hlsPlaylist = loadManifest(dataSource, uri);
    if (hlsPlaylist instanceof HlsMasterPlaylist) {
      return (HlsMasterPlaylist) hlsPlaylist;
    } else {
      return HlsMasterPlaylist.createSingleVariantMasterPlaylist(hlsPlaylist.baseUri);
    }
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource,
      HlsMasterPlaylist manifest,
      RenditionKey[] keys,
      boolean allowIndexLoadErrors)
      throws InterruptedException, IOException {
    HashSet<Uri> encryptionKeyUris = new HashSet<>();
    ArrayList<Segment> segments = new ArrayList<>();
    for (RenditionKey renditionKey : keys) {
      HlsMediaPlaylist mediaPlaylist = null;
      Uri uri = UriUtil.resolveToUri(manifest.baseUri, renditionKey.url);
      try {
        mediaPlaylist = (HlsMediaPlaylist) loadManifest(dataSource, uri);
      } catch (IOException e) {
        if (!allowIndexLoadErrors) {
          throw e;
        }
      }
      segments.add(new Segment(mediaPlaylist != null ? mediaPlaylist.startTimeUs : Long.MIN_VALUE,
          new DataSpec(uri)));
      if (mediaPlaylist == null) {
        continue;
      }

      HlsMediaPlaylist.Segment lastInitSegment = null;
      List<HlsMediaPlaylist.Segment> hlsSegments = mediaPlaylist.segments;
      for (int i = 0; i < hlsSegments.size(); i++) {
        HlsMediaPlaylist.Segment segment = hlsSegments.get(i);
        HlsMediaPlaylist.Segment initSegment = segment.initializationSegment;
        if (initSegment != null && initSegment != lastInitSegment) {
          lastInitSegment = initSegment;
          addSegment(segments, mediaPlaylist, initSegment, encryptionKeyUris);
        }
        addSegment(segments, mediaPlaylist, segment, encryptionKeyUris);
      }
    }
    return segments;
  }

  private static HlsPlaylist loadManifest(DataSource dataSource, Uri uri) throws IOException {
    ParsingLoadable<HlsPlaylist> loadable =
        new ParsingLoadable<>(dataSource, uri, C.DATA_TYPE_MANIFEST, new HlsPlaylistParser());
    loadable.load();
    return loadable.getResult();
  }

  private static void addSegment(
      ArrayList<Segment> segments,
      HlsMediaPlaylist mediaPlaylist,
      HlsMediaPlaylist.Segment hlsSegment,
      HashSet<Uri> encryptionKeyUris) {
    long startTimeUs = mediaPlaylist.startTimeUs + hlsSegment.relativeStartTimeUs;
    if (hlsSegment.fullSegmentEncryptionKeyUri != null) {
      Uri keyUri = UriUtil.resolveToUri(mediaPlaylist.baseUri,
          hlsSegment.fullSegmentEncryptionKeyUri);
      if (encryptionKeyUris.add(keyUri)) {
        segments.add(new Segment(startTimeUs, new DataSpec(keyUri)));
      }
    }
    Uri resolvedUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, hlsSegment.url);
    segments.add(new Segment(startTimeUs,
        new DataSpec(resolvedUri, hlsSegment.byterangeOffset, hlsSegment.byterangeLength, null)));
  }

  private static void extractUrls(List<HlsUrl> hlsUrls, ArrayList<RenditionKey> renditionKeys) {
    for (int i = 0; i < hlsUrls.size(); i++) {
      renditionKeys.add(new RenditionKey(hlsUrls.get(i).url));
    }
  }

}
