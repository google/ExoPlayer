/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;

/**
 * Extractor based on the official MicroSoft spec
 * https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference
 */
public class AviExtractor implements Extractor {
  //Minimum time between keyframes in the AviSeekMap
  static final long MIN_KEY_FRAME_RATE_US = 2_000_000L;
  static final long UINT_MASK = 0xffffffffL;

  static long getUInt(@NonNull ByteBuffer byteBuffer) {
    return byteBuffer.getInt() & UINT_MASK;
  }

  @NonNull
  static String toString(int tag) {
    final StringBuilder sb = new StringBuilder(4);
    for (int i=0;i<4;i++) {
      sb.append((char)(tag & 0xff));
      tag >>=8;
    }
    return sb.toString();
  }

  static long alignPosition(long position) {
    if ((position & 1) == 1) {
      position++;
    }
    return position;
  }

  static void alignInput(@NonNull ExtractorInput input) throws IOException {
    // This isn't documented anywhere, but most files are aligned to even bytes
    // and can have gaps of zeros
    if ((input.getPosition() & 1) == 1) {
      input.skipFully(1);
    }
  }

  @NonNull
  static ByteBuffer allocate(int bytes) {
    final byte[] buffer = new byte[bytes];
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return byteBuffer;
  }

  @VisibleForTesting
  static int getStreamId(int chunkId) {
    final int upperChar = chunkId & 0xff;
    if (Character.isDigit(upperChar)) {
      final int lowerChar = (chunkId >> 8) & 0xff;
      if (Character.isDigit(upperChar)) {
        return (lowerChar & 0xf) + ((upperChar & 0xf) * 10);
      }
    }
    return -1;
  }

  static final String TAG = "AviExtractor";
  @VisibleForTesting
  static final int PEEK_BYTES = 28;

  @VisibleForTesting
  static final int STATE_READ_TRACKS = 0;
  @VisibleForTesting
  static final int STATE_FIND_MOVI = 1;
  @VisibleForTesting
  static final int STATE_READ_IDX1 = 2;
  @VisibleForTesting
  static final int STATE_READ_CHUNKS = 3;
  @VisibleForTesting
  static final int STATE_SEEK_START = 4;

  static final int AVIIF_KEYFRAME = 16;


  static final int RIFF = 0x46464952; // RIFF
  static final int AVI_ = 0x20495641; // AVI<space>
  //movie data box
  static final int MOVI = 0x69766f6d; // movi
  //Index
  static final int IDX1 = 0x31786469; // idx1

  static final int JUNK = 0x4b4e554a; // JUNK
  static final int REC_ = 0x20636572; // rec<space>

  @VisibleForTesting
  int state;
  @VisibleForTesting
  ExtractorOutput output;
  private AviHeaderBox aviHeader;
  private long durationUs = C.TIME_UNSET;
  /**
   * ChunkHandlers by StreamId
   */
  private ChunkHandler[] chunkHandlers = new ChunkHandler[0];
  //At the start of the movi tag
  private long moviOffset;
  private long moviEnd;
  @VisibleForTesting
  AviSeekMap aviSeekMap;

  //Set if a chunk is only partially read
  private transient ChunkHandler chunkHandler;

  /**
   *
   * @param bytes Must be at least 20
   */
  @Nullable
  static private ByteBuffer getAviBuffer(@NonNull ExtractorInput input, int bytes) throws IOException {
    if (input.getLength() < bytes) {
      return null;
    }
    final ByteBuffer byteBuffer = allocate(bytes);
    input.peekFully(byteBuffer.array(), 0, bytes);
    final int riff = byteBuffer.getInt();
    if (riff != AviExtractor.RIFF) {
      return null;
    }
    long reportedLen = getUInt(byteBuffer) + byteBuffer.position();
    final long inputLen = input.getLength();
    if (inputLen != C.LENGTH_UNSET && inputLen != reportedLen) {
      w("Header length doesn't match stream length");
    }
    int avi = byteBuffer.getInt();
    if (avi != AviExtractor.AVI_) {
      return null;
    }
    final int list = byteBuffer.getInt();
    if (list != ListBox.LIST) {
      return null;
    }
    return byteBuffer;
  }

  @Override
  public boolean sniff(@NonNull ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = getAviBuffer(input, PEEK_BYTES);
    if (byteBuffer == null) {
      return false;
    }
    //Len
    byteBuffer.getInt();
    final int hdrl = byteBuffer.getInt();
    if (hdrl != ListBox.TYPE_HDRL) {
      return false;
    }
    final int avih = byteBuffer.getInt();
    return avih == AviHeaderBox.AVIH;
  }

  @VisibleForTesting
  void setSeekMap(AviSeekMap aviSeekMap) {
    this.aviSeekMap = aviSeekMap;
    output.seekMap(aviSeekMap);
  }

  @Nullable
  public static ListBox readHeaderList(ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = getAviBuffer(input, 20);
    if (byteBuffer == null) {
      return null;
    }
    input.skipFully(20);
    final int listSize = byteBuffer.getInt();
    final ListBox listBox = ListBox.newInstance(listSize, new BoxFactory(), input);
    if (listBox.getListType() != ListBox.TYPE_HDRL) {
      return null;
    }
    return listBox;
  }

  long getDuration() {
    return durationUs;
  }

  @Override
  public void init(@NonNull ExtractorOutput output) {
    this.state = STATE_READ_TRACKS;
    this.output = output;
  }

  @VisibleForTesting
  ChunkHandler parseStream(final ListBox streamList, int streamId) {
    final StreamHeaderBox streamHeader = streamList.getChild(StreamHeaderBox.class);
    final StreamFormatBox streamFormat = streamList.getChild(StreamFormatBox.class);
    if (streamHeader == null) {
      w("Missing Stream Header");
      return null;
    }
    //i(streamHeader.toString());
    if (streamFormat == null) {
      w("Missing Stream Format");
      return null;
    }
    final long durationUs = streamHeader.getDurationUs();
    //Initial estimate
    final int length = streamHeader.getLength();
    final Format.Builder builder = new Format.Builder();
    builder.setId(streamId);
    final int suggestedBufferSize = streamHeader.getSuggestedBufferSize();
    if (suggestedBufferSize != 0) {
      builder.setMaxInputSize(suggestedBufferSize);
    }
    final StreamNameBox streamName = streamList.getChild(StreamNameBox.class);
    if (streamName != null) {
      builder.setLabel(streamName.getName());
    }
    final ChunkClock clock = new ChunkClock(durationUs, length);
    final ChunkHandler chunkHandler;
    if (streamHeader.isVideo()) {
      final VideoFormat videoFormat = streamFormat.getVideoFormat();
      final String mimeType = videoFormat.getMimeType();
      if (mimeType == null) {
        Log.w(TAG, "Unknown FourCC: " + toString(videoFormat.getCompression()));
        return null;
      }
      final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_VIDEO);
      builder.setWidth(videoFormat.getWidth());
      builder.setHeight(videoFormat.getHeight());
      builder.setFrameRate(streamHeader.getFrameRate());
      builder.setSampleMimeType(mimeType);

      if (MimeTypes.VIDEO_H264.equals(mimeType)) {
        chunkHandler = new AvcChunkHandler(streamId, trackOutput, clock, builder);
      } else if (MimeTypes.VIDEO_MP4V.equals(mimeType)) {
        chunkHandler = new Mp4vChunkHandler(streamId, trackOutput, clock, builder);
      } else {
        chunkHandler = new ChunkHandler(streamId, ChunkHandler.TYPE_VIDEO, trackOutput, clock);
      }
      trackOutput.format(builder.build());
      this.durationUs = durationUs;
    } else if (streamHeader.isAudio()) {
      final AudioFormat audioFormat = streamFormat.getAudioFormat();
      final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_AUDIO);
      final String mimeType = audioFormat.getMimeType();
      builder.setSampleMimeType(mimeType);
      builder.setChannelCount(audioFormat.getChannels());
      builder.setSampleRate(audioFormat.getSamplesPerSecond());
      final int bytesPerSecond = audioFormat.getAvgBytesPerSec();
      if (bytesPerSecond != 0) {
        builder.setAverageBitrate(bytesPerSecond * 8);
      }
      if (MimeTypes.AUDIO_RAW.equals(mimeType)) {
        final short bps = audioFormat.getBitsPerSample();
        if (bps == 8) {
          builder.setPcmEncoding(C.ENCODING_PCM_8BIT);
        } else if (bps == 16){
          builder.setPcmEncoding(C.ENCODING_PCM_16BIT);
        }
      }
      if (MimeTypes.AUDIO_AAC.equals(mimeType) && audioFormat.getCbSize() > 0) {
        builder.setInitializationData(Collections.singletonList(audioFormat.getCodecData()));
      }
      trackOutput.format(builder.build());
      if (MimeTypes.AUDIO_MPEG.equals(mimeType)) {
        chunkHandler = new MpegAudioChunkHandler(streamId, trackOutput, clock,
            audioFormat.getSamplesPerSecond());
      } else {
        chunkHandler = new ChunkHandler(streamId, ChunkHandler.TYPE_AUDIO,
            trackOutput, clock);
      }
      chunkHandler.setKeyFrames(ChunkHandler.ALL_KEY_FRAMES);
    }else {
      chunkHandler = null;
    }
    return chunkHandler;
  }

  private int readTracks(ExtractorInput input) throws IOException {
    final ListBox headerList = readHeaderList(input);
    if (headerList == null) {
      throw new IOException("AVI Header List not found");
    }
    aviHeader = headerList.getChild(AviHeaderBox.class);
    if (aviHeader == null) {
      throw new IOException("AviHeader not found");
    }
    chunkHandlers = new ChunkHandler[aviHeader.getStreams()];
    //This is usually wrong, so it will be overwritten by video if present
    durationUs = aviHeader.getTotalFrames() * (long)aviHeader.getMicroSecPerFrame();

    int streamId = 0;
    for (Box box : headerList.getChildren()) {
      if (box instanceof ListBox && ((ListBox) box).getListType() == ListBox.TYPE_STRL) {
        final ListBox streamList = (ListBox) box;
        chunkHandlers[streamId] = parseStream(streamList, streamId);
        streamId++;
      }
    }
    output.endTracks();
    state = STATE_FIND_MOVI;
    return RESULT_CONTINUE;
  }

  int findMovi(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ByteBuffer byteBuffer = allocate(12);
    input.readFully(byteBuffer.array(), 0,12);
    final int tag = byteBuffer.getInt();
    final long size = getUInt(byteBuffer);
    final long position = input.getPosition();
    //-4 because we over read for the LIST type
    long nextBox = alignPosition(position + size - 4);
    if (tag == ListBox.LIST) {
      final int listType = byteBuffer.getInt();
      if (listType == MOVI) {
        moviOffset = position - 4;
        moviEnd = moviOffset + size;
        if (aviHeader.hasIndex()) {
          state = STATE_READ_IDX1;
        } else {
          output.seekMap(new SeekMap.Unseekable(getDuration()));
          state = STATE_READ_TRACKS;
          nextBox = moviOffset + 4;
        }
      }
    }
    seekPosition.position = nextBox;
    return RESULT_SEEK;
  }

  @VisibleForTesting
  ChunkHandler getVideoTrack() {
    for (@Nullable ChunkHandler chunkHandler : chunkHandlers) {
      if (chunkHandler != null && chunkHandler.isVideo()) {
        return chunkHandler;
      }
    }
    return null;
  }

  void fixTimings(final int[] keyFrameCounts, final long videoDuration) {
    for (@Nullable final ChunkHandler chunkHandler : chunkHandlers) {
      if (chunkHandler != null) {
        if (chunkHandler.isAudio()) {
          final long durationUs = chunkHandler.getClock().durationUs;
          i("Audio #" + chunkHandler.getId() + " chunks: " + chunkHandler.chunks + " us=" + durationUs +
              " size=" + chunkHandler.size);
          final ChunkClock linearClock = chunkHandler.getClock();
          //If the audio track duration is off from the video by >5 % recalc using video
          if ((durationUs - videoDuration) / (float)videoDuration > .05f) {
            w("Audio #" + chunkHandler.getId() + " duration is off using videoDuration");
            linearClock.setDuration(videoDuration);
          }
          linearClock.setChunks(chunkHandler.chunks);
          if (chunkHandler.chunks != keyFrameCounts[chunkHandler.getId()]) {
            w("Audio is not all key frames chunks=" + chunkHandler.chunks + " keyFrames=" +
                keyFrameCounts[chunkHandler.getId()]);
          }
        }
      }
    }
  }

  /**
   * Reads the index and sets the keyFrames and creates the SeekMap
   */
  void readIdx1(ExtractorInput input, int remaining) throws IOException {
    final ChunkHandler videoTrack = getVideoTrack();
    if (videoTrack == null) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      w("No video track found");
      return;
    }
    if (remaining < 16) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      w("Index too short");
      return;
    }
    final ByteBuffer firstEntry = AviExtractor.allocate(16);
    input.peekFully(firstEntry.array(), 0, 16);

    final int videoId = videoTrack.getId();
    final ByteBuffer indexByteBuffer = allocate(Math.min(remaining, 64 * 1024));
    final byte[] bytes = indexByteBuffer.array();

    //These are ints/2
    final UnboundedIntArray keyFrameOffsetsDiv2 = new UnboundedIntArray();
    final int[] keyFrameCounts = new int[chunkHandlers.length];
    final UnboundedIntArray[] seekIndexes = new UnboundedIntArray[chunkHandlers.length];
    for (int i=0;i<seekIndexes.length;i++) {
      seekIndexes[i] = new UnboundedIntArray();
    }
    final long usPerVideoChunk = videoTrack.getClock().getUs(1);
    //Chunks in 2 seconds
    final int chunksPerKeyFrame = (int)(MIN_KEY_FRAME_RATE_US / usPerVideoChunk);
    final HashMap<Integer, Integer> tagMap = new HashMap<>();
    while (remaining > 0) {
      final int toRead = Math.min(indexByteBuffer.remaining(), remaining);
      input.readFully(bytes, indexByteBuffer.position(), toRead);
      indexByteBuffer.limit(indexByteBuffer.position() + toRead);
      remaining -= toRead;
      while (indexByteBuffer.remaining() >= 16) {
        final int chunkId = indexByteBuffer.getInt();
        Integer count = tagMap.get(chunkId);
        if (count == null) {
          count = 1;
        } else {
          count += 1;
        }
        tagMap.put(chunkId, count);
        final ChunkHandler chunkHandler = getChunkHandler(chunkId);
        if (chunkHandler == null) {
          if (chunkId != AviExtractor.REC_) {
            w("Unknown Track Type: " + toString(chunkId));
          }
          indexByteBuffer.position(indexByteBuffer.position() + 12);
          continue;
        }
        final int flags = indexByteBuffer.getInt();
        final int offset = indexByteBuffer.getInt();
        final int size = indexByteBuffer.getInt();
        if ((flags & AVIIF_KEYFRAME) == AVIIF_KEYFRAME) {
          if (chunkHandler.isVideo()) {
            int indexSize = seekIndexes[videoId].getSize();
            if (indexSize == 0 || chunkHandler.chunks - seekIndexes[videoId].get(indexSize - 1) >= chunksPerKeyFrame) {
              keyFrameOffsetsDiv2.add(offset / 2);
              for (@Nullable ChunkHandler seekTrack : chunkHandlers) {
                if (seekTrack != null) {
                  seekIndexes[seekTrack.getId()].add(seekTrack.chunks);
                }
              }
            }
          }
          keyFrameCounts[chunkHandler.getId()]++;
        }
        chunkHandler.chunks++;
        chunkHandler.size+=size;
      }
      indexByteBuffer.compact();
    }
    if (videoTrack.chunks == keyFrameCounts[videoTrack.getId()]) {
      videoTrack.setKeyFrames(ChunkHandler.ALL_KEY_FRAMES);
    } else {
      videoTrack.setKeyFrames(seekIndexes[videoId].getArray());
    }

    //Work-around a bug where the offset is from the start of the file, not "movi"
    final long seekOffset = firstEntry.getInt(8) > moviOffset ? 0L : moviOffset;
    final AviSeekMap seekMap = new AviSeekMap(videoId, videoTrack.clock.durationUs, videoTrack.chunks,
        keyFrameOffsetsDiv2.getArray(), seekIndexes, seekOffset);

    i("Video chunks=" + videoTrack.chunks + " us=" + seekMap.getDurationUs());

    fixTimings(keyFrameCounts, durationUs);

    setSeekMap(seekMap);
  }

  @Nullable
  @VisibleForTesting
  ChunkHandler getChunkHandler(int chunkId) {
    for (@Nullable ChunkHandler chunkHandler : chunkHandlers) {
      if (chunkHandler != null && chunkHandler.handlesChunkId(chunkId)) {
        return chunkHandler;
      }
    }
    return null;
  }

  int readChunks(@NonNull ExtractorInput input) throws IOException {
    if (chunkHandler != null) {
      if (chunkHandler.resume(input)) {
        chunkHandler = null;
        alignInput(input);
      }
    } else {
      final int toRead = 8;
      ByteBuffer byteBuffer = allocate(toRead);
      final byte[] bytes = byteBuffer.array();
      alignInput(input);
      input.readFully(bytes, 0, toRead);
      //This is super inefficient, but should be rare
      while (bytes[0] == 0) {
        for (int i=1;i<toRead;i++) {
          bytes[i - 1] = bytes[i];
        }
        int read = input.read(bytes, toRead - 1, 1);
        if (read == C.RESULT_END_OF_INPUT) {
          return RESULT_END_OF_INPUT;
        }
      }
      final int chunkId = byteBuffer.getInt();
      if (chunkId == ListBox.LIST) {
        input.skipFully(8);
        return RESULT_CONTINUE;
      }
      final int size = byteBuffer.getInt();
      if (chunkId == JUNK) {
        input.skipFully(size);
        alignInput(input);
        return RESULT_CONTINUE;
      }
      final ChunkHandler chunkHandler = getChunkHandler(chunkId);
      if (chunkHandler == null) {
        input.skipFully(size);
        alignInput(input);
        w("Unknown tag=" + toString(chunkId) + " pos=" + (input.getPosition() - 8)
            + " size=" + size + " moviEnd=" + moviEnd);
        return RESULT_CONTINUE;
      }
      if (chunkHandler.newChunk(size, input)) {
        alignInput(input);
      } else {
        this.chunkHandler = chunkHandler;
      }
    }
    if (input.getPosition() >= moviEnd) {
      return C.RESULT_END_OF_INPUT;
    }
    return RESULT_CONTINUE;
  }

  @Override
  public int read(@NonNull ExtractorInput input, @NonNull PositionHolder seekPosition) throws IOException {
    switch (state) {
      case STATE_READ_CHUNKS:
        return readChunks(input);
      case STATE_SEEK_START:
        state = STATE_READ_CHUNKS;
        seekPosition.position = moviOffset + 4;
        return RESULT_SEEK;
      case STATE_READ_TRACKS:
        return readTracks(input);
      case STATE_FIND_MOVI:
        return findMovi(input, seekPosition);
      case STATE_READ_IDX1: {
        if (aviHeader.hasIndex()) {
          ByteBuffer byteBuffer = allocate(8);
          input.readFully(byteBuffer.array(), 0,8);
          final int tag = byteBuffer.getInt();
          final int size = byteBuffer.getInt();
          if (tag == IDX1) {
            readIdx1(input, size);
          }
        } else {
          output.seekMap(new SeekMap.Unseekable(getDuration()));
        }
        seekPosition.position = moviOffset + 4;
        state = STATE_READ_CHUNKS;
        return RESULT_SEEK;
      }
    }
    return RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
    //i("Seek pos=" + position +", us="+timeUs);
    chunkHandler = null;
    if (position <= 0) {
      if (moviOffset != 0) {
        setIndexes(new int[chunkHandlers.length]);
        state = STATE_SEEK_START;
      }
    } else {
      if (aviSeekMap != null) {
        setIndexes(aviSeekMap.getIndexes(position));
      }
    }
  }

  private void setIndexes(@NonNull int[] indexes) {
    for (@Nullable ChunkHandler chunkHandler : chunkHandlers) {
      if (chunkHandler != null) {
        chunkHandler.setIndex(indexes[chunkHandler.getId()]);
      }
    }
  }

  @Override
  public void release() {
    //Intentionally blank
  }

  @VisibleForTesting
  void setChunkHandlers(ChunkHandler[] chunkHandlers) {
    this.chunkHandlers = chunkHandlers;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setAviHeader(final AviHeaderBox aviHeaderBox) {
    aviHeader = aviHeaderBox;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setMovi(final int offset, final int end) {
    moviOffset = offset;
    moviEnd = end;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  ChunkHandler getChunkHandler() {
    return chunkHandler;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setChunkHandler(final ChunkHandler chunkHandler) {
    this.chunkHandler = chunkHandler;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  long getMoviOffset() {
    return moviOffset;
  }

  private static void w(String message) {
    try {
      Log.w(TAG, message);
    } catch (RuntimeException e) {
      //Catch not mocked for tests
    }
  }

  private static void i(String message) {
    try {
      Log.i(TAG, message);
    } catch (RuntimeException e) {
      //Catch not mocked for tests
    }
  }
}
