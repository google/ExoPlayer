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

/**
 * Based on the official MicroSoft spec
 * https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference
 */
public class AviExtractor implements Extractor {
  static final long UINT_MASK = 0xffffffffL;

  static long getUInt(ByteBuffer byteBuffer) {
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

  static void alignInput(ExtractorInput input) throws IOException {
    // This isn't documented anywhere, but most files are aligned to even bytes
    // and can have gaps of zeros
    if ((input.getPosition() & 1) == 1) {
      input.skipFully(1);
    }
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

  private static final int AVIIF_KEYFRAME = 16;


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

  static final long SEEK_GAP = 2_000_000L; //Time between seek points in micro seconds

  @VisibleForTesting
  int state;
  @VisibleForTesting
  ExtractorOutput output;
  private AviHeaderBox aviHeader;
  private long durationUs = C.TIME_UNSET;
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
   * @param input
   * @param bytes Must be at least 20
   */
  @Nullable
  private ByteBuffer getAviBuffer(ExtractorInput input, int bytes) throws IOException {
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
  public boolean sniff(ExtractorInput input) throws IOException {
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
    if (avih != AviHeaderBox.AVIH) {
      return false;
    }
    return true;
  }

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

  @VisibleForTesting
  void setSeekMap(AviSeekMap aviSeekMap) {
    this.aviSeekMap = aviSeekMap;
    output.seekMap(aviSeekMap);
  }

  @Nullable
  ListBox readHeaderList(ExtractorInput input) throws IOException {
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
  public void init(ExtractorOutput output) {
    this.state = STATE_READ_TRACKS;
    this.output = output;
  }

  @VisibleForTesting
  AviTrack parseStream(final ListBox streamList, int streamId) {
    final StreamHeaderBox streamHeader = streamList.getChild(StreamHeaderBox.class);
    final StreamFormatBox streamFormat = streamList.getChild(StreamFormatBox.class);
    if (streamHeader == null) {
      Log.w(TAG, "Missing Stream Header");
      return null;
    }
    if (streamFormat == null) {
      Log.w(TAG, "Missing Stream Format");
      return null;
    }
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
        Log.w(TAG, "Unknown FourCC: " + toString(streamHeader.getFourCC()));
        return null;
      }
      final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_VIDEO);
      builder.setWidth(videoFormat.getWidth());
      builder.setHeight(videoFormat.getHeight());
      builder.setFrameRate(streamHeader.getFrameRate());
      builder.setSampleMimeType(mimeType);

      if (MimeTypes.VIDEO_H264.equals(mimeType)) {
        final AvcChunkPeeker avcChunkPeeker = new AvcChunkPeeker(builder, trackOutput, streamHeader.getUsPerSample());
        aviTrack = new AviTrack(streamId, videoFormat, avcChunkPeeker.getPicCountClock(), trackOutput);
        aviTrack.setChunkPeeker(avcChunkPeeker);
      } else {
        aviTrack = new AviTrack(streamId, videoFormat,
            new LinearClock(streamHeader.getUsPerSample()), trackOutput);
        if (MimeTypes.VIDEO_MP4V.equals(mimeType)) {
          aviTrack.setChunkPeeker(new Mp4vChunkPeeker(builder, trackOutput));
        }
      }
      trackOutput.format(builder.build());
      durationUs = streamHeader.getUsPerSample() * streamHeader.getLength();
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
      aviTrack = new AviTrack(streamId, audioFormat, new LinearClock(streamHeader.getUsPerSample()),
          trackOutput);
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

  private AviTrack getVideoTrack() {
    for (@Nullable AviTrack aviTrack : aviTracks) {
      if (aviTrack != null && aviTrack.isVideo()) {
        return aviTrack;
      }
    }
    return null;
  }

  /**
   * Reads the index and sets the keyFrames and creates the SeekMap
   * @param input
   * @param remaining
   * @throws IOException
   */
  void readIdx1(ExtractorInput input, int remaining) throws IOException {
    final AviTrack videoTrack = getVideoTrack();
    if (videoTrack == null) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      Log.w(TAG, "No video track found");
      return;
    }
    final ByteBuffer indexByteBuffer = allocate(Math.min(remaining, 64 * 1024));
    final byte[] bytes = indexByteBuffer.array();

    final int[] chunkCounts = new int[aviTracks.length];
    final UnboundedIntArray[] seekOffsets = new UnboundedIntArray[aviTracks.length];
    for (int i=0;i<seekOffsets.length;i++) {
      seekOffsets[i] = new UnboundedIntArray();
    }
    //TODO: Change this to min frame rate
    final int seekFrameRate = (int)(1f/(videoTrack.getClock().usPerChunk / 1_000_000f) * 2);

    final UnboundedIntArray keyFrameList = new UnboundedIntArray();
    while (remaining > 0) {
      final int toRead = Math.min(indexByteBuffer.remaining(), remaining);
      input.readFully(bytes, indexByteBuffer.position(), toRead);
      remaining -= toRead;
      while (indexByteBuffer.remaining() >= 16) {
        final int chunkId = indexByteBuffer.getInt();
        final AviTrack aviTrack = getAviTrack(chunkId);
        if (aviTrack == null) {
          if (chunkId != AviExtractor.REC_) {
            Log.w(TAG, "Unknown Track Type: " + toString(chunkId));
          }
          indexByteBuffer.position(indexByteBuffer.position() + 12);
          continue;
        }
        final int flags = indexByteBuffer.getInt();
        final int offset = indexByteBuffer.getInt();
        indexByteBuffer.position(indexByteBuffer.position() + 4);
        //int size = indexByteBuffer.getInt();
        if (aviTrack.isVideo()) {
          if (!aviTrack.isAllKeyFrames() && (flags & AVIIF_KEYFRAME) == AVIIF_KEYFRAME) {
            keyFrameList.add(chunkCounts[aviTrack.id]);
          }
          if (chunkCounts[aviTrack.id] % seekFrameRate == 0) {
            seekOffsets[aviTrack.id].add(offset);
            for (int i=0;i<seekOffsets.length;i++) {
              if (i != aviTrack.id) {
                seekOffsets[i].add(chunkCounts[i]);
              }
            }
          }
        }
        chunkCounts[aviTrack.id]++;
      }
      indexByteBuffer.compact();
    }
    //Set the keys frames
    if (!videoTrack.isAllKeyFrames()) {
      final int[] keyFrames = keyFrameList.getArray();
      videoTrack.setKeyFrames(keyFrames);
    }

    //Correct the timings
    durationUs = chunkCounts[videoTrack.id] * videoTrack.getClock().usPerChunk;

    for (int i=0;i<chunkCounts.length;i++) {
      final AviTrack aviTrack = aviTracks[i];
      if (aviTrack != null && aviTrack.isAudio()) {
        final long calcUsPerSample = (durationUs/chunkCounts[i]);
        final LinearClock linearClock = aviTrack.getClock();
        final float deltaPercent = Math.abs(calcUsPerSample - linearClock.usPerChunk) / (float)linearClock.usPerChunk;
        if (deltaPercent >.01) {
          Log.i(TAG, "Updating stream " + i + " calcUsPerSample=" + calcUsPerSample + " reported=" + linearClock.usPerChunk);
          linearClock.usPerChunk = calcUsPerSample;
        }
      }
    }
    final AviSeekMap seekMap = new AviSeekMap(videoTrack, seekOffsets, seekFrameRate, moviOffset, getDuration());
    setSeekMap(seekMap);
  }

  @Nullable
  private AviTrack getAviTrack(int chunkId) {
    final int streamId = getStreamId(chunkId);
    if (streamId >= 0 && streamId < aviTracks.length) {
      return aviTracks[streamId];
    }
    return null;
  }

  int checkAlign(final ExtractorInput input, PositionHolder seekPosition) {
    final long position = input.getPosition();
    if ((position & 1) ==1) {
      seekPosition.position = position +1;
      return RESULT_SEEK;
    }
    return RESULT_CONTINUE;
  }

  int readSamples(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    if (chunkHandler != null) {
      if (chunkHandler.resume(input)) {
        chunkHandler = null;
        return checkAlign(input, seekPosition);
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
        Log.w(TAG, "Unknown tag=" + toString(chunkId) + " pos=" + (input.getPosition() - 8)
            + " size=" + size + " moviEnd=" + moviEnd);
        return RESULT_SEEK;
      }
      if (aviTrack.newChunk(chunkId, size, input)) {
        return checkAlign(input, seekPosition);
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

  private static void w(String message) {
    try {
      Log.w(TAG, message);
    } catch (RuntimeException e) {
      //Catch not mocked for tests
    }
  }
}
