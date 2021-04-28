package com.google.android.exoplayer2.extractor;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DisplayOrientationSeiReaderTest {

  private static final byte[] ROTATION_90 = {6, 47, 3, 8, 0, 9, -128};
  private static final byte[] ROTATION_180 = {6, 47, 3, 16, 0, 9, -128};
  private static final byte[] ROTATION_270 = {6, 47, 3, 24, 0, 9, -128};
  private static final byte[] ROTATION_360 = {6, 47, 3, 0, 0, 9, -128};

  private static final byte[] ROTATION_0_HOR_FLIP = {6, 47, 3, 64, 0, 9, -128};
  private static final byte[] ROTATION_0_VER_FLIP = {6, 47, 3, 32, 0, 9, -128};
  private static final byte[] ROTATION_0_HOR_VER_FLIP = {6, 47, 3, 96, 0, 9, -128};

  private DisplayOrientationSeiReader subject;

  @Before
  public void setup() {
    subject = new DisplayOrientationSeiReader();
  }

  @Test
  public void testDisplayOrientationParsing() {
    assertDisplayOrientationData(ROTATION_90, 90, false, false);
    assertDisplayOrientationData(ROTATION_180, 180, false, false);
    assertDisplayOrientationData(ROTATION_270, 270, false, false);
    assertDisplayOrientationData(ROTATION_360, 0, false, false);
    assertDisplayOrientationData(ROTATION_0_HOR_FLIP, 0, true, false);
    assertDisplayOrientationData(ROTATION_0_VER_FLIP, 0, false, true);
    assertDisplayOrientationData(ROTATION_0_HOR_VER_FLIP, 0, true, true);
  }

  @Test
  public void testDisplayOrientationParsingDoesNotAdvanceBitStream() {
    Stream.of(
        ROTATION_90,
        ROTATION_180,
        ROTATION_270,
        ROTATION_360,
        ROTATION_0_HOR_FLIP,
        ROTATION_0_VER_FLIP,
        ROTATION_0_HOR_VER_FLIP
    ).map(ParsableByteArray::new)
        .forEach((data) -> {
          subject.read(data);
          assertEquals(0, data.getPosition());
        });
  }

  private void assertDisplayOrientationData(
      byte[] payload,
      int anticlockwiseRotation,
      boolean horizontalFlip,
      boolean verticalFlip
  ) {
    ParsableByteArray data = new ParsableByteArray(payload);
    DisplayOrientationSeiReader.DisplayOrientationData orientationData = subject.read(data);

    assertEquals(anticlockwiseRotation, orientationData.anticlockwiseRotation);
    assertEquals(horizontalFlip, orientationData.horizontalFlip);
    assertEquals(verticalFlip, orientationData.verticalFlip);
    assertEquals(1, orientationData.rotationPeriod);
    assertEquals(3, orientationData.payloadSize);
  }
}