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
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkHolder;
import com.google.android.exoplayer.chunk.DataChunk;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer.extractor.ts.PtsTimestampAdjuster;
import com.google.android.exoplayer.extractor.ts.TsExtractor;
import com.google.android.exoplayer.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer.hls.playlist.Variant;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource.InvalidResponseCodeException;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * A temporary test source of HLS chunks.
 */
public class HlsChunkSource {

  /**
   * The default time for which a media playlist should be blacklisted.
   */
  public static final long DEFAULT_PLAYLIST_BLACKLIST_MS = 60000;

  private static final String TAG = "HlsChunkSource";
  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final String MP3_FILE_EXTENSION = ".mp3";
  private static final String VTT_FILE_EXTENSION = ".vtt";
  private static final String WEBVTT_FILE_EXTENSION = ".webvtt";

  private final String baseUri;
  private final DataSource dataSource;
  private final FormatEvaluator adaptiveFormatEvaluator;
  private final Evaluation evaluation;
  private final HlsPlaylistParser playlistParser;
  private final PtsTimestampAdjusterProvider timestampAdjusterProvider;
  private final Variant[] variants;
  private final HlsMediaPlaylist[] variantPlaylists;
  private final long[] variantLastPlaylistLoadTimesMs;
  private final int initialEnabledVariantIndex;

  private boolean seenFirstExternalTrackSelection;
  private byte[] scratchSpace;
  private boolean live;
  private long durationUs;
  private IOException fatalError;

  private Uri encryptionKeyUri;
  private byte[] encryptionKey;
  private String encryptionIvString;
  private byte[] encryptionIv;

  // Properties of enabled variants.
  private Variant[] enabledVariants;
  private long[] enabledVariantBlacklistTimes;
  private boolean[] enabledVariantBlacklistFlags;

  /**
   * @param baseUri The playlist's base uri.
   * @param variants The available variants.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param timestampAdjusterProvider A provider of {@link PtsTimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   */
  public HlsChunkSource(String baseUri, Variant[] variants, DataSource dataSource,
      PtsTimestampAdjusterProvider timestampAdjusterProvider,
      FormatEvaluator adaptiveFormatEvaluator) {
    this.baseUri = baseUri;
    this.variants = variants;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.timestampAdjusterProvider = timestampAdjusterProvider;
    playlistParser = new HlsPlaylistParser();
    evaluation = new Evaluation();
    variantPlaylists = new HlsMediaPlaylist[variants.length];
    variantLastPlaylistLoadTimesMs = new long[variants.length];
    int[] initialTrackSelection = new int[variants.length];
    for (int i = 0; i < variants.length; i++) {
      initialTrackSelection[i] = i;
    }
    selectTracksInternal(initialTrackSelection, false);
    initialEnabledVariantIndex = getEnabledVariantIndex(variants[0].format);
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
   * Returns whether this source supports adaptation between its tracks.
   *
   * @return Whether this source supports adaptation between its tracks.
   */
  public boolean isAdaptive() {
    return adaptiveFormatEvaluator != null;
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
   * Returns the duration of the source, or {@link C#UNSET_TIME_US} if the duration is unknown.
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
    return variants.length;
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
    return variants[index].format;
  }

  /**
   * Selects tracks for use.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param tracks The track indices.
   */
  public void selectTracks(int[] tracks) {
    selectTracksInternal(tracks, true);
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
   * Gets the next chunk to load.
   * <p>
   * If a chunk is available then {@link ChunkHolder#chunk} is set. If the end of the stream has
   * been reached then {@link ChunkHolder#endOfStream} is set. If a chunk is not available but the
   * end of the stream has not been reached, the {@link ChunkHolder} is not modified.
   *
   * @param previous The most recently loaded media chunk.
   * @param playbackPositionUs The current playback position. If {@code previous} is null then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out A holder to populate.
   */
  public void getNextChunk(HlsMediaChunk previous, long playbackPositionUs, ChunkHolder out) {
    updateFormatEvaluation(previous, playbackPositionUs);
    int variantIndex = 0;
    for (int i = 0; i < variants.length; i++) {
      if (variants[i].format == evaluation.format) {
        variantIndex = i;
        break;
      }
    }
    boolean switchingVariant = previous != null
        && variants[variantIndex].format != previous.format;

    HlsMediaPlaylist mediaPlaylist = variantPlaylists[variantIndex];
    if (mediaPlaylist == null) {
      // We don't have the media playlist for the next variant. Request it now.
      out.chunk = newMediaPlaylistChunk(variantIndex, evaluation.trigger, evaluation.data);
      return;
    }

    int chunkMediaSequence;
    if (previous == null) {
      chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, playbackPositionUs,
          true, true) + mediaPlaylist.mediaSequence;
    } else {
      chunkMediaSequence = switchingVariant ? previous.chunkIndex : previous.chunkIndex + 1;
      if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
        fatalError = new BehindLiveWindowException();
        return;
      }
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (!mediaPlaylist.live) {
        out.endOfStream = true;
      } else if (shouldRerequestLiveMediaPlaylist(variantIndex)) {
        out.chunk = newMediaPlaylistChunk(variantIndex, evaluation.trigger, evaluation.data);
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
        out.chunk = newEncryptionKeyChunk(keyUri, segment.encryptionIV, variantIndex,
            evaluation.trigger, evaluation.data);
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
      if (previous == null) {
        startTimeUs = 0;
      } else if (switchingVariant) {
        startTimeUs = previous.startTimeUs;
      } else {
        startTimeUs = previous.endTimeUs;
      }
    } else /* Not live */ {
      startTimeUs = segment.startTimeUs;
    }
    long endTimeUs = startTimeUs + (long) (segment.durationSecs * C.MICROS_PER_SECOND);
    Format format = variants[variantIndex].format;

    // Configure the extractor that will read the chunk.
    Extractor extractor;
    boolean extractorNeedsInit = true;
    String lastPathSegment = chunkUri.getLastPathSegment();
    if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      // TODO: Inject a timestamp adjuster and use it along with ID3 PRIV tag values with owner
      // identifier com.apple.streaming.transportStreamTimestamp. This may also apply to the MP3
      // case below.
      extractor = new AdtsExtractor(startTimeUs);
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      extractor = new Mp3Extractor(startTimeUs);
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
      extractor = new WebvttExtractor(format.language, timestampAdjuster);
    } else if (previous == null
        || previous.discontinuitySequenceNumber != segment.discontinuitySequenceNumber
        || format != previous.format) {
      // MPEG-2 TS segments, but we need a new extractor.
      PtsTimestampAdjuster timestampAdjuster = timestampAdjusterProvider.getAdjuster(true,
          segment.discontinuitySequenceNumber, startTimeUs);
      if (timestampAdjuster == null) {
        // The master source has yet to instantiate an adjuster for the discontinuity sequence.
        return;
      }
      int workaroundFlags = 0;
      String codecs = variants[variantIndex].codecs;
      if (!TextUtils.isEmpty(codecs)) {
        // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
        // exist. If we know from the codec attribute that they don't exist, then we can explicitly
        // ignore them even if they're declared.
        if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
          workaroundFlags |= TsExtractor.WORKAROUND_IGNORE_AAC_STREAM;
        }
        if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
          workaroundFlags |= TsExtractor.WORKAROUND_IGNORE_H264_STREAM;
        }
      }
      extractor = new TsExtractor(timestampAdjuster, workaroundFlags);
    } else {
      // MPEG-2 TS segments, and we need to continue using the same extractor.
      extractor = previous.extractor;
      extractorNeedsInit = false;
    }

    out.chunk = new HlsMediaChunk(dataSource, dataSpec, format, evaluation.trigger, evaluation.data,
        startTimeUs, endTimeUs, chunkMediaSequence, segment.discontinuitySequenceNumber, extractor,
        extractorNeedsInit, switchingVariant, encryptionKey, encryptionIv);
  }

  /**
   * Invoked when the {@link HlsTrackStreamWrapper} has finished loading a chunk obtained from this
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
   * Invoked when the {@link HlsTrackStreamWrapper} encounters an error loading a chunk obtained
   * from this source.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param cancelable Whether the load can be canceled.
   * @param e The error.
   * @return True if the load should be canceled. False otherwise.
   */
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, IOException e) {
    if (cancelable && e instanceof InvalidResponseCodeException) {
      InvalidResponseCodeException responseCodeException = (InvalidResponseCodeException) e;
      int responseCode = responseCodeException.responseCode;
      if (responseCode == 404 || responseCode == 410) {
        int enabledVariantIndex = getEnabledVariantIndex(chunk.format);
        boolean alreadyBlacklisted = enabledVariantBlacklistFlags[enabledVariantIndex];
        enabledVariantBlacklistFlags[enabledVariantIndex] = true;
        enabledVariantBlacklistTimes[enabledVariantIndex] = SystemClock.elapsedRealtime();
        if (alreadyBlacklisted) {
          // The playlist was already blacklisted.
          Log.w(TAG, "Already blacklisted variant (" + responseCode + "): "
              + chunk.dataSpec.uri);
          return false;
        } else if (!allEnabledVariantsBlacklisted()) {
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

  private void selectTracksInternal(int[] tracks, boolean isExternal) {
    seenFirstExternalTrackSelection |= isExternal;

    // Construct and sort the enabled variants.
    enabledVariants = new Variant[tracks.length];
    for (int i = 0; i < tracks.length; i++) {
      enabledVariants[i] = variants[tracks[i]];
    }
    Arrays.sort(enabledVariants, new Comparator<Variant>() {
      private final Comparator<Format> formatComparator =
          new Format.DecreasingBandwidthComparator();
      @Override
      public int compare(Variant first, Variant second) {
        return formatComparator.compare(first.format, second.format);
      }
    });

    // Reset the enabled variant blacklist flags.
    enabledVariantBlacklistTimes = new long[enabledVariants.length];
    enabledVariantBlacklistFlags = new boolean[enabledVariants.length];

    if (enabledVariants.length > 1) {
      Format[] formats = new Format[enabledVariants.length];
      for (int i = 0; i < formats.length; i++) {
        formats[i] = enabledVariants[i].format;
      }
      // TODO[REFACTOR]: We need to disable this at some point.
      adaptiveFormatEvaluator.enable(formats);
      if (!Util.contains(formats, evaluation.format)) {
        evaluation.format = null;
      }
    } else {
      evaluation.trigger = FormatEvaluator.TRIGGER_UNKNOWN;
      evaluation.data = null;
    }
  }

  private void updateFormatEvaluation(HlsMediaChunk previous, long playbackPositionUs) {
    clearStaleBlacklistedVariants();
    if (!seenFirstExternalTrackSelection
        && !enabledVariantBlacklistFlags[initialEnabledVariantIndex]) {
      // Use the first variant prior to external track selection, unless it's been blacklisted.
      evaluation.format = variants[0].format;
    } else if (enabledVariants.length > 1) {
      long bufferedDurationUs;
      if (previous != null) {
        // Use start time of the previous chunk rather than its end time because switching format
        // will require downloading overlapping segments.
        bufferedDurationUs = Math.max(0, previous.startTimeUs - playbackPositionUs);
      } else {
        bufferedDurationUs = 0;
      }
      adaptiveFormatEvaluator.evaluateFormat(bufferedDurationUs, enabledVariantBlacklistFlags,
          evaluation);
    } else {
      evaluation.format = enabledVariants[0].format;
    }
  }

  private boolean shouldRerequestLiveMediaPlaylist(int variantIndex) {
    HlsMediaPlaylist mediaPlaylist = variantPlaylists[variantIndex];
    long timeSinceLastMediaPlaylistLoadMs =
        SystemClock.elapsedRealtime() - variantLastPlaylistLoadTimesMs[variantIndex];
    // Don't re-request media playlist more often than one-half of the target duration.
    return timeSinceLastMediaPlaylistLoadMs >= (mediaPlaylist.targetDurationSecs * 1000) / 2;
  }

  private MediaPlaylistChunk newMediaPlaylistChunk(int variantIndex, int formatEvaluatorTrigger,
      Object formatEvaluatorData) {
    Uri mediaPlaylistUri = UriUtil.resolveToUri(baseUri, variants[variantIndex].url);
    DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null,
        DataSpec.FLAG_ALLOW_GZIP);
    return new MediaPlaylistChunk(dataSource, dataSpec, variants[variantIndex].format,
        formatEvaluatorTrigger, formatEvaluatorData, scratchSpace, playlistParser, variantIndex,
        mediaPlaylistUri);
  }

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv, int variantIndex,
      int formatEvaluatorTrigger, Object formatEvaluatorData) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNBOUNDED, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(dataSource, dataSpec, variants[variantIndex].format,
        formatEvaluatorTrigger, formatEvaluatorData, scratchSpace, iv);
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
    variantLastPlaylistLoadTimesMs[variantIndex] = SystemClock.elapsedRealtime();
    variantPlaylists[variantIndex] = mediaPlaylist;
    live |= mediaPlaylist.live;
    durationUs = live ? C.UNSET_TIME_US : mediaPlaylist.durationUs;
  }

  private boolean allEnabledVariantsBlacklisted() {
    for (boolean enabledVariantBlacklistFlag : enabledVariantBlacklistFlags) {
      if (!enabledVariantBlacklistFlag) {
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
    private final Uri playlistUri;

    private HlsMediaPlaylist result;

    public MediaPlaylistChunk(DataSource dataSource, DataSpec dataSpec, Format format,
        int formatEvaluatorTrigger, Object formatEvaluatorData, byte[] scratchSpace,
        HlsPlaylistParser playlistParser, int variantIndex,
        Uri playlistUri) {
      super(dataSource, dataSpec, C.DATA_TYPE_MANIFEST, format, formatEvaluatorTrigger,
          formatEvaluatorData, scratchSpace);
      this.variantIndex = variantIndex;
      this.playlistParser = playlistParser;
      this.playlistUri = playlistUri;
    }

    @Override
    protected void consume(byte[] data, int limit) throws IOException {
      result = (HlsMediaPlaylist) playlistParser.parse(playlistUri,
          new ByteArrayInputStream(data, 0, limit));
    }

    public HlsMediaPlaylist getResult() {
      return result;
    }

  }

  private static final class EncryptionKeyChunk extends DataChunk {

    public final String iv;

    private byte[] result;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, Format format,
        int formatEvaluatorTrigger, Object formatEvaluatorData, byte[] scratchSpace, String iv) {
      super(dataSource, dataSpec, C.DATA_TYPE_DRM, format, formatEvaluatorTrigger,
          formatEvaluatorData, scratchSpace);
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
