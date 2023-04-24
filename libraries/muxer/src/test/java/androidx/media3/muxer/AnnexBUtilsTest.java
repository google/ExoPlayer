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
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString(""));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buf);

    assertThat(components).isEmpty();
  }

  @Test
  public void findNalUnits_noNalUnit_returnsEmptyList() {
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("ABCDEFABC"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buf);

    assertThat(components).isEmpty();
  }

  @Test
  public void findNalUnits_singleNalUnit_returnsSingleElement() {
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buf);

    assertThat(components).hasSize(1);
    assertThat(components.get(0)).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEF")));
  }

  @Test
  public void findNalUnits_multipleNalUnits_allReturned() {
    ByteBuffer buf =
        ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF00000001DDCC00000001BBAA"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buf);

    assertThat(components).hasSize(3);
    assertThat(components.get(0)).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEF")));
    assertThat(components.get(1)).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("DDCC")));
    assertThat(components.get(2)).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("BBAA")));
  }

  @Test
  public void findNalUnits_partialStartCodes_ignored() {
    // The NAL unit has lots of zeros but no start code.
    ByteBuffer buf =
        ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF0000AB0000CDEF00000000AB"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buf);

    assertThat(components).hasSize(1);
    assertThat(components.get(0))
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEF0000AB0000CDEF00000000AB")));
  }

  @Test
  public void findNalUnits_startCodeWithManyZeros_stillSplits() {
    // The NAL unit has a start code that starts with more than 3 zeros (although too many zeros
    // aren't allowed).
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB"));

    ImmutableList<ByteBuffer> components = AnnexBUtils.findNalUnits(buf);

    assertThat(components).hasSize(2);
    assertThat(components.get(0)).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEF0000")));
    assertThat(components.get(1)).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("AB")));
  }

  @Test
  public void stripEmulationPrevention_noEmulationPreventionBytes_copiesInput() {
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buf);

    assertThat(output)
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB")));
  }

  @Test
  public void stripEmulationPrevention_emulationPreventionPresent_bytesStripped() {
    // The NAL unit has a 00 00 03 * sequence.
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF00000300000001AB"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buf);

    assertThat(output)
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("00000001ABCDEF000000000001AB")));
  }

  @Test
  public void stripEmulationPrevention_03WithoutEnoughZeros_notStripped() {
    // The NAL unit has a 03 byte around, but not preceded by enough zeros.
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("ABCDEFABCD0003EFABCD03ABCD"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buf);

    assertThat(output)
        .isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEFABCD0003EFABCD03ABCD")));
  }

  @Test
  public void stripEmulationPrevention_03AtEnd_stripped() {
    // The NAL unit has a 03 byte at the very end of the input.
    ByteBuffer buf = ByteBuffer.wrap(getBytesFromHexString("ABCDEF000003"));

    ByteBuffer output = AnnexBUtils.stripEmulationPrevention(buf);

    assertThat(output).isEqualTo(ByteBuffer.wrap(getBytesFromHexString("ABCDEF0000")));
  }
}
