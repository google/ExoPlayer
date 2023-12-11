package com.google.android.exoplayer2.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FormatHolderTest {

  private FormatHolder subject;
  private FakeTrackOutput output;

  @Before
  public void setup() {
    output = new FakeTrackOutput(true);
    subject = new FormatHolder(output);
  }

  @Test
  public void testThrowsIfFormatIsNotHeld() {
    assertThrows(IllegalStateException.class, subject::getFormat);
  }

  @Test
  public void testHasFormatBehavesAsExpected() {
    assertFalse(subject.hasFormat());
    Format format = new Format.Builder().build();

    subject.update(format);

    assertTrue(subject.hasFormat());
    assertSame(format, subject.getFormat());
    assertSame(format, output.lastFormat);
  }

  @Test
  public void testSubjectIgnoresIdenticalFormats() {
    Format firstFormat = new Format.Builder().build();
    Format nextFormat = new Format.Builder().build();

    subject.update(firstFormat);
    subject.update(nextFormat);

    assertTrue(subject.hasFormat());
    assertSame(firstFormat, subject.getFormat());
    assertSame(firstFormat, output.lastFormat);
  }

  @Test
  public void testSubjectAcceptsDifferentFormats() {
    Format firstFormat = new Format.Builder().build();
    Format nextFormat = new Format.Builder().setRotationDegrees(90).build();

    subject.update(firstFormat);
    subject.update(nextFormat);

    assertTrue(subject.hasFormat());
    assertSame(nextFormat, subject.getFormat());
    assertSame(nextFormat, output.lastFormat);
  }

  @Test
  public void testSubjectAcceptsDisplayOrientationData() {
    Format firstFormat = new Format.Builder().setRotationDegrees(0).build();

    subject.update(firstFormat);
    subject.update(new DisplayOrientationSeiReader.DisplayOrientationData(false,
        false,
        90,
        1,
        3));

    assertTrue(subject.hasFormat());
    assertSame(subject.getFormat(), output.lastFormat);
    assertEquals(270, subject.getFormat().rotationDegrees);
  }

  @Test
  public void testThrowsIfDisplayOrientationIsUpdatedWithNoFormat() {
    assertThrows(IllegalStateException.class,
        () -> subject.update(new DisplayOrientationSeiReader.DisplayOrientationData(false,
            false,
            90,
            1,
            3)));
  }
}