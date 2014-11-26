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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.hls.TsExtractor.SamplePool;
import com.google.android.exoplayer.upstream.Aes128DataSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.BitArray;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.SystemClock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A temporary test source of HLS chunks.
 * <p>
 * TODO: Figure out whether this should merge with the chunk package, or whether the hls
 * implementation is going to naturally diverge.
 */
public class HlsChunkSource {

  /**
   * Adaptive switching is disabled.
   * <p>
   * The initially selected variant will be used throughout playback.
   */
  public static final int ADAPTIVE_MODE_NONE = 0;

  /**
   * Adaptive switches splice overlapping segments of the old and new variants.
   * <p>
   * When performing a switch from one variant to another, overlapping segments will be requested
   * from both the old and new variants. These segments will then be spliced together, allowing
   * a seamless switch from one variant to another even if keyframes are misaligned or if keyframes
   * are not positioned at the start of each segment.
   * <p>
   * Note that where it can be guaranteed that the source content has keyframes positioned at the
   * start of each segment, {@link #ADAPTIVE_MODE_ABRUPT} should always be used in preference to
   * this mode.
   */
  public static final int ADAPTIVE_MODE_SPLICE = 1;

  /**
   * Adaptive switches are performed at segment boundaries.
   * <p>
   * For this mode to perform seamless switches, the source content is required to have keyframes
   * positioned at the start of each segment. If this is not the case a visual discontinuity may
   * be experienced when switching from one variant to another.
   * <p>
   * Note that where it can be guaranteed that the source content does have keyframes positioned at
   * the start of each segment, this mode should always be used in preference to
   * {@link #ADAPTIVE_MODE_SPLICE} because it requires fetching less data.
   */
  public static final int ADAPTIVE_MODE_ABRUPT = 3;

  /**
   * The default target buffer duration in milliseconds.
   */
  public static final long DEFAULT_TARGET_BUFFER_DURATION_MS = 40000;

  /**
   * The default minimum duration of media that needs to be buffered for a switch to a higher
   * quality variant to be considered.
   */
  public static final long DEFAULT_MIN_BUFFER_TO_SWITCH_UP_MS = 5000;

  /**
   * The default maximum duration of media that needs to be buffered for a switch to a lower
   * quality variant to be considered.
   */
  public static final long DEFAULT_MAX_BUFFER_TO_SWITCH_DOWN_MS = 20000;

  private static final float BANDWIDTH_FRACTION = 0.8f;

  private final SamplePool samplePool = new TsExtractor.SamplePool();
  private final DataSource upstreamDataSource;
  private final HlsPlaylistParser playlistParser;
  private final Variant[] enabledVariants;
  private final BandwidthMeter bandwidthMeter;
  private final BitArray bitArray;
  private final int adaptiveMode;
  private final Uri baseUri;
  private final int maxWidth;
  private final int maxHeight;
  private final long targetBufferDurationUs;
  private final long minBufferDurationToSwitchUpUs;
  private final long maxBufferDurationToSwitchDownUs;

  /* package */ final HlsMediaPlaylist[] mediaPlaylists;
  /* package */ final long[] lastMediaPlaylistLoadTimesMs;
  /* package */ boolean live;
  /* package */ long durationUs;

  private int variantIndex;
  private DataSource encryptedDataSource;
  private Uri encryptionKeyUri;
  private String encryptedDataSourceIv;
  private byte[] encryptedDataSourceSecretKey;

  public HlsChunkSource(DataSource dataSource, String playlistUrl, HlsPlaylist playlist,
      BandwidthMeter bandwidthMeter, int[] variantIndices, int adaptiveMode) {
    this(dataSource, playlistUrl, playlist, bandwidthMeter, variantIndices, adaptiveMode,
        DEFAULT_TARGET_BUFFER_DURATION_MS, DEFAULT_MIN_BUFFER_TO_SWITCH_UP_MS,
        DEFAULT_MAX_BUFFER_TO_SWITCH_DOWN_MS);
  }

  /**
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param playlistUrl The playlist URL.
   * @param playlist The hls playlist.
   * @param bandwidthMeter provides an estimate of the currently available bandwidth.
   * @param variantIndices A subset of variant indices to consider, or null to consider all of the
   *     variants in the master playlist.
   * @param adaptiveMode The mode for switching from one variant to another. One of
   *     {@link #ADAPTIVE_MODE_NONE}, {@link #ADAPTIVE_MODE_ABRUPT} and
   *     {@link #ADAPTIVE_MODE_SPLICE}.
   * @param targetBufferDurationMs The targeted duration of media to buffer ahead of the current
   *     playback position. Note that the greater this value, the greater the amount of memory
   *     that will be consumed.
   * @param minBufferDurationToSwitchUpMs The minimum duration of media that needs to be buffered
   *     for a switch to a higher quality variant to be considered.
   * @param maxBufferDurationToSwitchDownMs The maximum duration of media that needs to be buffered
   *     for a switch to a lower quality variant to be considered.
   */
  public HlsChunkSource(DataSource dataSource, String playlistUrl, HlsPlaylist playlist,
      BandwidthMeter bandwidthMeter, int[] variantIndices, int adaptiveMode,
      long targetBufferDurationMs, long minBufferDurationToSwitchUpMs,
      long maxBufferDurationToSwitchDownMs) {
    this.upstreamDataSource = dataSource;
    this.bandwidthMeter = bandwidthMeter;
    this.adaptiveMode = adaptiveMode;
    targetBufferDurationUs = targetBufferDurationMs * 1000;
    minBufferDurationToSwitchUpUs = minBufferDurationToSwitchUpMs * 1000;
    maxBufferDurationToSwitchDownUs = maxBufferDurationToSwitchDownMs * 1000;
    baseUri = playlist.baseUri;
    bitArray = new BitArray();
    playlistParser = new HlsPlaylistParser();

    if (playlist.type == HlsPlaylist.TYPE_MEDIA) {
      enabledVariants = new Variant[] {new Variant(0, playlistUrl, 0, null, -1, -1)};
      mediaPlaylists = new HlsMediaPlaylist[1];
      lastMediaPlaylistLoadTimesMs = new long[1];
      setMediaPlaylist(0, (HlsMediaPlaylist) playlist);
    } else {
      Assertions.checkState(playlist.type == HlsPlaylist.TYPE_MASTER);
      enabledVariants = filterVariants((HlsMasterPlaylist) playlist, variantIndices);
      mediaPlaylists = new HlsMediaPlaylist[enabledVariants.length];
      lastMediaPlaylistLoadTimesMs = new long[enabledVariants.length];
    }

    int maxWidth = -1;
    int maxHeight = -1;
    // Select the first variant from the master playlist that's enabled.
    long minOriginalVariantIndex = Integer.MAX_VALUE;
    for (int i = 0; i < enabledVariants.length; i++) {
      if (enabledVariants[i].index < minOriginalVariantIndex) {
        minOriginalVariantIndex = enabledVariants[i].index;
        variantIndex = i;
      }
      maxWidth = Math.max(enabledVariants[i].width, maxWidth);
      maxHeight = Math.max(enabledVariants[i].width, maxHeight);
    }
    // TODO: We should allow the default values to be passed through the constructor.
    this.maxWidth = maxWidth > 0 ? maxWidth : 1920;
    this.maxHeight = maxHeight > 0 ? maxHeight : 1080;
  }

  public long getDurationUs() {
    return live ? TrackRenderer.UNKNOWN_TIME_US : durationUs;
  }

  /**
   * Adaptive implementations must set the maximum video dimensions on the supplied
   * {@link MediaFormat}. Other implementations do nothing.
   * <p>
   * Only called when the source is enabled.
   *
   * @param out The {@link MediaFormat} on which the maximum video dimensions should be set.
   */
  public void getMaxVideoDimensions(MediaFormat out) {
    out.setMaxVideoDimensions(maxWidth, maxHeight);
  }

  /**
   * Returns the next {@link HlsChunk} that should be loaded.
   *
   * @param previousTsChunk The previously loaded chunk that the next chunk should follow.
   * @param seekPositionUs If there is no previous chunk, this parameter must specify the seek
   *     position. If there is a previous chunk then this parameter is ignored.
   * @param playbackPositionUs The current playback position.
   * @return The next chunk to load.
   */
  public HlsChunk getChunkOperation(TsChunk previousTsChunk, long seekPositionUs,
      long playbackPositionUs) {
    if (previousTsChunk != null && (previousTsChunk.isLastChunk
        || previousTsChunk.endTimeUs - playbackPositionUs >= targetBufferDurationUs)) {
      // We're either finished, or we have the target amount of data buffered.
      return null;
    }

    int nextVariantIndex = variantIndex;
    boolean switchingVariant = false;
    boolean switchingVariantSpliced = false;
    if (adaptiveMode == ADAPTIVE_MODE_NONE) {
      // Do nothing.
    } else {
      nextVariantIndex = getNextVariantIndex(previousTsChunk, playbackPositionUs);
      switchingVariant = nextVariantIndex != variantIndex;
      switchingVariantSpliced = switchingVariant && adaptiveMode == ADAPTIVE_MODE_SPLICE;
    }

    HlsMediaPlaylist mediaPlaylist = mediaPlaylists[nextVariantIndex];
    if (mediaPlaylist == null) {
      // We don't have the media playlist for the next variant. Request it now.
      return newMediaPlaylistChunk(nextVariantIndex);
    }

    variantIndex = nextVariantIndex;
    int chunkMediaSequence = 0;
    boolean liveDiscontinuity = false;
    if (live) {
      if (previousTsChunk == null) {
        chunkMediaSequence = getLiveStartChunkMediaSequence(variantIndex);
      } else {
        chunkMediaSequence = switchingVariantSpliced
            ? previousTsChunk.chunkIndex : previousTsChunk.chunkIndex + 1;
        if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
          // If the chunk is no longer in the playlist. Skip ahead and start again.
          chunkMediaSequence = getLiveStartChunkMediaSequence(variantIndex);
          liveDiscontinuity = true;
        }
      }
    } else {
      // Not live.
      if (previousTsChunk == null) {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, seekPositionUs, true,
            true) + mediaPlaylist.mediaSequence;
      } else {
        chunkMediaSequence = switchingVariantSpliced
            ? previousTsChunk.chunkIndex : previousTsChunk.chunkIndex + 1;
      }
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (mediaPlaylist.live && shouldRerequestMediaPlaylist(variantIndex)) {
        return newMediaPlaylistChunk(variantIndex);
      } else {
        return null;
      }
    }

    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);
    Uri chunkUri = Util.getMergedUri(mediaPlaylist.baseUri, segment.url);

    // Check if encryption is specified.
    if (HlsMediaPlaylist.ENCRYPTION_METHOD_AES_128.equals(segment.encryptionMethod)) {
      Uri keyUri = Util.getMergedUri(mediaPlaylist.baseUri, segment.encryptionKeyUri);
      if (!keyUri.equals(encryptionKeyUri)) {
        // Encryption is specified and the key has changed.
        HlsChunk toReturn = newEncryptionKeyChunk(keyUri, segment.encryptionIV);
        return toReturn;
      }
      if (!Util.areEqual(segment.encryptionIV, encryptedDataSourceIv)) {
        initEncryptedDataSource(keyUri, segment.encryptionIV, encryptedDataSourceSecretKey);
      }
    } else {
      clearEncryptedDataSource();
    }

    // Configure the data source and spec for the chunk.
    DataSource dataSource = encryptedDataSource != null ? encryptedDataSource : upstreamDataSource;
    DataSpec dataSpec = new DataSpec(chunkUri, segment.byterangeOffset, segment.byterangeLength,
        null);

    // Compute start and end times, and the sequence number of the next chunk.
    long startTimeUs;
    if (live) {
      if (previousTsChunk == null) {
        startTimeUs = 0;
      } else if (switchingVariantSpliced) {
        startTimeUs = previousTsChunk.startTimeUs;
      } else {
        startTimeUs = previousTsChunk.endTimeUs;
      }
    } else /* Not live */ {
      startTimeUs = segment.startTimeUs;
    }
    long endTimeUs = startTimeUs + (long) (segment.durationSecs * 1000000);
    boolean isLastChunk = !mediaPlaylist.live && chunkIndex == mediaPlaylist.segments.size() - 1;

    // Configure the extractor that will read the chunk.
    TsExtractor extractor;
    if (previousTsChunk == null || segment.discontinuity || switchingVariant || liveDiscontinuity) {
      extractor = new TsExtractor(startTimeUs, samplePool, switchingVariantSpliced);
    } else {
      extractor = previousTsChunk.extractor;
    }

    return new TsChunk(dataSource, dataSpec, extractor, enabledVariants[variantIndex].index,
        startTimeUs, endTimeUs, chunkMediaSequence, isLastChunk);
  }

  private int getNextVariantIndex(TsChunk previousTsChunk, long playbackPositionUs) {
    int idealVariantIndex = getVariantIndexForBandwdith(
        (int) (bandwidthMeter.getBitrateEstimate() * BANDWIDTH_FRACTION));
    if (idealVariantIndex == variantIndex) {
      // We're already using the ideal variant.
      return variantIndex;
    }
    // We're not using the ideal variant for the available bandwidth, but only switch if the
    // conditions are appropriate.
    long bufferedPositionUs = previousTsChunk == null ? playbackPositionUs
        : adaptiveMode == ADAPTIVE_MODE_SPLICE ? previousTsChunk.startTimeUs
        : previousTsChunk.endTimeUs;
    long bufferedUs = bufferedPositionUs - playbackPositionUs;
    if ((idealVariantIndex > variantIndex && bufferedUs < maxBufferDurationToSwitchDownUs)
        || (idealVariantIndex < variantIndex && bufferedUs > minBufferDurationToSwitchUpUs)) {
      // Switch variant.
      return idealVariantIndex;
    }
    // Stick with the current variant for now.
    return variantIndex;
  }

  private int getVariantIndexForBandwdith(int bandwidth) {
    for (int i = 0; i < enabledVariants.length - 1; i++) {
      if (enabledVariants[i].bandwidth <= bandwidth) {
        return i;
      }
    }
    return enabledVariants.length - 1;
  }

  private boolean shouldRerequestMediaPlaylist(int variantIndex) {
    // Don't re-request media playlist more often than one-half of the target duration.
    HlsMediaPlaylist mediaPlaylist = mediaPlaylists[variantIndex];
    long timeSinceLastMediaPlaylistLoadMs =
        SystemClock.elapsedRealtime() - lastMediaPlaylistLoadTimesMs[variantIndex];
    return timeSinceLastMediaPlaylistLoadMs >= (mediaPlaylist.targetDurationSecs * 1000) / 2;
  }

  private int getLiveStartChunkMediaSequence(int variantIndex) {
    // For live start playback from the third chunk from the end.
    HlsMediaPlaylist mediaPlaylist = mediaPlaylists[variantIndex];
    int chunkIndex = mediaPlaylist.segments.size() > 3 ? mediaPlaylist.segments.size() - 3 : 0;
    return chunkIndex + mediaPlaylist.mediaSequence;
  }

  private MediaPlaylistChunk newMediaPlaylistChunk(int variantIndex) {
    Uri mediaPlaylistUri = Util.getMergedUri(baseUri, enabledVariants[variantIndex].url);
    DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null);
    Uri baseUri = Util.parseBaseUri(mediaPlaylistUri.toString());
    return new MediaPlaylistChunk(variantIndex, upstreamDataSource, dataSpec, baseUri);
  }

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNBOUNDED, null);
    return new EncryptionKeyChunk(upstreamDataSource, dataSpec, iv);
  }

  /* package */ void initEncryptedDataSource(Uri keyUri, String iv, byte[] secretKey) {
    String trimmedIv;
    if (iv.toLowerCase(Locale.getDefault()).startsWith("0x")) {
      trimmedIv = iv.substring(2);
    } else {
      trimmedIv = iv;
    }

    byte[] ivData = new BigInteger(trimmedIv, 16).toByteArray();
    byte[] ivDataWithPadding = new byte[16];
    int offset = ivData.length > 16 ? ivData.length - 16 : 0;
    System.arraycopy(ivData, offset, ivDataWithPadding, ivDataWithPadding.length - ivData.length
        + offset, ivData.length - offset);

    encryptedDataSource = new Aes128DataSource(secretKey, ivDataWithPadding, upstreamDataSource);
    encryptionKeyUri = keyUri;
    encryptedDataSourceIv = iv;
    encryptedDataSourceSecretKey = secretKey;
  }

  private void clearEncryptedDataSource() {
    encryptionKeyUri = null;
    encryptedDataSource = null;
    encryptedDataSourceIv = null;
    encryptedDataSourceSecretKey = null;
  }

  /* package */ void setMediaPlaylist(int variantIndex, HlsMediaPlaylist mediaPlaylist) {
    lastMediaPlaylistLoadTimesMs[variantIndex] = SystemClock.elapsedRealtime();
    mediaPlaylists[variantIndex] = mediaPlaylist;
    live |= mediaPlaylist.live;
    durationUs = mediaPlaylist.durationUs;
  }

  private static Variant[] filterVariants(HlsMasterPlaylist masterPlaylist, int[] variantIndices) {
    List<Variant> masterVariants = masterPlaylist.variants;
    ArrayList<Variant> enabledVariants = new ArrayList<Variant>();
    if (variantIndices != null) {
      for (int i = 0; i < variantIndices.length; i++) {
        enabledVariants.add(masterVariants.get(variantIndices[i]));
      }
    } else {
      // If variantIndices is null then all variants are initially considered.
      enabledVariants.addAll(masterVariants);
    }

    ArrayList<Variant> definiteVideoVariants = new ArrayList<Variant>();
    ArrayList<Variant> definiteAudioOnlyVariants = new ArrayList<Variant>();
    for (int i = 0; i < enabledVariants.size(); i++) {
      Variant variant = enabledVariants.get(i);
      if (variant.height > 0 || variantHasExplicitCodecWithPrefix(variant, "avc")) {
        definiteVideoVariants.add(variant);
      } else if (variantHasExplicitCodecWithPrefix(variant, "mp4a")) {
        definiteAudioOnlyVariants.add(variant);
      }
    }

    if (!definiteVideoVariants.isEmpty()) {
      // We've identified some variants as definitely containing video. Assume variants within the
      // master playlist are marked consistently, and hence that we have the full set. Filter out
      // any other variants, which are likely to be audio only.
      enabledVariants = definiteVideoVariants;
    } else if (definiteAudioOnlyVariants.size() < enabledVariants.size()) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      enabledVariants.removeAll(definiteAudioOnlyVariants);
    } else {
      // Leave the enabled variants unchanged. They're likely either all video or all audio.
    }

    Collections.sort(enabledVariants, new Variant.DecreasingBandwidthComparator());

    Variant[] enabledVariantsArray = new Variant[enabledVariants.size()];
    enabledVariants.toArray(enabledVariantsArray);
    return enabledVariantsArray;
  }

  private static boolean variantHasExplicitCodecWithPrefix(Variant variant, String prefix) {
    String[] codecs = variant.codecs;
    if (codecs == null) {
      return false;
    }
    for (int i = 0; i < codecs.length; i++) {
      if (codecs[i].startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private class MediaPlaylistChunk extends BitArrayChunk {

    @SuppressWarnings("hiding")
    private final int variantIndex;
    private final Uri playlistBaseUri;

    public MediaPlaylistChunk(int variantIndex, DataSource dataSource, DataSpec dataSpec,
        Uri playlistBaseUri) {
      super(dataSource, dataSpec, bitArray);
      this.variantIndex = variantIndex;
      this.playlistBaseUri = playlistBaseUri;
    }

    @Override
    protected void consume(BitArray data) throws IOException {
      HlsPlaylist playlist = playlistParser.parse(
          new ByteArrayInputStream(data.getData(), 0, data.bytesLeft()), null, null,
          playlistBaseUri);
      Assertions.checkState(playlist.type == HlsPlaylist.TYPE_MEDIA);
      HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) playlist;
      setMediaPlaylist(variantIndex, mediaPlaylist);
    }

  }

  private class EncryptionKeyChunk extends BitArrayChunk {

    private final String iv;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, String iv) {
      super(dataSource, dataSpec, bitArray);
      this.iv = iv;
    }

    @Override
    protected void consume(BitArray data) throws IOException {
      byte[] secretKey = new byte[data.bytesLeft()];
      data.readBytes(secretKey, 0, secretKey.length);
      initEncryptedDataSource(dataSpec.uri, iv, secretKey);
    }

  }

}
