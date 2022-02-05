package com.google.android.exoplayer2.extractor.avi;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.MpegAudioUtil;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MpegAudioChunkHandlerTest {
  private static final int FPS = 24;
  private Format MP3_FORMAT = new Format.Builder().setChannelCount(2).
      setSampleMimeType(MimeTypes.AUDIO_MPEG).setSampleRate(44100).build();
  private static final long CHUNK_MS = C.MICROS_PER_SECOND / FPS;
  private final MpegAudioUtil.Header header = new MpegAudioUtil.Header();
  private FakeTrackOutput fakeTrackOutput;
  private MpegAudioChunkHandler mpegAudioChunkHandler;
  private byte[] mp3Frame;
  private long frameUs;

  @Before
  public void before() throws IOException {
    fakeTrackOutput = new FakeTrackOutput(false);
    fakeTrackOutput.format(MP3_FORMAT);
    mpegAudioChunkHandler = new MpegAudioChunkHandler(0, fakeTrackOutput,
        new ChunkClock(C.MICROS_PER_SECOND, FPS), MP3_FORMAT.sampleRate);

    if (mp3Frame == null) {
      final Context context = ApplicationProvider.getApplicationContext();
      mp3Frame = TestUtil.getByteArray(context,"extractordumps/avi/frame.mp3.dump");
      header.setForHeaderData(ByteBuffer.wrap(mp3Frame).getInt());
      //About 26ms
      frameUs = header.samplesPerFrame * C.MICROS_PER_SECOND / header.sampleRate;
    }
  }

  @Test
  public void newChunk_givenNonMpegData() throws IOException {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[1024]).
        build();

    mpegAudioChunkHandler.newChunk((int)input.getLength(), input);
    Assert.assertEquals(1024, fakeTrackOutput.getSampleData(0).length);
    Assert.assertEquals(CHUNK_MS, mpegAudioChunkHandler.getClock().getUs());
  }
  @Test
  public void newChunk_givenEmptyChunk() throws IOException {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).
        build();
    mpegAudioChunkHandler.newChunk((int)input.getLength(), input);
    Assert.assertEquals(C.MICROS_PER_SECOND / 24, mpegAudioChunkHandler.getClock().getUs());
  }

  @Test
  public void setIndex_given12frames() {
    mpegAudioChunkHandler.setIndex(12);
    Assert.assertEquals(500_000L, mpegAudioChunkHandler.getTimeUs());
  }

  @Test
  public void newChunk_givenSingleFrame() throws IOException {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(mp3Frame).build();

    mpegAudioChunkHandler.newChunk(mp3Frame.length, input);
    Assert.assertArrayEquals(mp3Frame, fakeTrackOutput.getSampleData(0));
    Assert.assertEquals(frameUs, mpegAudioChunkHandler.getTimeUs());
  }

  @Test
  public void newChunk_givenSeekAndFragmentedFrames() throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(mp3Frame.length * 2);
    byteBuffer.put(mp3Frame, mp3Frame.length / 2, mp3Frame.length / 2);
    byteBuffer.put(mp3Frame);
    final int remainder = byteBuffer.remaining();
    byteBuffer.put(mp3Frame, 0, remainder);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        build();

    mpegAudioChunkHandler.setIndex(1); //Seek
    Assert.assertFalse(mpegAudioChunkHandler.newChunk(byteBuffer.capacity(), input));
    Assert.assertArrayEquals(mp3Frame, fakeTrackOutput.getSampleData(0));
    Assert.assertEquals(frameUs + CHUNK_MS, mpegAudioChunkHandler.getTimeUs());

    Assert.assertTrue(mpegAudioChunkHandler.resume(input));
    Assert.assertEquals(header.frameSize - remainder, mpegAudioChunkHandler.getFrameRemaining());
  }

  @Test
  public void newChunk_givenTwoFrames() throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(mp3Frame.length * 2);
    byteBuffer.put(mp3Frame);
    byteBuffer.put(mp3Frame);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        build();
    Assert.assertFalse(mpegAudioChunkHandler.newChunk(byteBuffer.capacity(), input));
    Assert.assertEquals(1, fakeTrackOutput.getSampleCount());
    Assert.assertEquals(0L, fakeTrackOutput.getSampleTimeUs(0));

    Assert.assertTrue(mpegAudioChunkHandler.resume(input));
    Assert.assertEquals(2, fakeTrackOutput.getSampleCount());
    Assert.assertEquals(frameUs, fakeTrackOutput.getSampleTimeUs(1));
  }
}
