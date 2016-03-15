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

import com.google.android.exoplayer.BehindLiveWindowException;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.chunk.BaseChunkSampleSourceEventListener;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.DataChunk;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer.extractor.ts.PtsTimestampAdjuster;
import com.google.android.exoplayer.extractor.ts.TsExtractor;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A temporary test source of HLS chunks.
 */
public class HlsChunkSource {

  /**
   * Interface definition for a callback to be notified of {@link HlsChunkSource} events.
   */
  public interface EventListener extends BaseChunkSampleSourceEventListener {}

  public static final int TYPE_DEFAULT = 0;
  public static final int TYPE_VTT = 1;

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
   * The default time for which a media playlist should be blacklisted.
   */
  public static final long DEFAULT_PLAYLIST_BLACKLIST_MS = 60000;

  private static final String TAG = "HlsChunkSource";
  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final String MP3_FILE_EXTENSION = ".mp3";
  private static final String VTT_FILE_EXTENSION = ".vtt";
  private static final String WEBVTT_FILE_EXTENSION = ".webvtt";

  private final ManifestFetcher<HlsPlaylist> manifestFetcher;
  private final int type;
  private final DataSource dataSource;
  private final FormatEvaluator adaptiveFormatEvaluator;
  private final Evaluation evaluation;
  private final HlsPlaylistParser playlistParser;
  private final PtsTimestampAdjusterProvider timestampAdjusterProvider;
  private final int adaptiveMode;

  private boolean manifestFetcherEnabled;
  private byte[] scratchSpace;
  private boolean live;
  private long durationUs;
  private IOException fatalError;
  private HlsMasterPlaylist masterPlaylist;
  private String baseUri;

  private Uri encryptionKeyUri;
  private byte[] encryptionKey;
  private String encryptionIvString;
  private byte[] encryptionIv;

  // Properties of exposed tracks.
  private Variant[] exposedVariants;

  // Properties of enabled variants.
  private Variant[] enabledVariants;
  private HlsMediaPlaylist[] enabledVariantPlaylists;
  private long[] enabledVariantLastPlaylistLoadTimesMs;
  private long[] enabledVariantBlacklistTimes;
  private boolean[] enabledVariantBlacklistFlags;
  private int selectedVariantIndex;

  /**
   * @param manifestFetcher A fetcher for the playlist.
   * @param type The type of chunk provided by the source. One of {@link #TYPE_DEFAULT} and
   *     {@link #TYPE_VTT}.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   * @param timestampAdjusterProvider A provider of {@link PtsTimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   * @param adaptiveMode The mode for switching from one variant to another. One of
   *     {@link #ADAPTIVE_MODE_NONE}, {@link #ADAPTIVE_MODE_ABRUPT} and
   *     {@link #ADAPTIVE_MODE_SPLICE}.
   */
  public HlsChunkSource(ManifestFetcher<HlsPlaylist> manifestFetcher, int type,
      DataSource dataSource, BandwidthMeter bandwidthMeter,
      PtsTimestampAdjusterProvider timestampAdjusterProvider, int adaptiveMode) {
    this.manifestFetcher = manifestFetcher;
    this.type = type;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter);
    this.timestampAdjusterProvider = timestampAdjusterProvider;
    this.adaptiveMode = adaptiveMode;
    playlistParser = new HlsPlaylistParser();
    evaluation = new Evaluation();
  }

  /**
   * If the source is currently having difficulty providing chunks, then this method throws the
   * underlying error. Otherwise does nothing.
   *
   * @throws IOException The underlying error.
   */
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    }
  }

  /**
   * Prepares the source.
   *
   * @return True if the source was prepared, false otherwise.
   */
  public boolean prepare() throws IOException {
    if (!manifestFetcherEnabled) {
      // TODO[REFACTOR]: We need to disable this at some point.
      manifestFetcher.enable();
      manifestFetcherEnabled = true;
    }
    if (masterPlaylist == null) {
      HlsPlaylist playlist = manifestFetcher.getManifest();
      if (playlist == null) {
        manifestFetcher.maybeThrowError();
        manifestFetcher.requestRefresh();
        return false;
      } else {
        baseUri = playlist.baseUri;
        if (playlist.type == HlsPlaylist.TYPE_MASTER) {
          masterPlaylist = (HlsMasterPlaylist) playlist;
        } else {
          Format format = Format.createContainerFormat("0", MimeTypes.APPLICATION_M3U8, null,
              Format.NO_VALUE);
          List<Variant> variants = new ArrayList<>();
          variants.add(new Variant(baseUri, format, null));
          masterPlaylist = new HlsMasterPlaylist(baseUri, variants,
              Collections.<Variant>emptyList());
        }
        processMasterPlaylist(masterPlaylist);
        // TODO[REFACTOR]: Come up with a sane default here.
        selectTracks(new int[] {0});
      }
    }
    return true;
  }

  /**
   * Returns whether this is a live playback.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return True if this is a live playback. False otherwise.
   */
  public boolean isLive() {
    return live;
  }

  /**
   * Returns the duration of the source, or {@link C#UNKNOWN_TIME_US} if the duration is unknown.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The number of tracks.
   */
  public long getDurationUs() {
    return durationUs;
  }

  /**
   * Returns the number of tracks exposed by the source.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The number of tracks.
   */
  public int getTrackCount() {
    return exposedVariants.length;
  }

  /**
   * Returns the format of the track at the specified index.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param index The track index.
   * @return The format of the track.
   */
  public Format getTrackFormat(int index) {
    return exposedVariants[index].format;
  }

  /**
   * Selects tracks for use.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param tracks The track indices.
   */
  public void selectTracks(int[] tracks) {
    evaluation.clear();
    enabledVariants = new Variant[tracks.length];
    enabledVariantPlaylists = new HlsMediaPlaylist[enabledVariants.length];
    enabledVariantLastPlaylistLoadTimesMs = new long[enabledVariants.length];
    enabledVariantBlacklistTimes = new long[enabledVariants.length];
    enabledVariantBlacklistFlags = new boolean[enabledVariants.length];
    // Construct and sort the enabled variants.
    for (int i = 0; i < tracks.length; i++) {
      enabledVariants[i] = exposedVariants[tracks[i]];
    }
    Arrays.sort(enabledVariants, new Comparator<Variant>() {
      private final Comparator<Format> formatComparator =
          new Format.DecreasingBandwidthComparator();
      @Override
      public int compare(Variant first, Variant second) {
        return formatComparator.compare(first.format, second.format);
      }
    });
    if (enabledVariants.length > 1) {
      // TODO[REFACTOR]: We need to disable this at some point.
      Format[] formats = new Format[enabledVariants.length];
      for (int i = 0; i < formats.length; i++) {
        formats[i] = enabledVariants[i].format;
      }
      adaptiveFormatEvaluator.enable(formats);
    }
  }

  /**
   * Notifies the source that a seek has occurred.
   * <p>
   * This method should only be called after the source has been prepared.
   */
  public void seek() {
    if (type == TYPE_DEFAULT) {
      timestampAdjusterProvider.reset();
    }
  }

  /**
   * Resets the source.
   * <p>
   * This method should only be called after the source has been prepared.
   */
  public void reset() {
    fatalError = null;
  }

  /**
   * Updates the provided {@link ChunkOperationHolder} to contain the next operation that should
   * be performed by the calling {@link HlsSampleSource}.
   *
   * @param previousTsChunk The previously loaded chunk that the next chunk should follow.
   * @param playbackPositionUs The current playback position. If previousTsChunk is null then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out The holder to populate with the result. {@link ChunkOperationHolder#queueSize} is
   *     unused.
   */
  public void getChunkOperation(TsChunk previousTsChunk, long playbackPositionUs,
      ChunkOperationHolder out) {
    int nextVariantIndex;
    boolean switchingVariantSpliced;
    if (adaptiveMode == ADAPTIVE_MODE_NONE) {
      nextVariantIndex = selectedVariantIndex;
      switchingVariantSpliced = false;
    } else {
      nextVariantIndex = getNextVariantIndex(previousTsChunk, playbackPositionUs);
      switchingVariantSpliced = previousTsChunk != null
          && enabledVariants[nextVariantIndex].format != previousTsChunk.format
          && adaptiveMode == ADAPTIVE_MODE_SPLICE;
    }

    HlsMediaPlaylist mediaPlaylist = enabledVariantPlaylists[nextVariantIndex];
    if (mediaPlaylist == null) {
      // We don't have the media playlist for the next variant. Request it now.
      out.chunk = newMediaPlaylistChunk(nextVariantIndex);
      return;
    }

    selectedVariantIndex = nextVariantIndex;
    int chunkMediaSequence = 0;
    if (live) {
      if (previousTsChunk == null) {
        chunkMediaSequence = getLiveStartChunkMediaSequence(nextVariantIndex);
      } else {
        chunkMediaSequence = switchingVariantSpliced
            ? previousTsChunk.chunkIndex : previousTsChunk.chunkIndex + 1;
        if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
          fatalError = new BehindLiveWindowException();
          return;
        }
      }
    } else {
      // Not live.
      if (previousTsChunk == null) {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, playbackPositionUs,
            true, true) + mediaPlaylist.mediaSequence;
      } else {
        chunkMediaSequence = switchingVariantSpliced
            ? previousTsChunk.chunkIndex : previousTsChunk.chunkIndex + 1;
      }
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (!mediaPlaylist.live) {
        out.endOfStream = true;
      } else if (shouldRerequestLiveMediaPlaylist(nextVariantIndex)) {
        out.chunk = newMediaPlaylistChunk(nextVariantIndex);
      }
      return;
    }

    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);
    Uri chunkUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url);

    // Check if encryption is specified.
    if (segment.isEncrypted) {
      Uri keyUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.encryptionKeyUri);
      if (!keyUri.equals(encryptionKeyUri)) {
        // Encryption is specified and the key has changed.
        out.chunk = newEncryptionKeyChunk(keyUri, segment.encryptionIV, selectedVariantIndex);
        return;
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
    int trigger = Chunk.TRIGGER_UNSPECIFIED;
    Format format = enabledVariants[selectedVariantIndex].format;

    // Configure the extractor that will read the chunk.
    HlsExtractorWrapper extractorWrapper;
    String lastPathSegment = chunkUri.getLastPathSegment();
    if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      // TODO: Inject a timestamp adjuster and use it along with ID3 PRIV tag values with owner
      // identifier com.apple.streaming.transportStreamTimestamp. This may also apply to the MP3
      // case below.
      Extractor extractor = new AdtsExtractor(startTimeUs);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariantSpliced);
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      Extractor extractor = new Mp3Extractor(startTimeUs);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariantSpliced);
    } else if (lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      PtsTimestampAdjuster timestampAdjuster = timestampAdjusterProvider.getAdjuster(false,
          segment.discontinuitySequenceNumber, startTimeUs);
      if (timestampAdjuster == null) {
        // The master source has yet to instantiate an adjuster for the discontinuity sequence.
        // TODO: There's probably an edge case if the master starts playback at a chunk belonging to
        // a discontinuity sequence greater than the one that this source is trying to start at.
        return;
      }
      Extractor extractor = new WebvttExtractor(format.language, timestampAdjuster);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariantSpliced);
    } else if (previousTsChunk == null
        || previousTsChunk.discontinuitySequenceNumber != segment.discontinuitySequenceNumber
        || format != previousTsChunk.format) {
      // MPEG-2 TS segments, but we need a new extractor.
      PtsTimestampAdjuster timestampAdjuster = timestampAdjusterProvider.getAdjuster(true,
          segment.discontinuitySequenceNumber, startTimeUs);
      if (timestampAdjuster == null) {
        // The master source has yet to instantiate an adjuster for the discontinuity sequence.
        return;
      }
      int workaroundFlags = 0;
      String codecs = enabledVariants[selectedVariantIndex].codecs;
      if (!TextUtils.isEmpty(codecs)) {
        // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
        // exist. If we know from the codec attribute that they don't exist, then we can explicitly
        // ignore them even if they're declared.
        if (MimeTypes.getAudioMediaMimeType(codecs) != MimeTypes.AUDIO_AAC) {
          workaroundFlags |= TsExtractor.WORKAROUND_IGNORE_AAC_STREAM;
        }
        if (MimeTypes.getVideoMediaMimeType(codecs) != MimeTypes.VIDEO_H264) {
          workaroundFlags |= TsExtractor.WORKAROUND_IGNORE_H264_STREAM;
        }
      }
      Extractor extractor = new TsExtractor(timestampAdjuster, workaroundFlags);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariantSpliced);
    } else {
      // MPEG-2 TS segments, and we need to continue using the same extractor.
      extractorWrapper = previousTsChunk.extractorWrapper;
    }

    out.chunk = new TsChunk(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs,
        chunkMediaSequence, segment.discontinuitySequenceNumber, extractorWrapper, encryptionKey,
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
    if (chunk.bytesLoaded() == 0
        && (chunk instanceof TsChunk || chunk instanceof MediaPlaylistChunk
            || chunk instanceof EncryptionKeyChunk)
        && (e instanceof InvalidResponseCodeException)) {
      InvalidResponseCodeException responseCodeException = (InvalidResponseCodeException) e;
      int responseCode = responseCodeException.responseCode;
      if (responseCode == 404 || responseCode == 410) {
        int enabledVariantIndex;
        if (chunk instanceof TsChunk) {
          TsChunk tsChunk = (TsChunk) chunk;
          enabledVariantIndex = getEnabledVariantIndex(tsChunk.format);
        } else if (chunk instanceof MediaPlaylistChunk) {
          MediaPlaylistChunk playlistChunk = (MediaPlaylistChunk) chunk;
          enabledVariantIndex = playlistChunk.variantIndex;
        } else {
          EncryptionKeyChunk encryptionChunk = (EncryptionKeyChunk) chunk;
          enabledVariantIndex = encryptionChunk.variantIndex;
        }
        boolean alreadyBlacklisted = enabledVariantBlacklistFlags[enabledVariantIndex];
        enabledVariantBlacklistFlags[enabledVariantIndex] = true;
        enabledVariantBlacklistTimes[enabledVariantIndex] = SystemClock.elapsedRealtime();
        if (alreadyBlacklisted) {
          // The playlist was already blacklisted.
          Log.w(TAG, "Already blacklisted variant (" + responseCode + "): "
              + chunk.dataSpec.uri);
          return false;
        } else if (!allVariantsBlacklisted()) {
          // We've handled the 404/410 by blacklisting the variant.
          Log.w(TAG, "Blacklisted variant (" + responseCode + "): "
              + chunk.dataSpec.uri);
          return true;
        } else {
          // This was the last non-blacklisted playlist. Don't blacklist it.
          Log.w(TAG, "Final variant not blacklisted (" + responseCode + "): "
              + chunk.dataSpec.uri);
          enabledVariantBlacklistFlags[enabledVariantIndex] = false;
          return false;
        }
      }
    }
    return false;
  }

  // Private methods.

  private void processMasterPlaylist(HlsMasterPlaylist playlist) {
    if (type == TYPE_VTT) {
      List<Variant> subtitleVariants = playlist.subtitles;
      if (subtitleVariants != null) {
        exposedVariants = new Variant[subtitleVariants.size()];
        subtitleVariants.toArray(exposedVariants);
      } else {
        exposedVariants = new Variant[0];
      }
      return;
    }

    // Type is TYPE_DEFAULT.
    List<Variant> enabledVariantList = new ArrayList<>(playlist.variants);
    ArrayList<Variant> definiteVideoVariants = new ArrayList<>();
    ArrayList<Variant> definiteAudioOnlyVariants = new ArrayList<>();
    for (int i = 0; i < enabledVariantList.size(); i++) {
      Variant variant = enabledVariantList.get(i);
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
      enabledVariantList = definiteVideoVariants;
    } else if (definiteAudioOnlyVariants.size() < enabledVariantList.size()) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      enabledVariantList.removeAll(definiteAudioOnlyVariants);
    } else {
      // Leave the enabled variants unchanged. They're likely either all video or all audio.
    }

    exposedVariants = new Variant[enabledVariantList.size()];
    enabledVariantList.toArray(exposedVariants);
  }

  private static boolean variantHasExplicitCodecWithPrefix(Variant variant, String prefix) {
    String codecs = variant.codecs;
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

  private int getNextVariantIndex(TsChunk previousTsChunk, long playbackPositionUs) {
    clearStaleBlacklistedVariants();
    long switchingOverlapUs;
    List<TsChunk> queue;
    if (previousTsChunk != null) {
      switchingOverlapUs = adaptiveMode == ADAPTIVE_MODE_SPLICE
          ? previousTsChunk.endTimeUs - previousTsChunk.startTimeUs : 0;
      queue = Collections.singletonList(previousTsChunk);
    } else {
      switchingOverlapUs = 0;
      queue = Collections.<TsChunk>emptyList();
    }
    if (enabledVariants.length > 1) {
      adaptiveFormatEvaluator.evaluate(queue, playbackPositionUs, switchingOverlapUs,
          enabledVariantBlacklistFlags, evaluation);
    } else {
      evaluation.format = enabledVariants[0].format;
      evaluation.trigger = Chunk.TRIGGER_MANUAL;
    }
    for (int i = 0; i < enabledVariants.length; i++) {
      if (enabledVariants[i].format == evaluation.format) {
        return i;
      }
    }
    throw new IllegalStateException();
  }

  private boolean shouldRerequestLiveMediaPlaylist(int nextVariantIndex) {
    // Don't re-request media playlist more often than one-half of the target duration.
    HlsMediaPlaylist mediaPlaylist = enabledVariantPlaylists[nextVariantIndex];
    long timeSinceLastMediaPlaylistLoadMs =
        SystemClock.elapsedRealtime() - enabledVariantLastPlaylistLoadTimesMs[nextVariantIndex];
    return timeSinceLastMediaPlaylistLoadMs >= (mediaPlaylist.targetDurationSecs * 1000) / 2;
  }

  private int getLiveStartChunkMediaSequence(int variantIndex) {
    // For live start playback from the third chunk from the end.
    HlsMediaPlaylist mediaPlaylist = enabledVariantPlaylists[variantIndex];
    int chunkIndex = mediaPlaylist.segments.size() > 3 ? mediaPlaylist.segments.size() - 3 : 0;
    return chunkIndex + mediaPlaylist.mediaSequence;
  }

  private MediaPlaylistChunk newMediaPlaylistChunk(int variantIndex) {
    Uri mediaPlaylistUri = UriUtil.resolveToUri(baseUri, enabledVariants[variantIndex].url);
    DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null,
        DataSpec.FLAG_ALLOW_GZIP);
    return new MediaPlaylistChunk(dataSource, dataSpec, scratchSpace, playlistParser, variantIndex,
        mediaPlaylistUri.toString());
  }

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv, int variantIndex) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNBOUNDED, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(dataSource, dataSpec, scratchSpace, iv, variantIndex);
  }

  private void setEncryptionData(Uri keyUri, String iv, byte[] secretKey) {
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

  private void setMediaPlaylist(int variantIndex, HlsMediaPlaylist mediaPlaylist) {
    enabledVariantLastPlaylistLoadTimesMs[variantIndex] = SystemClock.elapsedRealtime();
    enabledVariantPlaylists[variantIndex] = mediaPlaylist;
    live |= mediaPlaylist.live;
    durationUs = live ? C.UNKNOWN_TIME_US : mediaPlaylist.durationUs;
  }

  private boolean allVariantsBlacklisted() {
    for (int i = 0; i < enabledVariantBlacklistFlags.length; i++) {
      if (!enabledVariantBlacklistFlags[i]) {
        return false;
      }
    }
    return true;
  }

  private void clearStaleBlacklistedVariants() {
    long currentTime = SystemClock.elapsedRealtime();
    for (int i = 0; i < enabledVariantBlacklistFlags.length; i++) {
      if (enabledVariantBlacklistFlags[i]
          && currentTime - enabledVariantBlacklistTimes[i] > DEFAULT_PLAYLIST_BLACKLIST_MS) {
        enabledVariantBlacklistFlags[i] = false;
      }
    }
  }

  private int getEnabledVariantIndex(Format format) {
    for (int i = 0; i < enabledVariants.length; i++) {
      if (enabledVariants[i].format == format) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  // Private classes.

  private static final class MediaPlaylistChunk extends DataChunk {

    public final int variantIndex;

    private final HlsPlaylistParser playlistParser;
    private final String playlistUrl;

    private HlsMediaPlaylist result;

    public MediaPlaylistChunk(DataSource dataSource, DataSpec dataSpec, byte[] scratchSpace,
        HlsPlaylistParser playlistParser, int variantIndex, String playlistUrl) {
      super(dataSource, dataSpec, Chunk.TYPE_MANIFEST, Chunk.TRIGGER_UNSPECIFIED, null,
          Chunk.NO_PARENT_ID, scratchSpace);
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

  private static final class EncryptionKeyChunk extends DataChunk {

    public final String iv;
    public final int variantIndex;

    private byte[] result;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, byte[] scratchSpace,
        String iv, int variantIndex) {
      super(dataSource, dataSpec, Chunk.TYPE_DRM, Chunk.TRIGGER_UNSPECIFIED, null,
          Chunk.NO_PARENT_ID, scratchSpace);
      this.iv = iv;
      this.variantIndex = variantIndex;
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
