package com.google.android.exoplayer2.extractor.ts;

import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.testutil.Dumper;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class TsExtractorHlsTest {

  @Test
  public void consumeInitThenSingleIframe() throws Exception {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(123), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_iframe_init.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_iframe.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        input.setPosition((int) seekPositionHolder.position);
      }
    }

    Dumper dumper = new Dumper();
    output.dump(dumper);
    System.out.print(dumper.toString());
  }

  @Test
  public void consumeIframeWithInit() throws Exception {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    final int firstSampleTimestampUs = 123;
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(firstSampleTimestampUs), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_iframe_withinit_one_frame.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }
//
//    Dumper dumper = new Dumper();
//    output.dump(dumper);
//    System.out.print(dumper.toString());

    assertThat(output.numberOfTracks).isEqualTo(2);
    FakeTrackOutput trackOutput = output.trackOutputs.get(27);
    assertThat(trackOutput).isNotNull();

    Format actualFormat = trackOutput.lastFormat;
    Format expectedFormat = new Format.Builder()
        .setId("1/27")
        .setSampleMimeType("video/avc")
        .setCodecs("avc1.64001F")
        .setWidth(1280)
        .setHeight(720)
        .setInitializationData(actualFormat.initializationData)   // Ignore this.
        .build();
    assertThat(actualFormat).isEqualTo(expectedFormat);

    // One sample, with the Access Unit containing the IDR and associated NALU is present
    trackOutput.assertSampleCount(1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(firstSampleTimestampUs);
    byte[] sampleData = trackOutput.getSampleData(0);
    assertThat(sampleData.length).isEqualTo(245324);
    assertThat(Arrays.hashCode(sampleData)).isEqualTo(0x569E5F74);
  }
}
