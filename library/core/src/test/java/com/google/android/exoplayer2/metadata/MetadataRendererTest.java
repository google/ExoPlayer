/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata;

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.sample;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageEncoder;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.scte35.TimeSignalCommand;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MetadataRenderer}. */
@RunWith(AndroidJUnit4.class)
public class MetadataRendererTest {

  private static final byte[] SCTE35_TIME_SIGNAL_BYTES =
      Bytes.concat(
          TestUtil.createByteArray(
              0, // table_id.
              0x80, // section_syntax_indicator, private_indicator, reserved, section_length(4).
              0x14, // section_length(8).
              0x00, // protocol_version.
              0x00), // encrypted_packet, encryption_algorithm, pts_adjustment(1).
          TestUtil.createByteArray(0x00, 0x00, 0x00, 0x00), // pts_adjustment(32).
          TestUtil.createByteArray(
              0x00, // cw_index.
              0x00, // tier(8).
              0x00, // tier(4), splice_command_length(4).
              0x05, // splice_command_length(8).
              0x06, // splice_command_type = time_signal.
              // Start of splice_time().
              0x80), // time_specified_flag, reserved, pts_time(1).
          TestUtil.createByteArray(
              0x52, 0x03, 0x02, 0x8f), // pts_time(32). PTS for a second after playback position.
          TestUtil.createByteArray(
              0x00, 0x00, 0x00, 0x00)); // CRC_32 (ignored, check happens at extraction).

  private static final Format EMSG_FORMAT =
      new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_EMSG).build();

  private final EventMessageEncoder eventMessageEncoder = new EventMessageEncoder();

  @Test
  public void decodeMetadata() throws Exception {
    EventMessage emsg =
        new EventMessage(
            "urn:test-scheme-id",
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Test data".getBytes(UTF_8));

    List<Metadata> metadata = runRenderer(eventMessageEncoder.encode(emsg));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).length()).isEqualTo(1);
    assertThat(metadata.get(0).get(0)).isEqualTo(emsg);
  }

  @Test
  public void decodeMetadata_handlesId3WrappedInEmsg() throws Exception {
    EventMessage emsg =
        new EventMessage(
            EventMessage.ID3_SCHEME_ID_AOM,
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            encodeTxxxId3Frame("Test description", "Test value"));

    List<Metadata> metadata = runRenderer(eventMessageEncoder.encode(emsg));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).length()).isEqualTo(1);
    TextInformationFrame expectedId3Frame =
        new TextInformationFrame("TXXX", "Test description", ImmutableList.of("Test value"));
    assertThat(metadata.get(0).get(0)).isEqualTo(expectedId3Frame);
  }

  @Test
  public void decodeMetadata_handlesScte35WrappedInEmsg() throws Exception {

    EventMessage emsg =
        new EventMessage(
            EventMessage.SCTE35_SCHEME_ID,
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            SCTE35_TIME_SIGNAL_BYTES);

    List<Metadata> metadata = runRenderer(eventMessageEncoder.encode(emsg));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).length()).isEqualTo(1);
    assertThat(metadata.get(0).get(0)).isInstanceOf(TimeSignalCommand.class);
  }

  @Test
  public void decodeMetadata_skipsMalformedWrappedMetadata() throws Exception {
    EventMessage emsg =
        new EventMessage(
            EventMessage.ID3_SCHEME_ID_AOM,
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Not a real ID3 tag".getBytes(ISO_8859_1));

    List<Metadata> metadata = runRenderer(eventMessageEncoder.encode(emsg));

    assertThat(metadata).isEmpty();
  }

  @Test
  public void renderMetadata_withTimelyOutput() throws Exception {
    EventMessage emsg =
        new EventMessage(
            "urn:test-scheme-id",
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Test data".getBytes(UTF_8));
    byte[] encodedEmsg = eventMessageEncoder.encode(emsg);
    List<Metadata> metadata = new ArrayList<>();
    MetadataRenderer renderer =
        new MetadataRenderer(/* output= */ metadata::add, /* outputLooper= */ null);
    FakeSampleStream fakeSampleStream =
        createFakeSampleStream(
            ImmutableList.of(
                sample(/* timeUs= */ 100_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                sample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 0L);

    // Call render() twice, the first call is to read the format and the second call will read the
    // metadata.
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    renderer.render(/* positionUs= */ 500_000, /* elapsedRealtimeUs= */ 0);

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).presentationTimeUs).isEqualTo(100_000);
  }

  @Test
  public void renderMetadata_withEarlyOutput() throws Exception {
    EventMessage emsg =
        new EventMessage(
            "urn:test-scheme-id",
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Test data".getBytes(UTF_8));
    byte[] encodedEmsg = eventMessageEncoder.encode(emsg);
    List<Metadata> metadata = new ArrayList<>();
    MetadataRenderer renderer =
        new MetadataRenderer(
            /* output= */ metadata::add,
            /* outputLooper= */ null,
            MetadataDecoderFactory.DEFAULT,
            /* outputMetadataEarly= */ true);
    FakeSampleStream fakeSampleStream =
        createFakeSampleStream(
            ImmutableList.of(
                sample(/* timeUs= */ 100_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                sample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 0L);

    // Call render() twice, the first call is to read the format and the second call will read the
    // metadata.
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    renderer.render(/* positionUs= */ 500_000, /* elapsedRealtimeUs= */ 0);

    // The renderer outputs metadata early.
    assertThat(metadata).hasSize(2);
    assertThat(metadata.get(0).presentationTimeUs).isEqualTo(100_000);
    assertThat(metadata.get(1).presentationTimeUs).isEqualTo(1_000_000);
  }

  @Test
  public void replaceStream_withIncreasingOffsetUs_updatesPendingMetadataPresentationTime()
      throws Exception {
    EventMessage emsg =
        new EventMessage(
            "urn:test-scheme-id",
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Test data".getBytes(UTF_8));
    byte[] encodedEmsg = eventMessageEncoder.encode(emsg);
    List<Metadata> metadataOutput = new ArrayList<>();
    MetadataRenderer renderer =
        new MetadataRenderer(
            /* output= */ metadataOutput::add,
            /* outputLooper= */ null,
            MetadataDecoderFactory.DEFAULT,
            /* outputMetadataEarly= */ false);
    FakeSampleStream fakeSampleStream =
        createFakeSampleStream(
            ImmutableList.of(
                sample(/* timeUs= */ 100_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                sample(/* timeUs= */ 200_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    // Start of the first reading period.
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 0L);
    // Read the format
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    // Read and render the first metadata. The second metadata is immediately read as pending.
    // The offset is added to timeUs of the samples when reading (100_000 and 200_000).
    renderer.render(/* positionUs= */ 99_999, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).isEmpty();
    renderer.render(/* positionUs= */ 100_000, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).hasSize(1);

    // Start of the 2nd reading period. Replace the stream with a different offset. This adjusts the
    // presentation time of the pending metadata.
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 100_000L);
    renderer.render(/* positionUs= */ 199_999, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).hasSize(1);

    // Output second metadata.
    renderer.render(/* positionUs= */ 200_000, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).hasSize(2);
    assertThat(metadataOutput.get(0).presentationTimeUs).isEqualTo(100_000);
    assertThat(metadataOutput.get(1).presentationTimeUs).isEqualTo(100_000);
  }

  @Test
  public void replaceStream_withDecreasingOffsetUs_updatesPendingMetadataPresentationTime()
      throws Exception {
    EventMessage emsg =
        new EventMessage(
            "urn:test-scheme-id",
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Test data".getBytes(UTF_8));
    byte[] encodedEmsg = eventMessageEncoder.encode(emsg);
    List<Metadata> metadataOutput = new ArrayList<>();
    MetadataRenderer renderer =
        new MetadataRenderer(
            /* output= */ metadataOutput::add,
            /* outputLooper= */ null,
            MetadataDecoderFactory.DEFAULT,
            /* outputMetadataEarly= */ false);
    FakeSampleStream fakeSampleStream =
        createFakeSampleStream(
            ImmutableList.of(
                sample(/* timeUs= */ 100_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                sample(/* timeUs= */ 200_000, C.BUFFER_FLAG_KEY_FRAME, encodedEmsg),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    // Start of the first reading period.
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 100_000L);
    // Read the format
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    // Read and render the first metadata. The second metadata is immediately read as pending.
    // The offset of 0 is added to timeUs of the samples when reading (100_000 and 200_000).
    renderer.render(/* positionUs= */ 199_999, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).isEmpty();
    renderer.render(/* positionUs= */ 200_000, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).hasSize(1);

    // Start of the 2nd reading period. Replace the stream with a different offset and adjust the
    // presentation time of the pending metadata.
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 0L);
    renderer.render(/* positionUs= */ 299_999, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).hasSize(1);

    // Output second metadata.
    renderer.render(/* positionUs= */ 300_000, /* elapsedRealtimeUs= */ 0);
    assertThat(metadataOutput).hasSize(2);
    assertThat(metadataOutput.get(0).presentationTimeUs).isEqualTo(100_000);
    assertThat(metadataOutput.get(1).presentationTimeUs).isEqualTo(300_000);
  }

  private static List<Metadata> runRenderer(byte[] input) throws ExoPlaybackException {
    List<Metadata> metadata = new ArrayList<>();
    MetadataRenderer renderer = new MetadataRenderer(metadata::add, /* outputLooper= */ null);
    FakeSampleStream fakeSampleStream =
        createFakeSampleStream(
            ImmutableList.of(
                sample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME, input), END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    renderer.replaceStream(
        new Format[] {EMSG_FORMAT},
        fakeSampleStream,
        /* startPositionUs= */ 0L,
        /* offsetUs= */ 0L);
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0); // Read the format
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0); // Read the data

    return Collections.unmodifiableList(metadata);
  }

  private static FakeSampleStream createFakeSampleStream(
      ImmutableList<FakeSampleStream.FakeSampleStreamItem> samples) {
    return new FakeSampleStream(
        new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
        /* mediaSourceEventDispatcher= */ null,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher(),
        EMSG_FORMAT,
        samples);
  }

  /**
   * Builds an ID3v2 tag containing a single 'user defined text information frame' (id='TXXX') with
   * {@code description} and {@code value}.
   *
   * <ul>
   *   <li><a href="http://id3.org/id3v2.4.0-structure">ID3v2 overall structure</a>
   *   <li><a href="http://id3.org/id3v2.4.0-frames">'user defined text information frame' spec</a>
   *       (item 4.2.6)
   * </ul>
   */
  private static byte[] encodeTxxxId3Frame(String description, String value) {
    byte[] id3FrameData =
        Bytes.concat(
            "TXXX".getBytes(ISO_8859_1), // ID for a 'user defined text information frame'
            TestUtil.createByteArray(0, 0, 0, 0), // Frame size (set later)
            TestUtil.createByteArray(0, 0), // Frame flags
            TestUtil.createByteArray(0), // Character encoding = ISO-8859-1
            description.getBytes(ISO_8859_1),
            TestUtil.createByteArray(0), // String null terminator
            value.getBytes(ISO_8859_1),
            TestUtil.createByteArray(0)); // String null terminator
    int frameSizeIndex = 7;
    int frameSize = id3FrameData.length - 10;
    Assertions.checkArgument(
        frameSize < 128, "frameSize must fit in 7 bits to avoid synch-safe encoding: " + frameSize);
    id3FrameData[frameSizeIndex] = (byte) frameSize;

    byte[] id3Bytes =
        Bytes.concat(
            "ID3".getBytes(ISO_8859_1), // identifier
            TestUtil.createByteArray(0x04, 0x00), // version
            TestUtil.createByteArray(0), // Tag flags
            TestUtil.createByteArray(0, 0, 0, 0), // Tag size (set later)
            id3FrameData);
    int tagSizeIndex = 9;
    int tagSize = id3Bytes.length - 10;
    Assertions.checkArgument(
        tagSize < 128, "tagSize must fit in 7 bits to avoid synch-safe encoding: " + tagSize);
    id3Bytes[tagSizeIndex] = (byte) tagSize;
    return id3Bytes;
  }
}
