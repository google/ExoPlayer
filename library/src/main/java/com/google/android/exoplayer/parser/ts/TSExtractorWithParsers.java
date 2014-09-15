package com.google.android.exoplayer.parser.ts;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.hls.Extractor;
import com.google.android.exoplayer.hls.Packet;
import com.google.android.exoplayer.hls.Parser;
import com.google.android.exoplayer.parser.aac.ADTSParser;
import com.google.android.exoplayer.upstream.DataSource;

import java.util.HashMap;

/**
 * Created by martin on 18/08/14.
 */
public class TSExtractorWithParsers extends Extractor {
  private final Parser[] parser = new Parser[2];
  private TSExtractor extractor;
  private Parser currentParser;

  public TSExtractorWithParsers(DataSource dataSource, HashMap<Object, Object> allocatorsMap) throws ParserException {
    extractor = new TSExtractor(dataSource, allocatorsMap);
    int audioStreamType = extractor.getStreamType(Packet.TYPE_AUDIO);
    if (audioStreamType == STREAM_TYPE_AAC_ADTS) {
      parser[Packet.TYPE_AUDIO] = new ADTSParser();
    }
  }

  @Override
  public Packet read() throws ParserException {
    while(true) {
      if (currentParser != null) {
        Packet packet = currentParser.read();
        if (packet == null) {
          currentParser = null;
        } else {
          return packet;
        }
      }
      Packet packet = extractor.read();
      if (packet == null) {
        return null;
      }

      if (parser[packet.type] != null) {
        parser[packet.type].pushPacket(packet);
        currentParser = parser[packet.type];
      } else {
        return packet;
      }
    }
  }

  @Override
  public int getStreamType(int type) {
    return extractor.getStreamType(type);
  }

  public void release() {
    for (int i = 0; i < 2; i++) {
      if (parser[i] != null) {
        parser[i].release();
      }
    }
  }
}
