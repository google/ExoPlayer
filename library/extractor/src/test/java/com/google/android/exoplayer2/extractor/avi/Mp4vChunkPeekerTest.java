package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

public class Mp4vChunkPeekerTest {

  private ByteBuffer makeSequence() {
    return DataHelper.appendNal(AviExtractor.allocate(32),Mp4vChunkPeeker.SEQUENCE_START_CODE);
  }

  @Test
  public void peek_givenNoSequence() throws IOException {
    ByteBuffer byteBuffer = makeSequence();
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    final Mp4vChunkPeeker mp4vChunkPeeker = new Mp4vChunkPeeker(formatBuilder, fakeTrackOutput);
    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(1f, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }

  @Test
  public void peek_givenAspectRatio() throws IOException {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final Mp4vChunkPeeker mp4vChunkPeeker = new Mp4vChunkPeeker(formatBuilder, fakeTrackOutput);
    final FakeExtractorInput input = DataHelper.getInput("mp4v_sequence.dump");

    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(1.2121212, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }

  @Test
  public void peek_givenCustomAspectRatio() throws IOException {
    ByteBuffer byteBuffer = makeSequence();
    byteBuffer.putInt(0x5555);
    DataHelper.appendNal(byteBuffer, (byte)Mp4vChunkPeeker.LAYER_START_CODE);

    BitBuffer bitBuffer = new BitBuffer();
    bitBuffer.push(false); //random_accessible_vol
    bitBuffer.push(8, 8); //video_object_type_indication
    bitBuffer.push(true); // is_object_layer_identifier
    bitBuffer.push(7, 7); // video_object_layer_verid, video_object_layer_priority
    bitBuffer.push(4, Mp4vChunkPeeker.Extended_PAR);
    bitBuffer.push(8, 16);
    bitBuffer.push(8, 9);
    final byte bytes[] = bitBuffer.getBytes();
    byteBuffer.put(bytes);

    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    final Mp4vChunkPeeker mp4vChunkPeeker = new Mp4vChunkPeeker(formatBuilder, fakeTrackOutput);
    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(16f/9f, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }
}