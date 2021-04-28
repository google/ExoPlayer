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

/** A class for rating with a single degree of rating, "thumb up" vs "thumb down". */
public final class ThumbRating extends Rating {

  @RatingType private static final int TYPE = RATING_TYPE_THUMB;

  private final boolean isRated;

  /** Whether the rating has a thumb up or thumb down rating. */
  public final boolean thumbUp;

  /** Creates a unrated ThumbRating instance. */
  public ThumbRating() {
    isRated = false;
    thumbUp = false;
  }

  /**
   * Creates a ThumbRating instance.
   *
   * @param thumbIsUp true for a "thumb up" rating, false for "thumb down".
   */
  public ThumbRating(boolean thumbIsUp) {
    isRated = true;
    thumbUp = thumbIsUp;
  }

  @Override
  public boolean isRated() {
    return isRated;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(isRated, thumbUp);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof ThumbRating)) {
      return false;
    }
    ThumbRating other = (ThumbRating) obj;
    return thumbUp == other.thumbUp && isRated == other.isRated;
  }

  @Override
  public String toString() {
    return "ThumbRating: " + (isRated ? "isThumbUp=" + thumbUp : "unrated");
  }

  // Bundleable implementation.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FIELD_RATING_TYPE, FIELD_IS_RATED, FIELD_IS_THUMB_UP})
  private @interface FieldNumber {}

  private static final int FIELD_IS_RATED = 1;
  private static final int FIELD_IS_THUMB_UP = 2;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(keyForField(FIELD_RATING_TYPE), TYPE);
    bundle.putBoolean(keyForField(FIELD_IS_RATED), isRated);
    bundle.putBoolean(keyForField(FIELD_IS_THUMB_UP), thumbUp);
    return bundle;
  }

  public static final Creator<ThumbRating> CREATOR = ThumbRating::fromBundle;

  private static ThumbRating fromBundle(Bundle bundle) {
    checkArgument(
        bundle.getInt(keyForField(FIELD_RATING_TYPE), /* defaultValue= */ RATING_TYPE_DEFAULT)
            == TYPE);
    boolean isRated = bundle.getBoolean(keyForField(FIELD_IS_RATED), /* defaultValue= */ false);
    return isRated
        ? new ThumbRating(
            bundle.getBoolean(keyForField(FIELD_IS_THUMB_UP), /* defaultValue= */ false))
        : new ThumbRating();
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
