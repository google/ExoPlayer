package com.google.android.exoplayer2.extractor.avi;

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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

public class AviExtractor implements Extractor {
  static final String TAG = "AviExtractor";
  private static final int PEEK_BYTES = 28;

  private final int STATE_READ_TRACKS = 0;
  private final int STATE_FIND_MOVI = 1;
  private final int STATE_FIND_IDX1 = 2;
  private final int STATE_READ_SAMPLES = 3;

  static final int RIFF = AviUtil.toInt(new byte[]{'R','I','F','F'});
  static final int AVI_ = AviUtil.toInt(new byte[]{'A','V','I',' '});
  //Stream List
  static final int STRL = 's' | ('t' << 8) | ('r' << 16) | ('l' << 24);
  //Stream CODEC data
  static final int STRD = 's' | ('t' << 8) | ('r' << 16) | ('d' << 24);
  //movie data box
  static final int MOVI = 'm' | ('o' << 8) | ('v' << 16) | ('i' << 24);
  //Index
  static final int IDX1 = 'i' | ('d' << 8) | ('x' << 16) | ('1' << 24);

  private final int flags;

  private int state;
  private ExtractorOutput output;
  private AviHeader aviHeader;
  //After the movi position
  private long firstChunkPosition;

  public AviExtractor() {
    this(0);
  }

  public AviExtractor(int flags) {
    this.flags = flags;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return peakHeaderList(input);
  }

  static ByteBuffer allocate(int bytes) {
    final byte[] buffer = new byte[bytes];
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return byteBuffer;
  }
  boolean peakHeaderList(ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = allocate(PEEK_BYTES);
    input.peekFully(byteBuffer.array(), 0, PEEK_BYTES);
    final int riff = byteBuffer.getInt();
    if (riff != AviExtractor.RIFF) {
      return false;
    }
    long reportedLen = AviUtil.getUInt(byteBuffer) + byteBuffer.position();
    final long inputLen = input.getLength();
    if (inputLen != C.LENGTH_UNSET && inputLen != reportedLen) {
      Log.w(TAG, "Header length doesn't match stream length");
    }
    int avi = byteBuffer.getInt();
    if (avi != AviExtractor.AVI_) {
      return false;
    }
    final int list = byteBuffer.getInt();
    if (list != IAviList.LIST) {
      return false;
    }
    //Len
    byteBuffer.getInt();
    final int hdrl = byteBuffer.getInt();
    if (hdrl != IAviList.TYPE_HDRL) {
      return false;
    }
    final int avih = byteBuffer.getInt();
    if (avih != AviHeader.AVIH) {
      return false;
    }
    return true;
  }
  @Nullable
  ResidentList readHeaderList(ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = allocate(20);
    input.readFully(byteBuffer.array(), 0, byteBuffer.capacity());
    final int riff = byteBuffer.getInt();
    if (riff != AviExtractor.RIFF) {
      return null;
    }
    long reportedLen = AviUtil.getUInt(byteBuffer) + byteBuffer.position();
    final long inputLen = input.getLength();
    if (inputLen != C.LENGTH_UNSET && inputLen != reportedLen) {
      Log.w(TAG, "Header length doesn't match stream length");
    }
    int avi = byteBuffer.getInt();
    if (avi != AviExtractor.AVI_) {
      return null;
    }
    final ResidentList header = ResidentList.getInstance(byteBuffer, input, ResidentList.class);
    if (header == null) {
      return null;
    }
    if (header.getListType() != IAviList.TYPE_HDRL) {
      Log.e(TAG, "Expected " +AviUtil.toString(IAviList.TYPE_HDRL) + ", got: " +
          AviUtil.toString(header.getType()));
      return null;
    }
    return header;
  }

  long getDuration() {
    if (aviHeader == null) {
      return C.TIME_UNSET;
    }
    return aviHeader.getFrames() * (long)aviHeader.getMicroSecPerFrame();
  }

  @Override
  public void init(ExtractorOutput output) {
    this.state = STATE_READ_TRACKS;
    this.output = output;
  }

  private static ResidentBox peekNext(final List<ResidentBox> streams, int i, int type) {
    if (i + 1 < streams.size() && streams.get(i + 1).getType() == type) {
      return streams.get(i + 1);
    }
    return null;
  }

  private int readTracks(ExtractorInput input) throws IOException {
    final ResidentList headerList = readHeaderList(input);
    if (headerList == null) {
      throw new IOException("AVI Header List not found");
    }
    final List<ResidentBox> headerChildren = headerList.getBoxList();
    aviHeader = AviUtil.getBox(headerChildren, AviHeader.class);
    if (aviHeader == null) {
      throw new IOException("AviHeader not found");
    }
    headerChildren.remove(aviHeader);
    //headerChildren should only be Stream Lists now

    int streamId = 0;
    for (Box box : headerChildren) {
      if (box instanceof ResidentList && ((ResidentList) box).getListType() == STRL) {
        final ResidentList streamList = (ResidentList) box;
        final List<ResidentBox> streamChildren = streamList.getBoxList();
        for (int i=0;i<streamChildren.size();i++) {
          final ResidentBox residentBox = streamChildren.get(i);
          if (residentBox instanceof StreamHeader) {
            final StreamHeader streamHeader = (StreamHeader) residentBox;
            final StreamFormat streamFormat = (StreamFormat) peekNext(streamChildren, i, StreamFormat.STRF);
            if (streamFormat != null) {
              i++;
              if (streamHeader.isVideo()) {
                final VideoFormat videoFormat = streamFormat.getVideoFormat();
                final ResidentBox codecBox = (ResidentBox) peekNext(streamChildren, i, STRD);
                final List<byte[]> codecData;
                if (codecBox != null) {
                  codecData = Collections.singletonList(codecBox.byteBuffer.array());
                  i++;
                } else {
                  codecData = null;
                }
                final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_VIDEO);
                final Format.Builder builder = new Format.Builder();
                builder.setWidth(videoFormat.getWidth());
                builder.setHeight(videoFormat.getHeight());
                builder.setFrameRate(streamHeader.getFrameRate());
                builder.setCodecs(streamHeader.getCodec());
                builder.setInitializationData(codecData);
                trackOutput.format(builder.build());
              } else if (streamHeader.isAudio()) {
                final AudioFormat audioFormat = streamFormat.getAudioFormat();
                final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_AUDIO);
                final Format.Builder builder = new Format.Builder();
                builder.setCodecs(audioFormat.getCodec());
                builder.setChannelCount(audioFormat.getChannels());
                builder.setSampleRate(audioFormat.getSamplesPerSecond());
                if (audioFormat.getFormatTag() == AudioFormat.WAVE_FORMAT_PCM) {
                  //TODO: Determine if this is LE or BE - Most likely LE
                  final short bps = audioFormat.getBitsPerSample();
                  if (bps == 8) {
                    builder.setPcmEncoding(C.ENCODING_PCM_8BIT);
                  } else if (bps == 16){
                    builder.setPcmEncoding(C.ENCODING_PCM_16BIT);
                  }
                }
                trackOutput.format(builder.build());
              }
            }
            streamId++;
          }
        }
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
    final long size = byteBuffer.getInt() & AviUtil.UINT_MASK;
    final long position = input.getPosition();
    //-4 because we over read for the LIST type
    long nextBox = position + size - 4;
    if (tag == IAviList.LIST) {
      final int listType = byteBuffer.getInt();
      if (listType == MOVI) {
        firstChunkPosition = position;
        if (aviHeader.hasIndex()) {
          state = STATE_FIND_IDX1;
        } else {
          output.seekMap(new SeekMap.Unseekable(getDuration()));
          state = STATE_READ_TRACKS;
          nextBox = firstChunkPosition;
        }
      }
    }
    seekPosition.position = nextBox;
    return RESULT_SEEK;
  }

  int findIdx1(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ByteBuffer byteBuffer = allocate(8);
    input.readFully(byteBuffer.array(), 0,8);
    final int tag = byteBuffer.getInt();
    long remaining = byteBuffer.getInt() & AviUtil.UINT_MASK;
    //TODO: Sanity check on file length
    if (tag == IDX1) {
      final ByteBuffer index = allocate(4096);
      final byte[] bytes = index.array();
      index.position(index.capacity());
      while (remaining > 0) {
        if (!index.hasRemaining()) {
          index.clear();
          final int toRead = (int)Math.min(4096, remaining);
          if (!input.readFully(bytes, 0, toRead, true)) {
            seekPosition.position = firstChunkPosition;
            output.seekMap(new SeekMap.Unseekable(getDuration()));
            break;
          }
          index.limit(toRead);
          remaining -=toRead;
        }

      }

//TODO
    } else {
      seekPosition.position = input.getPosition() + remaining;
    }
    return RESULT_SEEK;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    switch (state) {
      case STATE_READ_TRACKS:
        return readTracks(input);
      case STATE_FIND_MOVI:
        return findMovi(input, seekPosition);
      case STATE_FIND_IDX1:
        return findIdx1(input, seekPosition);
    }
    return RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {

  }

  @Override
  public void release() {

  }
}
