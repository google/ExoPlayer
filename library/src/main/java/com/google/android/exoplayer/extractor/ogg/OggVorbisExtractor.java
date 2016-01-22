/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.extractor.ogg.VorbisUtil.Mode;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

/**
 * {@link Extractor} to extract Vorbis data out of Ogg byte stream.
 */
public final class OggVorbisExtractor implements Extractor {

  private static final String TAG = "OggVorbisExtractor";

  private static final int OGG_MAX_SEGMENT_SIZE = 255;

  private final ParsableByteArray scratch = new ParsableByteArray(
      new byte[OGG_MAX_SEGMENT_SIZE * 255], 0);
  private final OggReader oggReader =  new OggReader();

  private TrackOutput trackOutput;
  private VorbisSetup vorbisSetup;
  private int previousPacketBlockSize;
  private long elapsedSamples;
  private boolean seenFirstAudioPacket;

  private VorbisUtil.VorbisIdHeader vorbisIdHeader;
  private VorbisUtil.CommentHeader commentHeader;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    try {
      OggReader.PageHeader header = new OggReader.PageHeader();
      OggReader.populatePageHeader(input, header, scratch, true);
      if ((header.type & 0x02) != 0x02) {
        throw new ParserException("expected page to be first page of a logical stream");
      }
      input.resetPeekPosition();
    } catch (ParserException e) {
      Log.e(TAG, e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public void init(ExtractorOutput output) {
    trackOutput = output.track(0);
    output.endTracks();
    output.seekMap(SeekMap.UNSEEKABLE);
  }

  @Override
  public void seek() {
    oggReader.reset();
    previousPacketBlockSize = -1;
    elapsedSamples = 0;
    seenFirstAudioPacket = false;
    scratch.reset();
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {

    if (vorbisSetup == null) {
      vorbisSetup = readSetupHeaders(input, scratch);
      ArrayList<byte[]> codecInitialisationData = new ArrayList<>();
      codecInitialisationData.clear();
      codecInitialisationData.add(vorbisSetup.idHeader.data);
      codecInitialisationData.add(vorbisSetup.setupHeaderData);

      long duration = input.getLength() == C.LENGTH_UNBOUNDED ? C.UNKNOWN_TIME_US
          : input.getLength() * 8000000 / vorbisSetup.idHeader.getApproximateBitrate();
      trackOutput.format(MediaFormat.createAudioFormat(null, MimeTypes.AUDIO_VORBIS,
          this.vorbisSetup.idHeader.bitrateNominal, OGG_MAX_SEGMENT_SIZE * 255, duration,
          this.vorbisSetup.idHeader.channels, (int) this.vorbisSetup.idHeader.sampleRate,
          codecInitialisationData, null));
    }
    if (oggReader.readPacket(input, scratch)) {
      // if this is an audio packet...
      if ((scratch.data[0] & 0x01) != 1) {
        // ... we need to decode the block size
        int packetBlockSize = decodeBlockSize(scratch.data[0], vorbisSetup);
        // a packet contains samples produced from overlapping the previous and current frame data
        // (https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-350001.3.2)
        int samplesInPacket = seenFirstAudioPacket ? (packetBlockSize + previousPacketBlockSize) / 4
            : 0;
        // codec expects the number of samples appended to audio data
        appendNumberOfSamples(scratch, samplesInPacket);

        // calculate time and send audio data to codec
        long timeUs = elapsedSamples * C.MICROS_PER_SECOND / vorbisSetup.idHeader.sampleRate;
        trackOutput.sampleData(scratch, scratch.limit());
        trackOutput.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, scratch.limit(), 0, null);

        // update state in members for next iteration
        seenFirstAudioPacket = true;
        elapsedSamples += samplesInPacket;
        previousPacketBlockSize = packetBlockSize;
      }
      scratch.reset();
      return RESULT_CONTINUE;
    }
    return RESULT_END_OF_INPUT;
  }

  //@VisibleForTesting
  /* package */ VorbisSetup readSetupHeaders(ExtractorInput input,  ParsableByteArray scratch)
      throws IOException, InterruptedException {

    if (vorbisIdHeader == null) {
      oggReader.readPacket(input, scratch);
      vorbisIdHeader = VorbisUtil.readVorbisIdentificationHeader(scratch);
      scratch.reset();
    }

    if (commentHeader == null) {
      oggReader.readPacket(input, scratch);
      commentHeader = VorbisUtil.readVorbisCommentHeader(scratch);
      scratch.reset();
    }

    oggReader.readPacket(input, scratch);
    // the third packet contains the setup header
    byte[] setupHeaderData = new byte[scratch.limit()];
    // raw data of vorbis setup header has to be passed to decoder as CSD buffer #2
    System.arraycopy(scratch.data, 0, setupHeaderData, 0, scratch.limit());
    // partially decode setup header to get the modes
    Mode[] modes = VorbisUtil.readVorbisModes(scratch, vorbisIdHeader.channels);
    // we need the ilog of modes all the time when extracting, so we compute it once
    int iLogModes = VorbisUtil.iLog(modes.length - 1);
    scratch.reset();

    return new VorbisSetup(vorbisIdHeader, commentHeader, setupHeaderData, modes, iLogModes);
  }

  //@VisibleForTesting
  /* package */ static void appendNumberOfSamples(ParsableByteArray buffer,
      long packetSampleCount) {

    buffer.setLimit(buffer.limit() + 4);
    // The vorbis decoder expects the number of samples in the packet
    // to be appended to the audio data as an int32
    buffer.data[buffer.limit() - 4] = (byte) ((packetSampleCount) & 0xFF);
    buffer.data[buffer.limit() - 3] = (byte) ((packetSampleCount >>> 8) & 0xFF);
    buffer.data[buffer.limit() - 2] = (byte) ((packetSampleCount >>> 16) & 0xFF);
    buffer.data[buffer.limit() - 1] = (byte) ((packetSampleCount >>> 24) & 0xFF);
  }

  private static int decodeBlockSize(byte firstByteOfAudioPacket, VorbisSetup vorbisSetup) {
    // read modeNumber (https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-730004.3.1)
    int modeNumber = VorbisUtil.readBits(firstByteOfAudioPacket, vorbisSetup.iLogModes, 1);
    int currentBlockSize;
    if (!vorbisSetup.modes[modeNumber].blockFlag) {
      currentBlockSize = vorbisSetup.idHeader.blockSize0;
    } else {
      currentBlockSize = vorbisSetup.idHeader.blockSize1;
    }
    return currentBlockSize;
  }

  /**
   * Class to hold all data read from Vorbis setup headers.
   */
  /* package */ static final class VorbisSetup {

    public final VorbisUtil.VorbisIdHeader idHeader;
    public final VorbisUtil.CommentHeader commentHeader;
    public final byte[] setupHeaderData;
    public final Mode[] modes;
    public final int iLogModes;

    public VorbisSetup(VorbisUtil.VorbisIdHeader idHeader, VorbisUtil.CommentHeader
        commentHeader, byte[] setupHeaderData, Mode[] modes, int iLogModes) {
      this.idHeader = idHeader;
      this.commentHeader = commentHeader;
      this.setupHeaderData = setupHeaderData;
      this.modes = modes;
      this.iLogModes = iLogModes;
    }

  }

}
