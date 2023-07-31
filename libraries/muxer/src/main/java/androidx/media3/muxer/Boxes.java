/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.muxer.ColorUtils.MEDIAFORMAT_STANDARD_TO_PRIMARIES_AND_MATRIX;
import static androidx.media3.muxer.ColorUtils.MEDIAFORMAT_TRANSFER_TO_MP4_TRANSFER;
import static androidx.media3.muxer.Mp4Utils.MVHD_TIMEBASE;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.container.NalUnitUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Writes out various types of boxes as per MP4 (ISO/IEC 14496-12) standards.
 *
 * <p>Boxes do not construct their sub-boxes but take them as input {@linkplain ByteBuffer byte
 * buffers}.
 */
/* package */ final class Boxes {
  private Boxes() {}

  public static final ImmutableList<Byte> XMP_UUID =
      ImmutableList.of(
          (byte) 0xBE,
          (byte) 0x7A,
          (byte) 0xCF,
          (byte) 0xCB,
          (byte) 0x97,
          (byte) 0xA9,
          (byte) 0x42,
          (byte) 0xE8,
          (byte) 0x9C,
          (byte) 0x71,
          (byte) 0x99,
          (byte) 0x94,
          (byte) 0x91,
          (byte) 0xE3,
          (byte) 0xAF,
          (byte) 0xAC);

  /**
   * Returns the tkhd box.
   *
   * <p>This is a per-track header box.
   */
  public static ByteBuffer tkhd(
      int trackId,
      int trackDurationVu,
      int modificationTimestampSeconds,
      int orientation,
      Format format) {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x00000007); // version and flags; allow presentation, etc.

    contents.putInt(modificationTimestampSeconds); // creation_time
    contents.putInt(modificationTimestampSeconds); // modification_time

    contents.putInt(trackId);
    contents.putInt(0); // reserved

    contents.putInt(trackDurationVu);

    contents.putInt(0); // reserved
    contents.putInt(0); // reserved

    contents.putInt(0); // layer = 0 and alternate_group = 0
    contents.putShort(MimeTypes.isAudio(format.sampleMimeType) ? (short) 0x0100 : 0); // volume
    contents.putShort((short) 0); // reserved

    contents.put(rotationMatrixFromOrientation(orientation));

    int width = format.width != Format.NO_VALUE ? format.width : 0;
    int height = format.height != Format.NO_VALUE ? format.height : 0;

    contents.putInt(width << 16);
    contents.putInt(height << 16);

    contents.flip();
    return BoxUtils.wrapIntoBox("tkhd", contents);
  }

  /**
   * Returns the mvhd box.
   *
   * <p>This is the movie header for the entire MP4 file.
   */
  public static ByteBuffer mvhd(
      int nextEmptyTrackId, int modificationTimestampSeconds, long videoDurationUs) {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0); // version and flags

    contents.putInt(modificationTimestampSeconds); // creation_time
    contents.putInt(modificationTimestampSeconds); // modification_time
    contents.putInt((int) MVHD_TIMEBASE); // The per-track timescales might be different.
    contents.putInt(
        (int) Mp4Utils.vuFromUs(videoDurationUs, MVHD_TIMEBASE)); // Duration of the entire video.
    contents.putInt(0x00010000); // rate = 1.0
    contents.putShort((short) 0x0100); // volume = full volume
    contents.putShort((short) 0); // reserved

    contents.putInt(0); // reserved
    contents.putInt(0); // reserved

    // Default values (unity matrix). It looks like that this needs to be an identity matrix, since
    // some players will apply both this and the per-track transformation, while some only go with
    // the per-track one.
    int[] matrix = {0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
    for (int i = 0; i < matrix.length; i++) {
      contents.putInt(matrix[i]);
    }

    for (int i = 0; i < 6; i++) {
      contents.putInt(0); // pre_defined
    }

    // Next empty track id.
    contents.putInt(nextEmptyTrackId);

    contents.flip();
    return BoxUtils.wrapIntoBox("mvhd", contents);
  }

  /**
   * Returns the mdhd box.
   *
   * <p>This is a per-track (media) header.
   */
  public static ByteBuffer mdhd(
      long trackDurationVu,
      int videoUnitTimebase,
      int modificationTimestampSeconds,
      @Nullable String languageCode) {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags

    contents.putInt(modificationTimestampSeconds); // creation_time
    contents.putInt(modificationTimestampSeconds); // modification_time

    contents.putInt(videoUnitTimebase);

    contents.putInt((int) trackDurationVu);

    contents.putShort(languageCodeFromString(languageCode));
    contents.putShort((short) 0);

    contents.flip();
    return BoxUtils.wrapIntoBox("mdhd", contents);
  }

  /**
   * Returns the vmhd box.
   *
   * <p>This is a header for video tracks.
   */
  public static ByteBuffer vmhd() {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags

    contents.putShort((short) 0); // graphicsmode
    // opcolor (red, green, blue)
    contents.putShort((short) 0);
    contents.putShort((short) 0);
    contents.putShort((short) 0);

    contents.flip();
    return BoxUtils.wrapIntoBox("vmhd", contents);
  }

  /**
   * Returns the smhd box.
   *
   * <p>This is a header for audio tracks.
   */
  public static ByteBuffer smhd() {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags

    contents.putShort((short) 0); // balance
    contents.putShort((short) 0); // reserved

    contents.flip();
    return BoxUtils.wrapIntoBox("smhd", contents);
  }

  /**
   * Returns the nmhd box.
   *
   * <p>This is a header for metadata tracks.
   */
  public static ByteBuffer nmhd() {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags

    contents.flip();
    return BoxUtils.wrapIntoBox("nmhd", contents);
  }

  /**
   * Returns a text metadata sample entry box as per ISO/IEC 14496-12: 8.5.2.2.
   *
   * <p>This contains the sample entry (to be placed within the sample description box) for the text
   * metadata tracks.
   */
  public static ByteBuffer textMetaDataSampleEntry(Format format) {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    String mimeType = checkNotNull(format.sampleMimeType);
    byte[] mimeBytes = Util.getUtf8Bytes(mimeType);
    contents.put(mimeBytes); // content_encoding
    contents.put((byte) 0x00);
    contents.put(mimeBytes); // mime_format
    contents.put((byte) 0x00);

    contents.flip();
    return BoxUtils.wrapIntoBox("mett", contents);
  }

  /** Returns the minf (media info) box. */
  public static ByteBuffer minf(ByteBuffer... subBoxes) {
    return BoxUtils.wrapBoxesIntoBox("minf", Arrays.asList(subBoxes));
  }

  /** Returns the dref (data references) box. */
  public static ByteBuffer dref(ByteBuffer... dataLocationBoxes) {
    // We have a "number of contained boxes" field; let's pretend this is also a box so that
    // wrapBoxesIntoBoxes() can concatenate it with the rest.
    ByteBuffer header = ByteBuffer.allocate(8);
    header.putInt(0);
    header.putInt(dataLocationBoxes.length);
    header.flip();

    List<ByteBuffer> contents = new ArrayList<>();
    contents.add(header);
    Collections.addAll(contents, dataLocationBoxes);

    return BoxUtils.wrapBoxesIntoBox("dref", contents);
  }

  /** Returns the dinf (data information) box. */
  public static ByteBuffer dinf(ByteBuffer dref) {
    return BoxUtils.wrapIntoBox("dinf", dref);
  }

  /**
   * Returns the url box.
   *
   * <p>This box declares the location of media data (whether it is in this file or in some other
   * remote file).
   */
  public static ByteBuffer localUrl() {
    ByteBuffer contents = ByteBuffer.allocate(4);

    // Flag indicating that the data is in fact in this very file instead of a remote
    // URL. Accordingly, no actual URL string is present.
    contents.putInt(1);

    // Since we set the flag to 1, no actual URL needs to follow.

    contents.flip();
    return BoxUtils.wrapIntoBox("url ", contents);
  }

  /**
   * Returns the hdlr box.
   *
   * <p>This box includes tha handler specification for a track (signals whether this is video,
   * audio or metadata).
   *
   * @param handlerType The handle type, as defined in ISO/IEC 14496-12: 8.4.3.3.
   * @param handlerName The handler name, a human-readable name to identify track type for debugging
   *     and inspection purposes.
   * @return {@link ByteBuffer} containing the hdlr box.
   */
  public static ByteBuffer hdlr(String handlerType, String handlerName) {
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags.
    contents.putInt(0); // pre_defined.
    contents.put(Util.getUtf8Bytes(handlerType)); // handler_type.
    contents.putInt(0); // reserved.
    contents.putInt(0); // reserved.
    contents.putInt(0); // reserved.
    contents.put(Util.getUtf8Bytes(handlerName)); // name.
    contents.put((byte) 0); // The null terminator for name.

    contents.flip();
    return BoxUtils.wrapIntoBox("hdlr", contents);
  }

  /**
   * Returns the mdia box.
   *
   * <p>This box describes the media format of a track.
   */
  public static ByteBuffer mdia(ByteBuffer... subBoxes) {
    return BoxUtils.wrapBoxesIntoBox("mdia", Arrays.asList(subBoxes));
  }

  /**
   * Returns the trak box.
   *
   * <p>This is a top level track descriptor box; each track has one.
   */
  public static ByteBuffer trak(ByteBuffer... subBoxes) {
    return BoxUtils.wrapBoxesIntoBox("trak", Arrays.asList(subBoxes));
  }

  /**
   * Returns the udta box.
   *
   * <p>This box contains user data like location info.
   */
  public static ByteBuffer udta(@Nullable Mp4Location location) {
    // We can just omit the entire box if there is no location info available.
    if (location == null) {
      return ByteBuffer.allocate(0);
    }

    String locationString =
        String.format(Locale.US, "%+.4f%+.4f/", location.latitude, location.longitude);

    ByteBuffer xyzBoxContents = ByteBuffer.allocate(locationString.length() + 2 + 2);
    xyzBoxContents.putShort((short) (xyzBoxContents.capacity() - 4));
    xyzBoxContents.putShort((short) 0x15C7); // language code?

    xyzBoxContents.put(Util.getUtf8Bytes(locationString));
    checkState(xyzBoxContents.limit() == xyzBoxContents.capacity());
    xyzBoxContents.flip();

    return BoxUtils.wrapIntoBox(
        "udta",
        BoxUtils.wrapIntoBox(
            new byte[] {
              (byte) 0xA9, // copyright symbol
              'x',
              'y',
              'z'
            },
            xyzBoxContents));
  }

  /**
   * Returns the keys box.
   *
   * <p>This box contains a list of metadata keys.
   */
  public static ByteBuffer keys(List<String> keyNames) {
    // This should be an adaptive size here; we don't yet care since it's usually small.
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags.
    contents.putInt(keyNames.size()); // num of entries

    for (int i = 0; i < keyNames.size(); i++) {
      ByteBuffer keyNameBuffer = ByteBuffer.wrap(Util.getUtf8Bytes(keyNames.get(i)));
      contents.put(BoxUtils.wrapIntoBox("mdta", keyNameBuffer));
    }

    contents.flip();
    return BoxUtils.wrapIntoBox("keys", contents);
  }

  /**
   * Returns the ilst box.
   *
   * <p>This box contains a list of metadata values.
   */
  public static ByteBuffer ilst(List<Object> values) {
    // This should be an adaptive size here; we don't yet care since it's usually small.
    ByteBuffer contents = ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    for (int i = 0; i < values.size(); i++) {
      int keyId = i + 1;
      Object value = values.get(i);

      ByteBuffer valueContents;
      if (value instanceof String) {
        String valueString = (String) value;
        byte[] valueBytes = Util.getUtf8Bytes(valueString);
        valueContents = ByteBuffer.allocate(valueBytes.length + 8);
        valueContents.putInt(1); // type code for UTF-8 string
        valueContents.putInt(0); // default country / language
        valueContents.put(valueBytes);

      } else if (value instanceof Float) {
        valueContents = ByteBuffer.allocate(12);
        valueContents.putInt(23); // float32
        valueContents.putInt(0); // language / country
        valueContents.putFloat((float) value);
      } else {
        throw new IllegalArgumentException("Unknown metadata type: " + value.getClass());
      }

      valueContents.flip();
      ByteBuffer valueBox = BoxUtils.wrapIntoBox("data", valueContents);
      contents.putInt(valueBox.remaining() + 8);
      contents.putInt(keyId);
      contents.put(valueBox);
    }

    contents.flip();
    return BoxUtils.wrapIntoBox("ilst", contents);
  }

  /** Returns the meta (metadata) box. */
  public static ByteBuffer meta(ByteBuffer... subBoxes) {
    return BoxUtils.wrapBoxesIntoBox("meta", Arrays.asList(subBoxes));
  }

  /**
   * Returns the uuid box.
   *
   * <p>This box is used for XMP and other metadata.
   */
  public static ByteBuffer uuid(List<Byte> uuid, ByteBuffer contents) {
    checkArgument(contents.remaining() > 0);
    return BoxUtils.wrapBoxesIntoBox(
        "uuid", ImmutableList.of(ByteBuffer.wrap(Bytes.toArray(uuid)), contents));
  }

  /**
   * Returns the moov box.
   *
   * <p>This box is a top level movie descriptor box (there is a single one of this per Mp4 file).
   */
  public static ByteBuffer moov(
      ByteBuffer mvhdBox,
      ByteBuffer udtaBox,
      ByteBuffer metaBox,
      List<ByteBuffer> trakBoxes,
      ByteBuffer mvexBox) {
    List<ByteBuffer> subBoxes = new ArrayList<>();
    subBoxes.add(mvhdBox);
    subBoxes.add(udtaBox);
    subBoxes.add(metaBox);
    subBoxes.addAll(trakBoxes);
    subBoxes.add(mvexBox);

    return BoxUtils.wrapBoxesIntoBox("moov", subBoxes);
  }

  /** Returns an audio sample entry box based on the MIME type. */
  public static ByteBuffer audioSampleEntry(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    checkArgument(mimeType.equals(MimeTypes.AUDIO_AAC), "Unsupported audio format: " + mimeType);
    String fourcc = "mp4a";

    checkArgument(!format.initializationData.isEmpty(), "csd-0 not found in the format.");

    byte[] csd0 = format.initializationData.get(0);
    checkArgument(csd0.length > 0, "csd-0 is empty.");

    ByteBuffer csd0ByteBuffer = ByteBuffer.wrap(csd0);
    ByteBuffer contents =
        ByteBuffer.allocate(csd0ByteBuffer.limit() + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x00); // reserved
    contents.putShort((short) 0x0); // reserved
    contents.putShort((short) 0x1); // data ref index
    contents.putInt(0x00); // reserved
    contents.putInt(0x00); // reserved

    int channelCount = format.channelCount;
    contents.putShort((short) channelCount);
    contents.putShort((short) 16); // sample size
    contents.putShort((short) 0x0); // predefined
    contents.putShort((short) 0x0); // reserved

    int sampleRate = format.sampleRate;
    contents.putInt(sampleRate << 16);

    contents.put(audioEsdsBox(csd0ByteBuffer, format.peakBitrate, format.averageBitrate));

    contents.flip();
    return BoxUtils.wrapIntoBox(fourcc, contents);
  }

  /** Returns a codec specific box. */
  public static ByteBuffer codecSpecificBox(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    switch (mimeType) {
      case "video/avc":
        return avcCBox(format);
      case "video/hevc":
        return hvcCBox(format);
      case "video/av01":
        return av1CBox(format);
      default:
        throw new IllegalArgumentException("Unsupported video format: " + mimeType);
    }
  }

  /**
   * Returns a {@code VisualSampleEntry} box based upon the MIME type.
   *
   * <p>The {@code VisualSampleEntry} schema is defined in ISO/IEC 14496-12: 8.5.2.2.
   */
  public static ByteBuffer videoSampleEntry(Format format) {
    ByteBuffer codecSpecificBox = codecSpecificBox(format);
    String fourcc = codecSpecificFourcc(format);

    ByteBuffer contents =
        ByteBuffer.allocate(Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE + codecSpecificBox.limit());

    // reserved = 0 (6 bytes)
    contents.putInt(0);
    contents.putShort((short) 0);

    contents.putShort((short) 1); // data_reference_index

    contents.putShort((short) 0); // pre_defined
    contents.putShort((short) 0); // reserved

    // pre_defined
    contents.putInt(0);
    contents.putInt(0);
    contents.putInt(0);

    contents.putShort(format.width != Format.NO_VALUE ? (short) format.width : 0);
    contents.putShort(format.height != Format.NO_VALUE ? (short) format.height : 0);

    contents.putInt(0x00480000); // horizresolution = 72 dpi
    contents.putInt(0x00480000); // vertresolution = 72 dpi

    contents.putInt(0); // reserved

    contents.putShort((short) 1); // frame_count

    // compressorname
    contents.putLong(0);
    contents.putLong(0);
    contents.putLong(0);
    contents.putLong(0);

    contents.putShort((short) 0x0018); // depth
    contents.putShort((short) -1); // pre_defined

    contents.put(codecSpecificBox);

    contents.put(paspBox());

    // Put in a "colr" box if any of the three color format parameters has a non-default (0) value.
    // TODO: b/278101856 - Only null check should be enough once we disallow invalid values.
    if (format.colorInfo != null
        && (format.colorInfo.colorSpace != 0
            || format.colorInfo.colorTransfer != 0
            || format.colorInfo.colorRange != 0)) {
      contents.put(colrBox(format.colorInfo));
    }

    contents.flip();
    return BoxUtils.wrapIntoBox(fourcc, contents);
  }

  /**
   * Converts sample presentation times (in microseconds) to sample durations (in timebase units)
   * that will go into the stts box.
   *
   * <p>ISO/IEC 14496-12: 8.6.1.3.1 recommends each track starts at 0. Therefore, the first sample
   * presentation timestamp is set to 0 and the duration of that sample may be larger as a result.
   *
   * @param writtenSamples All the written samples.
   * @param minInputPresentationTimestampUs The global minimum presentation timestamp which needs to
   *     be subtracted from each sample's presentation timestamp.
   * @param videoUnitTimescale The timescale of the track.
   * @param lastDurationBehavior The behaviour for the last sample duration.
   * @return A list of all the sample durations.
   */
  // TODO: b/280084657 - Add support for setting last sample duration.
  public static List<Long> durationsVuForStts(
      List<MediaCodec.BufferInfo> writtenSamples,
      long minInputPresentationTimestampUs,
      int videoUnitTimescale,
      @Mp4Muxer.LastFrameDurationBehavior int lastDurationBehavior) {
    List<Long> durationsVu = new ArrayList<>();

    long currentTimeVu = 0L;

    for (int sampleId = 0; sampleId < writtenSamples.size(); sampleId++) {
      long samplePtsUs = writtenSamples.get(sampleId).presentationTimeUs;
      long sampleSpanEndsAtUs =
          sampleId == writtenSamples.size() - 1
              ? samplePtsUs
              : writtenSamples.get(sampleId + 1).presentationTimeUs;

      sampleSpanEndsAtUs -= minInputPresentationTimestampUs;

      long sampleSpanEndsAtVu = Mp4Utils.vuFromUs(sampleSpanEndsAtUs, videoUnitTimescale);

      long durationVu = sampleSpanEndsAtVu - currentTimeVu;
      currentTimeVu = sampleSpanEndsAtVu;

      if (durationVu >= Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            String.format(Locale.US, "Timestamp delta %d doesn't fit into an int", durationVu));
      }

      durationsVu.add(durationVu);
    }

    adjustLastSampleDuration(durationsVu, lastDurationBehavior);

    return durationsVu;
  }

  /** Generates the stts (decoding time to sample) box. */
  public static ByteBuffer stts(List<Long> durationsVu) {
    ByteBuffer contents =
        ByteBuffer.allocate(durationsVu.size() * 8 + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x0); // version and flags.

    // We will know total entry count only after processing all the sample durations, so put in a
    // placeholder for total entry count and store its index.
    int totalEntryCountIndex = contents.position();
    contents.putInt(0x0); // entry_count.

    int totalEntryCount = 0;
    long lastDurationVu = -1L;
    int lastSampleCountIndex = -1;

    // Note that the framework MediaMuxer adjust time deltas within plus-minus 100 us, so that
    // samples have repeating duration values. It saves few entries in the table.
    for (int i = 0; i < durationsVu.size(); i++) {
      long durationVu = durationsVu.get(i);
      if (lastDurationVu != durationVu) {
        lastDurationVu = durationVu;
        lastSampleCountIndex = contents.position();

        // sample_count; this will be updated instead of adding a new entry if the next sample has
        // the same duration.
        contents.putInt(1);
        contents.putInt((int) durationVu); // sample_delta.
        totalEntryCount++;
      } else {
        contents.putInt(lastSampleCountIndex, contents.getInt(lastSampleCountIndex) + 1);
      }
    }

    contents.putInt(totalEntryCountIndex, totalEntryCount);

    contents.flip();
    return BoxUtils.wrapIntoBox("stts", contents);
  }

  /** Returns the stsz (sample size) box. */
  public static ByteBuffer stsz(List<MediaCodec.BufferInfo> writtenSamples) {
    ByteBuffer contents =
        ByteBuffer.allocate(writtenSamples.size() * 4 + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x0); // version and flags.

    // TODO: b/270583563 - Consider optimizing for identically-sized samples.
    //  sample_size; specifying the default sample size. Set to zero to indicate that the samples
    //  have different sizes and they are stored in the sample size table.
    contents.putInt(0);

    contents.putInt(writtenSamples.size()); // sample_count.

    for (int i = 0; i < writtenSamples.size(); i++) {
      contents.putInt(writtenSamples.get(i).size);
    }

    contents.flip();
    return BoxUtils.wrapIntoBox("stsz", contents);
  }

  /** Returns the stsc (sample to chunk) box. */
  public static ByteBuffer stsc(List<Integer> writtenChunkSampleCounts) {
    ByteBuffer contents =
        ByteBuffer.allocate(
            writtenChunkSampleCounts.size() * 12 + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x0); // version and flags.
    contents.putInt(writtenChunkSampleCounts.size()); // entry_count.

    int currentChunk = 1;

    // TODO: b/270583563 - Consider optimizing for consecutive chunks having same number of samples.
    for (int i = 0; i < writtenChunkSampleCounts.size(); i++) {
      int samplesInChunk = writtenChunkSampleCounts.get(i);
      contents.putInt(currentChunk); // first_chunk.
      contents.putInt(samplesInChunk); // samples_per_chunk.
      // sample_description_index; we have only one sample description in each track.
      contents.putInt(1);

      currentChunk += 1;
    }

    contents.flip();
    return BoxUtils.wrapIntoBox("stsc", contents);
  }

  /** Returns the co64 (chunk offset) box. */
  public static ByteBuffer co64(List<Long> writtenChunkOffsets) {
    ByteBuffer contents =
        ByteBuffer.allocate(writtenChunkOffsets.size() * 8 + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x0); // version.
    contents.putInt(writtenChunkOffsets.size()); // entry_count.

    for (int i = 0; i < writtenChunkOffsets.size(); i++) {
      contents.putLong(writtenChunkOffsets.get(i)); // chunk_offset.
    }

    contents.flip();
    return BoxUtils.wrapIntoBox("co64", contents);
  }

  /** Returns the stss (sync sample) box. */
  public static ByteBuffer stss(List<MediaCodec.BufferInfo> writtenSamples) {
    ByteBuffer contents =
        ByteBuffer.allocate(writtenSamples.size() * 4 + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x0); // version and flags.

    // We will know total entry count only after processing all the sample, so put in a placeholder
    // for total entry count and store its index.
    int totalEntryCountIndex = contents.position();
    contents.putInt(writtenSamples.size()); // entry_count.

    int currentSampleNumber = 1;
    int totalKeyFrames = 0;
    for (int i = 0; i < writtenSamples.size(); i++) {
      MediaCodec.BufferInfo info = writtenSamples.get(i);
      if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) > 0) {
        contents.putInt(currentSampleNumber); // sample_number.
        totalKeyFrames++;
      }

      currentSampleNumber++;
    }

    contents.putInt(totalEntryCountIndex, totalKeyFrames);

    contents.flip();
    return BoxUtils.wrapIntoBox("stss", contents);
  }

  /** Returns the stsd (sample description) box. */
  public static ByteBuffer stsd(ByteBuffer sampleEntryBox) {
    ByteBuffer contents =
        ByteBuffer.allocate(sampleEntryBox.limit() + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.putInt(0x0); // version and flags.
    contents.putInt(1); // entry_count, We have only one sample description in each track.
    contents.put(sampleEntryBox);

    contents.flip();
    return BoxUtils.wrapIntoBox("stsd", contents);
  }

  /** Returns the stbl (sample table) box. */
  public static ByteBuffer stbl(ByteBuffer... subBoxes) {
    return BoxUtils.wrapBoxesIntoBox("stbl", Arrays.asList(subBoxes));
  }

  /** Creates the ftyp box. */
  public static ByteBuffer ftyp() {
    List<ByteBuffer> boxBytes = new ArrayList<>();

    String majorVersion = "isom";
    boxBytes.add(ByteBuffer.wrap(Util.getUtf8Bytes(majorVersion)));

    int minorVersion = 0x020000;
    ByteBuffer minorBytes = ByteBuffer.allocate(4);
    minorBytes.putInt(minorVersion);
    minorBytes.flip();
    boxBytes.add(minorBytes);

    String[] compatibleBrands = {"isom", "iso2", "mp41"};
    for (String compatibleBrand : compatibleBrands) {
      boxBytes.add(ByteBuffer.wrap(Util.getUtf8Bytes(compatibleBrand)));
    }

    return BoxUtils.wrapBoxesIntoBox("ftyp", boxBytes);
  }

  /** Adjusts the duration of the very last sample if needed. */
  private static void adjustLastSampleDuration(
      List<Long> durationsToBeAdjustedVu, @Mp4Muxer.LastFrameDurationBehavior int behavior) {
    // Technically, MP4 files store not timestamps but frame durations. Thus, if we interpret
    // timestamps as the start of frames then it's not obvious what's the duration of the very
    // last frame should be. If our samples follow each other in roughly regular intervals (e.g. in
    // a normal, 30 fps video), it makes sense to assume that the last sample will last the same ~33
    // ms as the all the other ones before. On the other hand, if we have just a few, irregularly
    // spaced frames, with duplication, the entire duration of the video will increase, creating
    // abnormal gaps.

    if (durationsToBeAdjustedVu.size() <= 2) {
      // Nothing to duplicate if there are 0 or 1 entries.
      return;
    }

    switch (behavior) {
      case Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION:
        // This is the default MediaMuxer behavior: the last sample duration is a copy of the
        // previous sample duration.
        durationsToBeAdjustedVu.set(
            durationsToBeAdjustedVu.size() - 1,
            durationsToBeAdjustedVu.get(durationsToBeAdjustedVu.size() - 2));
        break;
      case Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME:
        // Keep the last sample duration as short as possible.
        checkState(Iterables.getLast(durationsToBeAdjustedVu) == 0L);
        break;
      default:
        throw new IllegalArgumentException(
            "Unexpected value for the last frame duration behavior " + behavior);
    }
  }

  /** Returns the avcC box as per ISO/IEC 14496-15: 5.3.3.1.2. */
  private static ByteBuffer avcCBox(Format format) {
    checkArgument(
        format.initializationData.size() >= 2, "csd-0 and/or csd-1 not found in the format.");

    byte[] csd0 = format.initializationData.get(0);
    checkArgument(csd0.length > 0, "csd-0 is empty.");

    byte[] csd1 = format.initializationData.get(1);
    checkArgument(csd1.length > 0, "csd-1 is empty.");

    ByteBuffer csd0ByteBuffer = ByteBuffer.wrap(csd0);
    ByteBuffer csd1ByteBuffer = ByteBuffer.wrap(csd1);

    ByteBuffer contents =
        ByteBuffer.allocate(
            csd0ByteBuffer.limit() + csd1ByteBuffer.limit() + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    contents.put((byte) 0x01); // configurationVersion

    ImmutableList<ByteBuffer> csd0NalUnits = AnnexBUtils.findNalUnits(csd0ByteBuffer);
    checkArgument(csd0NalUnits.size() == 1, "SPS data not found in csd0.");

    ByteBuffer sps = csd0NalUnits.get(0);
    byte[] spsData = new byte[sps.remaining()];
    sps.get(spsData);
    sps.rewind();

    NalUnitUtil.SpsData h264SpsData =
        NalUnitUtil.parseSpsNalUnit(spsData, /* nalOffset= */ 0, spsData.length);
    contents.put((byte) h264SpsData.profileIdc); // AVCProfileIndication
    contents.put((byte) h264SpsData.constraintsFlagsAndReservedZero2Bits); // profile_compatibility
    contents.put((byte) h264SpsData.levelIdc); // AVCLevelIndication

    contents.put((byte) 0xFF); // 6 bits reserved ('0b111111') + 2 bits lengthSizeMinusOne (3)
    contents.put((byte) 0xE1); // 3 bits reserved ('0b111') + 5 bits numOfSequenceParameterSets (1)
    contents.putShort((short) sps.remaining()); // sequenceParameterSetLength
    contents.put(sps); // sequenceParameterSetNALUnit
    sps.rewind();

    ImmutableList<ByteBuffer> csd1NalUnits = AnnexBUtils.findNalUnits(csd1ByteBuffer);
    checkState(csd1NalUnits.size() == 1, "PPS data not found in csd1.");

    contents.put((byte) 0x01); // numOfPictureParameterSets

    ByteBuffer pps = csd1NalUnits.get(0);
    contents.putShort((short) pps.remaining()); // pictureParameterSetLength
    contents.put(pps); // pictureParameterSetNALUnit
    pps.rewind();

    contents.flip();
    return BoxUtils.wrapIntoBox("avcC", contents);
  }

  /** Returns the hvcC box as per ISO/IEC 14496-15: 8.3.3.1.2. */
  private static ByteBuffer hvcCBox(Format format) {
    // For H.265, all three codec-specific NALUs (VPS, SPS, PPS) are packed into csd-0.
    checkArgument(!format.initializationData.isEmpty(), "csd-0 not found in the format.");

    byte[] csd0 = format.initializationData.get(0);
    checkArgument(csd0.length > 0, "csd-0 is empty.");

    ByteBuffer csd0ByteBuffer = ByteBuffer.wrap(csd0);

    ByteBuffer contents =
        ByteBuffer.allocate(csd0ByteBuffer.limit() + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);

    ImmutableList<ByteBuffer> nalusWithEmulationPrevention =
        AnnexBUtils.findNalUnits(csd0ByteBuffer);

    // Remove emulation prevention bytes to parse the actual csd-0 data.
    // For storing the csd-0 data into MP4 file, use original NALUs with emulation prevention bytes.
    List<ByteBuffer> nalusWithoutEmulationPrevention = new ArrayList<>();
    for (int i = 0; i < nalusWithEmulationPrevention.size(); i++) {
      nalusWithoutEmulationPrevention.add(
          AnnexBUtils.stripEmulationPrevention(nalusWithEmulationPrevention.get(i)));
    }

    contents.put((byte) 0x01); // configurationVersion

    // Assuming that VPS, SPS and PPS are in this order in csd-0.
    ByteBuffer vps = nalusWithoutEmulationPrevention.get(0);

    if (vps.get(vps.position()) != 0x40) {
      throw new IllegalArgumentException("First NALU in csd-0 is not the VPS.");
    }

    // general_profile_space (2 bits) + general_tier_flag (1 bit) + general_profile_idc (5 bits)
    contents.put(vps.get(6));

    contents.putInt(vps.getInt(7)); // general_profile_compatibility_flags

    // general_constraint_indicator_flags (6 bytes)
    contents.putInt(vps.getInt(11));
    contents.putShort(vps.getShort(15));

    contents.put(vps.get(17)); // general_level_idc

    // First 4 bits reserved + min_spatial_segmentation_idc (12 bits)
    contents.putShort((short) 0xF000);

    // First 6 bits reserved + parallelismType (2 bits)
    contents.put((byte) 0xFC);

    ByteBuffer sps = nalusWithEmulationPrevention.get(1);
    byte[] spsArray = new byte[sps.remaining()];
    sps.get(spsArray);
    sps.rewind();

    NalUnitUtil.H265SpsData h265SpsData =
        NalUnitUtil.parseH265SpsNalUnit(
            spsArray, /* nalOffset= */ 0, /* nalLimit= */ spsArray.length);

    byte chromaFormat = (byte) (0xFC | h265SpsData.chromaFormatIdc); // First 6 bits reserved
    byte bitDepthLumaMinus8 =
        (byte) (0xF8 | h265SpsData.bitDepthLumaMinus8); // First 5 bits reserved
    byte bitDepthChromaMinus8 =
        (byte) (0xF8 | h265SpsData.bitDepthChromaMinus8); // First 5 bits reserved
    contents.put(chromaFormat);
    contents.put(bitDepthLumaMinus8);
    contents.put(bitDepthChromaMinus8);

    // avgFrameRate; value 0 indicates an unspecified average frame rate.
    contents.putShort((short) 0);

    // constantFrameRate (2 bits) + numTemporalLayers (3 bits) + temporalIdNested (1 bit) +
    // lengthSizeMinusOne (2 bits)
    contents.put((byte) 0x0F);

    // Put all NALUs.
    contents.put((byte) nalusWithEmulationPrevention.size()); // numOfArrays

    for (int i = 0; i < nalusWithEmulationPrevention.size(); i++) {
      ByteBuffer nalu = nalusWithEmulationPrevention.get(i);

      // array_completeness (1 bit) + reserved (1 bit) + NAL_unit_type (6 bits)
      byte naluType = (byte) ((nalu.get(0) >> 1) & 0x3F);
      contents.put(naluType);

      contents.putShort((short) 1); // numNalus; number of NALUs in array
      contents.putShort((short) nalu.limit()); // nalUnitLength
      contents.put(nalu);
    }

    contents.flip();
    return BoxUtils.wrapIntoBox("hvcC", contents);
  }

  /** Returns the av1C box. */
  private static ByteBuffer av1CBox(Format format) {
    // For AV1, the entire codec-specific box is packed into csd-0.
    checkArgument(!format.initializationData.isEmpty(), "csd-0 is not found in the format");

    byte[] csd0 = format.initializationData.get(0);
    checkArgument(csd0.length > 0, "csd-0 is empty.");

    return BoxUtils.wrapIntoBox("av1C", ByteBuffer.wrap(csd0));
  }

  /** Returns the pasp box. */
  private static ByteBuffer paspBox() {
    ByteBuffer contents = ByteBuffer.allocate(8);

    contents.putInt(1 << 16); // hspacing
    contents.putInt(1 << 16); // vspacing

    contents.rewind();
    return BoxUtils.wrapIntoBox("pasp", contents);
  }

  /** Returns the colr box. */
  @SuppressWarnings("InlinedApi")
  private static ByteBuffer colrBox(ColorInfo colorInfo) {
    ByteBuffer contents = ByteBuffer.allocate(20);
    contents.put((byte) 'n');
    contents.put((byte) 'c');
    contents.put((byte) 'l');
    contents.put((byte) 'x');

    // Parameters going into the file.
    short primaries = 0;
    short transfer = 0;
    short matrix = 0;
    byte range = 0;

    if (colorInfo.colorSpace != Format.NO_VALUE) {
      int standard = colorInfo.colorSpace;
      if (standard < 0 || standard >= MEDIAFORMAT_STANDARD_TO_PRIMARIES_AND_MATRIX.size()) {
        throw new IllegalArgumentException("Color standard not implemented: " + standard);
      }

      primaries = MEDIAFORMAT_STANDARD_TO_PRIMARIES_AND_MATRIX.get(standard).get(0);
      matrix = MEDIAFORMAT_STANDARD_TO_PRIMARIES_AND_MATRIX.get(standard).get(1);
    }

    if (colorInfo.colorTransfer != Format.NO_VALUE) {
      int transferInFormat = colorInfo.colorTransfer;
      if (transferInFormat < 0 || transferInFormat >= MEDIAFORMAT_TRANSFER_TO_MP4_TRANSFER.size()) {
        throw new IllegalArgumentException("Color transfer not implemented: " + transferInFormat);
      }

      transfer = MEDIAFORMAT_TRANSFER_TO_MP4_TRANSFER.get(transferInFormat);
    }

    if (colorInfo.colorRange != Format.NO_VALUE) {
      int rangeInFormat = colorInfo.colorRange;
      // Handled values are 0 (unknown), 1 (full) and 2 (limited).
      if (rangeInFormat < 0 || rangeInFormat > 2) {
        throw new IllegalArgumentException("Color range not implemented: " + rangeInFormat);
      }

      // Set this to 0x80 only for full range, 0 otherwise.
      range = rangeInFormat == C.COLOR_RANGE_FULL ? (byte) 0x80 : 0;
    }

    contents.putShort(primaries);
    contents.putShort(transfer);
    contents.putShort(matrix);
    contents.put(range);

    contents.flip();
    return BoxUtils.wrapIntoBox("colr", contents);
  }

  /** Returns video codec specific fourcc. */
  private static String codecSpecificFourcc(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    switch (mimeType) {
      case "video/avc":
        return "avc1";
      case "video/hevc":
        return "hvc1";
      case "video/av01":
        return "av01";
      default:
        throw new IllegalArgumentException("Unsupported video format: " + mimeType);
    }
  }

  /** Returns the esds box. */
  private static ByteBuffer audioEsdsBox(
      ByteBuffer csd0ByteBuffer, int peakBitrate, int averageBitrate) {
    int csd0Size = csd0ByteBuffer.limit();

    ByteBuffer contents = ByteBuffer.allocate(csd0Size + Mp4Utils.MAX_FIXED_LEAF_BOX_SIZE);
    contents.putInt(0x0); // version and flags.
    contents.put((byte) 0x03); // ES_DescrTag

    // We're normally using a variable-length encoding for the length of various sub-packages (esds
    // etc.), in a nested way, so outer lengths need to account for variable-length inner lengths
    // too (to save ~10 bytes per video file). Meanwhile, AAC codec-specific
    // data is typically just 2 bytes, so every length actually fits into a byte. Here, we're just
    // skipping the entire complex story by asserting that we won't ever need variable-length sizes.
    checkArgument(csd0Size + 21 < 127, "CSD too long; we might need variable-length encoding?");

    contents.put((byte) (23 + csd0Size));

    contents.putShort((short) 0x0000); // ES_ID
    contents.put((byte) 0x00);

    contents.put((byte) 0x04); // DecoderConfigDescrTag
    contents.put((byte) (15 + csd0Size));
    contents.put((byte) 0x40); // objectTypeIndication
    contents.put((byte) 0x15); // streamType AudioStream

    contents.putShort((short) 0x03);
    contents.put((byte) 0x00); // 24-bit buffer size (0x300)

    contents.putInt(peakBitrate != Format.NO_VALUE ? peakBitrate : 0);
    contents.putInt(averageBitrate != Format.NO_VALUE ? averageBitrate : 0);

    contents.put((byte) 0x05); // DecoderSpecificInfoTag
    contents.put((byte) csd0Size);
    contents.put(csd0ByteBuffer);
    csd0ByteBuffer.rewind();

    contents.put((byte) 0x06); // SLConfigDescriptorTag
    contents.put((byte) 0x01);
    contents.put((byte) 0x02);

    contents.flip();
    return BoxUtils.wrapIntoBox("esds", contents);
  }

  /** Packs a three-letter language code into a short, packing 3x5 bits. */
  private static short languageCodeFromString(@Nullable String code) {
    if (code == null) {
      return 0;
    }

    byte[] bytes = Util.getUtf8Bytes(code);

    if (bytes.length != 3) {
      throw new IllegalArgumentException("Non-length-3 language code: " + code);
    }

    // Use an int so that we don't bump into the issue of Java not having unsigned types. We take
    // the last 5 bits of each letter to supply 5 bits each of the eventual code.

    int value = (bytes[2] & 0x1F);
    value += (bytes[1] & 0x1F) << 5;
    value += (bytes[0] & 0x1F) << 10;

    // This adds up to 15 bits; the 16th one is really supposed to be 0.
    checkState((value & 0x8000) == 0);
    return (short) (value & 0xFFFF);
  }

  /**
   * Generates an orientation matrix, to be included in the MP4 header.
   *
   * <p>The supported values are 0, 90, 180 and 270 (degrees).
   */
  private static byte[] rotationMatrixFromOrientation(int orientation) {
    // The transformation matrix is defined as below:
    // | a b u |
    // | c d v |
    // | x y w |
    // To specify the orientation (u, v, w) are restricted to (0, 0, 0x40000000).
    // Reference: ISO/IEC 14496-12: 8.2.2.3.
    int fixedOne = 65536;
    switch (orientation) {
      case 0:
        return Util.toByteArray(fixedOne, 0, 0, 0, fixedOne, 0, 0, 0, 0x40000000);
      case 90:
        return Util.toByteArray(0, fixedOne, 0, -fixedOne, 0, 0, 0, 0, 0x40000000);
      case 180:
        return Util.toByteArray(-fixedOne, 0, 0, 0, -fixedOne, 0, 0, 0, 0x40000000);
      case 270:
        return Util.toByteArray(0, -fixedOne, 0, fixedOne, 0, 0, 0, 0, 0x40000000);
      default:
        throw new IllegalArgumentException("invalid orientation " + orientation);
    }
  }
}
