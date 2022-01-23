package com.google.android.exoplayer2.extractor.avi;

import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.List;
import java.util.Map;

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

  static final String TAG = "AviExtractor";
  private static final int PEEK_BYTES = 28;

  private static final int STATE_READ_TRACKS = 0;
  private static final int STATE_FIND_MOVI = 1;
  private static final int STATE_READ_IDX1 = 2;
  private static final int STATE_READ_SAMPLES = 3;
  private static final int STATE_SEEK_START = 4;

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

  private int state;
  private ExtractorOutput output;
  private AviHeaderBox aviHeader;
  private long durationUs = C.TIME_UNSET;
  private SparseArray<AviTrack> idTrackMap = new SparseArray<>();
  //At the start of the movi tag
  private long moviOffset;
  private long moviEnd;
  private AviSeekMap aviSeekMap;
  private int flags;

//  private long indexOffset; //Usually chunkStart

  //If partial read
  private transient AviTrack chunkHandler;

  static void alignInput(ExtractorInput input) throws IOException {
    // This isn't documented anywhere, but most files are aligned to even bytes
    // and can have gaps of zeros
    if ((input.getPosition() & 1) == 1) {
      input.skipFully(1);
    }
  }

  static long alignPosition(long position) {
    if ((position & 1) == 1) {
      position++;
    }
    return position;
  }

  public AviExtractor() {
    this(0);
  }

  public AviExtractor(int flags) {
    this.flags = flags;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return peekHeaderList(input);
  }

  static ByteBuffer allocate(int bytes) {
    final byte[] buffer = new byte[bytes];
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return byteBuffer;
  }

  private void setSeekMap(AviSeekMap aviSeekMap) {
    this.aviSeekMap = aviSeekMap;
    output.seekMap(aviSeekMap);
  }

  static boolean peekHeaderList(ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = allocate(PEEK_BYTES);
    input.peekFully(byteBuffer.array(), 0, PEEK_BYTES);
    final int riff = byteBuffer.getInt();
    if (riff != AviExtractor.RIFF) {
      return false;
    }
    long reportedLen = getUInt(byteBuffer) + byteBuffer.position();
    final long inputLen = input.getLength();
    if (inputLen != C.LENGTH_UNSET && inputLen != reportedLen) {
      Log.w(TAG, "Header length doesn't match stream length");
    }
    int avi = byteBuffer.getInt();
    if (avi != AviExtractor.AVI_) {
      return false;
    }
    final int list = byteBuffer.getInt();
    if (list != ListBox.LIST) {
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

  @Nullable
  ListBox readHeaderList(ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = allocate(20);
    input.readFully(byteBuffer.array(), 0, byteBuffer.capacity());
    final int riff = byteBuffer.getInt();
    if (riff != AviExtractor.RIFF) {
      return null;
    }
    long reportedLen = getUInt(byteBuffer) + byteBuffer.position();
    final long inputLen = input.getLength();
    if (inputLen != C.LENGTH_UNSET && inputLen != reportedLen) {
      Log.w(TAG, "Header length doesn't match stream length");
    }
    final int avi = byteBuffer.getInt();
    if (avi != AviExtractor.AVI_) {
      return null;
    }
    final int list = byteBuffer.getInt();
    if (list != ListBox.LIST) {
      return null;
    }
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

  private static Box peekNext(final List<Box> streams, int i, int type) {
    if (i + 1 < streams.size() && streams.get(i + 1).getType() == type) {
      return streams.get(i + 1);
    }
    return null;
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
    //This is usually wrong, so it will be overwritten by video if present
    durationUs = aviHeader.getFrames() * (long)aviHeader.getMicroSecPerFrame();

    int streamId = 0;
    for (Box box : headerList.getChildren()) {
      if (box instanceof ListBox && ((ListBox) box).getListType() == STRL) {
        final ListBox streamList = (ListBox) box;
        final StreamHeaderBox streamHeader = streamList.getChild(StreamHeaderBox.class);
        final StreamFormatBox streamFormat = streamList.getChild(StreamFormatBox.class);
        if (streamHeader == null) {
          Log.w(TAG, "Missing Stream Header");
          continue;
        }
        if (streamFormat == null) {
          Log.w(TAG, "Missing Stream Format");
          continue;
        }
        final Format.Builder builder = new Format.Builder();
        builder.setId(streamId);
        final StreamNameBox streamName = streamList.getChild(StreamNameBox.class);
        if (streamName != null) {
          builder.setLabel(streamName.getName());
        }
        if (streamHeader.isVideo()) {
          final String mimeType = streamHeader.getMimeType();
          if (mimeType == null) {
            Log.w(TAG, "Unknown FourCC: " + toString(streamHeader.getFourCC()));
            continue;
          }
          final VideoFormat videoFormat = streamFormat.getVideoFormat();
          final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_VIDEO);
          builder.setWidth(videoFormat.getWidth());
          builder.setHeight(videoFormat.getHeight());
          builder.setFrameRate(streamHeader.getFrameRate());
          builder.setSampleMimeType(mimeType);

          final AviTrack aviTrack;
          switch (mimeType) {
            case MimeTypes.VIDEO_MP4V:
              aviTrack = new Mp4vAviTrack(streamId, streamHeader, trackOutput, builder);
              break;
            case MimeTypes.VIDEO_H264:
              aviTrack = new AvcAviTrack(streamId, streamHeader, trackOutput, builder);
              break;
            default:
              aviTrack = new AviTrack(streamId, streamHeader, trackOutput);
          }
          trackOutput.format(builder.build());
          idTrackMap.put('0' | (('0' + streamId) << 8) | ('d' << 16) | ('c' << 24), aviTrack);
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
          idTrackMap.put('0' | (('0' + streamId) << 8) | ('w' << 16) | ('b' << 24),
              new AviTrack(streamId, streamHeader, trackOutput));
        }
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

  /**
   * Reads the index and sets the keyFrames and creates the SeekMap
   * @param input
   * @param remaining
   * @throws IOException
   */
  void readIdx1(ExtractorInput input, int remaining) throws IOException {
    final ByteBuffer indexByteBuffer = allocate(Math.min(remaining, 64 * 1024));
    final byte[] bytes = indexByteBuffer.array();

    final HashMap<Integer, UnboundedIntArray> audioIdFrameMap = new HashMap<>();
    AviTrack videoTrack = null;
    //Video seek offsets
    UnboundedIntArray videoSeekOffset = new UnboundedIntArray();
    for (int i=0;i<idTrackMap.size();i++) {
      final AviTrack aviTrack = idTrackMap.valueAt(i);
      if (videoTrack == null && aviTrack.isVideo()) {
        videoTrack = aviTrack;
      } else {
        audioIdFrameMap.put(idTrackMap.keyAt(i), new UnboundedIntArray());
      }
    }
    if (videoTrack == null) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      Log.w(TAG, "No video track found");
      return;
    }
    resetFrames();
    final int seekFrameRate = (int)(videoTrack.streamHeaderBox.getFrameRate() * 2);

    final UnboundedIntArray keyFrameList = new UnboundedIntArray();
    while (remaining > 0) {
      final int toRead = Math.min(indexByteBuffer.remaining(), remaining);
      input.readFully(bytes, indexByteBuffer.position(), toRead);
      remaining -= toRead;
      while (indexByteBuffer.remaining() >= 16) {
        final int id = indexByteBuffer.getInt();
        final AviTrack aviTrack = idTrackMap.get(id);
        if (aviTrack == null) {
          if (id != AviExtractor.REC_) {
            Log.w(TAG, "Unknown Track Type: " + toString(id));
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
            keyFrameList.add(aviTrack.frame);
          }
          if (aviTrack.frame % seekFrameRate == 0) {

            videoSeekOffset.add(offset);
            for (Map.Entry<Integer, UnboundedIntArray> entry : audioIdFrameMap.entrySet()) {
              final int audioId = entry.getKey();
              final UnboundedIntArray videoFrameMap = entry.getValue();
              final AviTrack audioTrack = idTrackMap.get(audioId);
              videoFrameMap.add(audioTrack.frame);
            }
          }
        }
        aviTrack.advance();
      }
      indexByteBuffer.compact();
    }
    videoSeekOffset.pack();
    if (!videoTrack.isAllKeyFrames()) {
      keyFrameList.pack();
      final int[] keyFrames = keyFrameList.getArray();
      videoTrack.setKeyFrames(keyFrames);
    }

    //Correct the timings
    durationUs = videoTrack.usPerSample * videoTrack.frame;

    final SparseArray<int[]> idFrameArray = new SparseArray<>();
    for (Map.Entry<Integer, UnboundedIntArray> entry : audioIdFrameMap.entrySet()) {
      entry.getValue().pack();
      idFrameArray.put(entry.getKey(), entry.getValue().getArray());
      final AviTrack aviTrack = idTrackMap.get(entry.getKey());
      //Sometimes this value is way off
      long calcUsPerSample = (getDuration()/aviTrack.frame);
      float deltaPercent = Math.abs(calcUsPerSample - aviTrack.usPerSample) / (float)aviTrack.usPerSample;
      if (deltaPercent >.01) {
        aviTrack.usPerSample = getDuration()/aviTrack.frame;
        Log.d(TAG, "Frames act=" + getDuration() + " calc=" + (aviTrack.usPerSample * aviTrack.frame));
      }
    }
    final AviSeekMap seekMap = new AviSeekMap(videoTrack, seekFrameRate, videoSeekOffset.getArray(),
        idFrameArray, moviOffset, getDuration());
    setSeekMap(seekMap);
    resetFrames();
  }

  int readSamples(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    if (chunkHandler != null) {
      if (chunkHandler.resume(input)) {
        chunkHandler = null;
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
      final int id = byteBuffer.getInt();
      final int size = byteBuffer.getInt();
      AviTrack sampleTrack = idTrackMap.get(id);
      if (sampleTrack == null) {
        if (id == ListBox.LIST) {
          seekPosition.position = input.getPosition() + 4;
        } else {
          seekPosition.position = alignPosition(input.getPosition() + size);
          if (id != JUNK) {
            Log.w(TAG, "Unknown tag=" + toString(id) + " pos=" + (input.getPosition() - 8)
                + " size=" + size + " moviEnd=" + moviEnd);
          }
        }
        return RESULT_SEEK;
      } else {
        if (!sampleTrack.newChunk(id, size, input)) {
          chunkHandler = sampleTrack;
        }
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
        resetFrames();
        state = STATE_SEEK_START;
      }
    } else {
      if (aviSeekMap != null) {
        aviSeekMap.setFrames(position, timeUs, idTrackMap);
      }
    }
  }

  void resetFrames() {
    for (int i=0;i<idTrackMap.size();i++) {
      final AviTrack aviTrack = idTrackMap.valueAt(i);
      aviTrack.seekFrame(0);
    }
  }

  @Override
  public void release() {
  }
}
