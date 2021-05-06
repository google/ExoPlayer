/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.internal.DoNotInstrument;

/** Tests for {@link Rating} and its subclasses. */
@RunWith(AndroidJUnit4.class)
@DoNotInstrument
public class RatingTest {

  @Test
  public void unratedHeartRating() {
    HeartRating rating = new HeartRating();
    assertThat(rating.isRated()).isFalse();
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void ratedHeartRating() {
    boolean hasHeart = true;
    HeartRating rating = new HeartRating(hasHeart);
    assertThat(rating.isRated()).isTrue();
    assertThat(rating.isHeart()).isEqualTo(hasHeart);
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void unratedPercentageRating() {
    PercentageRating rating = new PercentageRating();
    assertThat(rating.isRated()).isFalse();
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void ratedPercentageRating() {
    float percentage = 20.5f;
    PercentageRating rating = new PercentageRating(percentage);
    assertThat(rating.isRated()).isTrue();
    assertThat(rating.getPercent()).isEqualTo(percentage);
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void unratedThumbRating() {
    ThumbRating rating = new ThumbRating();
    assertThat(rating.isRated()).isFalse();
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void ratedThumbRating() {
    boolean isThumbUp = true;
    ThumbRating rating = new ThumbRating(isThumbUp);
    assertThat(rating.isRated()).isTrue();
    assertThat(rating.isThumbsUp()).isEqualTo(isThumbUp);
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void unratedStarRating() {
    int maxStars = 5;
    StarRating rating = new StarRating(maxStars);
    assertThat(rating.isRated()).isFalse();
    assertThat(rating.getMaxStars()).isEqualTo(maxStars);
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  @Test
  public void ratedStarRating() {
    int maxStars = 5;
    float starRating = 3.1f;
    StarRating rating = new StarRating(maxStars, starRating);
    assertThat(rating.isRated()).isTrue();
    assertThat(rating.getMaxStars()).isEqualTo(maxStars);
    assertThat(rating.getStarRating()).isEqualTo(starRating);
    assertThat(roundTripViaBundle(rating)).isEqualTo(rating);
  }

  private static Rating roundTripViaBundle(Rating rating) {
    return Rating.CREATOR.fromBundle(rating.toBundle());
  }
}
