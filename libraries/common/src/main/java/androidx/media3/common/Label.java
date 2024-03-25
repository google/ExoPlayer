/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** A label for a {@link Format}. */
@UnstableApi
public class Label {
  /**
   * The language of this label, as an IETF BCP 47 conformant tag, or null if unknown or not
   * applicable.
   */
  @Nullable public final String language;

  /** The value for this label. */
  public final String value;

  /**
   * Creates a label.
   *
   * @param language The language of this label, as an IETF BCP 47 conformant tag, or null if
   *     unknown or not applicable.
   * @param value The label value.
   */
  public Label(@Nullable String language, String value) {
    this.language = Util.normalizeLanguageCode(language);
    this.value = value;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Label label = (Label) o;
    return Util.areEqual(language, label.language) && Util.areEqual(value, label.value);
  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + (language != null ? language.hashCode() : 0);
    return result;
  }

  private static final String FIELD_LANGUAGE_INDEX = Util.intToStringMaxRadix(0);
  private static final String FIELD_VALUE_INDEX = Util.intToStringMaxRadix(1);

  /** Serializes this instance to a {@link Bundle}. */
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (language != null) {
      bundle.putString(FIELD_LANGUAGE_INDEX, language);
    }
    bundle.putString(FIELD_VALUE_INDEX, value);
    return bundle;
  }

  /** Deserializes an instance from a {@link Bundle} produced by {@link #toBundle()}. */
  public static Label fromBundle(Bundle bundle) {
    return new Label(
        bundle.getString(FIELD_LANGUAGE_INDEX), checkNotNull(bundle.getString(FIELD_VALUE_INDEX)));
  }
}
