package com.google.android.exoplayer2.extractor.ts;

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.DvbTeletextInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;
import java.util.List;

/** Parses DVB Teletext subtitle data and extracts individual frames. */
public final class DvbTeletextReader implements ElementaryStreamReader {

  private final List<DvbTeletextInfo> teletextInfos;
  private final TrackOutput[] outputs;

  private boolean writingSample;
  private int sampleBytesWritten;
  private long sampleTimeUs;

  /**
   * @param teletextInfos Information about the DVB Teletext subtitles associated to the stream.
   */
  public DvbTeletextReader(List<DvbTeletextInfo> teletextInfos) {
    this.teletextInfos = teletextInfos;
    outputs = new TrackOutput[teletextInfos.size()];
    sampleTimeUs = C.TIME_UNSET;
  }

  @Override
  public void seek() {
    writingSample = false;
    sampleTimeUs = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    for (int i = 0; i < outputs.length; i++) {
      DvbTeletextInfo teletextInfo = teletextInfos.get(i);
      idGenerator.generateNewId();
      TrackOutput output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_TEXT);
      Format.Builder formatBuilder = new Format.Builder()
          .setId(idGenerator.getFormatId())
          .setSampleMimeType(MimeTypes.APPLICATION_TELETEXT)
          .setInitializationData(Collections.singletonList(teletextInfo.initializationData))
          .setLanguage(teletextInfo.language);
      if (teletextInfo.type == 0x02) { // Teletext subtitle page
        formatBuilder.setRoleFlags(C.ROLE_FLAG_SUBTITLE);
      } else if (teletextInfo.type == 0x05) { // Teletext subtitle page for hearing impaired people
        formatBuilder.setRoleFlags(C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND);
      }
      output.format(formatBuilder.build());
      outputs[i] = output;
    }
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    if ((flags & FLAG_DATA_ALIGNMENT_INDICATOR) == 0) {
      return;
    }
    writingSample = true;
    if (pesTimeUs != C.TIME_UNSET) {
      sampleTimeUs = pesTimeUs;
    }
    sampleBytesWritten = 0;
  }

  @Override
  public void packetFinished() {
    if (writingSample) {
      if (sampleTimeUs != C.TIME_UNSET) {
        for (TrackOutput output : outputs) {
          output.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytesWritten, 0, null);
        }
      }
      writingSample = false;
    }
  }

  @Override
  public void consume(ParsableByteArray data)
      throws ParserException {
    if (writingSample) {
      int dataPosition = data.getPosition();
      int bytesAvailable = data.bytesLeft();
      for (TrackOutput output : outputs) {
        data.setPosition(dataPosition);
        output.sampleData(data, bytesAvailable);
      }
      sampleBytesWritten += bytesAvailable;
    }
  }
}
