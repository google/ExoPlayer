/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.text;

import static androidx.media3.common.util.Assertions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import com.google.common.collect.Lists;

/* package */ class CuesListTestUtil {

  private CuesListTestUtil() {}

  public static void assertNoCuesBetween(
      CuesResolver cuesResolver, long startTimeUs, long endTimeUs) {
    assertCueTextBetween(cuesResolver, startTimeUs, endTimeUs);
  }

  public static void assertCueTextBetween(
      CuesResolver cuesResolver, long startTimeUs, long endTimeUs, String... expectedCueTexts) {
    checkArgument(startTimeUs != C.TIME_UNSET);
    checkArgument(endTimeUs != C.TIME_END_OF_SOURCE);

    assertThat(Lists.transform(cuesResolver.getCuesAtTimeUs(startTimeUs), c -> c.text))
        .containsExactlyElementsIn(expectedCueTexts)
        .inOrder();
    assertThat(Lists.transform(cuesResolver.getCuesAtTimeUs(startTimeUs + 1), c -> c.text))
        .containsExactlyElementsIn(expectedCueTexts)
        .inOrder();
    assertThat(Lists.transform(cuesResolver.getCuesAtTimeUs(endTimeUs - 1), c -> c.text))
        .containsExactlyElementsIn(expectedCueTexts)
        .inOrder();

    assertThat(cuesResolver.getPreviousCueChangeTimeUs(startTimeUs)).isEqualTo(startTimeUs);
    assertThat(cuesResolver.getPreviousCueChangeTimeUs(endTimeUs - 1)).isEqualTo(startTimeUs);

    assertThat(cuesResolver.getNextCueChangeTimeUs(startTimeUs)).isEqualTo(endTimeUs);
  }

  public static void assertCueTextUntilEnd(
      CuesResolver cuesResolver, long startTimeUs, String... expectedCueTexts) {
    assertThat(Lists.transform(cuesResolver.getCuesAtTimeUs(startTimeUs), c -> c.text))
        .containsExactlyElementsIn(expectedCueTexts)
        .inOrder();
    assertThat(Lists.transform(cuesResolver.getCuesAtTimeUs(startTimeUs + 1), c -> c.text))
        .containsExactlyElementsIn(expectedCueTexts)
        .inOrder();
    assertThat(cuesResolver.getPreviousCueChangeTimeUs(startTimeUs)).isEqualTo(startTimeUs);
    assertThat(cuesResolver.getNextCueChangeTimeUs(startTimeUs)).isEqualTo(C.TIME_END_OF_SOURCE);
  }

  public static void assertCuesStartAt(CuesResolver cuesResolver, long timeUs) {
    assertThat(cuesResolver.getCuesAtTimeUs(timeUs - 1)).isEmpty();
    assertThat(cuesResolver.getPreviousCueChangeTimeUs(timeUs - 1)).isEqualTo(C.TIME_UNSET);
    assertThat(cuesResolver.getNextCueChangeTimeUs(timeUs - 1)).isEqualTo(timeUs);
  }

  public static void assertCuesEndAt(CuesResolver cuesResolver, long timeUs) {
    assertThat(cuesResolver.getCuesAtTimeUs(timeUs)).isEmpty();
    assertThat(cuesResolver.getPreviousCueChangeTimeUs(timeUs)).isEqualTo(timeUs);
    assertThat(cuesResolver.getNextCueChangeTimeUs(timeUs)).isEqualTo(C.TIME_END_OF_SOURCE);
  }
}
