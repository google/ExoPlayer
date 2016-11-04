/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.TimestampAdjuster;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkedTrackBlacklistUtil;
import com.google.android.exoplayer2.source.chunk.DataChunk;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.UriUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;

/**
 * Source of Hls (possibly adaptive) chunks.
 */
/* package */ class HlsChunkSource {

  /**
   * Chunk holder that allows the scheduling of retries.
   */
  public static final class HlsChunkHolder {

    public HlsChunkHolder() {
      clear();
    }

    /**
     * The chunk to be loaded next.
     */
    public Chunk chunk;

    /**
     * Indicates that the end of the stream has been reached.
     */
    public boolean endOfStream;

    /**
     * Indicates that the chunk source is waiting for the referred playlist to be refreshed.
     */
    public HlsMasterPlaylist.HlsUrl playlist;

    /**
     * Clears the holder.
     */
    public void clear() {
      chunk = null;
      endOfStream = false;
      playlist = null;
    }

  }

  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final String AC3_FILE_EXTENSION = ".ac3";
  private static final String EC3_FILE_EXTENSION = ".ec3";
  private static final String MP3_FILE_EXTENSION = ".mp3";
  private static final String MP4_FILE_EXTENSION = ".mp4";
  private static final String VTT_FILE_EXTENSION = ".vtt";
  private static final String WEBVTT_FILE_EXTENSION = ".webvtt";

  private final DataSource dataSource;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final HlsMasterPlaylist.HlsUrl[] variants;
  private final HlsPlaylistTracker playlistTracker;
  private final TrackGroup trackGroup;

  private byte[] scratchSpace;
  private IOException fatalError;

  private HlsInitializationChunk lastLoadedInitializationChunk;
  private Uri encryptionKeyUri;
  private byte[] encryptionKey;
  private String encryptionIvString;
  private byte[] encryptionIv;

  // Note: The track group in the selection is typically *not* equal to trackGroup. This is due to
  // the way in which HlsSampleStreamWrapper generates track groups. Use only index based methods
  // in TrackSelection to avoid unexpected behavior.
  private TrackSelection trackSelection;

  /**
   * @param playlistTracker The {@link HlsPlaylistTracker} from which to obtain media playlists.
   * @param variants The available variants.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param timestampAdjusterProvider A provider of {@link TimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   */
  public HlsChunkSource(HlsPlaylistTracker playlistTracker, HlsMasterPlaylist.HlsUrl[] variants,
      DataSource dataSource, TimestampAdjusterProvider timestampAdjusterProvider) {
    this.playlistTracker = playlistTracker;
    this.variants = variants;
    this.dataSource = dataSource;
    this.timestampAdjusterProvider = timestampAdjusterProvider;

    Format[] variantFormats = new Format[variants.length];
    int[] initialTrackSelection = new int[variants.length];
    for (int i = 0; i < variants.length; i++) {
      variantFormats[i] = variants[i].format;
      initialTrackSelection[i] = i;
    }
    trackGroup = new TrackGroup(variantFormats);
    trackSelection = new InitializationTrackSelection(trackGroup, initialTrackSelection);
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
   * Returns the track group exposed by the source.
   */
  public TrackGroup getTrackGroup() {
    return trackGroup;
  }

  /**
   * Selects tracks for use.
   *
   * @param trackSelection The track selection.
   */
  public void selectTracks(TrackSelection trackSelection) {
    this.trackSelection = trackSelection;
  }

  /**
   * Resets the source.
   */
  public void reset() {
    fatalError = null;
  }

  /**
   * Returns the next chunk to load.
   * <p>
   * If a chunk is available then {@link HlsChunkHolder#chunk} is set. If the end of the stream has
   * been reached then {@link HlsChunkHolder#endOfStream} is set. If a chunk is not available but
   * the end of the stream has not been reached, {@link HlsChunkHolder#playlist} is set to
   * contain the {@link HlsMasterPlaylist.HlsUrl} that refers to the playlist that needs refreshing.
   *
   * @param previous The most recently loaded media chunk.
   * @param playbackPositionUs The current playback position. If {@code previous} is null then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out A holder to populate.
   */
  public void getNextChunk(HlsMediaChunk previous, long playbackPositionUs, HlsChunkHolder out) {
    int oldVariantIndex = previous == null ? C.INDEX_UNSET
        : trackGroup.indexOf(previous.trackFormat);
    // Use start time of the previous chunk rather than its end time because switching format will
    // require downloading overlapping segments.
    long bufferedDurationUs = previous == null ? 0
        : Math.max(0, previous.getAdjustedStartTimeUs() - playbackPositionUs);
    trackSelection.updateSelectedTrack(bufferedDurationUs);
    int newVariantIndex = trackSelection.getSelectedIndexInTrackGroup();

    boolean switchingVariant = oldVariantIndex != newVariantIndex;
    HlsMediaPlaylist mediaPlaylist = playlistTracker.getPlaylistSnapshot(variants[newVariantIndex]);
    if (mediaPlaylist == null) {
      out.playlist = variants[newVariantIndex];
      // Retry when playlist is refreshed.
      return;
    }

    int chunkMediaSequence;
    if (previous == null || switchingVariant) {
      long targetPositionUs = previous == null ? playbackPositionUs : previous.startTimeUs;
      if (targetPositionUs > mediaPlaylist.getEndTimeUs()) {
        // If the playlist is too old to contain the chunk, we need to refresh it.
        chunkMediaSequence = mediaPlaylist.mediaSequence + mediaPlaylist.segments.size();
      } else {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, targetPositionUs, true,
            !playlistTracker.isLive() || previous == null) + mediaPlaylist.mediaSequence;
        if (chunkMediaSequence < mediaPlaylist.mediaSequence && previous != null) {
          // We try getting the next chunk without adapting in case that's the reason for falling
          // behind the live window.
          newVariantIndex = oldVariantIndex;
          mediaPlaylist = playlistTracker.getPlaylistSnapshot(variants[newVariantIndex]);
          chunkMediaSequence = previous.getNextChunkIndex();
        }
      }
    } else {
      chunkMediaSequence = previous.getNextChunkIndex();
    }
    if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
      fatalError = new BehindLiveWindowException();
      return;
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (mediaPlaylist.hasEndTag) {
        out.endOfStream = true;
      } else /* Live */ {
        out.playlist = variants[newVariantIndex];
      }
      return;
    }

    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);

    // Check if encryption is specified.
    if (segment.isEncrypted) {
      Uri keyUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.encryptionKeyUri);
      if (!keyUri.equals(encryptionKeyUri)) {
        // Encryption is specified and the key has changed.
        out.chunk = newEncryptionKeyChunk(keyUri, segment.encryptionIV, newVariantIndex,
            trackSelection.getSelectionReason(), trackSelection.getSelectionData());
        return;
      }
      if (!Util.areEqual(segment.encryptionIV, encryptionIvString)) {
        setEncryptionData(keyUri, segment.encryptionIV, encryptionKey);
      }
    } else {
      clearEncryptionData();
    }

    // Compute start and end times, and the sequence number of the next chunk.
    long startTimeUs = segment.startTimeUs;
    if (previous != null && !switchingVariant) {
      startTimeUs = previous.getAdjustedEndTimeUs();
    }
    long endTimeUs = startTimeUs + segment.durationUs;
    Format format = variants[newVariantIndex].format;

    Uri chunkUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url);

    // Configure the extractor that will read the chunk.
    Extractor extractor;
    boolean useInitializedExtractor = lastLoadedInitializationChunk != null
        && lastLoadedInitializationChunk.format == format;
    boolean needNewExtractor = previous == null
        || previous.discontinuitySequenceNumber != segment.discontinuitySequenceNumber
        || format != previous.trackFormat;
    boolean extractorNeedsInit = true;
    boolean isTimestampMaster = false;
    TimestampAdjuster timestampAdjuster = null;
    String lastPathSegment = chunkUri.getLastPathSegment();
    if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      // TODO: Inject a timestamp adjuster and use it along with ID3 PRIV tag values with owner
      // identifier com.apple.streaming.transportStreamTimestamp. This may also apply to the MP3
      // case below.
      extractor = new AdtsExtractor(startTimeUs);
    } else if (lastPathSegment.endsWith(AC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(EC3_FILE_EXTENSION)) {
      extractor = new Ac3Extractor(startTimeUs);
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      extractor = new Mp3Extractor(startTimeUs);
    } else if (lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      timestampAdjuster = timestampAdjusterProvider.getAdjuster(segment.discontinuitySequenceNumber,
          startTimeUs);
      extractor = new WebvttExtractor(format.language, timestampAdjuster);
    } else if (lastPathSegment.endsWith(MP4_FILE_EXTENSION)) {
      isTimestampMaster = true;
      if (needNewExtractor) {
        if (useInitializedExtractor) {
          extractor = lastLoadedInitializationChunk.extractor;
        } else {
          timestampAdjuster = timestampAdjusterProvider.getAdjuster(
              segment.discontinuitySequenceNumber, startTimeUs);
          extractor = new FragmentedMp4Extractor(0, timestampAdjuster);
        }
      } else {
        extractor = previous.extractor;
      }
    } else if (needNewExtractor) {
      // MPEG-2 TS segments, but we need a new extractor.
      isTimestampMaster = true;
      if (useInitializedExtractor) {
        extractor = lastLoadedInitializationChunk.extractor;
      } else {
        timestampAdjuster = timestampAdjusterProvider.getAdjuster(
            segment.discontinuitySequenceNumber, startTimeUs);
        // This flag ensures the change of pid between streams does not affect the sample queues.
        @DefaultTsPayloadReaderFactory.Flags
        int esReaderFactoryFlags = 0;
        String codecs = format.codecs;
        if (!TextUtils.isEmpty(codecs)) {
          // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
          // exist. If we know from the codec attribute that they don't exist, then we can
          // explicitly ignore them even if they're declared.
          if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
            esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
          }
          if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
            esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
          }
        }
        extractor = new TsExtractor(timestampAdjuster,
            new DefaultTsPayloadReaderFactory(esReaderFactoryFlags), true);
      }
    } else {
      // MPEG-2 TS segments, and we need to continue using the same extractor.
      extractor = previous.extractor;
      extractorNeedsInit = false;
    }

    if (needNewExtractor && mediaPlaylist.initializationSegment != null
        && !useInitializedExtractor) {
      out.chunk = buildInitializationChunk(mediaPlaylist, extractor, format);
      return;
    }

    lastLoadedInitializationChunk = null;
    // Configure the data source and spec for the chunk.
    DataSpec dataSpec = new DataSpec(chunkUri, segment.byterangeOffset, segment.byterangeLength,
        null);
    out.chunk = new HlsMediaChunk(dataSource, dataSpec, variants[newVariantIndex],
        trackSelection.getSelectionReason(), trackSelection.getSelectionData(),
        startTimeUs, endTimeUs, chunkMediaSequence, segment.discontinuitySequenceNumber,
        isTimestampMaster, timestampAdjuster, extractor, extractorNeedsInit, switchingVariant,
        encryptionKey, encryptionIv);
  }

  /**
   * Called when the {@link HlsSampleStreamWrapper} has finished loading a chunk obtained from this
   * source.
   *
   * @param chunk The chunk whose load has been completed.
   */
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof HlsMediaChunk) {
      HlsMediaChunk mediaChunk = (HlsMediaChunk) chunk;
      playlistTracker.onChunkLoaded(mediaChunk.hlsUrl, mediaChunk.chunkIndex,
          mediaChunk.getAdjustedStartTimeUs());
    }
    if (chunk instanceof HlsInitializationChunk) {
      lastLoadedInitializationChunk = (HlsInitializationChunk) chunk;
    } else if (chunk instanceof EncryptionKeyChunk) {
      EncryptionKeyChunk encryptionKeyChunk = (EncryptionKeyChunk) chunk;
      scratchSpace = encryptionKeyChunk.getDataHolder();
      setEncryptionData(encryptionKeyChunk.dataSpec.uri, encryptionKeyChunk.iv,
          encryptionKeyChunk.getResult());
    }
  }

  /**
   * Called when the {@link HlsSampleStreamWrapper} encounters an error loading a chunk obtained
   * from this source.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param cancelable Whether the load can be canceled.
   * @param e The error.
   * @return Whether the load should be canceled.
   */
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, IOException e) {
    return cancelable && ChunkedTrackBlacklistUtil.maybeBlacklistTrack(trackSelection,
        trackSelection.indexOf(trackGroup.indexOf(chunk.trackFormat)), e);
  }

  // Private methods.

  private HlsInitializationChunk buildInitializationChunk(HlsMediaPlaylist mediaPlaylist,
      Extractor extractor, Format format) {
    Segment initSegment = mediaPlaylist.initializationSegment;
    // The initialization segment is required before the actual media chunk.
    Uri initSegmentUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, initSegment.url);
    DataSpec initDataSpec = new DataSpec(initSegmentUri, initSegment.byterangeOffset,
        initSegment.byterangeLength, null);
    return new HlsInitializationChunk(dataSource, initDataSpec,
        trackSelection.getSelectionReason(), trackSelection.getSelectionData(), extractor,
        format);
  }

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv, int variantIndex,
      int trackSelectionReason, Object trackSelectionData) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNSET, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(dataSource, dataSpec, variants[variantIndex].format,
        trackSelectionReason, trackSelectionData, scratchSpace, iv);
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

  // Private classes.

  /**
   * A {@link TrackSelection} to use for initialization.
   */
  private static final class InitializationTrackSelection extends BaseTrackSelection {

    private int selectedIndex;

    public InitializationTrackSelection(TrackGroup group, int[] tracks) {
      super(group, tracks);
      selectedIndex = indexOf(group.getFormat(0));
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
      long nowMs = SystemClock.elapsedRealtime();
      if (!isBlacklisted(selectedIndex, nowMs)) {
        return;
      }
      // Try from lowest bitrate to highest.
      for (int i = length - 1; i >= 0; i--) {
        if (!isBlacklisted(i, nowMs)) {
          selectedIndex = i;
          return;
        }
      }
      // Should never happen.
      throw new IllegalStateException();
    }

    @Override
    public int getSelectedIndex() {
      return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Override
    public Object getSelectionData() {
      return null;
    }

  }

  private static final class EncryptionKeyChunk extends DataChunk {

    public final String iv;

    private byte[] result;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
        int trackSelectionReason, Object trackSelectionData, byte[] scratchSpace, String iv) {
      super(dataSource, dataSpec, C.DATA_TYPE_DRM, trackFormat, trackSelectionReason,
          trackSelectionData, scratchSpace);
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
