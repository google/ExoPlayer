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
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.chunk.BaseChunkSampleSourceEventListener;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.DataChunk;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer.extractor.ts.TsExtractor;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.UriUtil;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
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
   * Interface definition for a callback to be notified of {@link HlsChunkSource} events.
   */
  public interface EventListener extends BaseChunkSampleSourceEventListener {}

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
   * The default minimum duration of media that needs to be buffered for a switch to a higher
   * quality variant to be considered.
   */
  public static final long DEFAULT_MIN_BUFFER_TO_SWITCH_UP_MS = 5000;

  /**
   * The default maximum duration of media that needs to be buffered for a switch to a lower
   * quality variant to be considered.
   */
  public static final long DEFAULT_MAX_BUFFER_TO_SWITCH_DOWN_MS = 20000;

  /**
   * The default time for which a media playlist should be blacklisted.
   */
  public static final long DEFAULT_PLAYLIST_BLACKLIST_MS = 60000;

  private static final String TAG = "HlsChunkSource";
  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final float BANDWIDTH_FRACTION = 0.8f;

  private final DataSource dataSource;
  private final HlsPlaylistParser playlistParser;
  private final List<Variant> variants;
  private final Format[] enabledFormats;
  private final BandwidthMeter bandwidthMeter;
  private final int adaptiveMode;
  private final String baseUri;
  private final int maxWidth;
  private final int maxHeight;
  private final long minBufferDurationToSwitchUpUs;
  private final long maxBufferDurationToSwitchDownUs;
  private final AudioCapabilities audioCapabilities;

  /* package */ byte[] scratchSpace;
  /* package */ final HlsMediaPlaylist[] mediaPlaylists;
  /* package */ final long[] mediaPlaylistBlacklistTimesMs;
  /* package */ final long[] lastMediaPlaylistLoadTimesMs;
  /* package */ boolean live;
  /* package */ long durationUs;

  private int formatIndex;
  private Uri encryptionKeyUri;
  private byte[] encryptionKey;
  private String encryptionIvString;
  private byte[] encryptionIv;

  public HlsChunkSource(DataSource dataSource, String playlistUrl, HlsPlaylist playlist,
      BandwidthMeter bandwidthMeter, int[] variantIndices, int adaptiveMode,
      AudioCapabilities audioCapabilities) {
    this(dataSource, playlistUrl, playlist, bandwidthMeter, variantIndices, adaptiveMode,
        DEFAULT_MIN_BUFFER_TO_SWITCH_UP_MS, DEFAULT_MAX_BUFFER_TO_SWITCH_DOWN_MS,
        audioCapabilities);
  }

  /**
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param playlistUrl The playlist URL.
   * @param playlist The hls playlist.
   * @param bandwidthMeter provides an estimate of the currently available bandwidth.
   * @param variantIndices If {@code playlist} is a {@link HlsMasterPlaylist}, the subset of variant
   *     indices to consider, or null to consider all of the variants. For other playlist types
   *     this parameter is ignored.
   * @param adaptiveMode The mode for switching from one variant to another. One of
   *     {@link #ADAPTIVE_MODE_NONE}, {@link #ADAPTIVE_MODE_ABRUPT} and
   *     {@link #ADAPTIVE_MODE_SPLICE}.
   * @param minBufferDurationToSwitchUpMs The minimum duration of media that needs to be buffered
   *     for a switch to a higher quality variant to be considered.
   * @param maxBufferDurationToSwitchDownMs The maximum duration of media that needs to be buffered
   *     for a switch to a lower quality variant to be considered.
   * @param audioCapabilities The audio capabilities for playback on this device, or {@code null} if
   *     the default capabilities should be assumed.
   */
  public HlsChunkSource(DataSource dataSource, String playlistUrl, HlsPlaylist playlist,
      BandwidthMeter bandwidthMeter, int[] variantIndices, int adaptiveMode,
      long minBufferDurationToSwitchUpMs, long maxBufferDurationToSwitchDownMs,
      AudioCapabilities audioCapabilities) {
    this.dataSource = dataSource;
    this.bandwidthMeter = bandwidthMeter;
    this.adaptiveMode = adaptiveMode;
    this.audioCapabilities = audioCapabilities;
    minBufferDurationToSwitchUpUs = minBufferDurationToSwitchUpMs * 1000;
    maxBufferDurationToSwitchDownUs = maxBufferDurationToSwitchDownMs * 1000;
    baseUri = playlist.baseUri;
    playlistParser = new HlsPlaylistParser();

    if (playlist.type == HlsPlaylist.TYPE_MEDIA) {
      variants = Collections.singletonList(new Variant(0, playlistUrl, 0, null, -1, -1));
      variantIndices = null;
      mediaPlaylists = new HlsMediaPlaylist[1];
      mediaPlaylistBlacklistTimesMs = new long[1];
      lastMediaPlaylistLoadTimesMs = new long[1];
      setMediaPlaylist(0, (HlsMediaPlaylist) playlist);
    } else {
      variants = ((HlsMasterPlaylist) playlist).variants;
      int variantCount = variants.size();
      mediaPlaylists = new HlsMediaPlaylist[variantCount];
      mediaPlaylistBlacklistTimesMs = new long[variantCount];
      lastMediaPlaylistLoadTimesMs = new long[variantCount];
    }

    enabledFormats = buildEnabledFormats(variants, variantIndices);

    int maxWidth = -1;
    int maxHeight = -1;
    // Select the first variant from the master playlist that's enabled.
    int minEnabledVariantIndex = Integer.MAX_VALUE;
    for (int i = 0; i < enabledFormats.length; i++) {
      int variantIndex = getVariantIndex(enabledFormats[i]);
      if (variantIndex < minEnabledVariantIndex) {
        minEnabledVariantIndex = variantIndex;
        formatIndex = i;
      }
      maxWidth = Math.max(enabledFormats[i].width, maxWidth);
      maxHeight = Math.max(enabledFormats[i].height, maxHeight);
    }
    // TODO: We should allow the default values to be passed through the constructor.
    this.maxWidth = maxWidth > 0 ? maxWidth : 1920;
    this.maxHeight = maxHeight > 0 ? maxHeight : 1080;
  }

  public long getDurationUs() {
    return live ? C.UNKNOWN_TIME_US : durationUs;
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
   * Returns the next {@link Chunk} that should be loaded.
   *
   * @param previousTsChunk The previously loaded chunk that the next chunk should follow.
   * @param seekPositionUs If there is no previous chunk, this parameter must specify the seek
   *     position. If there is a previous chunk then this parameter is ignored.
   * @param playbackPositionUs The current playback position.
   * @return The next chunk to load.
   */
  public Chunk getChunkOperation(TsChunk previousTsChunk, long seekPositionUs,
      long playbackPositionUs) {
    int nextFormatIndex;
    boolean switchingVariantSpliced;
    if (adaptiveMode == ADAPTIVE_MODE_NONE) {
      nextFormatIndex = formatIndex;
      switchingVariantSpliced = false;
    } else {
      nextFormatIndex = getNextFormatIndex(previousTsChunk, playbackPositionUs);
      switchingVariantSpliced = nextFormatIndex != formatIndex
          && adaptiveMode == ADAPTIVE_MODE_SPLICE;
    }

    int variantIndex = getVariantIndex(enabledFormats[nextFormatIndex]);
    HlsMediaPlaylist mediaPlaylist = mediaPlaylists[variantIndex];
    if (mediaPlaylist == null) {
      // We don't have the media playlist for the next variant. Request it now.
      return newMediaPlaylistChunk(variantIndex);
    }

    formatIndex = nextFormatIndex;
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
    Uri chunkUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url);

    // Check if encryption is specified.
    if (segment.isEncrypted) {
      Uri keyUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.encryptionKeyUri);
      if (!keyUri.equals(encryptionKeyUri)) {
        // Encryption is specified and the key has changed.
        Chunk toReturn = newEncryptionKeyChunk(keyUri, segment.encryptionIV);
        return toReturn;
      }
      if (!Util.areEqual(segment.encryptionIV, encryptionIvString)) {
        setEncryptionData(keyUri, segment.encryptionIV, encryptionKey);
      }
    } else {
      clearEncryptionData();
    }

    // Configure the data source and spec for the chunk.
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
    long endTimeUs = startTimeUs + (long) (segment.durationSecs * C.MICROS_PER_SECOND);
    boolean isLastChunk = !mediaPlaylist.live && chunkIndex == mediaPlaylist.segments.size() - 1;
    int trigger = Chunk.TRIGGER_UNSPECIFIED;
    Format format = enabledFormats[formatIndex];

    // Configure the extractor that will read the chunk.
    HlsExtractorWrapper extractorWrapper;
    if (previousTsChunk == null || segment.discontinuity || !format.equals(previousTsChunk.format)
        || liveDiscontinuity) {
      Extractor extractor = chunkUri.getLastPathSegment().endsWith(AAC_FILE_EXTENSION)
          ? new AdtsExtractor(startTimeUs)
          : new TsExtractor(startTimeUs, audioCapabilities);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariantSpliced);
    } else {
      extractorWrapper = previousTsChunk.extractorWrapper;
    }

    return new TsChunk(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs,
        chunkMediaSequence, isLastChunk, extractorWrapper, encryptionKey,
        encryptionIv);
  }

  /**
   * Invoked when the {@link HlsSampleSource} has finished loading a chunk obtained from this
   * source.
   *
   * @param chunk The chunk whose load has been completed.
   */
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof MediaPlaylistChunk) {
      MediaPlaylistChunk mediaPlaylistChunk = (MediaPlaylistChunk) chunk;
      scratchSpace = mediaPlaylistChunk.getDataHolder();
      setMediaPlaylist(mediaPlaylistChunk.variantIndex, mediaPlaylistChunk.getResult());
    } else if (chunk instanceof EncryptionKeyChunk) {
      EncryptionKeyChunk encryptionKeyChunk = (EncryptionKeyChunk) chunk;
      scratchSpace = encryptionKeyChunk.getDataHolder();
      setEncryptionData(encryptionKeyChunk.dataSpec.uri, encryptionKeyChunk.iv,
          encryptionKeyChunk.getResult());
    }
  }

  /**
   * Invoked when the {@link HlsSampleSource} encounters an error loading a chunk obtained from
   * this source.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param e The error.
   * @return True if the error was handled by the source. False otherwise.
   */
  public boolean onChunkLoadError(Chunk chunk, IOException e) {
    if (chunk.bytesLoaded() == 0 && (chunk instanceof MediaPlaylistChunk)
        && (e instanceof InvalidResponseCodeException)) {
      InvalidResponseCodeException responseCodeException = (InvalidResponseCodeException) e;
      int responseCode = responseCodeException.responseCode;
      if (responseCode == 404 || responseCode == 410) {
        MediaPlaylistChunk playlistChunk = (MediaPlaylistChunk) chunk;
        mediaPlaylistBlacklistTimesMs[playlistChunk.variantIndex] = SystemClock.elapsedRealtime();
        if (!allPlaylistsBlacklisted()) {
          // We've handled the 404/410 by blacklisting the playlist.
          Log.w(TAG, "Blacklisted playlist (" + responseCode + "): "
              + playlistChunk.dataSpec.uri);
          return true;
        } else {
          // This was the last non-blacklisted playlist. Don't blacklist it.
          Log.w(TAG, "Final playlist not blacklisted (" + responseCode + "): "
              + playlistChunk.dataSpec.uri);
          mediaPlaylistBlacklistTimesMs[playlistChunk.variantIndex] = 0;
          return false;
        }
      }
    }
    return false;
  }

  private int getNextFormatIndex(TsChunk previousTsChunk, long playbackPositionUs) {
    clearStaleBlacklistedPlaylists();
    if (previousTsChunk == null) {
      // Don't consider switching if we don't have a previous chunk.
      return formatIndex;
    }
    long bitrateEstimate = bandwidthMeter.getBitrateEstimate();
    if (bitrateEstimate == BandwidthMeter.NO_ESTIMATE) {
      // Don't consider switching if we don't have a bandwidth estimate.
      return formatIndex;
    }
    int idealFormatIndex = getFormatIndexForBandwidth(
        (int) (bitrateEstimate * BANDWIDTH_FRACTION));
    if (idealFormatIndex == formatIndex) {
      // We're already using the ideal format.
      return formatIndex;
    }
    // We're not using the ideal format for the available bandwidth, but only switch if the
    // conditions are appropriate.
    long bufferedPositionUs = adaptiveMode == ADAPTIVE_MODE_SPLICE ? previousTsChunk.startTimeUs
        : previousTsChunk.endTimeUs;
    long bufferedUs = bufferedPositionUs - playbackPositionUs;
    if (mediaPlaylistBlacklistTimesMs[formatIndex] != 0
        || (idealFormatIndex > formatIndex && bufferedUs < maxBufferDurationToSwitchDownUs)
        || (idealFormatIndex < formatIndex && bufferedUs > minBufferDurationToSwitchUpUs)) {
      // Switch format.
      return idealFormatIndex;
    }
    // Stick with the current format for now.
    return formatIndex;
  }

  private int getFormatIndexForBandwidth(int bitrate) {
    int lowestQualityEnabledFormatIndex = -1;
    for (int i = 0; i < enabledFormats.length; i++) {
      int variantIndex = getVariantIndex(enabledFormats[i]);
      if (mediaPlaylistBlacklistTimesMs[variantIndex] == 0) {
        if (enabledFormats[i].bitrate <= bitrate) {
          return i;
        }
        lowestQualityEnabledFormatIndex = i;
      }
    }
    // At least one format should always be enabled.
    Assertions.checkState(lowestQualityEnabledFormatIndex != -1);
    return lowestQualityEnabledFormatIndex;
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
    Uri mediaPlaylistUri = UriUtil.resolveToUri(baseUri, variants.get(variantIndex).url);
    DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null,
        DataSpec.FLAG_ALLOW_GZIP);
    return new MediaPlaylistChunk(dataSource, dataSpec, scratchSpace, playlistParser, variantIndex,
        mediaPlaylistUri.toString());
  }

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNBOUNDED, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(dataSource, dataSpec, scratchSpace, iv);
  }

  /* package */ void setEncryptionData(Uri keyUri, String iv, byte[] secretKey) {
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

    encryptionKeyUri = keyUri;
    encryptionKey = secretKey;
    encryptionIvString = iv;
    encryptionIv = ivDataWithPadding;
  }

  private void clearEncryptionData() {
    encryptionKeyUri = null;
    encryptionKey = null;
    encryptionIvString = null;
    encryptionIv = null;
  }

  /* package */ void setMediaPlaylist(int variantIndex, HlsMediaPlaylist mediaPlaylist) {
    lastMediaPlaylistLoadTimesMs[variantIndex] = SystemClock.elapsedRealtime();
    mediaPlaylists[variantIndex] = mediaPlaylist;
    live |= mediaPlaylist.live;
    durationUs = mediaPlaylist.durationUs;
  }

  private static Format[] buildEnabledFormats(List<Variant> variants, int[] variantIndices) {
    ArrayList<Variant> enabledVariants = new ArrayList<Variant>();
    if (variantIndices != null) {
      for (int i = 0; i < variantIndices.length; i++) {
        enabledVariants.add(variants.get(variantIndices[i]));
      }
    } else {
      // If variantIndices is null then all variants are initially considered.
      enabledVariants.addAll(variants);
    }

    ArrayList<Variant> definiteVideoVariants = new ArrayList<Variant>();
    ArrayList<Variant> definiteAudioOnlyVariants = new ArrayList<Variant>();
    for (int i = 0; i < enabledVariants.size(); i++) {
      Variant variant = enabledVariants.get(i);
      if (variant.format.height > 0 || variantHasExplicitCodecWithPrefix(variant, "avc")) {
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

    Format[] enabledFormats = new Format[enabledVariants.size()];
    for (int i = 0; i < enabledFormats.length; i++) {
      enabledFormats[i] = enabledVariants.get(i).format;
    }

    Arrays.sort(enabledFormats, new Format.DecreasingBandwidthComparator());
    return enabledFormats;
  }

  private static boolean variantHasExplicitCodecWithPrefix(Variant variant, String prefix) {
    String codecs = variant.format.codecs;
    if (TextUtils.isEmpty(codecs)) {
      return false;
    }
    String[] codecArray = codecs.split("(\\s*,\\s*)|(\\s*$)");
    for (int i = 0; i < codecArray.length; i++) {
      if (codecArray[i].startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private boolean allPlaylistsBlacklisted() {
    for (int i = 0; i < mediaPlaylistBlacklistTimesMs.length; i++) {
      if (mediaPlaylistBlacklistTimesMs[i] == 0) {
        return false;
      }
    }
    return true;
  }

  private void clearStaleBlacklistedPlaylists() {
    long currentTime = SystemClock.elapsedRealtime();
    for (int i = 0; i < mediaPlaylistBlacklistTimesMs.length; i++) {
      if (mediaPlaylistBlacklistTimesMs[i] != 0
          && currentTime - mediaPlaylistBlacklistTimesMs[i] > DEFAULT_PLAYLIST_BLACKLIST_MS) {
        mediaPlaylistBlacklistTimesMs[i] = 0;
      }
    }
  }

  private int getVariantIndex(Format format) {
    for (int i = 0; i < variants.size(); i++) {
      if (variants.get(i).format.equals(format)) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  private static class MediaPlaylistChunk extends DataChunk {

    public final int variantIndex;

    private final HlsPlaylistParser playlistParser;
    private final String playlistUrl;

    private HlsMediaPlaylist result;

    public MediaPlaylistChunk(DataSource dataSource, DataSpec dataSpec, byte[] scratchSpace,
        HlsPlaylistParser playlistParser, int variantIndex, String playlistUrl) {
      super(dataSource, dataSpec, Chunk.TYPE_MANIFEST, Chunk.TRIGGER_UNSPECIFIED, null,
          scratchSpace);
      this.variantIndex = variantIndex;
      this.playlistParser = playlistParser;
      this.playlistUrl = playlistUrl;
    }

    @Override
    protected void consume(byte[] data, int limit) throws IOException {
      result = (HlsMediaPlaylist) playlistParser.parse(playlistUrl,
          new ByteArrayInputStream(data, 0, limit));
    }

    public HlsMediaPlaylist getResult() {
      return result;
    }

  }

  private static class EncryptionKeyChunk extends DataChunk {

    public final String iv;

    private byte[] result;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, byte[] scratchSpace,
        String iv) {
      super(dataSource, dataSpec, Chunk.TYPE_DRM, Chunk.TRIGGER_UNSPECIFIED, null, scratchSpace);
      this.iv = iv;
    }

    @Override
    protected void consume(byte[] data, int limit) throws IOException {
      result = Arrays.copyOf(data, limit);
    }

    public byte[] getResult() {
      return result;
    }

  }

}
