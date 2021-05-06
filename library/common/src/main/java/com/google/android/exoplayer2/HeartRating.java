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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A rating expressed as "heart" or "no heart". It can be used to indicate whether the content is a
 * favorite.
 */
public final class HeartRating extends Rating {

  private final boolean rated;
  private final boolean isHeart;

  /** Creates a unrated instance. */
  public HeartRating() {
    rated = false;
    isHeart = false;
  }

  /**
   * Creates a rated instance.
   *
   * @param isHeart {@code true} for "heart", {@code false} for "no heart".
   */
  public HeartRating(boolean isHeart) {
    rated = true;
    this.isHeart = isHeart;
  }

  @Override
  public boolean isRated() {
    return rated;
  }

  /** Returns whether the rating is "heart". */
  public boolean isHeart() {
    return isHeart;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rated, isHeart);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof HeartRating)) {
      return false;
    }
    HeartRating other = (HeartRating) obj;
    return isHeart == other.isHeart && rated == other.rated;
  }

  // Bundleable implementation.

  @RatingType private static final int TYPE = RATING_TYPE_HEART;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FIELD_RATING_TYPE, FIELD_RATED, FIELD_IS_HEART})
  private @interface FieldNumber {}

  private static final int FIELD_RATED = 1;
  private static final int FIELD_IS_HEART = 2;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(keyForField(FIELD_RATING_TYPE), TYPE);
    bundle.putBoolean(keyForField(FIELD_RATED), rated);
    bundle.putBoolean(keyForField(FIELD_IS_HEART), isHeart);
    return bundle;
  }

  /** Object that can restore a {@link HeartRating} from a {@link Bundle}. */
  public static final Creator<HeartRating> CREATOR = HeartRating::fromBundle;

  private static HeartRating fromBundle(Bundle bundle) {
    checkArgument(
        bundle.getInt(keyForField(FIELD_RATING_TYPE), /* defaultValue= */ RATING_TYPE_DEFAULT)
            == TYPE);
    boolean isRated = bundle.getBoolean(keyForField(FIELD_RATED), /* defaultValue= */ false);
    return isRated
        ? new HeartRating(bundle.getBoolean(keyForField(FIELD_IS_HEART), /* defaultValue= */ false))
        : new HeartRating();
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
