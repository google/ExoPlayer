package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.parser.ts.TSExtractorNative;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceStream;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class HLSMediaChunk extends MediaChunk {

  private HLSExtractor extractor;
  private MediaFormat videoMediaFormat;
  private ArrayList<Integer> trackList;
  NonBlockingInputStream inputStream;

  /**
   * Constructor for a chunk of media samples.
   * @param dataSource     A {@link com.google.android.exoplayer.upstream.DataSource} for loading the data.
   * @param trackList
   * @param videoMediaFormat
   * @param dataSpec       Defines the data to be loaded.
   * @param format         The format of the stream to which this chunk belongs.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   */
  public HLSMediaChunk(DataSource dataSource, ArrayList<Integer> trackList, MediaFormat videoMediaFormat, DataSpec dataSpec, Format format, long startTimeUs, long endTimeUs, int nextChunkIndex) {
    super(dataSource, dataSpec, format, 0, startTimeUs, startTimeUs, nextChunkIndex);
    this.videoMediaFormat = videoMediaFormat;
    this.trackList = trackList;
  }

  @Override
  public void seekToStart() {
    seekTo(0, false);
  }

  @Override
  public boolean seekTo(long positionUs, boolean allowNoop) {
    return false;
  }

  @Override
  public boolean prepare() throws ParserException {
    return true;
  }

  private int checkExtractor() {
    if (this.extractor == null) {
      NonBlockingInputStream inputStream = getNonBlockingInputStream();
      Assertions.checkState(inputStream != null);
      byte firstByte[] = new byte[1];
      int ret = inputStream.read(firstByte, 0, 1);
      if (ret != 1) {
        return ret;
      }

      resetReadPosition();
      if (firstByte[0] == 0x47) {
        //this.extractor = new TSExtractor(inputStream);
        this.extractor = new TSExtractorNative(inputStream);
      } else {
        this.extractor = new AACExtractor(inputStream);
      }
    }

    return 1;
  }

  @Override
  public boolean read(int track, SampleHolder holder) throws ParserException {

    if (checkExtractor() <= 0) {
      return false;
    }

    return (extractor.read(trackList.get(track), holder) == TSExtractor.RESULT_READ_SAMPLE_FULL);
  }

  @Override
  public MediaFormat getMediaFormat(int track) {
    int type = trackList.get(track);

    // XXX: not nice :-(
    while (checkExtractor() == 0) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    if (type == TSExtractor.TYPE_VIDEO) {
      return videoMediaFormat;
    } else {
      return extractor.getAudioMediaFormat();
    }
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    return null;
  }

  public boolean isReadFinished() {

    if (this.extractor == null) {
      return false;
    }
    return extractor.isReadFinished();
  }
}
