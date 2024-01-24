/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.muxer.Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION;
import static androidx.media3.muxer.Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_AUDIO_FORMAT;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_CSD_0;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_CSD_1;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_VIDEO_FORMAT;
import static androidx.media3.muxer.MuxerTestUtil.getExpectedDumpFilePath;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.media.MediaCodec;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.muxer.FragmentedMp4Writer.SampleMetadata;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.DumpableMp4Box;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link Boxes}. */
@RunWith(AndroidJUnit4.class)
public class BoxesTest {
  // A typical timescale is ~90_000. We're using 100_000 here to simplify calculations.
  // This makes one time unit equal to 10 microseconds.
  private static final int VU_TIMEBASE = 100_000;

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void createTkhdBox_forVideoTrack_matchesExpected() throws IOException {
    ByteBuffer tkhdBox =
        Boxes.tkhd(
            /* trackId= */ 1,
            /* trackDurationVu= */ 5_000_000,
            /* modificationTimestampSeconds= */ 1_000_000_000,
            /* orientation= */ 90,
            FAKE_VIDEO_FORMAT);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(tkhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("video_track_tkhd_box"));
  }

  @Test
  public void createTkhdBox_forAudioTrack_matchesExpected() throws IOException {
    ByteBuffer tkhdBox =
        Boxes.tkhd(
            /* trackId= */ 1,
            /* trackDurationVu= */ 5_000_000,
            /* modificationTimestampSeconds= */ 1_000_000_000,
            /* orientation= */ 90,
            FAKE_AUDIO_FORMAT);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(tkhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_track_tkhd_box"));
  }

  @Test
  public void createMvhdBox_matchesExpected() throws IOException {
    ByteBuffer mvhdBox =
        Boxes.mvhd(
            /* nextEmptyTrackId= */ 3,
            /* modificationTimestampSeconds= */ 1_000_000_000,
            /* videoDurationUs= */ 5_000_000);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(mvhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("mvhd_box"));
  }

  @Test
  public void createMdhdBox_matchesExpected() throws IOException {
    ByteBuffer mdhdBox =
        Boxes.mdhd(
            /* trackDurationVu= */ 5_000_000,
            VU_TIMEBASE,
            /* modificationTimestampSeconds= */ 1_000_000_000,
            /* languageCode= */ "und");

    DumpableMp4Box dumpableBox = new DumpableMp4Box(mdhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("mdhd_box"));
  }

  @Test
  public void createVmhdBox_matchesExpected() throws IOException {
    ByteBuffer vmhdBox = Boxes.vmhd();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(vmhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("vmhd_box"));
  }

  @Test
  public void createSmhdBox_matchesExpected() throws IOException {
    ByteBuffer smhdBox = Boxes.smhd();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(smhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("smhd_box"));
  }

  @Test
  public void createNmhdBox_matchesExpected() throws IOException {
    ByteBuffer nmhdBox = Boxes.nmhd();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(nmhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("nmhd_box"));
  }

  @Test
  public void createEmptyDinfBox_matchesExpected() throws IOException {
    ByteBuffer dinfBox = Boxes.dinf(Boxes.dref(Boxes.localUrl()));

    DumpableMp4Box dumpableBox = new DumpableMp4Box(dinfBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("dinf_box_empty"));
  }

  @Test
  public void createHdlrBox_matchesExpected() throws IOException {
    // Create hdlr box for video track.
    ByteBuffer hdlrBox = Boxes.hdlr(/* handlerType= */ "vide", /* handlerName= */ "VideoHandle");

    DumpableMp4Box dumpableBox = new DumpableMp4Box(hdlrBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("hdlr_box"));
  }

  @Test
  public void createUdtaBox_matchesExpected() throws IOException {
    Mp4Location mp4Location = new Mp4Location(33.0f, -120f);

    ByteBuffer udtaBox = Boxes.udta(mp4Location);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(udtaBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("udta_box"));
  }

  @Test
  public void createKeysBox_matchesExpected() throws Exception {
    List<String> keyNames = ImmutableList.of("com.android.version", "com.android.capture.fps");

    ByteBuffer keysBox = Boxes.keys(keyNames);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(keysBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("keys_box"));
  }

  @Test
  public void createIlstBox_matchesExpected() throws Exception {
    List<Object> values = ImmutableList.of("11", 120.0f);

    ByteBuffer ilstBox = Boxes.ilst(values);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(ilstBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("ilst_box"));
  }

  @Test
  public void createUuidBox_forXmpData_matchesExpected() throws Exception {
    ByteBuffer xmpData =
        ByteBuffer.wrap(TestUtil.getByteArray(context, "media/xmp/sample_datetime_xmp.xmp"));

    ByteBuffer xmpUuidBox = Boxes.uuid(Boxes.XMP_UUID, xmpData);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(xmpUuidBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("uuid_box_XMP"));
  }

  @Test
  public void createuuidBox_withEmptyXmpData_throws() {
    ByteBuffer xmpData = ByteBuffer.allocate(0);

    assertThrows(IllegalArgumentException.class, () -> Boxes.uuid(Boxes.XMP_UUID, xmpData));
  }

  @Test
  public void createAudioSampleEntryBox_forMp4a_matchesExpected() throws Exception {
    Format format =
        new Format.Builder()
            .setPeakBitrate(128000)
            .setSampleRate(48000)
            .setId(3)
            .setSampleMimeType("audio/mp4a-latm")
            .setChannelCount(2)
            .setAverageBitrate(128000)
            .setLanguage("```")
            .setMaxInputSize(502)
            .setInitializationData(ImmutableList.of(BaseEncoding.base16().decode("1190")))
            .build();

    ByteBuffer audioSampleEntryBox = Boxes.audioSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(audioSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_sample_entry_box_mp4a"));
  }

  @Test
  public void createAudioSampleEntryBox_withUnknownAudioFormat_throws() {
    // The audio format contains an unknown MIME type.
    Format format =
        new Format.Builder()
            .setPeakBitrate(128000)
            .setSampleRate(48000)
            .setId(3)
            .setSampleMimeType("audio/mp4a-unknown")
            .setChannelCount(2)
            .setAverageBitrate(128000)
            .setLanguage("```")
            .setMaxInputSize(502)
            .setInitializationData(ImmutableList.of(BaseEncoding.base16().decode("1190")))
            .build();

    assertThrows(IllegalArgumentException.class, () -> Boxes.audioSampleEntry(format));
  }

  @Test
  public void createVideoSampleEntryBox_forH265_matchesExpected() throws Exception {
    Format format =
        new Format.Builder()
            .setId(1)
            .setSampleMimeType("video/hevc")
            .setWidth(48)
            .setLanguage("und")
            .setMaxInputSize(114)
            .setFrameRate(25)
            .setHeight(32)
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode(
                            "0000000140010C01FFFF0408000003009FC800000300001E959809000000014201010408000003009FC800000300001EC1882165959AE4CAE68080000003008000000C84000000014401C173D089")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h265"));
  }

  @Test
  public void createVideoSampleEntryBox_forH265_hdr10_matchesExpected() throws Exception {
    Format format =
        new Format.Builder()
            .setPeakBitrate(9200)
            .setId(1)
            .setSampleMimeType("video/hevc")
            .setAverageBitrate(9200)
            .setLanguage("und")
            .setWidth(256)
            .setMaxInputSize(66)
            .setFrameRate(25)
            .setHeight(256)
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .build())
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode(
                            "0000000140010C01FFFF02200000030090000003000003003C9598090000000142010102200000030090000003000003003CA008080404D96566924CAE69C20000030002000003003210000000014401C172B46240")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h265_hdr10"));
  }

  @Test
  public void createVideoSampleEntryBox_forH264_matchesExpected() throws Exception {
    Format format =
        new Format.Builder()
            .setId(1)
            .setSampleMimeType("video/avc")
            .setLanguage("und")
            .setWidth(10)
            .setMaxInputSize(39)
            .setFrameRate(25)
            .setHeight(12)
            .setInitializationData(ImmutableList.of(FAKE_CSD_0, FAKE_CSD_1))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h264"));
  }

  @Test
  public void createVideoSampleEntryBox_forAv1_matchesExpected() throws IOException {
    Format format =
        new Format.Builder()
            .setId(1)
            .setSampleMimeType("video/av01")
            .setLanguage("und")
            .setWidth(10)
            .setMaxInputSize(49)
            .setFrameRate(25)
            .setHeight(12)
            .setInitializationData(
                ImmutableList.of(BaseEncoding.base16().decode("812000000A09200000019CDBFFF304")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_av1"));
  }

  @Test
  public void createVideoSampleEntryBox_withUnknownVideoFormat_throws() {
    // The video format contains an unknown MIME type.
    Format format =
        new Format.Builder()
            .setId(1)
            .setSampleMimeType("video/someweirdvideoformat")
            .setWidth(48)
            .setLanguage("und")
            .setMaxInputSize(114)
            .setFrameRate(25)
            .setHeight(32)
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode(
                            "0000000140010C01FFFF0408000003009FC800000300001E959809000000014201010408000003009FC800000300001EC1882165959AE4CAE68080000003008000000C84000000014401C173D089")))
            .build();

    assertThrows(IllegalArgumentException.class, () -> Boxes.videoSampleEntry(format));
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_singleSampleAtZeroTimestamp_returnsSampleLengthEqualsZero() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L);

    List<Long> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            /* firstSamplePresentationTimeUs= */ 0L,
            VU_TIMEBASE,
            LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME);

    assertThat(durationsVu).containsExactly(0L);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_singleSampleAtNonZeroTimestamp_returnsSampleLengthEqualsZero() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(5_000L);

    List<Long> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            /* firstSamplePresentationTimeUs= */ 0L,
            VU_TIMEBASE,
            LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME);

    assertThat(durationsVu).containsExactly(0L);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_differentSampleDurations_lastFrameDurationShort_returnsLastSampleOfZeroDuration() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 30_000L, 80_000L);

    List<Long> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            /* firstSamplePresentationTimeUs= */ 0L,
            VU_TIMEBASE,
            LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME);

    assertThat(durationsVu).containsExactly(3_000L, 5_000L, 0L);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_differentSampleDurations_lastFrameDurationDuplicate_returnsLastSampleOfDuplicateDuration() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 30_000L, 80_000L);

    List<Long> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            /* firstSamplePresentationTimeUs= */ 0L,
            VU_TIMEBASE,
            LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION);

    assertThat(durationsVu).containsExactly(3_000L, 5_000L, 5_000L);
  }

  @Test
  public void createSttsBox_withSingleSampleDuration_matchesExpected() throws IOException {
    ImmutableList<Long> sampleDurations = ImmutableList.of(500L);

    ByteBuffer sttsBox = Boxes.stts(sampleDurations);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(sttsBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("stts_box_single_sample_duration"));
  }

  @Test
  public void createSttsBox_withAllDifferentSampleDurations_matchesExpected() throws IOException {
    ImmutableList<Long> sampleDurations = ImmutableList.of(1_000L, 2_000L, 3_000L, 5_000L);

    ByteBuffer sttsBox = Boxes.stts(sampleDurations);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(sttsBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("stts_box_all_different_sample_durations"));
  }

  @Test
  public void createSttsBox_withFewConsecutiveSameSampleDurations_matchesExpected()
      throws IOException {
    ImmutableList<Long> sampleDurations = ImmutableList.of(1_000L, 2_000L, 2_000L, 2_000L);

    ByteBuffer sttsBox = Boxes.stts(sampleDurations);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(sttsBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("stts_box_few_same_sample_durations"));
  }

  @Test
  public void createStszBox_matchesExpected() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSampleSizes(100, 200, 150, 200);

    ByteBuffer stszBox = Boxes.stsz(sampleBufferInfos);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stszBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stsz_box"));
  }

  @Test
  public void createStscBox_matchesExpected() throws IOException {
    ImmutableList<Integer> chunkSampleCounts = ImmutableList.of(100, 500, 200, 100);

    ByteBuffer stscBox = Boxes.stsc(chunkSampleCounts);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stscBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stsc_box"));
  }

  @Test
  public void createStcoBox_matchesExpected() throws IOException {
    ImmutableList<Long> chunkOffsets = ImmutableList.of(1_000L, 5_000L, 7_000L, 10_000L);

    ByteBuffer stcoBox = Boxes.stco(chunkOffsets);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stcoBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stco_box"));
  }

  @Test
  public void createCo64Box_matchesExpected() throws IOException {
    ImmutableList<Long> chunkOffsets = ImmutableList.of(1_000L, 5_000L, 7_000L, 10_000L);

    ByteBuffer co64Box = Boxes.co64(chunkOffsets);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(co64Box);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("co64_box"));
  }

  @Test
  public void createStssBox_matchesExpected() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos = createBufferInfoListWithSomeKeyFrames();

    ByteBuffer stssBox = Boxes.stss(sampleBufferInfos);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stssBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stss_box"));
  }

  @Test
  public void createFtypBox_matchesExpected() throws IOException {
    ByteBuffer ftypBox = Boxes.ftyp();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(ftypBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("ftyp_box"));
  }

  @Test
  public void createMfhdBox_matchesExpected() throws IOException {
    ByteBuffer mfhdBox = Boxes.mfhd(/* sequenceNumber= */ 5);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(mfhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("mfhd_box"));
  }

  @Test
  public void createTfhdBox_matchesExpected() throws IOException {
    ByteBuffer tfhdBox = Boxes.tfhd(/* trackId= */ 1, /* baseDataOffset= */ 1_000L);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(tfhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("tfhd_box"));
  }

  @Test
  public void createTrunBox_matchesExpected() throws IOException {
    int sampleCount = 5;
    List<SampleMetadata> samplesMetadata = new ArrayList<>(sampleCount);
    for (int i = 0; i < sampleCount; i++) {
      samplesMetadata.add(
          new SampleMetadata(
              /* durationsVu= */ 2_000L,
              /* size= */ 5_000,
              /* flags= */ i == 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0));
    }

    ByteBuffer trunBox = Boxes.trun(samplesMetadata, /* dataOffset= */ 1_000);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(trunBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("trun_box"));
  }

  @Test
  public void createTrexBox_matchesExpected() throws IOException {
    ByteBuffer trexBox = Boxes.trex(/* trackId= */ 2);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(trexBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("trex_box"));
  }

  private static List<MediaCodec.BufferInfo> createBufferInfoListWithSamplePresentationTimestamps(
      long... timestampsUs) {
    List<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
    for (long timestampUs : timestampsUs) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.presentationTimeUs = timestampUs;
      bufferInfoList.add(bufferInfo);
    }

    return bufferInfoList;
  }

  private static List<MediaCodec.BufferInfo> createBufferInfoListWithSampleSizes(int... sizes) {
    List<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
    for (int size : sizes) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.size = size;
      bufferInfoList.add(bufferInfo);
    }

    return bufferInfoList;
  }

  private static List<MediaCodec.BufferInfo> createBufferInfoListWithSomeKeyFrames() {
    List<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      if (i % 5 == 0) { // Make every 5th frame as key frame.
        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
      }
      bufferInfoList.add(bufferInfo);
    }

    return bufferInfoList;
  }
}
