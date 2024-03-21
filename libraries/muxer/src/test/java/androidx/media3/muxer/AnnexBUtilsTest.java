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

import static androidx.media3.common.util.Util.getBytesFromHexString;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AnnexBUtils}. */
@RunWith(AndroidJUnit4.class)
public class AnnexBUtilsTest {
  @Test
  public void findNalUnits_emptyBuffer_returnsEmptyList() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString(""));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components).isEmpty();
  }

  @Test
  public void findNalUnits_noNalUnit_throws() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("ABCDEFABC"));

    assertThrows(IllegalStateException.class, () -> AnnexBUtils.findNalUnits(buffer));
  }

  @Test
  public void findNalUnits_singleNalUnitWithFourByteStartCode_returnsSingleElement() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components).containsExactly(ByteBuffer.wrap(getBytesFromHexString("ABCDEF")));
  }

  @Test
  public void findNalUnits_singleNalUnitWithThreeByteStartCode_returnsSingleElement() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("000001ABCDEF"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components).containsExactly(ByteBuffer.wrap(getBytesFromHexString("ABCDEF")));
  }

  @Test
  public void findNalUnits_multipleNalUnitsWithFourByteStartCode_allReturned() {
    ByteBuffer buffer =
        ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF00000001DDCC00000001BBAA"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components)
        .containsExactly(
            ByteBuffer.wrap(getBytesFromHexString("ABCDEF")),
            ByteBuffer.wrap(getBytesFromHexString("DDCC")),
            ByteBuffer.wrap(getBytesFromHexString("BBAA")))
        .inOrder();
  }

  @Test
  public void findNalUnits_multipleNalUnitsWithThreeByteStartCode_allReturned() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("000001ABCDEF000001DDCC000001BBAA"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components)
        .containsExactly(
            ByteBuffer.wrap(getBytesFromHexString("ABCDEF")),
            ByteBuffer.wrap(getBytesFromHexString("DDCC")),
            ByteBuffer.wrap(getBytesFromHexString("BBAA")))
        .inOrder();
  }

  @Test
  public void findNalUnits_withTrainingZeroesFollowedByGarbageData_throws() {
    // The NAL unit has lots of zeros but no start code.
    ByteBuffer buffer =
        ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF0000AB0000CDEF00000000AB"));

    assertThrows(IllegalStateException.class, () -> AnnexBUtils.findNalUnits(buffer));
  }

  @Test
  public void findNalUnits_withTrailingZeroes_stripsTrailingZeroes() {
    // The first NAL unit has some training zeroes.
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components)
        .containsExactly(
            ByteBuffer.wrap(getBytesFromHexString("ABCDEF")),
            ByteBuffer.wrap(getBytesFromHexString("AB")))
        .inOrder();
  }

  @Test
  public void findNalUnits_withBothThreeBytesAndFourBytesNalStartCode_returnsAllNalUnits() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000001AB000001CDEF"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buffer);

    assertThat(components)
        .containsExactly(
            ByteBuffer.wrap(getBytesFromHexString("ABCDEF")),
            ByteBuffer.wrap(getBytesFromHexString("AB")),
            ByteBuffer.wrap(getBytesFromHexString("CDEF")))
        .inOrder();
  }

  @Test
  public void stripEmulationPrevention_noEmulationPreventionBytes_copiesInput() {
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buffer);

    assertThat(output)
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB")));
  }

  @Test
  public void stripEmulationPrevention_emulationPreventionPresent_bytesStripped() {
    // The NAL unit has a 00 00 03 * sequence.
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF00000300000001AB"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buffer);

    assertThat(output)
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB")));
  }

  @Test
  public void stripEmulationPrevention_03WithoutEnoughZeros_notStripped() {
    // The NAL unit has a 03 byte around, but not preceded by enough zeros.
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("ABCDEFABCD0003EFABCD03ABCD"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buffer);

    assertThat(output)
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEFABCD0003EFABCD03ABCD")));
  }

  @Test
  public void stripEmulationPrevention_03AtEnd_stripped() {
    // The NAL unit has a 03 byte at the very end of the input.
    ByteBuffer buffer = ByteBuffer.wrap(getBytesFromHexString("ABCDEF000003"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buffer);

    assertThat(output).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEF0000")));
  }
}
