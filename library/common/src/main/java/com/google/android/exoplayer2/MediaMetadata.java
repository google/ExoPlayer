/*
 * Copyright 2020 The Android Open Source Project
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

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Metadata of the {@link MediaItem}. */
public final class MediaMetadata implements Bundleable {

  /** A builder for {@link MediaMetadata} instances. */
  public static final class Builder {

    @Nullable private String title;

    /** Sets the optional title. */
    public Builder setTitle(@Nullable String title) {
      this.title = title;
      return this;
    }

    /** Returns a new {@link MediaMetadata} instance with the current builder values. */
    public MediaMetadata build() {
      return new MediaMetadata(title);
    }
  }

  /** Optional title. */
  @Nullable public final String title;

  private MediaMetadata(@Nullable String title) {
    this.title = title;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MediaMetadata other = (MediaMetadata) obj;

    return Util.areEqual(title, other.title);
  }

  @Override
  public int hashCode() {
    return title == null ? 0 : title.hashCode();
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FIELD_TITLE})
  private @interface FieldNumber {}

  private static final int FIELD_TITLE = 0;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(keyForField(FIELD_TITLE), title);
    return bundle;
  }

  /** Object that can restore {@link MediaMetadata} from a {@link Bundle}. */
  public static final Creator<MediaMetadata> CREATOR =
      bundle -> new MediaMetadata(bundle.getString(keyForField(FIELD_TITLE)));

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
