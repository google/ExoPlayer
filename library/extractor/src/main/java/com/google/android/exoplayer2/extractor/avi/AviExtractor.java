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
 * Based on the official MicroSoft spec
 * https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference
 */
public class AviExtractor implements Extractor {
  //Minimum time between keyframes in the SeekMap
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

  static int alignPositionHolder(@NonNull ExtractorInput input, @NonNull PositionHolder seekPosition) {
    final long position = input.getPosition();
    if ((position & 1) == 1) {
      seekPosition.position = position + 1;
      return RESULT_SEEK;
    }
    return RESULT_CONTINUE;
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
  static final int STATE_READ_SAMPLES = 3;
  @VisibleForTesting
  static final int STATE_SEEK_START = 4;

  static final int AVIIF_KEYFRAME = 16;


  static final int RIFF = 'R' | ('I' << 8) | ('F' << 16) | ('F' << 24);
  static final int AVI_ = 'A' | ('V' << 8) | ('I' << 16) | (' ' << 24);
  //Stream List
  static final int STRL = 's' | ('t' << 8) | ('r' << 16) | ('l' << 24);
  //movie data box
  static final int MOVI = 'm' | ('o' << 8) | ('v' << 16) | ('i' << 24);
  //Index
  static final int IDX1 = 'i' | ('d' << 8) | ('x' << 16) | ('1' << 24);

  static final int JUNK = 'J' | ('U' << 8) | ('N' << 16) | ('K' << 24);
  static final int REC_ = 'r' | ('e' << 8) | ('c' << 16) | (' ' << 24);

  @VisibleForTesting
  int state;
  @VisibleForTesting
  ExtractorOutput output;
  private AviHeaderBox aviHeader;
  private long durationUs = C.TIME_UNSET;
  /**
   * AviTracks by StreamId
   */
  private AviTrack[] aviTracks = new AviTrack[0];
  //At the start of the movi tag
  private long moviOffset;
  private long moviEnd;
  @VisibleForTesting
  AviSeekMap aviSeekMap;

//  private long indexOffset; //Usually chunkStart

  //If partial read
  private transient AviTrack chunkHandler;

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
  static ListBox readHeaderList(ExtractorInput input) throws IOException {
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
  AviTrack parseStream(final ListBox streamList, int streamId) {
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
    final AviTrack aviTrack;
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
        final AvcChunkPeeker avcChunkPeeker = new AvcChunkPeeker(builder, trackOutput, durationUs,
            length);
        aviTrack = new AviTrack(streamId, C.TRACK_TYPE_VIDEO, avcChunkPeeker.getPicCountClock(),
            trackOutput);
        aviTrack.setChunkPeeker(avcChunkPeeker);
      } else {
        aviTrack = new AviTrack(streamId, C.TRACK_TYPE_VIDEO,
            new LinearClock(durationUs, length), trackOutput);
        if (MimeTypes.VIDEO_MP4V.equals(mimeType)) {
          aviTrack.setChunkPeeker(new Mp4vChunkPeeker(builder, trackOutput));
        }
      }
      trackOutput.format(builder.build());
      this.durationUs = durationUs;
    } else if (streamHeader.isAudio()) {
      final AudioFormat audioFormat = streamFormat.getAudioFormat();
      final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_AUDIO);
      final String mimeType = audioFormat.getMimeType();
      builder.setSampleMimeType(mimeType);
      //builder.setCodecs(audioFormat.getCodec());
      builder.setChannelCount(audioFormat.getChannels());
      builder.setSampleRate(audioFormat.getSamplesPerSecond());
      if (audioFormat.getFormatTag() == AudioFormat.WAVE_FORMAT_PCM) {
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
      aviTrack = new AviTrack(streamId, C.TRACK_TYPE_AUDIO,
          new LinearClock(durationUs, length), trackOutput);
      aviTrack.setKeyFrames(AviTrack.ALL_KEY_FRAMES);
    }else {
      aviTrack = null;
    }
    return aviTrack;
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
    aviTracks = new AviTrack[aviHeader.getStreams()];
    //This is usually wrong, so it will be overwritten by video if present
    durationUs = aviHeader.getTotalFrames() * (long)aviHeader.getMicroSecPerFrame();

    int streamId = 0;
    for (Box box : headerList.getChildren()) {
      if (box instanceof ListBox && ((ListBox) box).getListType() == STRL) {
        final ListBox streamList = (ListBox) box;
        aviTracks[streamId] = parseStream(streamList, streamId);
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
  AviTrack getVideoTrack() {
    for (@Nullable AviTrack aviTrack : aviTracks) {
      if (aviTrack != null && aviTrack.isVideo()) {
        return aviTrack;
      }
    }
    return null;
  }

  void updateAudioTiming(final int[] keyFrameCounts, final long videoDuration) {
    for (final AviTrack aviTrack : aviTracks) {
      if (aviTrack != null && aviTrack.isAudio()) {
        final long durationUs = aviTrack.getClock().durationUs;
        i("Audio #" + aviTrack.id + " chunks: " + aviTrack.chunks + " us=" + durationUs +
            " size=" + aviTrack.size);
        final LinearClock linearClock = aviTrack.getClock();
        //If the audio track duration is off from the video by >5 % recalc using video
        if ((durationUs - videoDuration) / (float)videoDuration > .05f) {
          w("Audio #" + aviTrack.id + " duration is off using videoDuration");
          linearClock.setDuration(videoDuration);
        }
        linearClock.setLength(aviTrack.chunks);
        if (aviTrack.chunks != keyFrameCounts[aviTrack.id]) {
          w("Audio is not all key frames chunks=" + aviTrack.chunks + " keyFrames=" +
              keyFrameCounts[aviTrack.id]);
        }
      }
    }
  }

  /**
   * Reads the index and sets the keyFrames and creates the SeekMap
   */
  void readIdx1(ExtractorInput input, int remaining) throws IOException {
    final AviTrack videoTrack = getVideoTrack();
    if (videoTrack == null) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      w("No video track found");
      return;
    }
    final int videoId = videoTrack.id;
    final ByteBuffer indexByteBuffer = allocate(Math.min(remaining, 64 * 1024));
    final byte[] bytes = indexByteBuffer.array();

    //These are ints/2
    final UnboundedIntArray keyFrameOffsetsDiv2 = new UnboundedIntArray();
    final int[] keyFrameCounts = new int[aviTracks.length];
    final UnboundedIntArray[] seekIndexes = new UnboundedIntArray[aviTracks.length];
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
        final AviTrack aviTrack = getAviTrack(chunkId);
        if (aviTrack == null) {
          if (chunkId != AviExtractor.REC_) {
            w("Unknown Track Type: " + toString(chunkId));
          }
          indexByteBuffer.position(indexByteBuffer.position() + 12);
          continue;
        }
        final int flags = indexByteBuffer.getInt();
        final int offset = indexByteBuffer.getInt();
        //Skip size
        //indexByteBuffer.position(indexByteBuffer.position() + 4);
        final int size = indexByteBuffer.getInt();
        if ((flags & AVIIF_KEYFRAME) == AVIIF_KEYFRAME) {
          if (aviTrack.isVideo()) {
            int indexSize = seekIndexes[videoId].getSize();
            if (indexSize == 0 || aviTrack.chunks - seekIndexes[videoId].get(indexSize - 1) >= chunksPerKeyFrame) {
              keyFrameOffsetsDiv2.add(offset / 2);
              for (AviTrack seekTrack : aviTracks) {
                if (seekTrack != null) {
                  seekIndexes[seekTrack.id].add(seekTrack.chunks);
                }
              }
            }
          }
          keyFrameCounts[aviTrack.id]++;
        }
        aviTrack.chunks++;
        aviTrack.size+=size;
      }
      indexByteBuffer.compact();
    }
    if (videoTrack.chunks == keyFrameCounts[videoTrack.id]) {
      videoTrack.setKeyFrames(AviTrack.ALL_KEY_FRAMES);
    } else {
      videoTrack.setKeyFrames(seekIndexes[videoId].getArray());
    }

    final AviSeekMap seekMap = new AviSeekMap(videoId, videoTrack.clock.durationUs, videoTrack.chunks,
        keyFrameOffsetsDiv2.getArray(), seekIndexes, moviOffset);

    i("Video chunks=" + videoTrack.chunks + " us=" + seekMap.getDurationUs());

    //Needs to be called after the duration is updated
    updateAudioTiming(keyFrameCounts, durationUs);

    setSeekMap(seekMap);
  }

  @Nullable
  private AviTrack getAviTrack(int chunkId) {
    for (AviTrack aviTrack : aviTracks) {
      if (aviTrack.handlesChunkId(chunkId)) {
        return aviTrack;
      }
    }
    return null;
  }

  int readSamples(@NonNull ExtractorInput input, @NonNull PositionHolder seekPosition) throws IOException {
    if (chunkHandler != null) {
      if (chunkHandler.resume(input)) {
        chunkHandler = null;
        return alignPositionHolder(input, seekPosition);
      }
    } else {
      ByteBuffer byteBuffer = allocate(8);
      final byte[] bytes = byteBuffer.array();
      alignInput(input);
      input.readFully(bytes, 0, 1);
      while (bytes[0] == 0) {
        input.readFully(bytes, 0, 1);
      }
      if (input.getPosition() >= moviEnd) {
        return RESULT_END_OF_INPUT;
      }
      input.readFully(bytes, 1, 7);
      final int chunkId = byteBuffer.getInt();
      if (chunkId == ListBox.LIST) {
        seekPosition.position = input.getPosition() + 8;
        return RESULT_SEEK;
      }
      final int size = byteBuffer.getInt();
      if (chunkId == JUNK) {
        seekPosition.position = alignPosition(input.getPosition() + size);
        return RESULT_SEEK;
      }
      final AviTrack aviTrack = getAviTrack(chunkId);
      if (aviTrack == null) {
        seekPosition.position = alignPosition(input.getPosition() + size);
        w("Unknown tag=" + toString(chunkId) + " pos=" + (input.getPosition() - 8)
            + " size=" + size + " moviEnd=" + moviEnd);
        return RESULT_SEEK;
      }
      if (aviTrack.newChunk(chunkId, size, input)) {
        return alignPositionHolder(input, seekPosition);
      } else {
        chunkHandler = aviTrack;
      }
    }
    return RESULT_CONTINUE;
  }

  @Override
  public int read(@NonNull ExtractorInput input, @NonNull PositionHolder seekPosition) throws IOException {
    switch (state) {
      case STATE_READ_SAMPLES:
        return readSamples(input, seekPosition);
      case STATE_SEEK_START:
        state = STATE_READ_SAMPLES;
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
        state = STATE_READ_SAMPLES;
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
        resetClocks();
        state = STATE_SEEK_START;
      }
    } else {
      if (aviSeekMap != null) {
        aviSeekMap.setFrames(position, timeUs, aviTracks);
      }
    }
  }

  void resetClocks() {
    for (@Nullable AviTrack aviTrack : aviTracks) {
      if (aviTrack != null) {
        aviTrack.getClock().setIndex(0);
      }
    }
  }

  @Override
  public void release() {
    //Intentionally blank
  }

  @VisibleForTesting
  void setAviTracks(AviTrack[] aviTracks) {
    this.aviTracks = aviTracks;
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
  AviTrack getChunkHandler() {
    return chunkHandler;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setChunkHandler(final AviTrack aviTrack) {
    chunkHandler = aviTrack;
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
